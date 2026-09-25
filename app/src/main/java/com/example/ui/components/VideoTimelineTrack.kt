package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.MovementEffect
import com.example.data.model.ProjectImage
import com.example.data.model.TransitionEffect
import java.io.File

/**
 * Linha do Tempo Profissional de Vídeo estilo DaVinci / Premiere / CapCut:
 * - Régua de tempo com divisões em segundos
 * - Agulha de reprodução (Playhead) sincronizada
 * - Trilha de vídeo com clipes de mídia contendo miniatura real, número da imagem e duração
 * - Marcadores de transições interativas entre cada clipe
 * - Seleção de clipe instantânea refletida na área de pré-visualização única
 */
@Composable
fun VideoTimelineTrack(
    images: List<ProjectImage>,
    currentImageIndex: Int,
    onSelectImage: (Int) -> Unit,
    selectedMovement: MovementEffect,
    selectedTransition: TransitionEffect,
    onSelectTransition: (TransitionEffect) -> Unit,
    isPlaying: Boolean,
    onAddMediaClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    // Animação da agulha de reprodução na régua quando em play
    val infiniteTransition = rememberInfiniteTransition(label = "timeline_playhead")
    val playheadOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "playhead_pos"
    )

    // Cálculo do tempo estimado total (cada imagem ~6s por padrão na timeline visual)
    val totalSeconds = (images.size * 6.0f).coerceAtLeast(6.0f)
    val currentSeconds = ((currentImageIndex * 6.0f) + (if (isPlaying) playheadOffset * 6.0f else 0.0f))
        .coerceIn(0.0f, totalSeconds)

    val timeFormatted = String.format(
        java.util.Locale.US,
        "%02d:%04.1fs",
        (currentSeconds / 60).toInt(),
        currentSeconds % 60
    )
    val totalTimeFormatted = String.format(
        java.util.Locale.US,
        "%02d:%04.1fs",
        (totalSeconds / 60).toInt(),
        totalSeconds % 60
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_timeline_track"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF12141C) // Fundo estúdio dark profissional
        ),
        border = BorderStroke(1.dp, Color(0xFF2A2E3D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // Cabeçalho da Linha do Tempo: Timecode & Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF1E2230),
                        border = BorderStroke(1.dp, Color(0xFF33384C))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) Color(0xFF00E676) else Color(0xFFFF5252))
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "TRILHA V1",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "$timeFormatted / $totalTimeFormatted",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E5FF)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1A1D28)
                ) {
                    Text(
                        text = "${images.size} Clipes de Imagem",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFB0B7C6),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Régua de Tempo (Timeline Ruler)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .background(Color(0xFF0A0C12))
                    .border(BorderStroke(0.5.dp, Color(0xFF1F2332)))
                    .horizontalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier.padding(start = 14.dp, end = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val countRuler = (images.size * 3).coerceAtLeast(10)
                    for (i in 0..countRuler) {
                        val sec = i * 2
                        Column(
                            horizontalAlignment = Alignment.Start,
                            modifier = Modifier.width(48.dp)
                        ) {
                            Text(
                                text = "${sec}s",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF6B7280)
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(6.dp)
                                    .background(Color(0xFF4B5563))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Trilha de Clipes de Mídia com Agulha Indicadora e Transições Intercaladas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(98.dp)
                    .horizontalScroll(scrollState)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 14.dp)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (images.isEmpty()) {
                        Surface(
                            modifier = Modifier
                                .width(220.dp)
                                .height(82.dp)
                                .clickable { onAddMediaClick() },
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E2230),
                            border = BorderStroke(1.dp, Color(0xFF3B4256))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Importar Fotos",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Clique para adicionar",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    } else {
                        images.forEachIndexed { index, projectImage ->
                            val isSelected = index == currentImageIndex

                            // Cartão do Clipe na Linha do Tempo
                            TimelineClipCard(
                                projectImage = projectImage,
                                index = index,
                                isSelected = isSelected,
                                onClick = { onSelectImage(index) },
                                movementName = if (isSelected) selectedMovement.name else "Animação"
                            )

                            // Marcador da Transição entre Clipes (entre imagem N e N+1)
                            if (index < images.size - 1) {
                                TimelineTransitionMarker(
                                    transition = selectedTransition,
                                    onClick = {
                                        onSelectTransition(selectedTransition)
                                    }
                                )
                            }
                        }

                        // Botão de Adicionar Mais Mídia na Linha do Tempo
                        Surface(
                            modifier = Modifier
                                .width(64.dp)
                                .height(82.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAddMediaClick() }
                                .testTag("timeline_add_media_button"),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF1A1D27),
                            border = BorderStroke(1.dp, Color(0xFF2E3448))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxHeight(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Adicionar Foto",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "+ Foto",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineClipCard(
    projectImage: ProjectImage,
    index: Int,
    isSelected: Boolean,
    movementName: String,
    onClick: () -> Unit
) {
    val file = File(projectImage.filePath)

    Surface(
        modifier = Modifier
            .width(135.dp)
            .height(82.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .testTag("timeline_clip_$index"),
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF1B2A3D) else Color(0xFF181B26),
        border = if (isSelected) {
            BorderStroke(2.dp, Color(0xFF00E5FF))
        } else {
            BorderStroke(1.dp, Color(0xFF2C3246))
        }
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniatura da Foto com ContentScale.Crop
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black)
            ) {
                if (file.exists()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(file)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Miniatura ${index + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(54.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier.size(54.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Tag Numérica IMAGEM X
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xCC000000),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "#${index + 1}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black else Color.White,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Metadados do Clipe
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "IMG ${index + 1}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = movementName,
                    fontSize = 9.sp,
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF9CA3AF),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF10131B)
                ) {
                    Text(
                        text = "⏱ 6.0s",
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF00E676),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineTransitionMarker(
    transition: TransitionEffect,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag("timeline_transition_marker"),
        shape = CircleShape,
        color = Color(0xFF231B38),
        border = BorderStroke(1.5.dp, Color(0xFFBB86FC)),
        shadowElevation = 3.dp
    ) {
        Box(
            modifier = Modifier.fillMaxHeight(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = "Transição",
                tint = Color(0xFFBB86FC),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
