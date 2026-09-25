package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.model.CtaVideoItem
import com.example.data.model.MovementEffect
import com.example.data.model.Project
import com.example.data.model.ProjectImage
import com.example.data.model.TransitionEffect
import com.example.data.model.TransitionSoundEffect
import com.example.data.model.VideoAspectRatio
import com.example.data.model.VideoBitratePreset
import com.example.data.model.VideoFps
import com.example.data.model.VideoResolution
import com.example.data.repository.ProjectRepository
import com.example.engine.CtaImportResult
import com.example.engine.CtaVideoEngine
import com.example.engine.ImageImportHelper
import com.example.engine.ImageImportResult
import com.example.engine.MediaHelper
import com.example.engine.RandomPromptResult
import com.example.engine.RenderingManager
import com.example.engine.RenderingState
import com.example.engine.SyntaxParseResult
import com.example.engine.SyntaxParser
import com.example.engine.TransitionSoundEngine
import com.example.engine.TransitionSoundValidationResult
import com.example.engine.TransitionValidationResult
import com.example.engine.ZipExtractResult
import com.example.engine.ZipExtractor
import com.example.service.VideoRenderingService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

enum class ResourcePanelTab {
    CAMERA_ANIMATION,
    TRANSITION_EFFECTS,
    TRANSITION_SOUNDS,
    CTA_VIDEOS
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository = ProjectRepository(
        AppDatabase.getInstance(application).projectDao(),
        application
    )

    private val appPreferences: AppPreferences = AppPreferences.getInstance(application)

    private var currentProjectId: Long = -1L

    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()

    private val _images = MutableStateFlow<List<ProjectImage>>(emptyList())
    val images: StateFlow<List<ProjectImage>> = _images.asStateFlow()

    // Navegação de imagem para o palco
    private val _currentImageIndex = MutableStateFlow(0)
    val currentImageIndex: StateFlow<Int> = _currentImageIndex.asStateFlow()

    private val _selectedMovement = MutableStateFlow(MovementEffect.ALL_EFFECTS[1]) // Pan Left default
    val selectedMovement: StateFlow<MovementEffect> = _selectedMovement.asStateFlow()

    // Configurações de Saída do Vídeo
    private val _selectedResolution = MutableStateFlow(VideoResolution.DEFAULT)
    val selectedResolution: StateFlow<VideoResolution> = _selectedResolution.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(VideoAspectRatio.RATIO_16_9)
    val selectedAspectRatio: StateFlow<VideoAspectRatio> = _selectedAspectRatio.asStateFlow()

    private val _selectedFps = MutableStateFlow(VideoFps.DEFAULT.fps)
    val selectedFps: StateFlow<Int> = _selectedFps.asStateFlow()

    private val _selectedBitrateMbps = MutableStateFlow(VideoBitratePreset.DEFAULT.mbps)
    val selectedBitrateMbps: StateFlow<Float> = _selectedBitrateMbps.asStateFlow()

    // Transições Suaves (20 transições + 0 Sem Transição)
    private val _selectedTransition = MutableStateFlow(TransitionEffect.DEFAULT)
    val selectedTransition: StateFlow<TransitionEffect> = _selectedTransition.asStateFlow()

    // Duração global selecionada para todas as transições (0.4s a 6.0s, padrão básico normal = 1.0s)
    private val _transitionDurationSeconds = MutableStateFlow(1.0f)
    val transitionDurationSeconds: StateFlow<Float> = _transitionDurationSeconds.asStateFlow()

    // Aba de recursos ativa na barra horizontal (null = exibe os 3 botões principais)
    private val _activeResourcePanel = MutableStateFlow<ResourcePanelTab?>(null)
    val activeResourcePanel: StateFlow<ResourcePanelTab?> = _activeResourcePanel.asStateFlow()

    private val _transitionIdsText = MutableStateFlow("1")
    val transitionIdsText: StateFlow<String> = _transitionIdsText.asStateFlow()

    private val _transitionError = MutableStateFlow<String?>(null)
    val transitionError: StateFlow<String?> = _transitionError.asStateFlow()

    // Sons de Transições Rápidas (17 sons profissionais + custom + 0 Sem Som)
    private val _allAvailableSounds = MutableStateFlow<List<TransitionSoundEffect>>(TransitionSoundEffect.BUILT_IN_SOUNDS)
    val allAvailableSounds: StateFlow<List<TransitionSoundEffect>> = _allAvailableSounds.asStateFlow()

    private val _selectedSound = MutableStateFlow(TransitionSoundEffect.DEFAULT)
    val selectedSound: StateFlow<TransitionSoundEffect> = _selectedSound.asStateFlow()

