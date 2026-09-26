package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.CtaVideoItem
import com.example.engine.CtaVideoEngine
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Carrossel horizontal de Vídeos de CTA ("VIDEOS CTA"):
 * - O primeiro item é "SEM CTA" para não aplicar nenhum CTA.
 * - Nas opções de CTA não exibe o nome da CTA, mas sim apenas o ID da CTA: 1, 2, 3...
 * - Vídeos carregados da pasta do projeto "VIDEOS CTA" NÃO possuem botão X e não podem ser removidos.
 * - Apenas vídeos de CTA que o usuário fez upload usando o botão no app possuem o botão X para remover (com confirmação).
 * - Abaixo exibe um campo pequeno e responsivo com o tempo padrão "00:00" onde o usuário não digita:
 *   ao clicar, abre um Popup onde seleciona o tempo desejado e clica em "Salvar".
 */
@Composable
fun CtaVideosCarousel(
    availableCtas: List<CtaVideoItem>,
    selectedCta: CtaVideoItem,
    onSelectCta: (CtaVideoItem) -> Unit,
    onUploadCustomCta: () -> Unit,
    onDeleteCta: (Int) -> Unit,
    ctaStartTimeText: String,
    onCtaStartTimeChange: (String) -> Unit,
    ctaTimeError: String?,
    ctaUploadError: String?,
    modifier: Modifier = Modifier
) {
    var ctaToDelete by remember { mutableStateOf<CtaVideoItem?>(null) }
    var isTimePickerPopupOpen by remember { mutableStateOf(false) }

    val displayTimeFormatted = remember(ctaStartTimeText) {
        formatCtaTimeAsMmSs(ctaStartTimeText)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cta_videos_carousel_section")
    ) {
        // Cabeçalho compacto da seção CTA
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        text = "Vídeos de CTA",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Selecione um ID de CTA para exibir sem fundo na prévia e no vídeo final",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (selectedCta.id == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = if (selectedCta.id == 0) "SEM CTA" else "CTA ${selectedCta.id}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedCta.id == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Barra horizontal rolável: 0 (SEM CTA) + IDs 1, 2, 3... + Botão Upload
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("cta_videos_lazy_row")
        ) {
            items(availableCtas, key = { it.id }) { ctaItem ->
                val isSelected = ctaItem.id == selectedCta.id
                CtaVideoOptionCard(
                    cta = ctaItem,
                    isSelected = isSelected,
                    onClick = { onSelectCta(ctaItem) },
                    // SOMENTE vídeos enviados pelo usuário (isCustom == true) possuem o botão X para remover!
                    onDeleteClick = if (ctaItem.isCustom) {
                        { ctaToDelete = ctaItem }
                    } else null
                )
            }

            // Ao lado do último CTA: Botão para upload de vídeo próprio de CTA com verificação de Chroma Key
            item(key = "upload_custom_cta_item") {
                UploadCustomCtaCard(
                    onClick = onUploadCustomCta
                )
            }
        }

        // Mensagem de erro caso o vídeo de CTA enviado não possua fundo de cor sólida (Chroma Key)
        AnimatedVisibility(visible = ctaUploadError != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .testTag("cta_upload_solid_bg_error_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = ctaUploadError ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Área compacta com o CAMPO PEQUENO RESPONSIVO de tempo (padrão "00:00", selecionável via Popup ao clicar)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .testTag("cta_exact_time_card"),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(
                width = 1.dp,
                color = if (ctaTimeError != null) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
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
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Tempo de Exibição da CTA",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp
                            )
                            Text(
                                text = "Toque no campo ao lado para selecionar o tempo",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Campo pequeno responsivo com tempo padrão "00:00" que abre o Popup ao clicar
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF141824),
                        border = BorderStroke(
                            width = 1.4.dp,
                            color = if (ctaTimeError != null) MaterialTheme.colorScheme.error else Color(0xFF00E5FF)
                        ),
                        modifier = Modifier
                            .widthIn(min = 96.dp, max = 128.dp)
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { isTimePickerPopupOpen = true }
                            .testTag("input_cta_display_time")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = displayTimeFormatted,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 13.sp,
                                color = Color.White
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Selecionar tempo",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = ctaTimeError != null) {
                    Text(
                        text = ctaTimeError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 4.dp, start = 4.dp)
                            .testTag("cta_time_error_text")
                    )
                }
            }
        }
    }

    // Popup de Seleção de Tempo da CTA (quando o campo pequeno responsivo é clicado)
    if (isTimePickerPopupOpen) {
        val initialTotalSec = remember(ctaStartTimeText) {
            (CtaVideoEngine.parseCtaTimeSeconds(ctaStartTimeText) ?: 0f).coerceAtLeast(0f).toInt()
        }
        var selectedMinutes by remember { mutableIntStateOf((initialTotalSec / 60).coerceIn(0, 59)) }
        var selectedSeconds by remember { mutableIntStateOf((initialTotalSec % 60).coerceIn(0, 59)) }
        val previewFormatted = String.format(Locale.US, "%02d:%02d", selectedMinutes, selectedSeconds)

        Dialog(onDismissRequest = { isTimePickerPopupOpen = false }) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF141824)),
                border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.6f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("cta_time_picker_popup")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Selecionar Tempo da CTA",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Escolha o minuto e segundo em que a CTA será exibida",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Visor Grande do Tempo Selecionado
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0B0F19),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = previewFormatted,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 26.sp,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Seletores de Minutos e Segundos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Coluna de Minutos
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "MINUTOS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            selectedMinutes = if (selectedMinutes > 0) selectedMinutes - 1 else 59
                                        },
                                        modifier = Modifier.testTag("cta_time_min_minus")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Diminuir minuto",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", selectedMinutes),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            selectedMinutes = (selectedMinutes + 1) % 60
                                        },
                                        modifier = Modifier.testTag("cta_time_min_plus")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Aumentar minuto",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = ":",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF00E5FF)
                        )

                        // Coluna de Segundos
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "SEGUNDOS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            selectedSeconds = if (selectedSeconds > 0) selectedSeconds - 1 else 59
                                        },
                                        modifier = Modifier.testTag("cta_time_sec_minus")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Remove,
                                            contentDescription = "Diminuir segundo",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = String.format(Locale.US, "%02d", selectedSeconds),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            selectedSeconds = (selectedSeconds + 1) % 60
                                        },
                                        modifier = Modifier.testTag("cta_time_sec_plus")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Aumentar segundo",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Atalhos rápidos de seleção de tempo
                    val quickPresets = listOf(0 to 0, 0 to 2, 0 to 5, 0 to 8, 0 to 10, 0 to 15, 0 to 20, 0 to 30, 0 to 45, 1 to 0)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickPresets.forEach { (m, s) ->
                            val label = String.format(Locale.US, "%02d:%02d", m, s)
                            val isActive = selectedMinutes == m && selectedSeconds == s
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isActive) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedMinutes = m
                                        selectedSeconds = s
                                    }
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) Color.Black else Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Botões Cancelar e Salvar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isTimePickerPopupOpen = false },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cancelar", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                onCtaStartTimeChange(previewFormatted)
                                isTimePickerPopupOpen = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(42.dp)
                                .testTag("save_cta_time_popup_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00E676),
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Salvar",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Notificação / Diálogo para confirmar a exclusão do CTA personalizado enviado pelo usuário
    if (ctaToDelete != null) {
        val target = ctaToDelete!!
        AlertDialog(
            onDismissRequest = { ctaToDelete = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = "Confirmar Exclusão de CTA",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Deseja realmente excluir o vídeo de CTA ${target.id} da lista?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCta(target.id)
                        ctaToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_cta_button")
                ) {
                    Text("Confirmar Exclusão")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { ctaToDelete = null },
                    modifier = Modifier.testTag("cancel_delete_cta_button")
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

private fun formatCtaTimeAsMmSs(rawText: String): String {
    val trimmed = rawText.trim()
    if (trimmed.isEmpty()) return "00:00"
    val parsedSeconds = CtaVideoEngine.parseCtaTimeSeconds(trimmed) ?: return "00:00"
    val totalSecs = parsedSeconds.coerceAtLeast(0f).toInt()
    val mins = (totalSecs / 60).coerceIn(0, 99)
    val secs = (totalSecs % 60).coerceIn(0, 59)
    return String.format(Locale.US, "%02d:%02d", mins, secs)
}

@Composable
private fun CtaVideoOptionCard(
    cta: CtaVideoItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)?
) {
    val isNoCta = cta.id == 0
    val previewFrames = remember(cta.id, cta.filePath) {
        if (isNoCta) emptyList()
        else CtaVideoEngine.getTransparentFramesForCta(cta, targetWidth = 220, targetHeight = 124, frameCount = 6)
    }
    var currentFrameIdx by remember { mutableIntStateOf(0) }

    LaunchedEffect(previewFrames.size, isSelected) {
        if (previewFrames.size > 1) {
            while (true) {
                delay(if (isSelected) 160L else 260L)
                currentFrameIdx = (currentFrameIdx + 1) % previewFrames.size
            }
        }
    }

    Card(
        modifier = Modifier
            .width(126.dp)
            .height(98.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("cta_card_${cta.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            } else {
                Color(0xFF161A26)
            }
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2D3548)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isNoCta) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Block,
                        contentDescription = "Sem CTA",
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SEM CTA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
                    )
                    Text(
                        text = "Não aplicar",
                        fontSize = 9.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            } else {
                // Exibe diretamente o vídeo salvo na pasta "VIDEOS CTA" ou enviado pelo usuário
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF10131C)),
                    contentAlignment = Alignment.Center
                ) {
                    val activeFrame = previewFrames.getOrNull(currentFrameIdx % previewFrames.size.coerceAtLeast(1))
                    if (activeFrame != null && !activeFrame.isRecycled) {
                        Image(
                            bitmap = activeFrame.asImageBitmap(),
                            contentDescription = "CTA ${cta.id}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 14.dp, bottom = 20.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }

                // Rótulo superior com ID numérico (1, 2, 3...)
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "${cta.id}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Botão X para excluir APENAS vídeos de CTA carregados via upload pelo usuário (isCustom == true)
                if (onDeleteClick != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFD32F2F).copy(alpha = 0.90f))
                            .border(1.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                            .clickable { onDeleteClick() }
                            .testTag("delete_cta_button_${cta.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Excluir CTA ${cta.id}",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Rodapé exibindo apenas o ID da CTA (1, 2, 3...) sem exibir o nome do arquivo
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "${cta.id}",
                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadCustomCtaCard(
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(126.dp)
            .height(98.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .testTag("upload_custom_cta_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF182234)
        ),
        border = BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.UploadFile,
                contentDescription = "Carregar CTA próprio",
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "+ Upload CTA",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = "Fundo Sólido / Chroma",
                fontSize = 8.5.sp,
                color = Color(0xFF00E5FF),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
