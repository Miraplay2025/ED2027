package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.engine.MediaHelper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MovementEffect
import com.example.data.model.ProjectImage
import com.example.data.model.TransitionEffect
import com.example.data.model.VideoAspectRatio
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Palco de Pré-visualização com:
 * - Moldura visual estilizada com acabamento de monitor de estúdio
 * - Seletor de 4 Proporções de Tela (16:9, 9:16, 1:1, 4:5)
 * - Navegação lateral entre imagens com setas (oculta seta esquerda na Imagem 1)
 * - Pré-visualização em tempo real de movimento de câmera ou transição CapCut entre Imagem 1 e 2
 */
@Composable
fun LivePreviewStage(
    images: List<ProjectImage>,
    currentImageIndex: Int,
    onPreviousImage: () -> Unit,
    onNextImage: () -> Unit,
    selectedMovement: MovementEffect,
    selectedAspectRatio: VideoAspectRatio,
    onAspectRatioChange: (VideoAspectRatio) -> Unit,
    selectedTransition: TransitionEffect,
    isPreviewingTransition: Boolean,
    onTogglePreviewMode: () -> Unit,
    isPlaying: Boolean,
    onTogglePlay: () -> Unit,
    transitionDurationSeconds: Float = 1.0f,
    modifier: Modifier = Modifier
) {
    val animProgress = remember { Animatable(0f) }
    var isAnimationFinished by remember { mutableStateOf(false) }
    var replayTrigger by remember { mutableIntStateOf(0) }

    // Duração do percurso completo do início ao fim
    val animationDuration = if (isPreviewingTransition) {
        (transitionDurationSeconds.coerceIn(0.4f, 6.0f) * 1000f).toInt().coerceAtLeast(400)
    } else {
        3200
    }

    LaunchedEffect(
        selectedMovement.id,
        selectedTransition.id,
        transitionDurationSeconds,
        isPreviewingTransition,
        currentImageIndex,
        replayTrigger,
        isPlaying
    ) {
        if (isPlaying) {
            isAnimationFinished = false
            animProgress.snapTo(0f) // Inicia no ponto 0.0f
            animProgress.animateTo(
                targetValue = 1f, // Executa até terminar completamente (1.0f)
                animationSpec = tween(
                    durationMillis = animationDuration,
                    easing = if (isPreviewingTransition) FastOutSlowInEasing else LinearEasing
                )
            )
            isAnimationFinished = true // Conclui e encerra o movimento ou a transição
        }
    }

    val currentProgress = animProgress.value

    val currentImage = images.getOrNull(currentImageIndex)
    val nextImage = if (images.size >= 2) {
        images.getOrNull((currentImageIndex + 1) % images.size) ?: images.getOrNull(1)
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("live_preview_stage"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Cabeçalho: Título + Modo de Pré-visualização + Botão Play/Replay
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                if (isPreviewingTransition) MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPreviewingTransition) Icons.Default.AutoAwesome else Icons.Default.Videocam,
                            contentDescription = null,
                            tint = if (isPreviewingTransition) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (isPreviewingTransition) "Prévia de Transição (1 ➔ 2)"
                            else "Palco Live • Imagem ${currentImageIndex + 1}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isPreviewingTransition)
                                "[ID ${selectedTransition.id}] ${selectedTransition.name}"
                            else
                                "${selectedMovement.name} (${selectedMovement.shortBadge})",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isPreviewingTransition) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (images.size >= 2) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isPreviewingTransition) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onTogglePreviewMode() }
                                .padding(end = 6.dp)
                        ) {
                            Text(
                                text = if (isPreviewingTransition) "Ver Câmera" else "Ver Transição",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isPreviewingTransition) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                        }
                    }

                    FilledTonalIconButton(
                        onClick = {
                            if (isAnimationFinished) {
                                replayTrigger++
                            } else {
                                onTogglePlay()
                            }
                        },
                        modifier = Modifier.testTag("toggle_preview_play")
                    ) {
                        Icon(
                            imageVector = when {
                                isAnimationFinished -> Icons.Default.Replay
                                isPlaying -> Icons.Default.Pause
                                else -> Icons.Default.PlayArrow
                            },
                            contentDescription = when {
                                isAnimationFinished -> "Reiniciar Pré-visualização"
                                isPlaying -> "Pausar"
                                else -> "Reproduzir"
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Seletor de Proporções de Tela Rolável Horizontalmente
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Crop,
                    contentDescription = "Proporção",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Proporção:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                VideoAspectRatio.ALL.forEach { ratio ->
                    val isSelected = ratio == selectedAspectRatio
                    FilterChip(
                        selected = isSelected,
                        onClick = { onAspectRatioChange(ratio) },
                        label = {
                            Text(
                                text = ratio.label,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.height(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Área do Visualizador de Vídeo com Contêiner Estritamente na Proporção Selecionada
            val isCurrentVideo = currentImage != null && MediaHelper.isVideo(currentImage.filePath)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (selectedAspectRatio.ratio < 0.9f) 0.65f else if (selectedAspectRatio.ratio < 1.1f) 0.85f else 1.0f)
                        .aspectRatio(selectedAspectRatio.ratio)
                        .heightIn(max = 360.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shadow(elevation = 6.dp, shape = RoundedCornerShape(16.dp))
                        .background(Color(0xFF0D0F12))
                        .border(
                            border = androidx.compose.foundation.BorderStroke(
                                2.dp,
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                        Color(0xFF3B82F6),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.85f)
                                    )
                                )
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Conteúdo da Imagem ou Vídeo ajustado à Proporção Escolhida
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        if (currentImage != null) {
                            if (isPreviewingTransition && nextImage != null) {
                                // Renderiza a transição em tempo real entre imagem 1 e 2 com movimento de câmera independente em cada cena
                                PreviewTransitionEffectView(
                                    image1 = currentImage,
                                    image2 = nextImage,
                                    transition = selectedTransition,
                                    movement = selectedMovement,
                                    progress = currentProgress
                                )
                            } else {
                                // Renderiza imagem ou vídeo atual (vídeos não suportam animação de movimento)
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(currentImage.filePath))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = "Pré-visualização da Mídia ${currentImageIndex + 1}",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            if (!isCurrentVideo) {
                                                selectedMovement.applyToGraphicsLayer(this, currentProgress)
                                            }
                                        }
                                )

                                if (isCurrentVideo) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                    ) {
                                        Text(
                                            text = "VÍDEO (Sem movimento de câmera)",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                        // Estado sem imagem
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Image,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Nenhuma imagem importada",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Adicione imagens abaixo para começar",
                                color = Color.Gray,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Seta de Navegação para a Esquerda (Voltar)
                // REGRA 6: Oculta automaticamente quando a tela voltar para a primeira imagem (IMAGEM 1)
                if (currentImageIndex > 0) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                    ) {
                        IconButton(
                            onClick = onPreviousImage,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_prev_image")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Imagem Anterior",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Seta de Navegação para a Direita (Avançar)
                // REGRA 6: Exibe seta para avançar às próximas imagens
                if (images.size > 1 && currentImageIndex < images.size - 1) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp)
                    ) {
                        IconButton(
                            onClick = onNextImage,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("btn_next_image")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Próxima Imagem",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Badge Flutuante no Topo com contador de fotos e proporção
                if (images.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.70f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "IMAGEM ${currentImageIndex + 1} de ${images.size} • ${selectedAspectRatio.label}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Detalhe de Moldura Decorativa de Estúdio (Cantos metálicos discretos)
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = if (isPlaying) Color(0xFF10B981) else Color.Gray,
                        modifier = Modifier.size(8.dp)
                    ) {}
                }

                // Botão Central de Repetir quando o movimento ou transição termina
                if (isAnimationFinished) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        border = BorderStroke(1.5.dp, Color(0xFF00E5FF)),
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(56.dp)
                            .clip(CircleShape)
                            .clickable { replayTrigger++ }
                            .testTag("preview_replay_center_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = "Repetir",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                // Linha de Progresso do Movimento / Transição (Inicia no 0% e Termina no 100%)
                LinearProgressIndicator(
                    progress = { currentProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter),
                    color = if (isAnimationFinished) Color(0xFF10B981) else Color(0xFF00E5FF),
                    trackColor = Color.White.copy(alpha = 0.20f)
                )

                // Rótulo Flutuante Inferior com status de execução/conclusão
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when {
                                isAnimationFinished -> Icons.Default.CheckCircle
                                isPreviewingTransition -> Icons.Default.AutoAwesome
                                else -> Icons.Default.Animation
                            },
                            contentDescription = null,
                            tint = when {
                                isAnimationFinished -> Color(0xFF10B981)
                                isPreviewingTransition -> Color(0xFFFBBF24)
                                else -> Color(0xFF60A5FA)
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = when {
                                isAnimationFinished -> if (isPreviewingTransition)
                                    "Transição Concluída (100%) • Toque para repetir"
                                else
                                    "Movimento Concluído (100%) • Toque para repetir"
                                isPreviewingTransition ->
                                    "Transição: #${selectedTransition.id} ${selectedTransition.name} (${(currentProgress * 100).toInt()}%)"
                                else ->
                                    "Câmera: #${selectedMovement.id} ${selectedMovement.name} (${(currentProgress * 100).toInt()}%)"
                            },
                            color = Color.White,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
}

/**
 * Visualização da Transição Suave em tempo real no Compose
 */
@Composable
private fun PreviewTransitionEffectView(
    image1: ProjectImage,
    image2: ProjectImage,
    transition: TransitionEffect,
    movement: MovementEffect,
    progress: Float
) {
    val t = progress.coerceIn(0f, 1f)
    val isVideo1 = MediaHelper.isVideo(image1.filePath)
    val isVideo2 = MediaHelper.isVideo(image2.filePath)
    // A cena 1 está concluindo seu movimento contínuo enquanto a cena 2 já inicia seu movimento imediatamente sem esperar a transição acabar
    val camProgress1 = (0.7f + 0.3f * t).coerceIn(0f, 1f)
    val camProgress2 = (0.3f * t).coerceIn(0f, 1f)

    Box(modifier = Modifier.fillMaxSize()) {
        val file1 = File(image1.filePath)
        val file2 = File(image2.filePath)

        when (transition.id) {
            0 -> {
                // 0 = Sem Transição
                val activeFile = if (t < 0.5f) file1 else file2
                val activeIsVideo = if (t < 0.5f) isVideo1 else isVideo2
                val activeCamProgress = if (t < 0.5f) camProgress1 else camProgress2
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current).data(activeFile).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (!activeIsVideo) {
                                movement.applyToGraphicsLayer(this, activeCamProgress)
                            }
                        }
                )
            }
            1 -> {
                // 1 = Dissolvência Suave (Cada camada executa seu movimento de câmera em camada separada da transição)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - t }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file1).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo1) movement.applyToGraphicsLayer(this, camProgress1)
                            }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = t }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file2).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo2) movement.applyToGraphicsLayer(this, camProgress2)
                            }
                    )
                }
            }
            4 -> {
                // 4 = Zoom Suave In
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.0f + 0.15f * t
                            scaleY = 1.0f + 0.15f * t
                            alpha = 1f - t
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file1).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo1) movement.applyToGraphicsLayer(this, camProgress1)
                            }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 0.90f + 0.10f * t
                            scaleY = 0.90f + 0.10f * t
                            alpha = t
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file2).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo2) movement.applyToGraphicsLayer(this, camProgress2)
                            }
                    )
                }
            }
            5 -> {
                // 5 = Zoom Suave Out
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.0f - 0.12f * t
                            scaleY = 1.0f - 0.12f * t
                            alpha = 1f - t
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file1).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo1) movement.applyToGraphicsLayer(this, camProgress1)
                            }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.15f - 0.15f * (1f - t)
                            scaleY = 1.15f - 0.15f * (1f - t)
                            alpha = t
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file2).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo2) movement.applyToGraphicsLayer(this, camProgress2)
                            }
                    )
                }
            }
            10 -> {
                // 10 = Zoom Cruzado
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.0f + 0.20f * (t * t)
                            scaleY = 1.0f + 0.20f * (t * t)
                            alpha = 1f - t
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file1).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo1) movement.applyToGraphicsLayer(this, camProgress1)
                            }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = 0.85f + 0.15f * (1f - (1f - t) * (1f - t))
                            scaleX = s
                            scaleY = s
                            alpha = t
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file2).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo2) movement.applyToGraphicsLayer(this, camProgress2)
                            }
                    )
                }
            }
            else -> {
                // Demais transições suaves com curva não linear e micro-escala em camada independente do movimento de câmera
                val scale1 = 1.0f + 0.04f * sin(t * Math.PI.toFloat())
                val scale2 = 1.0f - 0.03f * sin((1f - t) * Math.PI.toFloat())
                val sCurve = t * t * (3f - 2f * t)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale1
                            scaleY = scale1
                            alpha = 1f - sCurve
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file1).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo1) movement.applyToGraphicsLayer(this, camProgress1)
                            }
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale2
                            scaleY = scale2
                            alpha = sCurve
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current).data(file2).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (!isVideo2) movement.applyToGraphicsLayer(this, camProgress2)
                            }
                    )
                }
            }
        }
    }
}