    private val _transitionSoundIdsText = MutableStateFlow("1, 2, 3, 5")
    val transitionSoundIdsText: StateFlow<String> = _transitionSoundIdsText.asStateFlow()

    private val _transitionSoundError = MutableStateFlow<String?>(null)
    val transitionSoundError: StateFlow<String?> = _transitionSoundError.asStateFlow()

    private val _isPreviewingTransition = MutableStateFlow(false)
    val isPreviewingTransition: StateFlow<Boolean> = _isPreviewingTransition.asStateFlow()

    private val _syntaxText = MutableStateFlow("")
    val syntaxText: StateFlow<String> = _syntaxText.asStateFlow()

    private val _syntaxError = MutableStateFlow<String?>(null)
    val syntaxError: StateFlow<String?> = _syntaxError.asStateFlow()

    private val _isSyntaxModalOpen = MutableStateFlow(false)
    val isSyntaxModalOpen: StateFlow<Boolean> = _isSyntaxModalOpen.asStateFlow()

    private val _isProgressModalOpen = MutableStateFlow(false)
    val isProgressModalOpen: StateFlow<Boolean> = _isProgressModalOpen.asStateFlow()

    private val _isMasterConfigOpen = MutableStateFlow(false)
    val isMasterConfigOpen: StateFlow<Boolean> = _isMasterConfigOpen.asStateFlow()

    private val _isMediaGalleryOpen = MutableStateFlow(false)
    val isMediaGalleryOpen: StateFlow<Boolean> = _isMediaGalleryOpen.asStateFlow()

    private val _isLogoGalleryOpen = MutableStateFlow(false)
    val isLogoGalleryOpen: StateFlow<Boolean> = _isLogoGalleryOpen.asStateFlow()

    private val _isZipBrowserOpen = MutableStateFlow(false)
    val isZipBrowserOpen: StateFlow<Boolean> = _isZipBrowserOpen.asStateFlow()

    // Vídeos de CTA (10 opções iniciais: Sem CTA + CTA1.WEBM..CTA9.WEBM na pasta VIDEOS CTA + uploads com Chroma Key)
    val allCtaItems: StateFlow<List<CtaVideoItem>> = CtaVideoEngine.allCtaItems

    private val _selectedCta = MutableStateFlow(CtaVideoItem.NONE)
    val selectedCta: StateFlow<CtaVideoItem> = _selectedCta.asStateFlow()

    private val _ctaStartTimeText = MutableStateFlow("")
    val ctaStartTimeText: StateFlow<String> = _ctaStartTimeText.asStateFlow()

    private val _ctaTimeErrorMessage = MutableStateFlow<String?>(null)
    val ctaTimeErrorMessage: StateFlow<String?> = _ctaTimeErrorMessage.asStateFlow()

    private val _ctaNormalizedX = MutableStateFlow(0.50f)
    val ctaNormalizedX: StateFlow<Float> = _ctaNormalizedX.asStateFlow()

    private val _ctaNormalizedY = MutableStateFlow(0.78f)
    val ctaNormalizedY: StateFlow<Float> = _ctaNormalizedY.asStateFlow()

    private val _ctaScale = MutableStateFlow(0.36f)
    val ctaScale: StateFlow<Float> = _ctaScale.asStateFlow()

    private val _isCtaSelectedOnPreview = MutableStateFlow(false)
    val isCtaSelectedOnPreview: StateFlow<Boolean> = _isCtaSelectedOnPreview.asStateFlow()

    // Logo de Imagem sobreposto na tela de pré-visualização e no vídeo final do início ao fim
    private val _logoFilePath = MutableStateFlow<String?>(null)
    val logoFilePath: StateFlow<String?> = _logoFilePath.asStateFlow()

    private val _logoNormalizedX = MutableStateFlow(0.84f)
    val logoNormalizedX: StateFlow<Float> = _logoNormalizedX.asStateFlow()

    private val _logoNormalizedY = MutableStateFlow(0.16f)
    val logoNormalizedY: StateFlow<Float> = _logoNormalizedY.asStateFlow()

    private val _logoScale = MutableStateFlow(0.18f)
    val logoScale: StateFlow<Float> = _logoScale.asStateFlow()

    private val _isLogoSelectedOnPreview = MutableStateFlow(false)
    val isLogoSelectedOnPreview: StateFlow<Boolean> = _isLogoSelectedOnPreview.asStateFlow()

    private val _zipWarningMessage = MutableStateFlow<String?>(null)
    val zipWarningMessage: StateFlow<String?> = _zipWarningMessage.asStateFlow()

    private val _validationBannerMessage = MutableStateFlow<String?>(null)
    val validationBannerMessage: StateFlow<String?> = _validationBannerMessage.asStateFlow()

