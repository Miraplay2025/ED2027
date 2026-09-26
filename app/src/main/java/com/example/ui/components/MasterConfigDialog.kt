package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.MovementEffect
import com.example.data.model.Project
import com.example.data.model.ProjectImage
import com.example.data.model.SubtitleStyle
import com.example.data.model.TransitionEffect
import com.example.data.model.TransitionSoundEffect
import com.example.data.model.VideoBitratePreset
import com.example.data.model.VideoFps
import com.example.data.model.VideoResolution
import com.example.engine.RenderingState
import com.example.engine.SubtitleEngine

/**
 * Pop-up "Configurar Tudo" Central do Projeto:
 * - Centraliza todas as opções: Seleção de Transições, Sintaxe, Qualidade de Vídeo (Resolução, FPS, Bitrate),
 *   Área de Logs e Progresso em tempo real.
 * - Exibe claramente o diretório onde o vídeo será salvo logo antes do botão de iniciar renderização.
 * - Inclui botão explícito para fechar o pop-up ("Fechar Pop-up").
 * - Botão de Iniciar Renderização do Vídeo Final.
 */
@Composable
fun MasterConfigDialog(
    isOpen: Boolean,
    project: Project?,
    images: List<ProjectImage>,
    selectedTransition: TransitionEffect,
    transitionIdsText: String,
    transitionError: String?,
    onTransitionIdsChange: (String) -> Unit,
    onValidateTransitions: () -> Boolean = { true },
    transitionSoundIdsText: String = "",
    transitionSoundError: String? = null,
    onTransitionSoundIdsChange: (String) -> Unit = {},
    onValidateTransitionSounds: () -> Boolean = { true },
    availableSounds: List<TransitionSoundEffect> = emptyList(),
    onPlaySoundTest: (TransitionSoundEffect) -> Unit = {},
    selectedResolution: VideoResolution,
    onResolutionChange: (VideoResolution) -> Unit,
    selectedFps: Int,
    onFpsChange: (Int) -> Unit,
    selectedBitrateMbps: Float,
    onBitratePresetChange: (VideoBitratePreset) -> Unit,
    onBitrateSliderChange: (Float) -> Unit,
    syntaxText: String,
    syntaxError: String?,
    onSyntaxChange: (String) -> Unit,
    onAutoGeneratePrompts: () -> Unit,
    onSaveAndValidateSyntax: () -> Boolean,
    isSubtitlesEnabled: Boolean = false,
    onSubtitlesEnabledChange: (Boolean) -> Unit = {},
    subtitlesText: String = "",
    subtitlesError: String? = null,
    onSubtitlesTextChange: (String) -> Unit = {},
    onValidateSubtitles: () -> Boolean = { true },
    selectedSubtitleStyle: SubtitleStyle = SubtitleStyle.DEFAULT_STYLE,
    onSelectSubtitleStyle: (SubtitleStyle) -> Unit = {},
    initialTabIndex: Int = 0,
    renderingState: RenderingState,
    onStartRendering: () -> Boolean,
    onCancelRendering: () -> Unit,
    onChangeDirectoryClick: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    var selectedTab by remember(initialTabIndex) { mutableIntStateOf(initialTabIndex) }
    val tabTitles = listOf("Transições", "Sons de Transições", "Qualidade", "Animação de Câmera", "Legendas", "Logs & Status")

    // Estado temporário visível por 5 segundos quando houver erro ao clicar em Iniciar Renderização
    var visibleErrorText by remember { mutableStateOf<String?>(null) }
    var errorTriggerCount by remember { mutableIntStateOf(0) }
    val activeValidationError = transitionError ?: transitionSoundError ?: syntaxError ?: subtitlesError

    LaunchedEffect(activeValidationError, errorTriggerCount) {
        if (activeValidationError != null) {
            visibleErrorText = activeValidationError
            delay(5000L)
            visibleErrorText = null
        } else {
            visibleErrorText = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .testTag("master_config_dialog"),
            color = Color(0xFF141722),
            border = BorderStroke(1.5.dp, Color(0xFF2E354B)),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header com Título, Subtítulo e Botão Fechar Pop-up
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF1E2638),
                            border = BorderStroke(1.dp, Color(0xFF3E4C6D)),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "Configurar Tudo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "${images.size} fotos • ${project?.name ?: "Projeto"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Botão Fechar Pop-up explícito
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_master_config_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF23283A),
                            contentColor = Color.White
                        ),
                        contentPadding = ButtonDefaults.ContentPadding
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Fechar Pop-up",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Fechar Pop-up",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Abas de Navegação Organizadoras
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF1A1E2C),
                    contentColor = Color(0xFF00E5FF),
                    edgePadding = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    color = if (selectedTab == index) Color(0xFF00E5FF) else Color(0xFF8E9BB5)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Conteúdo Rolável da Aba Selecionada
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (selectedTab) {
                        0 -> TransitionsTabContent(
                            selectedTransition = selectedTransition,
                            transitionIdsText = transitionIdsText,
                            transitionError = transitionError,
                            onTransitionIdsChange = onTransitionIdsChange,
                            onValidateTransitions = { onValidateTransitions() },
                            onAutoGeneratePrompts = onAutoGeneratePrompts
                        )
                        1 -> TransitionSoundsTabContent(
                            transitionSoundIdsText = transitionSoundIdsText,
                            transitionSoundError = transitionSoundError,
                            onTransitionSoundIdsChange = onTransitionSoundIdsChange,
                            onValidateTransitionSounds = { onValidateTransitionSounds() },
                            availableSounds = availableSounds,
                            onPlaySoundTest = onPlaySoundTest
                        )
                        2 -> QualityTabContent(
                            selectedResolution = selectedResolution,
                            onResolutionChange = onResolutionChange,
                            selectedFps = selectedFps,
                            onFpsChange = onFpsChange,
                            selectedBitrateMbps = selectedBitrateMbps,
                            onBitratePresetChange = onBitratePresetChange,
                            onBitrateSliderChange = onBitrateSliderChange
                        )
                        3 -> SyntaxTabContent(
                            syntaxText = syntaxText,
                            syntaxError = syntaxError,
                            totalImages = images.size,
                            onSyntaxChange = onSyntaxChange,
                            onAutoGeneratePrompts = onAutoGeneratePrompts,
                            onSaveAndValidate = { onSaveAndValidateSyntax() }
                        )
                        4 -> SubtitlesTabContent(
                            isSubtitlesEnabled = isSubtitlesEnabled,
                            onSubtitlesEnabledChange = onSubtitlesEnabledChange,
                            subtitlesText = subtitlesText,
                            subtitlesError = subtitlesError,
                            onSubtitlesTextChange = onSubtitlesTextChange,
                            selectedSubtitleStyle = selectedSubtitleStyle,
                            onSelectSubtitleStyle = onSelectSubtitleStyle
                        )
                        5 -> LogsTabContent(
                            renderingState = renderingState,
                            onCancelRendering = onCancelRendering
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF262C3E))
                Spacer(modifier = Modifier.height(10.dp))

                // Banner de Erro de Validação (até 6 palavras na mesma linha sem corte, exibido por 5 segundos)
                if (visibleErrorText != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF3B1219),
                        border = BorderStroke(1.5.dp, Color(0xFFFF5252)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("master_config_validation_error_banner")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Erro de validação",
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = visibleErrorText ?: "",
                                color = Color(0xFFFF8A80),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Visible
                            )
                        }
                    }
                }

                // ==========================================
                // SEÇÃO OBRIGATÓRIA: DIRETÓRIO ONDE O VÍDEO SERÁ SALVO
                // (Exibido logo antes do botão Iniciar Renderização)
                // ==========================================
                SaveDirectoryBanner(
                    customOutputDirUri = project?.customOutputDirUri,
                    onChangeDirectoryClick = onChangeDirectoryClick
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ==========================================
                // BOTÃO INICIAR RENDERIZAÇÃO DO VÍDEO FINAL
                // ==========================================
                Button(
                    onClick = {
                        if (!renderingState.isRunning) {
                            val transOk = onValidateTransitions()
                            val soundOk = onValidateTransitionSounds()
                            val syntaxOk = onSaveAndValidateSyntax()
                            val subtitlesOk = if (isSubtitlesEnabled) onValidateSubtitles() else true
                            if (!transOk) {
                                errorTriggerCount++
                                selectedTab = 0
                            } else if (!soundOk) {
                                errorTriggerCount++
                                selectedTab = 1
                            } else if (!syntaxOk) {
                                errorTriggerCount++
                                selectedTab = 3
                            } else if (!subtitlesOk) {
                                errorTriggerCount++
                                selectedTab = 4
                            } else {
                                val started = onStartRendering()
                                if (started) {
                                    selectedTab = 5 // Move para a aba de logs automaticamente após validar tudo
                                } else {
                                    errorTriggerCount++
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("dialog_start_render_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (renderingState.isRunning) Color(0xFF388E3C) else Color(0xFF00B0FF),
                        contentColor = Color.White
                    )
                ) {
                    if (renderingState.isRunning) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Renderizando em Segundo Plano (${renderingState.progressPercent}%)",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Iniciar Renderização do Vídeo Final",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Exportar arquivo unificado com transições e movimentos",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Banner do Diretório de Salvamento:
 * Exibe a pasta configurada ou padrão interna criada pelo app (/Movies/AppAnimador/).
 */
@Composable
private fun SaveDirectoryBanner(
    customOutputDirUri: String?,
    onChangeDirectoryClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("save_directory_banner"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1B2030)
        ),
        border = BorderStroke(1.dp, Color(0xFF323B54))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF262E44),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (customOutputDirUri != null) Icons.Default.FolderSpecial else Icons.Default.FolderOpen,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Diretório onde o vídeo será salvo:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                    Text(
                        text = if (customOutputDirUri != null) {
                            "Pasta Selecionada (SAF)"
                        } else {
                            "Armazenamento Interno > Movies > AppAnimador"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00E676),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (customOutputDirUri != null) {
                            "Gravado como padrão persistente para todos os projetos futuros"
                        } else {
                            "Diretório padrão criado automaticamente pelo app antes de salvar"
                        },
                        fontSize = 9.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onChangeDirectoryClick,
                modifier = Modifier.testTag("change_save_directory_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFF00E5FF)
                ),
                border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
            ) {
                Text("Alterar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * Aba de Transições & Efeitos
 */
@Composable
private fun TransitionsTabContent(
    selectedTransition: TransitionEffect,
    transitionIdsText: String,
    transitionError: String?,
    onTransitionIdsChange: (String) -> Unit,
    onValidateTransitions: () -> Unit,
    onAutoGeneratePrompts: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2030)),
            border = BorderStroke(1.dp, Color(0xFF2A3248))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configuração de Transições Suaves",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Informe os IDs numéricos das transições separados por vírgula (ex: 1, 4, 2, 8). Cada ID deve ser válido entre 1 e 20 (ou 0 para sem transição).",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = transitionIdsText,
                    onValueChange = onTransitionIdsChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_transition_ids_input"),
                    label = { Text("IDs das Transições (ex: 1, 4, 2, 8)", fontSize = 12.sp) },
                    placeholder = { Text("1, 4, 2, 8") },
                    isError = transitionError != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF384360),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = onAutoGeneratePrompts,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                    ) {
                        Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sortear Transições", fontSize = 11.sp)
                    }

                    Text(
                        text = "Ativa no preview: #${selectedTransition.id} ${selectedTransition.name}",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

/**
 * Aba de Sons de Transições (Menu de Sons no Pop-up Configurar Tudo)
 */
@Composable
private fun TransitionSoundsTabContent(
    transitionSoundIdsText: String,
    transitionSoundError: String?,
    onTransitionSoundIdsChange: (String) -> Unit,
    onValidateTransitionSounds: () -> Unit,
    availableSounds: List<TransitionSoundEffect>,
    onPlaySoundTest: (TransitionSoundEffect) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("transition_sounds_tab_content")
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF192030)),
            border = BorderStroke(1.dp, Color(0xFF2B3650))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Sons de Transições Rápidas",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Digite os IDs dos sons separados por vírgula (ex: 1, 5, 13, 15, 17). A cada troca de mídia, o sistema sorteará aleatoriamente um som da lista para tocar.",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = transitionSoundIdsText,
                    onValueChange = onTransitionSoundIdsChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_transition_sound_ids_input"),
                    label = { Text("IDs dos Sons de Transição (ex: 1, 5, 13, 17)", fontSize = 12.sp) },
                    placeholder = { Text("1, 5, 13, 17") },
                    isError = transitionSoundError != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF384360),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Presets Rápidos
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onTransitionSoundIdsChange("1, 2, 3, 4") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cliques Tecla (1-4)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { onTransitionSoundIdsChange("5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Efeitos Pro (5-17)", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    FilledTonalButton(
                        onClick = { onTransitionSoundIdsChange("0") },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(0.7f)
                    ) {
                        Text("Sem Som (0)", fontSize = 10.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Catálogo de Sons (Toque para ouvir):",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            availableSounds.forEach { sound ->
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF161E2C),
                    border = BorderStroke(1.dp, Color(0xFF28344A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlaySoundTest(sound) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E283C)
                            ) {
                                Text(
                                    text = "ID ${sound.id}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = sound.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                                Text(
                                    text = sound.description,
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }

                        IconButton(
                            onClick = { onPlaySoundTest(sound) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Ouvir som",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Aba de Qualidade e Saída
 */
@Composable
private fun QualityTabContent(
    selectedResolution: VideoResolution,
    onResolutionChange: (VideoResolution) -> Unit,
    selectedFps: Int,
    onFpsChange: (Int) -> Unit,
    selectedBitrateMbps: Float,
    onBitratePresetChange: (VideoBitratePreset) -> Unit,
    onBitrateSliderChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        VideoOutputSettingsCard(
            selectedResolution = selectedResolution,
            onResolutionChange = onResolutionChange,
            selectedFps = selectedFps,
            onFpsChange = onFpsChange,
            selectedBitrateMbps = selectedBitrateMbps,
            onBitratePresetChange = onBitratePresetChange,
            onBitrateSliderChange = onBitrateSliderChange
        )
    }
}

/**
 * Aba de Animação de Câmera
 */
@Composable
private fun SyntaxTabContent(
    syntaxText: String,
    syntaxError: String?,
    totalImages: Int,
    onSyntaxChange: (String) -> Unit,
    onAutoGeneratePrompts: () -> Unit,
    onSaveAndValidate: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2030)),
            border = BorderStroke(1.dp, Color(0xFF2A3248))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Animação de Câmera",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Formato obrigatório: IMAGEM X + MOVIMENTO Y + Z.Zs (Movimentos 0 a 26, Duração máxima 12.0s)",
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = syntaxText,
                    onValueChange = onSyntaxChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .testTag("dialog_syntax_editor_input"),
                    isError = syntaxError != null,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF384360),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Botão "Aleatoriamente" posicionado abaixo do campo (gera duração máxima de 12 segundos)
                Button(
                    onClick = onAutoGeneratePrompts,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("random_camera_animation_button"),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Aleatoriamente",
                        fontSize = 13.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Aba de Legendas no Pop-up "Configurar Tudo":
 * - O usuário pode ativar ou desativar as legendas se desejar.
 * - Ao ativar, aparece por baixo o campo de 20vh onde fornece o tempo de início, texto da legenda e tempo final:
 *   00:00 + EXEMPLO DE TEXTO DA LEGENDA = 00:13, 00:14 + EXEMPLO DE TEXTO DA LEGENDA = 00:20...
 * - Possui exemplo placeholder que some automaticamente ao digitar no campo.
 * - Valida tempos duplicados de início ou fim exibindo erro com no máximo 5 palavras na mesma linha.
 * - Por baixo do campo disponibiliza 15 modelos de legendas super profissionais igual do CapCut
 *   (1º modelo = Estilo Padrão + 14 modelos profissionais representados por imagem demonstrativa).
 */
@Composable
private fun SubtitlesTabContent(
    isSubtitlesEnabled: Boolean,
    onSubtitlesEnabledChange: (Boolean) -> Unit,
    subtitlesText: String,
    subtitlesError: String?,
    onSubtitlesTextChange: (String) -> Unit,
    selectedSubtitleStyle: SubtitleStyle,
    onSelectSubtitleStyle: (SubtitleStyle) -> Unit
) {
    val configuration = LocalConfiguration.current
    val field20vhHeight = (configuration.screenHeightDp.dp * 0.20f).coerceIn(135.dp, 220.dp)
    val effectiveSelectedModel = if (selectedSubtitleStyle.id == 0) SubtitleStyle.DEFAULT_STYLE else selectedSubtitleStyle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("subtitles_tab_content")
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2030)),
            border = BorderStroke(1.dp, Color(0xFF2A3248))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Cabeçalho com chave de Ativar / Desativar Legendas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subtitles,
                            contentDescription = null,
                            tint = Color(0xFFFFEA00),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Ativar Legendas no Vídeo",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Text(
                                text = if (isSubtitlesEnabled) {
                                    "Ativado • Campo obrigatório para renderizar"
                                } else {
                                    "Desativado • Ative para configurar os tempos e textos"
                                },
                                fontSize = 10.sp,
                                color = if (isSubtitlesEnabled) Color(0xFF00E676) else Color(0xFF94A3B8)
                            )
                        }
                    }

                    Switch(
                        checked = isSubtitlesEnabled,
                        onCheckedChange = onSubtitlesEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = Color(0xFFFFEA00),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF262E44)
                        ),
                        modifier = Modifier.testTag("subtitles_enable_switch")
                    )
                }

                // Ao ativar as legendas, aparece por baixo o campo de 20vh + os 15 modelos CapCut
                AnimatedVisibility(visible = isSubtitlesEnabled) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = "Informe o tempo inicial, o texto da legenda e o tempo final separados por vírgula:",
                            fontSize = 11.sp,
                            color = Color(0xFFCBD5E1)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Campo de 20vh com exemplo placeholder que some ao digitar
                        OutlinedTextField(
                            value = subtitlesText,
                            onValueChange = onSubtitlesTextChange,
                            placeholder = {
                                Text(
                                    text = SubtitleEngine.PLACEHOLDER_EXAMPLE,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF64748B)
                                )
                            },
                            isError = subtitlesError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(field20vhHeight)
                                .testTag("dialog_subtitles_input"),
                            textStyle = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = Color.White
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFEA00),
                                unfocusedBorderColor = Color(0xFF384360),
                                errorBorderColor = Color(0xFFFF5252),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        // Erro de validação específico (no máximo 5 palavras na mesma linha)
                        if (subtitlesError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF3B1219),
                                border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("subtitles_inline_error_banner")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = subtitlesError,
                                        color = Color(0xFFFF8A80),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Visible
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .testTag("dialog_subtitles_models_row"),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SubtitleStyle.ALL_15_MODELS.forEach { style ->
                                val isSelected = style.id == effectiveSelectedModel.id
                                SubtitleStyleCardItem(
                                    style = style,
                                    isSelected = isSelected,
                                    onClick = { onSelectSubtitleStyle(style) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Aba de Logs & Progresso em Tempo Real
 */
@Composable
private fun LogsTabContent(
    renderingState: RenderingState,
    onCancelRendering: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2030)),
            border = BorderStroke(1.dp, Color(0xFF2A3248))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (renderingState.isRunning) "Renderização Ativa" else "Status do Processamento",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color.White
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (renderingState.isRunning) Color(0xFF00E676).copy(alpha = 0.2f) else Color(0xFF262E44)
                    ) {
                        Text(
                            text = "${renderingState.progressPercent}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (renderingState.isRunning) Color(0xFF00E676) else Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { renderingState.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = Color(0xFF00E5FF),
                    trackColor = Color(0xFF262C3E)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Terminal de Logs
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0E14)),
                    border = BorderStroke(0.5.dp, Color(0xFF262C3E))
                ) {
                    val logsScroll = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .verticalScroll(logsScroll)
                    ) {
                        if (renderingState.logs.isEmpty()) {
                            Text(
                                text = "Aguardando início da renderização...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        } else {
                            renderingState.logs.takeLast(40).forEach { logLine ->
                                Text(
                                    text = logLine,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                if (renderingState.isRunning) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCancelRendering,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF5252)),
                        border = BorderStroke(1.dp, Color(0xFFFF5252))
                    ) {
                        Icon(imageVector = Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Cancelar Renderização Atual", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
