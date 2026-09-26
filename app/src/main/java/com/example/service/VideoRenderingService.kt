package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.model.MovementEffect
import com.example.data.model.ParsedAnimationConfig
import com.example.data.model.ParsedSubtitleItem
import com.example.data.model.SubtitleStyle
import com.example.data.model.TransitionEffect
import com.example.engine.CtaRenderOverlayConfig
import com.example.engine.CtaVideoEngine
import com.example.engine.LogoRenderOverlayConfig
import com.example.engine.RenderSequenceItem
import com.example.engine.RenderingManager
import com.example.engine.Stage2SubtitleCheckResult
import com.example.engine.Stage2UserDecision
import com.example.engine.SubtitleEngine
import com.example.engine.SubtitleValidationResult
import com.example.engine.VideoEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VideoRenderingService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "channel_app_animador_render"

    private var isNotificationHiddenByUser = false
    private var lastNotificationContent = "Renderizando vídeo..."
    private var lastCurrent = 0
    private var lastTotal = 1
    private var lastPercent = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_CANCEL -> {
                RenderingManager.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISS_NOTIFICATION -> {
                isNotificationHiddenByUser = true
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                if (!RenderingManager.state.value.isRunning) {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_APP_FOREGROUND -> {
                isAppInForeground = true
                return START_NOT_STICKY
            }
            ACTION_APP_BACKGROUND -> {
                isAppInForeground = false
                if (RenderingManager.state.value.isRunning && !isNotificationHiddenByUser) {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(
                        NOTIFICATION_ID,
                        buildNotification(lastNotificationContent, lastCurrent, lastTotal, lastPercent)
                    )
                }
                return START_NOT_STICKY
            }
        }

        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
        val configs = intent.getParcelableArrayListExtra<RenderConfigParcel>(EXTRA_CONFIGS)
        val customOutputDirUri = intent.getStringExtra(EXTRA_CUSTOM_DIR_URI)
        val videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 1280)
        val videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 720)
        val videoFps = intent.getIntExtra(EXTRA_VIDEO_FPS, 30)
        val videoBitrate = intent.getIntExtra(EXTRA_VIDEO_BITRATE, 5_000_000)
        val resolutionLabel = intent.getStringExtra(EXTRA_RESOLUTION_LABEL) ?: "${videoWidth}x${videoHeight}"
        val transitionIds = intent.getIntegerArrayListExtra(EXTRA_TRANSITION_IDS) ?: arrayListOf(1)
        val transitionSoundIds = intent.getIntegerArrayListExtra(EXTRA_TRANSITION_SOUND_IDS) ?: arrayListOf<Int>()
        val transitionDurationSeconds = intent.getFloatExtra(EXTRA_TRANSITION_DURATION, 1.0f).coerceIn(0.4f, 6.0f)

        val ctaId = intent.getIntExtra(EXTRA_CTA_ID, 0)
        val ctaStartTimeSeconds = intent.getFloatExtra(EXTRA_CTA_START_TIME, -1f)
        val ctaNormX = intent.getFloatExtra(EXTRA_CTA_NORM_X, 0.5f)
        val ctaNormY = intent.getFloatExtra(EXTRA_CTA_NORM_Y, 0.78f)
        val ctaScale = intent.getFloatExtra(EXTRA_CTA_SCALE, 0.36f)

        val logoFilePath = intent.getStringExtra(EXTRA_LOGO_FILE_PATH)
        val logoNormX = intent.getFloatExtra(EXTRA_LOGO_NORM_X, 0.84f)
        val logoNormY = intent.getFloatExtra(EXTRA_LOGO_NORM_Y, 0.16f)
        val logoScale = intent.getFloatExtra(EXTRA_LOGO_SCALE, 0.18f)

        val isSubtitlesEnabled = intent.getBooleanExtra(EXTRA_SUBTITLES_ENABLED, false)
        val subtitlesText = intent.getStringExtra(EXTRA_SUBTITLES_TEXT) ?: ""
        val subtitleStyleId = intent.getIntExtra(EXTRA_SUBTITLE_STYLE_ID, 1)
        val timelineAudioPath = intent.getStringExtra(EXTRA_TIMELINE_AUDIO_PATH)

        if (projectId == -1L || configs.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Garante que o diretório padrão exista fisicamente antes de iniciar qualquer renderização
        AppPreferences.getInstance(applicationContext).ensureDefaultDirectory()
        CtaVideoEngine.init(applicationContext)

        isNotificationHiddenByUser = false
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando Etapa 1: Renderização do vídeo...", 0, configs.size, 0))

        serviceScope.launch {
            processBatch(
                projectId = projectId,
                configs = configs,
                transitionIds = transitionIds,
                transitionSoundIds = transitionSoundIds,
                transitionDurationSeconds = transitionDurationSeconds,
                customOutputDirUri = customOutputDirUri,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoFps = videoFps,
                videoBitrate = videoBitrate,
                resolutionLabel = resolutionLabel,
                ctaId = ctaId,
                ctaStartTimeSeconds = ctaStartTimeSeconds,
                ctaNormX = ctaNormX,
                ctaNormY = ctaNormY,
                ctaScale = ctaScale,
                logoFilePath = logoFilePath,
                logoNormX = logoNormX,
                logoNormY = logoNormY,
                logoScale = logoScale,
                isSubtitlesEnabled = isSubtitlesEnabled,
                subtitlesText = subtitlesText,
                subtitleStyleId = subtitleStyleId,
                timelineAudioPath = timelineAudioPath
            )
        }

        return START_NOT_STICKY
    }

    private suspend fun processBatch(
        projectId: Long,
        configs: List<RenderConfigParcel>,
        transitionIds: List<Int>,
        transitionSoundIds: List<Int>,
        transitionDurationSeconds: Float,
        customOutputDirUri: String?,
        videoWidth: Int,
        videoHeight: Int,
        videoFps: Int,
        videoBitrate: Int,
        resolutionLabel: String,
        ctaId: Int = 0,
        ctaStartTimeSeconds: Float = -1f,
        ctaNormX: Float = 0.5f,
        ctaNormY: Float = 0.78f,
        ctaScale: Float = 0.36f,
        logoFilePath: String? = null,
        logoNormX: Float = 0.84f,
        logoNormY: Float = 0.16f,
        logoScale: Float = 0.18f,
        isSubtitlesEnabled: Boolean = false,
        subtitlesText: String = "",
        subtitleStyleId: Int = 1,
        timelineAudioPath: String? = null
    ) {
        val totalImages = configs.size
        RenderingManager.startBatch(totalImages)

        val bitrateFormatted = String.format(java.util.Locale.US, "%.1f Mbps", videoBitrate / 1_000_000f)
        val transDurFormatted = String.format(java.util.Locale.US, "%.1fs", transitionDurationSeconds)
        RenderingManager.log("Iniciando Exportação de Vídeo Único: $resolutionLabel | $videoFps FPS | $bitrateFormatted")
        if (transitionIds.isNotEmpty()) {
            RenderingManager.log("Transições configuradas: [${transitionIds.joinToString(", ")}] • Duração: $transDurFormatted")
        }

        val db = AppDatabase.getInstance(applicationContext)
        val images = db.projectDao().getImagesForProjectSync(projectId)
        val imagesByOrder = images.associateBy { it.orderIndex }

        // Diretório padrão conforme requisito 7: /Movies/AppAnimador/ ou preferência persistida
        val effectiveOutputDirUri = customOutputDirUri ?: AppPreferences.getInstance(applicationContext).getDefaultOutputDirUri()
        val defaultOutputDir = AppPreferences.getInstance(applicationContext).ensureDefaultDirectory()

        // Constrói os itens da sequência completa de vídeo unificado
        val soundPool = if (transitionSoundIds.isNotEmpty()) transitionSoundIds else listOf(0)
        val sequenceItems = mutableListOf<RenderSequenceItem>()
        for ((index, item) in configs.withIndex()) {
            val projectImage = imagesByOrder[item.imageIndex]
            if (projectImage == null) {
                RenderingManager.log("Aviso: Mídia #${item.imageIndex} não encontrada no banco.")
                continue
            }

            val imageFile = File(projectImage.filePath)
            if (!imageFile.exists()) {
                RenderingManager.log("Aviso: Arquivo físico não encontrado em ${imageFile.absolutePath}")
                continue
            }

            val effect = MovementEffect.findById(item.movementId) ?: MovementEffect.ALL_EFFECTS[0]
            val transId = if (transitionIds.isNotEmpty()) {
                transitionIds[index % transitionIds.size]
            } else {
                1
            }
            val transEffect = TransitionEffect.getOrCut(transId)
            val hasNext = index < configs.size - 1
            val chosenSoundId = if (hasNext && soundPool.isNotEmpty()) soundPool.random() else 0

            sequenceItems.add(
                RenderSequenceItem(
                    imageFile = imageFile,
                    movementEffect = effect,
                    durationSeconds = item.durationSeconds,
                    transitionToNext = transEffect,
                    transitionSoundIdToNext = chosenSoundId
                )
            )
        }

        if (sequenceItems.isEmpty()) {
            RenderingManager.log("Nenhuma imagem válida encontrada para gerar o vídeo.")
            RenderingManager.completeBatch()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        RenderingManager.log("Processando sequência de ${sequenceItems.size} cenas em um único arquivo de vídeo...")

        val totalVideoDurationSec = sequenceItems.sumOf { it.durationSeconds.toDouble() }.toFloat()

        // Configura overlay de CTA caso selecionado e dentro do tempo do vídeo final
        val ctaOverlayConfig: CtaRenderOverlayConfig? = if (ctaId != 0 && ctaStartTimeSeconds >= 0f) {
            if (ctaStartTimeSeconds >= totalVideoDurationSec) {
                RenderingManager.log("Aviso CTA: Tempo informado (${ctaStartTimeSeconds}s) ultrapassa a duração do vídeo final (${totalVideoDurationSec}s). Inclusão de CTA ignorada.")
                null
            } else {
                val matchedCta = CtaVideoEngine.allCtaItems.value.find { it.id == ctaId }
                if (matchedCta != null && matchedCta.id != 0) {
                    RenderingManager.log("CTA aplicado: ${matchedCta.fileName} em ${ctaStartTimeSeconds}s (Posição X=${(ctaNormX * 100).toInt()}%, Y=${(ctaNormY * 100).toInt()}%)")
                    CtaRenderOverlayConfig(
                        ctaItem = matchedCta,
                        startTimeSeconds = ctaStartTimeSeconds,
                        normalizedX = ctaNormX,
                        normalizedY = ctaNormY,
                        scale = ctaScale
                    )
                } else null
            }
        } else null

        // Configura overlay de Logo contínuo (do início ao fim do vídeo)
        val logoOverlayConfig: LogoRenderOverlayConfig? = if (!logoFilePath.isNullOrBlank()) {
            val lFile = File(logoFilePath)
            if (lFile.exists()) {
                RenderingManager.log("Logo aplicado durante todo o vídeo (início ao fim): ${lFile.name}")
                LogoRenderOverlayConfig(
                    logoFile = lFile,
                    normalizedX = logoNormX,
                    normalizedY = logoNormY,
                    scale = logoScale
                )
            } else null
        } else null

        val timelineAudioFile: File? = if (!timelineAudioPath.isNullOrBlank()) {
            val af = File(timelineAudioPath)
            if (af.exists() && af.length() > 0L) {
                RenderingManager.log("Áudio da Linha do Tempo incluído: ${af.name}")
                af
            } else null
        } else null

        val stage1VideoFile = File(cacheDir, "video_etapa1_${System.currentTimeMillis()}.mp4")

        val stage1Success = VideoEncoder.encodeUnifiedSequenceToVideo(
            sequence = sequenceItems,
            outputFile = stage1VideoFile,
            targetWidth = videoWidth,
            targetHeight = videoHeight,
            frameRate = videoFps,
            bitRate = videoBitrate,
            transitionDurationSeconds = transitionDurationSeconds,
            ctaOverlay = ctaOverlayConfig,
            logoOverlay = logoOverlayConfig,
            subtitles = emptyList(),
            subtitleStyle = SubtitleStyle.NO_SUBTITLE,
            timelineAudioFile = timelineAudioFile,
            onGlobalProgress = { currentFrame, totalFrames, currentImgIndex, statusText ->
                val rawPercent = ((currentFrame.toFloat() / totalFrames) * 100).toInt().coerceIn(0, 100)
                val displayPercent = if (isSubtitlesEnabled) (rawPercent * 0.65f).toInt().coerceIn(0, 65) else rawPercent
                updateNotification(
                    "Etapa 1: Renderizando vídeo ($displayPercent%)",
                    currentImgIndex + 1,
                    sequenceItems.size,
                    displayPercent
                )
                RenderingManager.updateProgress(
                    currentImageIndex = currentImgIndex + 1,
                    totalImages = sequenceItems.size,
                    currentMovementName = "Etapa 1 • $statusText",
                    overallPercent = displayPercent
                )
            },
            isCancelled = { RenderingManager.isCancelRequested }
        )

        if (!stage1Success || !stage1VideoFile.exists() || stage1VideoFile.length() == 0L) {
            if (RenderingManager.isCancelRequested) {
                RenderingManager.log("Renderização cancelada pelo usuário.")
                updateNotification("Renderização cancelada", 0, totalImages, 100)
            } else {
                RenderingManager.log("Falha na Etapa 1 de renderização do vídeo.")
            }
            stage1VideoFile.delete()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // =========================================================================
        // CASO O USUÁRIO NÃO ATIVOU AS LEGENDAS: SALVA LOGO O VÍDEO NO DISPOSITIVO
        // =========================================================================
        if (!isSubtitlesEnabled) {
            val fileName = "video_completo_animado_${System.currentTimeMillis()}.mp4"
            val savedLocation = saveVideoToDestination(
                tempFile = stage1VideoFile,
                fileName = fileName,
                customOutputDirUri = effectiveOutputDirUri,
                defaultDir = defaultOutputDir
            )
            RenderingManager.addOutputFile(savedLocation)
            RenderingManager.log("VÍDEO ÚNICO FINAL gerado com sucesso!")
            RenderingManager.log("Salvo em: $savedLocation")
            stage1VideoFile.delete()
        } else {
            // =========================================================================
            // ETAPA 2 (LEGENDAS ATIVADAS):
            // 1. Ainda NÃO salva o vídeo no dispositivo.
            // 2. Obtém a duração completa do vídeo final criado na Etapa 1.
            // 3. Verifica se no campo de legendas existe alguma legenda com tempo inexistente no vídeo criado.
            // 4. Se existir tempo inexistente: dá PAUSA e avisa o usuário para corrigir e clicar em CONTINUAR,
            //    ou escolher "IGNORAR LEGENDAS" (salva logo sem legendas) ou "APLICAR MESMO ASSIM" (aplica só nos tempos validados).
            // =========================================================================
            val createdVideoDurationSec = VideoEncoder.probeCreatedVideoDurationSeconds(
                videoFile = stage1VideoFile,
                fallbackDurationSec = totalVideoDurationSec
            )
            val durationFormatted = SubtitleEngine.formatSecondsToMmSs(createdVideoDurationSec)
            RenderingManager.setStage(2, "Etapa 2: Verificando legendas na duração do vídeo ($durationFormatted)...")
            RenderingManager.log("Etapa 1 concluída. Duração total do vídeo criado: $durationFormatted (${String.format(java.util.Locale.US, "%.1fs", createdVideoDurationSec)}). Iniciando Etapa 2 (Legendas)...")

            var currentSubtitlesInput = subtitlesText
            var subtitlesToRender: List<ParsedSubtitleItem>? = null
            var shouldIgnoreSubtitles = false

            while (subtitlesToRender == null && !shouldIgnoreSubtitles && !RenderingManager.isCancelRequested) {
                when (val parseRes = SubtitleEngine.parseAndValidateSubtitles(currentSubtitlesInput)) {
                    is SubtitleValidationResult.Error -> {
                        updateNotification(
                            "Pausa na Etapa 2: Corrija as legendas para continuar",
                            sequenceItems.size,
                            sequenceItems.size,
                            65
                        )
                        val decision = RenderingManager.pauseAndAwaitStage2SubtitleDecision(
                            warningMessage = "Formato ou tempo duplicado nas legendas (${parseRes.message}). Corrija e clique em Continuar:",
                            invalidTimesLabel = parseRes.faultyPart,
                            videoDurationFormatted = durationFormatted,
                            currentSubtitlesText = currentSubtitlesInput,
                            correctionError = parseRes.message
                        )
                        when (decision) {
                            is Stage2UserDecision.IgnoreSubtitles -> {
                                shouldIgnoreSubtitles = true
                            }
                            is Stage2UserDecision.ApplyAnywayValidOnly -> {
                                shouldIgnoreSubtitles = true
                            }
                            is Stage2UserDecision.RetryWithCorrectedSubtitles -> {
                                currentSubtitlesInput = decision.correctedText
                            }
                        }
                    }
                    is SubtitleValidationResult.Success -> {
                        when (val stage2Check = SubtitleEngine.checkSubtitlesInFinalVideoDuration(
                            subtitles = parseRes.items,
                            videoDurationSeconds = createdVideoDurationSec
                        )) {
                            is Stage2SubtitleCheckResult.AllValid -> {
                                subtitlesToRender = stage2Check.validSubtitles
                            }
                            is Stage2SubtitleCheckResult.HasInvalidTimes -> {
                                updateNotification(
                                    "Pausa na Etapa 2: Tempo de legenda inexistente no vídeo ($durationFormatted)",
                                    sequenceItems.size,
                                    sequenceItems.size,
                                    65
                                )
                                val warningMsg = "O vídeo final possui duração de $durationFormatted, mas a(s) legenda(s) [${stage2Check.invalidTimesLabel}] possuem tempo inexistente no vídeo. Corrija abaixo e clique em Continuar, ou escolha uma opção:"
                                val decision = RenderingManager.pauseAndAwaitStage2SubtitleDecision(
                                    warningMessage = warningMsg,
                                    invalidTimesLabel = stage2Check.invalidTimesLabel,
                                    videoDurationFormatted = durationFormatted,
                                    currentSubtitlesText = currentSubtitlesInput,
                                    correctionError = null
                                )
                                when (decision) {
                                    is Stage2UserDecision.IgnoreSubtitles -> {
                                        RenderingManager.log("Usuário selecionou 'Ignorar Legendas'. Salvando vídeo final sem legendas...")
                                        shouldIgnoreSubtitles = true
                                    }
                                    is Stage2UserDecision.ApplyAnywayValidOnly -> {
                                        RenderingManager.log("Usuário selecionou 'Aplicar Mesmo Assim'. Ignorando tempos inexistentes e aplicando ${stage2Check.validSubtitles.size} legenda(s) validada(s)...")
                                        if (stage2Check.validSubtitles.isEmpty()) {
                                            shouldIgnoreSubtitles = true
                                        } else {
                                            subtitlesToRender = stage2Check.validSubtitles
                                        }
                                    }
                                    is Stage2UserDecision.RetryWithCorrectedSubtitles -> {
                                        RenderingManager.log("Verificando novamente os tempos das legendas corrigidos pelo usuário...")
                                        currentSubtitlesInput = decision.correctedText
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (RenderingManager.isCancelRequested) {
                stage1VideoFile.delete()
                updateNotification("Renderização cancelada", 0, totalImages, 100)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }

            if (shouldIgnoreSubtitles || subtitlesToRender.isNullOrEmpty()) {
                val fileName = "video_completo_animado_${System.currentTimeMillis()}.mp4"
                val savedLocation = saveVideoToDestination(
                    tempFile = stage1VideoFile,
                    fileName = fileName,
                    customOutputDirUri = effectiveOutputDirUri,
                    defaultDir = defaultOutputDir
                )
                RenderingManager.addOutputFile(savedLocation)
                RenderingManager.log("Vídeo final salvo no dispositivo sem legendas: $savedLocation")
                stage1VideoFile.delete()
            } else {
                // Etapa 2: Renderiza as legendas nos tempos específicos fornecidos no rodapé do vídeo final
                // Se o usuário não selecionou nenhum modelo (ID 0), usa o Estilo Padrão (ID 1)
                val effectiveSubtitleStyle = SubtitleStyle.getEffectiveRenderStyle(subtitleStyleId)
                RenderingManager.log("Etapa 2: Renderizando ${subtitlesToRender.size} legenda(s) no rodapé com modelo '${effectiveSubtitleStyle.name}'...")

                val stage2VideoFile = File(cacheDir, "video_etapa2_legendado_${System.currentTimeMillis()}.mp4")
                val stage2Success = VideoEncoder.encodeUnifiedSequenceToVideo(
                    sequence = sequenceItems,
                    outputFile = stage2VideoFile,
                    targetWidth = videoWidth,
                    targetHeight = videoHeight,
                    frameRate = videoFps,
                    bitRate = videoBitrate,
                    transitionDurationSeconds = transitionDurationSeconds,
                    ctaOverlay = ctaOverlayConfig,
                    logoOverlay = logoOverlayConfig,
                    subtitles = subtitlesToRender,
                    subtitleStyle = effectiveSubtitleStyle,
                    timelineAudioFile = timelineAudioFile,
                    onGlobalProgress = { currentFrame, totalFrames, currentImgIndex, _ ->
                        val stage2Ratio = currentFrame.toFloat() / totalFrames.coerceAtLeast(1)
                        val overallPercent = (65 + (stage2Ratio * 35f).toInt()).coerceIn(65, 100)
                        updateNotification(
                            "Etapa 2: Aplicando legendas no rodapé ($overallPercent%)",
                            currentImgIndex + 1,
                            sequenceItems.size,
                            overallPercent
                        )
                        RenderingManager.updateProgress(
                            currentImageIndex = currentImgIndex + 1,
                            totalImages = sequenceItems.size,
                            currentMovementName = "Etapa 2 • Legendas (${effectiveSubtitleStyle.name})",
                            overallPercent = overallPercent
                        )
                    },
                    isCancelled = { RenderingManager.isCancelRequested }
                )

                stage1VideoFile.delete()

                if (stage2Success && stage2VideoFile.exists() && stage2VideoFile.length() > 0L) {
                    val fileName = "video_completo_legendado_${System.currentTimeMillis()}.mp4"
                    val savedLocation = saveVideoToDestination(
                        tempFile = stage2VideoFile,
                        fileName = fileName,
                        customOutputDirUri = effectiveOutputDirUri,
                        defaultDir = defaultOutputDir
                    )
                    RenderingManager.addOutputFile(savedLocation)
                    RenderingManager.log("VÍDEO FINAL COM LEGENDAS gerado e salvo com sucesso!")
                    RenderingManager.log("Salvo em: $savedLocation")
                    stage2VideoFile.delete()
                } else {
                    stage2VideoFile.delete()
                    if (RenderingManager.isCancelRequested) {
                        RenderingManager.log("Renderização cancelada pelo usuário na Etapa 2.")
                        updateNotification("Renderização cancelada", 0, totalImages, 100)
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        return
                    } else {
                        RenderingManager.log("Falha na Etapa 2 ao aplicar legendas.")
                    }
                }
            }
        }

        if (RenderingManager.isCancelRequested) {
            updateNotification("Renderização cancelada", 0, totalImages, 100)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Marca projeto com data do último render
        val project = db.projectDao().getProjectSync(projectId)
        if (project != null) {
            db.projectDao().updateProject(project.copy(lastRenderedAt = System.currentTimeMillis()))
        }

        RenderingManager.completeBatch()
        stopForeground(STOP_FOREGROUND_DETACH)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildSuccessNotification("Vídeo final renderizado e salvo com sucesso!"))
        stopSelf()
    }

    private fun saveVideoToDestination(
        tempFile: File,
        fileName: String,
        customOutputDirUri: String?,
        defaultDir: File
    ): String {
        // Se usuário definiu pasta personalizada via SAF (content://) ou diretório de arquivo local
        if (!customOutputDirUri.isNullOrBlank()) {
            if (customOutputDirUri.startsWith("content://")) {
                try {
                    val treeUri = Uri.parse(customOutputDirUri)
                    val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
                    if (pickedDir != null && pickedDir.canWrite()) {
                        var targetDoc = pickedDir.findFile(fileName)
                        if (targetDoc != null) {
                            targetDoc.delete()
                        }
                        targetDoc = pickedDir.createFile("video/mp4", fileName)
                        if (targetDoc != null) {
                            contentResolver.openOutputStream(targetDoc.uri)?.use { outStream ->
                                FileInputStream(tempFile).use { inStream ->
                                    inStream.copyTo(outStream)
                                }
                            }
                            return targetDoc.uri.toString()
                        }
                    }
                } catch (e: Exception) {
                    RenderingManager.log("Aviso: Falha ao salvar no diretório SAF personalizado: ${e.message}. Salvando no diretório padrão.")
                }
            } else {
                try {
                    val customDirFile = File(customOutputDirUri)
                    if (!customDirFile.exists()) {
                        customDirFile.mkdirs()
                    }
                    if (customDirFile.exists() && customDirFile.canWrite()) {
                        val destFile = File(customDirFile, fileName)
                        tempFile.copyTo(destFile, overwrite = true)
                        MediaScannerConnection.scanFile(
                            applicationContext,
                            arrayOf(destFile.absolutePath),
                            arrayOf("video/mp4"),
                            null
                        )
                        return destFile.absolutePath
                    }
                } catch (_: Exception) {}
            }
        }

        // Diretório padrão: /Movies/AppAnimador/
        try {
            defaultDir.mkdirs()
            val destinationFile = File(defaultDir, fileName)
            tempFile.copyTo(destinationFile, overwrite = true)
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(destinationFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            return destinationFile.absolutePath
        } catch (e: Exception) {
            // Em versões de Android com Scoped Storage estrito (MediaStore insert)
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AppAnimador")
                    }
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(tempFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    uri.toString()
                } else {
                    val appMovies = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AppAnimador").apply { mkdirs() }
                    val fb = File(appMovies, fileName)
                    tempFile.copyTo(fb, overwrite = true)
                    fb.absolutePath
                }
            } catch (_: Exception) {
                val appMovies = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AppAnimador").apply { mkdirs() }
                val fb = File(appMovies, fileName)
                tempFile.copyTo(fb, overwrite = true)
                fb.absolutePath
            }
        }
    }

    private fun buildNotification(
        content: String,
        current: Int,
        total: Int,
        percent: Int
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VideoRenderingService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, VideoRenderingService::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Editor Automático — Renderizando Vídeo")
            .setContentText(content)
            .setSubText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", cancelPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Ocultar", dismissPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildSuccessNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, VideoRenderingService::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Oculta a barra de processamento (setProgress(0, 0, false)) e exibe o texto de sucesso
        // com o botão X ao lado para clicar e fechar a notificação
        val customRemoteViews = android.widget.RemoteViews(packageName, R.layout.notification_render_complete).apply {
            setTextViewText(R.id.txt_notification_title, "100% Concluído — Vídeo Salvo!")
            setTextViewText(R.id.txt_notification_message, content)
            setOnClickPendingIntent(R.id.btn_close_notification, dismissPendingIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("100% Concluído — Vídeo Salvo!")
            .setContentText(content)
            .setProgress(0, 0, false)
            .setCustomContentView(customRemoteViews)
            .setCustomBigContentView(customRemoteViews)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "✕ Fechar", dismissPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification(content: String, current: Int, total: Int, percent: Int) {
        lastNotificationContent = content
        lastCurrent = current
        lastTotal = total
        lastPercent = percent

        if (!isNotificationHiddenByUser) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(content, current, total, percent))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Renderização de Vídeo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso contínuo de renderização de vídeos em segundo plano"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_CANCEL = "com.example.service.action.CANCEL"
        const val ACTION_DISMISS_NOTIFICATION = "com.example.service.action.DISMISS_NOTIFICATION"
        const val ACTION_APP_FOREGROUND = "com.example.service.action.APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "com.example.service.action.APP_BACKGROUND"

        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_CONFIGS = "extra_configs"
        const val EXTRA_CUSTOM_DIR_URI = "extra_custom_dir_uri"
        const val EXTRA_VIDEO_WIDTH = "extra_video_width"
        const val EXTRA_VIDEO_HEIGHT = "extra_video_height"
        const val EXTRA_VIDEO_FPS = "extra_video_fps"
        const val EXTRA_VIDEO_BITRATE = "extra_video_bitrate"
        const val EXTRA_RESOLUTION_LABEL = "extra_resolution_label"
        const val EXTRA_TRANSITION_IDS = "extra_transition_ids"
        const val EXTRA_TRANSITION_SOUND_IDS = "extra_transition_sound_ids"
        const val EXTRA_TRANSITION_DURATION = "extra_transition_duration"
        const val EXTRA_CTA_ID = "extra_cta_id"
        const val EXTRA_CTA_START_TIME = "extra_cta_start_time"
        const val EXTRA_CTA_NORM_X = "extra_cta_norm_x"
        const val EXTRA_CTA_NORM_Y = "extra_cta_norm_y"
        const val EXTRA_CTA_SCALE = "extra_cta_scale"
        const val EXTRA_LOGO_FILE_PATH = "extra_logo_file_path"
        const val EXTRA_LOGO_NORM_X = "extra_logo_norm_x"
        const val EXTRA_LOGO_NORM_Y = "extra_logo_norm_y"
        const val EXTRA_LOGO_SCALE = "extra_logo_scale"
        const val EXTRA_SUBTITLES_ENABLED = "extra_subtitles_enabled"
        const val EXTRA_SUBTITLES_TEXT = "extra_subtitles_text"
        const val EXTRA_SUBTITLE_STYLE_ID = "extra_subtitle_style_id"
        const val EXTRA_TIMELINE_AUDIO_PATH = "extra_timeline_audio_path"

        @Volatile
        var isAppInForeground: Boolean = false

        fun notifyAppForeground(context: Context) {
            isAppInForeground = true
            try {
                val intent = Intent(context, VideoRenderingService::class.java).apply {
                    action = ACTION_APP_FOREGROUND
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun notifyAppBackground(context: Context) {
            isAppInForeground = false
            try {
                val intent = Intent(context, VideoRenderingService::class.java).apply {
                    action = ACTION_APP_BACKGROUND
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun start(
            context: Context,
            projectId: Long,
            configs: List<ParsedAnimationConfig>,
            customDirUri: String?,
            videoWidth: Int = 1280,
            videoHeight: Int = 720,
            videoFps: Int = 30,
            videoBitrateBps: Int = 5_000_000,
            resolutionLabel: String = "720p (1280x720) [HD]",
            transitionIds: List<Int> = listOf(1),
            transitionSoundIds: List<Int> = emptyList(),
            transitionDurationSeconds: Float = 1.0f,
            ctaId: Int = 0,
            ctaStartTimeSeconds: Float = -1f,
            ctaNormX: Float = 0.5f,
            ctaNormY: Float = 0.78f,
            ctaScale: Float = 0.36f,
            logoFilePath: String? = null,
            logoNormX: Float = 0.84f,
            logoNormY: Float = 0.16f,
            logoScale: Float = 0.18f,
            isSubtitlesEnabled: Boolean = false,
            subtitlesText: String = "",
            subtitleStyleId: Int = 1,
            timelineAudioPath: String? = null
        ) {
            val parcelList = ArrayList(configs.map {
                RenderConfigParcel(it.imageIndex, it.movementId, it.durationSeconds)
            })
            val intent = Intent(context, VideoRenderingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_ID, projectId)
                putParcelableArrayListExtra(EXTRA_CONFIGS, parcelList)
                putIntegerArrayListExtra(EXTRA_TRANSITION_IDS, ArrayList(transitionIds))
                putIntegerArrayListExtra(EXTRA_TRANSITION_SOUND_IDS, ArrayList(transitionSoundIds))
                putExtra(EXTRA_TRANSITION_DURATION, transitionDurationSeconds.coerceIn(0.4f, 6.0f))
                putExtra(EXTRA_CUSTOM_DIR_URI, customDirUri)
                putExtra(EXTRA_VIDEO_WIDTH, videoWidth)
                putExtra(EXTRA_VIDEO_HEIGHT, videoHeight)
                putExtra(EXTRA_VIDEO_FPS, videoFps)
                putExtra(EXTRA_VIDEO_BITRATE, videoBitrateBps)
                putExtra(EXTRA_RESOLUTION_LABEL, resolutionLabel)
                putExtra(EXTRA_CTA_ID, ctaId)
                putExtra(EXTRA_CTA_START_TIME, ctaStartTimeSeconds)
                putExtra(EXTRA_CTA_NORM_X, ctaNormX)
                putExtra(EXTRA_CTA_NORM_Y, ctaNormY)
                putExtra(EXTRA_CTA_SCALE, ctaScale)
                putExtra(EXTRA_LOGO_FILE_PATH, logoFilePath)
                putExtra(EXTRA_LOGO_NORM_X, logoNormX)
                putExtra(EXTRA_LOGO_NORM_Y, logoNormY)
                putExtra(EXTRA_LOGO_SCALE, logoScale)
                putExtra(EXTRA_SUBTITLES_ENABLED, isSubtitlesEnabled)
                putExtra(EXTRA_SUBTITLES_TEXT, subtitlesText)
                putExtra(EXTRA_SUBTITLE_STYLE_ID, subtitleStyleId)
                putExtra(EXTRA_TIMELINE_AUDIO_PATH, timelineAudioPath)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun dismissNotification(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(1001)
                val intent = Intent(context, VideoRenderingService::class.java).apply {
                    action = ACTION_DISMISS_NOTIFICATION
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun cancel(context: Context) {
            val intent = Intent(context, VideoRenderingService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