    private var errorDismissJob: Job? = null
    private var zipWarningJob: Job? = null

    private val _isTestPlaying = MutableStateFlow(true)
    val isTestPlaying: StateFlow<Boolean> = _isTestPlaying.asStateFlow()

    private val _messageEvents = MutableSharedFlow<String>()
    val messageEvents: SharedFlow<String> = _messageEvents.asSharedFlow()

    val renderingState: StateFlow<RenderingState> = RenderingManager.state

    init {
        TransitionSoundEngine.init(application)
        CtaVideoEngine.init(application)
        viewModelScope.launch {
            TransitionSoundEngine.customSounds.collect { customList ->
                _allAvailableSounds.value = TransitionSoundEffect.BUILT_IN_SOUNDS + customList
            }
        }
    }

    fun initProject(projectId: Long) {
        if (currentProjectId == projectId) return
        currentProjectId = projectId

        viewModelScope.launch {
            repository.getProject(projectId).collect { proj ->
                _project.value = proj
                if (proj != null) {
                    if (_syntaxText.value.isEmpty()) {
                        _syntaxText.value = proj.syntaxConfig
                    }
                    // Garante que a pasta padrão criada pelo app ou pasta recente esteja selecionada automaticamente
                    if (proj.customOutputDirUri.isNullOrBlank()) {
                        val defaultOrRecent = appPreferences.getDefaultOutputDirUri()
                        repository.updateCustomOutputDir(projectId, defaultOrRecent)
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.getImages(projectId).collect { imgList ->
                _images.value = imgList
                // Ajusta o índice da imagem exibida se necessário
                if (_currentImageIndex.value >= imgList.size) {
                    _currentImageIndex.value = (imgList.size - 1).coerceAtLeast(0)
                }
                // Detecta a proporção da primeira imagem como padrão inicial
                if (imgList.isNotEmpty()) {
                    try {
                        val firstFile = File(imgList[0].filePath)
                        if (firstFile.exists()) {
                            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeFile(firstFile.absolutePath, opts)
                            if (opts.outWidth > 0 && opts.outHeight > 0) {
                                val detected = VideoAspectRatio.detectFromDimensions(opts.outWidth, opts.outHeight)
                                _selectedAspectRatio.value = detected
                            }
                        }
                    } catch (_: Exception) {}
                }
                // Se o texto de sintaxe estiver vazio, inicializa com template automático
                if (_syntaxText.value.isBlank() && imgList.isNotEmpty()) {
                    val defaultText = SyntaxParser.generateDefaultSyntax(
                        totalImages = imgList.size,
                        defaultMovementId = _selectedMovement.value.id,
                        videoMediaIndices = getVideoMediaIndices()
                    )
                    _syntaxText.value = defaultText
                    repository.updateProjectSyntax(projectId, defaultText)
                }
            }
        }
    }

    fun selectMovement(effect: MovementEffect) {
        _selectedMovement.value = effect
        _isPreviewingTransition.value = false
        _isTestPlaying.value = true
    }

    fun selectResolution(resolution: VideoResolution) {
        _selectedResolution.value = resolution
    }

    fun selectAspectRatio(aspectRatio: VideoAspectRatio) {
        _selectedAspectRatio.value = aspectRatio
    }

    fun selectFps(fps: Int) {
        _selectedFps.value = fps
    }

    fun selectBitratePreset(preset: VideoBitratePreset) {
        _selectedBitrateMbps.value = preset.mbps
    }

    fun setBitrateMbps(mbps: Float) {
        _selectedBitrateMbps.value = (Math.round(mbps * 10f) / 10f).coerceIn(0.5f, 20.0f)
    }

    fun selectTransition(transition: TransitionEffect) {
        _selectedTransition.value = transition
        _transitionIdsText.value = transition.id.toString()
        _transitionError.value = null
        _isPreviewingTransition.value = true
        _isTestPlaying.value = true
    }

    fun setTransitionDurationSeconds(durationSeconds: Float) {
        val clamped = (Math.round(durationSeconds * 10f) / 10f).coerceIn(0.4f, 6.0f)
        _transitionDurationSeconds.value = clamped
    }

    fun openResourcePanel(panel: ResourcePanelTab) {
        _activeResourcePanel.value = panel
    }

    fun closeResourcePanel(): Boolean {
        if (_activeResourcePanel.value == ResourcePanelTab.CTA_VIDEOS && _selectedCta.value.id != 0) {
            val parsedTime = parseCtaStartTimeSeconds(_ctaStartTimeText.value)
            if (parsedTime == null) {
                val errMsg = "Informe o tempo exato em que a CTA deve ser exibida no vídeo final antes de voltar!"
                _ctaTimeErrorMessage.value = errMsg
                showFiveSecondValidationError("Informe o tempo da CTA")
                return false
            }
        }
        _ctaTimeErrorMessage.value = null
        _activeResourcePanel.value = null
        return true
    }

    fun selectCta(cta: CtaVideoItem) {
        _selectedCta.value = cta
        if (cta.id == 0) {
            _ctaTimeErrorMessage.value = null
            _isCtaSelectedOnPreview.value = false
        } else {
            _isCtaSelectedOnPreview.value = true
            _isLogoSelectedOnPreview.value = false
            _isTestPlaying.value = true
        }
    }

    fun updateCtaStartTimeText(newText: String) {
        _ctaStartTimeText.value = newText
        _ctaTimeErrorMessage.value = null
    }

    fun parseCtaStartTimeSeconds(rawText: String): Float? {
        val cleaned = rawText.trim().lowercase().removeSuffix("s").removeSuffix("seg").trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.contains(":")) {
            val parts = cleaned.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].trim().toIntOrNull() ?: return null
                val seconds = parts[1].trim().replace(",", ".").toFloatOrNull() ?: return null
                if (minutes < 0 || seconds < 0f) return null
                return minutes * 60f + seconds
            }
            return null
        }
        val value = cleaned.replace(",", ".").toFloatOrNull() ?: return null
        return if (value >= 0f) value else null
    }

