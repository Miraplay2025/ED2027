package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.engine.TimelineAudioItem
import java.io.File

/**
 * Linha do Tempo Profissional de Vídeo:
 * - Régua de tempo com divisões em segundos
 * - Agulha de reprodução (Playhead) sincronizada
 * - Trilha de vídeo com clipes de mídia contendo miniatura real, número da imagem e duração
 * - Marcadores de transições interativas entre cada clipe
 * - Abaixo das mídias na linha do tempo: botão pequeno "Adicionar Áudio" que, ao selecionar um áudio válido,
 *   é ocultado e exibe o áudio de forma profissional na linha do tempo (com botão Play/Pause no início e opções Excluir/Alterar ao clicar sobre o áudio).
 */
@Composable
fun VideoTimelineTrack(
    images: List<ProjectImage>,
    currentImageIndex: Int,
    onSelectImage: (Int) -> Unit,
    onDeleteImage: (Long) -> Unit = {},
    selectedMovement: MovementEffect,
    selectedTransition: TransitionEffect,
    onSelectTransition: (TransitionEffect) -> Unit,
    isPlaying: Boolean,
    onAddMediaClick: () -> Unit,
    timelineAudio: TimelineAudioItem? = null,
    isTimelineAudioPlaying: Boolean = false,
    onAddOrChangeAudioClick: () -> Unit = {},
    onTogglePlayTimelineAudio: () -> Unit = {},
    onDeleteTimelineAudio: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var mediaToDelete by remember { mutableStateOf<Pair<Int, ProjectImage>?>(null) }
    var showAudioActionsMenu by remember(timelineAudio?.filePath) { mutableStateOf(false) }
    var showConfirmDeleteAudioDialog by remember { mutableStateOf(false) }

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
            // Cabeçalho da Linha do Tempo: Status (sem o tempo ao lado de TRILHA V1)
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
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1A1D28)
                ) {
                    Text(
                        text = "${images.size} Clipes de Mídia",
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

                            // Cartão do Clipe na Linha do Tempo (ao clicar seleciona e pergunta se confirma excluir a mídia)
                            TimelineClipCard(
                                projectImage = projectImage,
                                index = index,
                                isSelected = isSelected,
                                onClick = {
                                    onSelectImage(index)
                                    mediaToDelete = Pair(index + 1, projectImage)
                                },
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
                                    contentDescription = "Adicionar Mídia",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "+ Mídia",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // =========================================================================
            // TRILHA DE ÁUDIO ABAIXO DAS MÍDIAS NA LINHA DO TEMPO
            // - Se nenhum áudio estiver selecionado: exibe o botão pequeno "Adicionar Áudio"
            // - Ao selecionar um áudio válido: oculta o botão "Adicionar Áudio" e exibe o áudio
            //   de forma profissional na linha do tempo com botão Play/Pause no início e
            //   opções "Excluir" (com confirmação) e "Alterar" ao clicar sobre o áudio.
            // =========================================================================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                if (timelineAudio == null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF192231),
                        border = BorderStroke(1.dp, Color(0xFF00E676).copy(alpha = 0.55f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onAddOrChangeAudioClick() }
                            .testTag("timeline_add_audio_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = "Adicionar Áudio",
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Adicionar Áudio",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF00E676)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("timeline_audio_track_container")
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF142426),
                            border = BorderStroke(
                                width = if (showAudioActionsMenu) 1.8.dp else 1.2.dp,
                                color = if (isTimelineAudioPlaying) Color(0xFF00E676) else Color(0xFF2DD4BF).copy(alpha = 0.65f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("timeline_audio_track_bar")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // No início do áudio: ícone de Play que alterna imediatamente com Pause ao clicar
                                Surface(
                                    shape = CircleShape,
                                    color = if (isTimelineAudioPlaying) Color(0xFF00E676) else Color(0xFF0F172A),
                                    border = BorderStroke(1.dp, Color(0xFF00E676)),
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .clickable { onTogglePlayTimelineAudio() }
                                        .testTag("timeline_audio_play_pause_button")
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isTimelineAudioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isTimelineAudioPlaying) "Pausar Áudio" else "Reproduzir Áudio",
                                            tint = if (isTimelineAudioPlaying) Color.Black else Color(0xFF00E676),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                // Corpo clicável do áudio na linha do tempo: ao clicar abre as duas opções EXCLUIR e ALTERAR
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { showAudioActionsMenu = !showAudioActionsMenu }
                                        .padding(vertical = 2.dp)
                                        .testTag("timeline_audio_body_clickable"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = Color(0xFF00E676),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = timelineAudio.displayName,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = if (isTimelineAudioPlaying) {
                                                    "Reproduzindo na Linha do Tempo • Toque para opções"
                                                } else {
                                                    "Trilha de Áudio Principal • Toque no áudio para Excluir ou Alterar"
                                                },
                                                fontSize = 9.sp,
                                                color = Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF0B1319)
                                    ) {
                                        Text(
                                            text = "♪ ${timelineAudio.formattedDuration}",
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF00E676),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Duas opções exibidas ao clicar sobre o áudio: EXCLUIR e ALTERAR
                        AnimatedVisibility(visible = showAudioActionsMenu) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .testTag("timeline_audio_options_row"),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showAudioActionsMenu = false
                                        onAddOrChangeAudioClick()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .testTag("timeline_audio_option_change")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Alterar",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00E5FF)
                                    )
                                }

                                Button(
                                    onClick = {
                                        showConfirmDeleteAudioDialog = true
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFD32F2F),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                        .testTag("timeline_audio_option_delete")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Excluir",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Notificação / Diálogo de confirmação para excluir todo o áudio feito upload na linha do tempo
    if (showConfirmDeleteAudioDialog && timelineAudio != null) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAudioDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Confirmar Exclusão de Áudio",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Deseja realmente excluir todo o áudio '${timelineAudio.displayName}' carregado na linha do tempo?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDeleteAudioDialog = false
                        showAudioActionsMenu = false
                        onDeleteTimelineAudio()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_timeline_audio_button")
                ) {
                    Text("Confirmar Exclusão")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmDeleteAudioDialog = false },
                    modifier = Modifier.testTag("cancel_delete_timeline_audio_button")
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Notificação / Diálogo de confirmação para excluir a mídia carregada ao clicar nela
    if (mediaToDelete != null) {
        val (displayId, targetMedia) = mediaToDelete!!
        AlertDialog(
            onDismissRequest = { mediaToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Confirmar Exclusão de Mídia",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Confirma excluir a Mídia #$displayId (${targetMedia.originalFileName})? A contagem de IDs será reorganizada automaticamente para se manter em ordem."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteImage(targetMedia.id)
                        mediaToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_media_button")
                ) {
                    Text("Confirmar Exclusão")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { mediaToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_media_button")
                ) {
                    Text("Cancelar")
                }
            }
        )
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
