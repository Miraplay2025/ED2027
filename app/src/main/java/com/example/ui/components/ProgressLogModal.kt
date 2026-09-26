package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.engine.RenderingState
import com.example.engine.SubtitleEngine

@Composable
fun ProgressLogModal(
    isOpen: Boolean,
    renderingState: RenderingState,
    onCancel: () -> Unit,
    onMinimize: () -> Unit,
    onStage2ContinueWithCorrectedSubtitles: (String) -> Unit = {},
    onStage2IgnoreSubtitles: () -> Unit = {},
    onStage2ApplyAnyway: () -> Unit = {}
) {
    if (!isOpen) return

    val animatedProgress by animateFloatAsState(
        targetValue = renderingState.progressPercent / 100f,
        label = "progress"
    )

    val listState = rememberLazyListState()
    var editedSubtitlesText by remember(renderingState.pendingSubtitlesText, renderingState.isPausedForSubtitleWarning) {
        mutableStateOf(renderingState.pendingSubtitlesText)
    }

    // Auto-scroll para o último log inserido no terminal
    LaunchedEffect(renderingState.logs.size) {
        if (renderingState.logs.isNotEmpty()) {
            listState.animateScrollToItem(renderingState.logs.size - 1)
        }
    }

    Dialog(
        onDismissRequest = onMinimize,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 720.dp)
                .testTag("progress_log_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header com Título e Botão Minimizar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        renderingState.isPausedForSubtitleWarning -> Color(0xFFFFB300)
                                        renderingState.isCompleted -> Color(0xFF10B981)
                                        renderingState.isRunning -> Color(0xFF3B82F6)
                                        renderingState.isCancelled -> Color(0xFFF59E0B)
                                        else -> Color(0xFFEF4444)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when {
                                renderingState.isPausedForSubtitleWarning -> "Etapa 2 Pausada • Verificar Legendas"
                                renderingState.isCompleted -> "Renderização Concluída"
                                else -> "Renderização (Etapa ${renderingState.currentStage}/2)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.testTag("minimize_progress_dialog")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Minimizar"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Barra de Progresso Percentual
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (renderingState.currentStage == 2) "Etapa 2: Legendas no Rodapé" else "Etapa 1: Renderização do Vídeo",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${renderingState.progressPercent}% Concluído",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .testTag("overall_progress_bar"),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // =========================================================================
                // ALERTA INTERATIVO DA ETAPA 2: PAUSA QUANDO EXISTE TEMPO DE LEGENDA
                // QUE NÃO EXISTE NO VÍDEO FINAL CRIADO
                // Permite:
                // 1. Corrigir o campo de legendas e clicar em "Continuar"
                // 2. Clicar em "Ignorar Legendas" (salva logo o vídeo final sem legendas)
                // 3. Clicar em "Aplicar Mesmo Assim" (aplica só nos tempos validados e ignora os inexistentes)
                // =========================================================================
                AnimatedVisibility(visible = renderingState.isPausedForSubtitleWarning) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF231C0E)),
                        border = BorderStroke(1.5.dp, Color(0xFFFFB300)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .testTag("stage2_subtitle_warning_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.WarningAmber,
                                    contentDescription = null,
                                    tint = Color(0xFFFFCA28),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Tempo Inexistente no Vídeo Final (${renderingState.finalVideoDurationFormatted ?: ""})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFFFFCA28)
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = renderingState.subtitleWarningMessage
                                    ?: "Existe legenda com tempo que ultrapassa a duração do vídeo final criado. Corrija abaixo e clique em Continuar, ou escolha uma das opções:",
                                fontSize = 11.sp,
                                color = Color(0xFFFDE68A)
                            )

                            if (renderingState.subtitleCorrectionError != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF3B1219),
                                    border = BorderStroke(1.dp, Color(0xFFFF5252)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("stage2_subtitle_error_banner")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = Color(0xFFFF5252),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = renderingState.subtitleCorrectionError,
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

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = editedSubtitlesText,
                                onValueChange = { editedSubtitlesText = it },
                                placeholder = {
                                    Text(
                                        text = SubtitleEngine.PLACEHOLDER_EXAMPLE,
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(100.dp)
                                    .testTag("stage2_subtitles_edit_input"),
                                textStyle = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = Color.White
                                ),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFFFCA28),
                                    unfocusedBorderColor = Color(0xFF64748B)
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Botão Principal: Corrigir e CONTINUAR (faz novamente a verificação)
                            Button(
                                onClick = {
                                    onStage2ContinueWithCorrectedSubtitles(editedSubtitlesText)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .testTag("stage2_continue_button"),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E676),
                                    contentColor = Color.Black
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Continuar (Verificar Novamente)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Opções "IGNORAR LEGENDAS" e "APLICAR MESMO ASSIM"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onStage2IgnoreSubtitles,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, Color(0xFFF87171)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("stage2_ignore_subtitles_button")
                                ) {
                                    Text(
                                        text = "Ignorar Legendas",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFF87171),
                                        maxLines = 1
                                    )
                                }

                                FilledTonalButton(
                                    onClick = onStage2ApplyAnyway,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = Color(0xFF0284C7),
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("stage2_apply_anyway_button")
                                ) {
                                    Text(
                                        text = "Aplicar Mesmo Assim",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Contador de Unidades: Processando imagem X de Y | Restantes: Z
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (renderingState.isRunning) {
                                "Processando mídia ${renderingState.currentImageIndex} de ${renderingState.totalImages}"
                            } else if (renderingState.isCompleted) {
                                "${renderingState.totalImages} mídias processadas"
                            } else {
                                "Parado em ${renderingState.currentImageIndex} de ${renderingState.totalImages}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = "Restantes: ${renderingState.remainingImages}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Terminal / Caixa de Logs Detalhados com auto-scroll
                Text(
                    text = "Logs em Tempo Real:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0D1117))
                        .padding(10.dp)
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(renderingState.logs) { logEntry ->
                            Text(
                                text = logEntry,
                                style = androidx.compose.ui.text.TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp,
                                    color = when {
                                        logEntry.contains("sucesso", ignoreCase = true) -> Color(0xFF34D399)
                                        logEntry.contains("pausa", ignoreCase = true) -> Color(0xFFFFCA28)
                                        logEntry.contains("erro", ignoreCase = true) || logEntry.contains("falha", ignoreCase = true) -> Color(0xFFF87171)
                                        logEntry.contains("cancelad", ignoreCase = true) -> Color(0xFFFBBF24)
                                        else -> Color(0xFFE2E8F0)
                                    }
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botões de Ação
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (renderingState.isRunning) {
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cancel_rendering_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancelar")
                        }
                    }

                    OutlinedButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("dismiss_progress_button")
                    ) {
                        Text(if (renderingState.isCompleted) "Fechar" else "Minimizar")
                    }
                }
            }
        }
    }
}