    fun uploadCustomCtaVideo(uri: Uri) {
        viewModelScope.launch {
            when (val result = CtaVideoEngine.importCustomCtaVideo(getApplication(), uri)) {
                is CtaImportResult.Success -> {
                    val newCta = result.ctaItem
                    _selectedCta.value = newCta
                    _isCtaSelectedOnPreview.value = true
                    _isLogoSelectedOnPreview.value = false
                    _isTestPlaying.value = true
                    _ctaTimeErrorMessage.value = null
                    _messageEvents.emit("CTA '${newCta.fileName}' carregada com fundo sólido removido automaticamente!")
                }
                is CtaImportResult.Error -> {
                    val msg = result.message.ifBlank { "Carregue CTA com fundo sólido" }
                    _ctaTimeErrorMessage.value = msg
                    showFiveSecondValidationError("Carregue CTA com fundo sólido")
                    _messageEvents.emit(msg)
                }
            }
        }
    }

    fun deleteCtaVideo(ctaId: Int) {
        viewModelScope.launch {
            val deleted = CtaVideoEngine.deleteCtaVideo(getApplication(), ctaId)
            if (deleted) {
                if (_selectedCta.value.id == ctaId) {
                    _selectedCta.value = CtaVideoItem.NONE
                    _ctaTimeErrorMessage.value = null
                    _isCtaSelectedOnPreview.value = false
                }
                _messageEvents.emit("CTA excluída com sucesso.")
            }
        }
    }

    fun updateCtaPosition(normX: Float, normY: Float) {
        _ctaNormalizedX.value = normX.coerceIn(0.08f, 0.92f)
        _ctaNormalizedY.value = normY.coerceIn(0.08f, 0.92f)
    }

    fun updateCtaScale(newScale: Float) {
        _ctaScale.value = newScale.coerceIn(0.16f, 0.85f)
    }

    fun selectCtaOnPreview() {
        _isCtaSelectedOnPreview.value = true
        _isLogoSelectedOnPreview.value = false
    }

    // Funções de LOGO (se já houver um logo na tela, clicar no botão LOGO não faz nada)
    fun onLogoButtonClick() {
        if (!_logoFilePath.value.isNullOrBlank()) {
            // Requisito: "SE O USUÁRIO CLICAR NO BOTÃO LOGO ENQUANTO HÁ UM LOGO NA TELA NADA ACONTECE."
            return
        }
        _isLogoGalleryOpen.value = true
    }

    fun closeLogoGallery() {
        _isLogoGalleryOpen.value = false
    }

    fun selectLogoFromGallery(context: Context, uri: Uri) {
        if (!_logoFilePath.value.isNullOrBlank()) {
            _isLogoGalleryOpen.value = false
            return
        }
        viewModelScope.launch {
            try {
                val logosDir = File(context.filesDir, "project_logos").apply { mkdirs() }
                val destFile = File(logosDir, "logo_${System.currentTimeMillis()}.png")
                val copied = context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false

                if (!copied || !destFile.exists() || destFile.length() == 0L) {
                    _messageEvents.emit("Não foi possível carregar a imagem de Logo.")
                    return@launch
                }

                // Verifica se é realmente uma imagem válida
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(destFile.absolutePath, opts)
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    destFile.delete()
                    showFiveSecondValidationError("Selecione apenas imagem de Logo")
                    return@launch
                }

                _logoFilePath.value = destFile.absolutePath
                _isLogoSelectedOnPreview.value = true
                _isCtaSelectedOnPreview.value = false
                _isLogoGalleryOpen.value = false
                _messageEvents.emit("Logo adicionado! Toque nele na pré-visualização para ajustar tamanho ou posição.")
            } catch (e: Exception) {
                _messageEvents.emit("Erro ao carregar imagem de Logo: ${e.message}")
            }
        }
    }

    fun removeLogo() {
        val currentPath = _logoFilePath.value
        _logoFilePath.value = null
        _isLogoSelectedOnPreview.value = false
        if (!currentPath.isNullOrBlank()) {
            try {
                File(currentPath).delete()
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            _messageEvents.emit("Logo removido da tela de pré-visualização.")
        }
    }

    fun updateLogoPosition(normX: Float, normY: Float) {
        _logoNormalizedX.value = normX.coerceIn(0.05f, 0.95f)
        _logoNormalizedY.value = normY.coerceIn(0.05f, 0.95f)
    }

    fun updateLogoScale(newScale: Float) {
        _logoScale.value = newScale.coerceIn(0.08f, 0.65f)
    }

    fun selectLogoOnPreview() {
        _isLogoSelectedOnPreview.value = true
        _isCtaSelectedOnPreview.value = false
    }

    private fun showFiveSecondValidationError(shortMessage: String) {
        _validationBannerMessage.value = shortMessage
        errorDismissJob?.cancel()
        errorDismissJob = viewModelScope.launch {
            delay(5000L)
            _validationBannerMessage.value = null
            _transitionError.value = null
            _transitionSoundError.value = null
            _syntaxError.value = null
        }
    }

    fun updateTransitionIdsText(newText: String) {
        _transitionIdsText.value = newText
        _transitionError.value = null
        val result = SyntaxParser.validateTransitionIds(newText)
        if (result is TransitionValidationResult.Success) {
            val firstId = result.transitionIds.firstOrNull() ?: 1
            _selectedTransition.value = TransitionEffect.getOrCut(firstId)
        }
    }

    fun validateTransitionIdsInput(emitSuccessMessage: Boolean = false): Boolean {
        return when (val result = SyntaxParser.validateTransitionIds(_transitionIdsText.value)) {
            is TransitionValidationResult.Success -> {
                _transitionError.value = null
                val firstId = result.transitionIds.firstOrNull() ?: 1
                _selectedTransition.value = TransitionEffect.getOrCut(firstId)
                true
            }
            is TransitionValidationResult.Error -> {
                _transitionError.value = result.message
                showFiveSecondValidationError(result.message)
                false
            }
        }
    }

    // Sons de Transições
    fun selectSound(sound: TransitionSoundEffect) {
        _selectedSound.value = sound
        TransitionSoundEngine.playSound(getApplication(), sound)
    }

    fun playSoundTest(sound: TransitionSoundEffect) {
        TransitionSoundEngine.playSound(getApplication(), sound)
    }

    fun updateTransitionSoundIdsText(newText: String) {
        _transitionSoundIdsText.value = newText
        _transitionSoundError.value = null
        val availableIds = _allAvailableSounds.value.map { it.id }.toSet()
        val result = SyntaxParser.validateTransitionSoundIds(newText, availableIds)
        if (result is TransitionSoundValidationResult.Success) {
            val firstId = result.soundIds.firstOrNull() ?: 1
            val matched = _allAvailableSounds.value.find { it.id == firstId } ?: TransitionSoundEffect.DEFAULT
            _selectedSound.value = matched
        }
    }

    fun validateTransitionSoundIdsInput(emitSuccessMessage: Boolean = false): Boolean {
        val availableIds = _allAvailableSounds.value.map { it.id }.toSet()
        return when (val result = SyntaxParser.validateTransitionSoundIds(_transitionSoundIdsText.value, availableIds)) {
            is TransitionSoundValidationResult.Success -> {
                _transitionSoundError.value = null
                val firstId = result.soundIds.firstOrNull() ?: 1
                val matched = _allAvailableSounds.value.find { it.id == firstId } ?: TransitionSoundEffect.DEFAULT
                _selectedSound.value = matched
                true
            }
            is TransitionSoundValidationResult.Error -> {
                _transitionSoundError.value = result.message
                showFiveSecondValidationError(result.message)
                false
            }
        }
    }

    fun uploadCustomSound(uri: Uri) {
        viewModelScope.launch {
            val result = TransitionSoundEngine.importCustomSound(getApplication(), uri)
            result.onSuccess { newSound ->
                _selectedSound.value = newSound
                _messageEvents.emit("Som personalizado '${newSound.name}' adicionado com sucesso (ID ${newSound.id})!")
            }.onFailure { e ->
                _messageEvents.emit("Erro ao importar som de áudio: ${e.message}")
            }
        }
    }

    fun deleteCustomSound(soundId: Int) {
        viewModelScope.launch {
            val success = TransitionSoundEngine.deleteCustomSound(getApplication(), soundId)
            if (success) {
                if (_selectedSound.value.id == soundId) {
                    _selectedSound.value = TransitionSoundEffect.DEFAULT
                }
                _messageEvents.emit("Som de transição excluído com sucesso.")
            }
        }
    }

    fun togglePreviewMode() {
        _isPreviewingTransition.value = !_isPreviewingTransition.value
    }

    fun previousImage() {
        if (_currentImageIndex.value > 0) {
            _currentImageIndex.value -= 1
        }
    }

    fun nextImage() {
        if (_currentImageIndex.value < _images.value.size - 1) {
            _currentImageIndex.value += 1
        }
    }

    fun setImageIndex(index: Int) {
        _currentImageIndex.value = index.coerceIn(0, (_images.value.size - 1).coerceAtLeast(0))
    }

    fun generateAutomaticPrompts() {
        val count = _images.value.size
        if (count == 0) {
            viewModelScope.launch {
                _messageEvents.emit("Importe mídias antes de gerar os prompts automáticos.")
            }
            return
        }

        val availableSoundIds = _allAvailableSounds.value.filter { it.id > 0 }.map { it.id }
        val result = SyntaxParser.generateRandomPrompts(
            totalImages = count,
            videoMediaIndices = getVideoMediaIndices(),
            availableSoundIds = availableSoundIds
        )
        _syntaxText.value = result.movementSyntaxText
        _transitionIdsText.value = result.transitionIdsText
        _transitionSoundIdsText.value = result.transitionSoundIdsText
        _syntaxError.value = null
        _transitionError.value = null
        _transitionSoundError.value = null

        viewModelScope.launch {
            repository.updateProjectSyntax(currentProjectId, result.movementSyntaxText)
            _messageEvents.emit("Prompts automáticos gerados com movimentos (0-26), durações (5-12s), transições (0-20) e sons!")
        }
    }

    fun openMasterConfig() {
        if (_syntaxText.value.isBlank() && _images.value.isNotEmpty()) {
            _syntaxText.value = SyntaxParser.generateDefaultSyntax(
                totalImages = _images.value.size,
                defaultMovementId = _selectedMovement.value.id,
                videoMediaIndices = getVideoMediaIndices()
            )
        }
        _isMasterConfigOpen.value = true
    }

    fun closeMasterConfig() {
        _isMasterConfigOpen.value = false
    }

    fun toggleTestPlaying() {
        _isTestPlaying.value = !_isTestPlaying.value
    }

    fun openSyntaxModal() {
        if (_syntaxText.value.isBlank() && _images.value.isNotEmpty()) {
            _syntaxText.value = SyntaxParser.generateDefaultSyntax(
                totalImages = _images.value.size,
                defaultMovementId = _selectedMovement.value.id,
                videoMediaIndices = getVideoMediaIndices()
            )
        }
        _syntaxError.value = null
        _isSyntaxModalOpen.value = true
    }

    fun closeSyntaxModal() {
        _isSyntaxModalOpen.value = false
        _syntaxError.value = null
    }

    fun updateSyntaxText(newText: String) {
        _syntaxText.value = newText
        _syntaxError.value = null
    }

    fun autoGenerateSyntax() {
        generateAutomaticPrompts()
    }

    fun validateAndSaveSyntax(): Boolean {
        val totalImages = _images.value.size
        when (val result = SyntaxParser.parseAndValidate(
            text = _syntaxText.value,
            totalProjectImages = totalImages,
            videoMediaIndices = getVideoMediaIndices()
        )) {
            is SyntaxParseResult.Success -> {
                _syntaxError.value = null
                viewModelScope.launch {
                    repository.updateProjectSyntax(currentProjectId, _syntaxText.value)
                }
                _isSyntaxModalOpen.value = false
                return true
            }
            is SyntaxParseResult.Error -> {
                _syntaxError.value = result.message
                showFiveSecondValidationError(result.message)
                return false
            }
        }
    }

    fun openMediaGallery() {
        _isMediaGalleryOpen.value = true
    }

    fun closeMediaGallery() {
        _isMediaGalleryOpen.value = false
    }

    fun openZipBrowser() {
        _zipWarningMessage.value = null
        _isZipBrowserOpen.value = true
    }

    fun closeZipBrowser() {
        _isZipBrowserOpen.value = false
        _zipWarningMessage.value = null
    }

    fun clearZipWarning() {
        _zipWarningMessage.value = null
    }

    fun importDirectImages(context: Context, uris: List<Uri>) {
        viewModelScope.launch {
            when (val result = ImageImportHelper.importImages(context, uris, currentProjectId)) {
                is ImageImportResult.Success -> {
                    repository.addImages(currentProjectId, result.imported)
                    _messageEvents.emit("${result.imported.size} mídia(s) adicionada(s) com sucesso.")
                    refreshDefaultSyntaxIfNeeded()
                }
                is ImageImportResult.Error -> {
                    _messageEvents.emit(result.message)
                }
            }
        }
    }

    fun importZipFile(context: Context, zipUri: Uri) {
        viewModelScope.launch {
            when (val result = ZipExtractor.extractZip(context, zipUri, currentProjectId)) {
                is ZipExtractResult.Success -> {
                    _zipWarningMessage.value = null
                    _isZipBrowserOpen.value = false
                    repository.addImages(currentProjectId, result.extractedFiles)
                    _messageEvents.emit("${result.extractedFiles.size} mídias extraídas do ZIP com sucesso.")
                    refreshDefaultSyntaxIfNeeded()
                }
                is ZipExtractResult.Error -> {
                    _zipWarningMessage.value = "Formato não suportado"
                    zipWarningJob?.cancel()
                    zipWarningJob = viewModelScope.launch {
                        delay(5000L)
                        _zipWarningMessage.value = null
                    }
                    _messageEvents.emit("Formato não suportado")
                }
            }
        }
    }

    private suspend fun refreshDefaultSyntaxIfNeeded() {
        val updatedImages = repository.getImagesSync(currentProjectId)
        if (updatedImages.isNotEmpty()) {
            val videoIndices = updatedImages.mapIndexedNotNull { index, img ->
                if (MediaHelper.isVideo(img.filePath)) index + 1 else null
            }.toSet()
            val defaultText = SyntaxParser.generateDefaultSyntax(
                totalImages = updatedImages.size,
                defaultMovementId = _selectedMovement.value.id,
                videoMediaIndices = videoIndices
            )
            _syntaxText.value = defaultText
            repository.updateProjectSyntax(currentProjectId, defaultText)
        }
    }

    fun deleteImage(imageId: Long) {
        viewModelScope.launch {
            val beforeImages = repository.getImagesSync(currentProjectId)
            val deletedOrderIndex = beforeImages.firstOrNull { it.id == imageId }?.orderIndex

            repository.deleteImage(currentProjectId, imageId)
            _messageEvents.emit("Mídia excluída com sucesso.")
            val remaining = repository.getImagesSync(currentProjectId)
            if (remaining.isNotEmpty()) {
                val videoIndices = remaining.mapIndexedNotNull { index, img ->
                    if (MediaHelper.isVideo(img.filePath)) index + 1 else null
                }.toSet()

                val existingLines = _syntaxText.value
                    .lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && it.contains(":") }

                val updatedSyntax = if (deletedOrderIndex != null && existingLines.size == beforeImages.size) {
                    val filteredLines = existingLines.filterIndexed { idx, _ -> (idx + 1) != deletedOrderIndex }
                    filteredLines.mapIndexed { newIdx, line ->
                        val afterColon = line.substringAfter(":")
                        "${newIdx + 1}: ${afterColon.trim()}"
                    }.joinToString("\n")
                } else {
                    SyntaxParser.generateDefaultSyntax(
                        totalImages = remaining.size,
                        defaultMovementId = _selectedMovement.value.id,
                        videoMediaIndices = videoIndices
                    )
                }

                _syntaxText.value = updatedSyntax
                repository.updateProjectSyntax(currentProjectId, updatedSyntax)
            } else {
                _syntaxText.value = ""
                repository.updateProjectSyntax(currentProjectId, "")
            }
        }
    }

    fun updateCustomOutputDir(uriString: String?) {
        viewModelScope.launch {
            repository.updateCustomOutputDir(currentProjectId, uriString)
            appPreferences.setDefaultOutputDirUri(uriString)
            _messageEvents.emit(
                if (uriString != null) "Pasta de destino personalizada definida e salva como padrão para projetos futuros."
                else "Diretório padrão (/Movies/AppAnimador/) redefinido."
            )
        }
    }

    fun startRendering(context: Context): Boolean {
        val totalImages = _images.value.size
        if (totalImages == 0) {
            showFiveSecondValidationError("Adicione mídias antes de renderizar")
            return false
        }

        // 1. Valida as IDs de transições ao clicar em Iniciar Renderização
        val transValidation = SyntaxParser.validateTransitionIds(_transitionIdsText.value)
        val transitionIds = when (transValidation) {
            is TransitionValidationResult.Success -> {
                _transitionError.value = null
                transValidation.transitionIds
            }
            is TransitionValidationResult.Error -> {
                _transitionError.value = transValidation.message
                showFiveSecondValidationError(transValidation.message)
                _isMasterConfigOpen.value = true
                return false
            }
        }

        // 2. Valida os IDs de sons de transição ao clicar em Iniciar Renderização
        val availableSoundIds = _allAvailableSounds.value.map { it.id }.toSet()
        val soundValidation = SyntaxParser.validateTransitionSoundIds(_transitionSoundIdsText.value, availableSoundIds)
        val transitionSoundIds = when (soundValidation) {
            is TransitionSoundValidationResult.Success -> {
                _transitionSoundError.value = null
                soundValidation.soundIds
            }
            is TransitionSoundValidationResult.Error -> {
                _transitionSoundError.value = soundValidation.message
                showFiveSecondValidationError(soundValidation.message)
                _isMasterConfigOpen.value = true
                return false
            }
        }

        // 3. Valida Animação de Câmera (sintaxe) ao clicar em Iniciar Renderização
        val parseResult = SyntaxParser.parseAndValidate(
            text = _syntaxText.value,
            totalProjectImages = totalImages,
            videoMediaIndices = getVideoMediaIndices()
        )
        if (parseResult is SyntaxParseResult.Error) {
            _syntaxError.value = parseResult.message
            showFiveSecondValidationError(parseResult.message)
            _isMasterConfigOpen.value = true
            return false
        } else {
            _syntaxError.value = null
        }

        val configs = (parseResult as SyntaxParseResult.Success).configs

        // 4. Valida o tempo obrigatório de CTA caso alguma CTA esteja selecionada
        val currentCta = _selectedCta.value
        val ctaStartSec: Float = if (currentCta.id != 0) {
            val parsedCtaTime = parseCtaStartTimeSeconds(_ctaStartTimeText.value)
            if (parsedCtaTime == null) {
                val errMsg = "Informe o tempo exato em que a CTA deve ser exibida no vídeo final!"
                _ctaTimeErrorMessage.value = errMsg
                showFiveSecondValidationError("Informe o tempo da CTA")
                _isMasterConfigOpen.value = false
                _activeResourcePanel.value = ResourcePanelTab.CTA_VIDEOS
                return false
            }
            parsedCtaTime
        } else {
            -1f
        }

        val resolution = _selectedResolution.value
        val aspectRatio = _selectedAspectRatio.value
        val (finalWidth, finalHeight) = aspectRatio.calculateDimensions(resolution)
        val fps = _selectedFps.value
        val bitrateBps = (_selectedBitrateMbps.value * 1_000_000).toInt()
        val transitionDuration = _transitionDurationSeconds.value.coerceIn(0.4f, 6.0f)

        // Abre o modal de progresso
        _isProgressModalOpen.value = true

        val effectiveDir = _project.value?.customOutputDirUri ?: appPreferences.getDefaultOutputDirUri()

        // Dispara Foreground Service com parâmetros configurados para exportação unificada
        VideoRenderingService.start(
            context = context,
            projectId = currentProjectId,
            configs = configs,
            customDirUri = effectiveDir,
            videoWidth = finalWidth,
            videoHeight = finalHeight,
            videoFps = fps,
            videoBitrateBps = bitrateBps,
            resolutionLabel = "${resolution.label} • ${aspectRatio.label} (${finalWidth}x${finalHeight})",
            transitionIds = transitionIds,
            transitionSoundIds = transitionSoundIds,
            transitionDurationSeconds = transitionDuration,
            ctaId = currentCta.id,
            ctaStartTimeSeconds = ctaStartSec,
            ctaNormX = _ctaNormalizedX.value,
            ctaNormY = _ctaNormalizedY.value,
            ctaScale = _ctaScale.value,
            logoFilePath = _logoFilePath.value,
            logoNormX = _logoNormalizedX.value,
            logoNormY = _logoNormalizedY.value,
            logoScale = _logoScale.value
        )
        return true
    }

    fun cancelRendering(context: Context) {
        VideoRenderingService.cancel(context)
    }

    fun closeProgressModal() {
        _isProgressModalOpen.value = false
    }

    fun reopenProgressModal() {
        _isProgressModalOpen.value = true
    }

    private fun getVideoMediaIndices(): Set<Int> {
        return _images.value.mapIndexedNotNull { index, img ->
            if (MediaHelper.isVideo(img.filePath)) index + 1 else null
        }.toSet()
    }
}
