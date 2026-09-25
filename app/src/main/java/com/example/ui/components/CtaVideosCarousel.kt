package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CtaVideoItem
import com.example.engine.CtaVideoEngine
import kotlinx.coroutines.delay

/**
 * Carrossel horizontal de Vídeos de CTA ("VIDEOS CTA"):
 * - O primeiro item é "SEM CTA" para não aplicar nenhum CTA.
 * - Seguido pelos vídeos reais/fake salvos na pasta "VIDEOS CTA" (CTA1.WEBM, CTA2.WEBM, CTA3.WEBM...).
 * - Ao lado do último CTA permite o Upload de um vídeo próprio de CTA com verificação e remoção
 *   automática de fundo sólido (Chroma Key).
 * - Cada CTA carregado possui um botão X para excluir com diálogo de confirmação.
 * - Abaixo exibe o campo obrigatório para informar o tempo exato (em segundos) em que o CTA
 *   deve ser exibido no vídeo final.
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
                        text = "Vídeos de CTA (Pasta VIDEOS CTA • .WEBM)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Selecione um CTA para exibir sem fundo na prévia e no vídeo final",
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
                    text = if (selectedCta.id == 0) "SEM CTA" else selectedCta.fileName,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (selectedCta.id == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Barra horizontal rolável com: 0 (SEM CTA) + CTA1.WEBM..CTA10.WEBM + CTAs enviados + Botão Upload
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
                    onDeleteClick = if (ctaItem.id != 0) {
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

        // Campo obrigatório abaixo da lista horizontal para preencher com o tempo exato em que a CTA deve ser exibida no vídeo final
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
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tempo Exato de Exibição do CTA no Vídeo Final (Obrigatório)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = ctaStartTimeText,
                    onValueChange = onCtaStartTimeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("input_cta_display_time"),
                    label = {
                        Text(
                            text = if (selectedCta.id != 0)
                                "Tempo exato em segundos para ${selectedCta.fileName} (ex: 3 ou 4.5)"
                            else
                                "Selecione um CTA acima e informe o tempo (segundos)",
                            fontSize = 11.sp
                        )
                    },
                    placeholder = { Text("Ex: 2.5 (segundos no vídeo final)", fontSize = 11.sp) },
                    singleLine = true,
                    isError = ctaTimeError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    trailingIcon = {
                        if (ctaTimeError != null) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = "Erro de tempo do CTA",
                                tint = MaterialTheme.colorScheme.error
                            )
                        } else if (ctaStartTimeText.isNotBlank() && CtaVideoEngine.parseCtaTimeSeconds(ctaStartTimeText) != null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Tempo válido",
                                tint = Color(0xFF00E676)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (ctaTimeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = if (ctaTimeError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(10.dp)
                )

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

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Dica: Toque no CTA exibido na tela de pré-visualização acima para arrastar a posição ou ajustar o tamanho.",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Notificação / Diálogo para confirmar a exclusão do CTA ao clicar no X
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
                    text = "Deseja realmente excluir o vídeo de CTA '${target.fileName}' da lista?"
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
                // Exibe diretamente o vídeo real salvo na pasta "VIDEOS CTA" (com fundo Chroma Key removido)
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
                            contentDescription = cta.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 14.dp, bottom = 20.dp, start = 4.dp, end = 4.dp)
                        )
                    }
                }

                // Rótulo superior com ID
                Surface(
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "#${cta.id}",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else Color.White,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }

                // Botão X para excluir o CTA carregado (exibe confirmação antes de excluir)
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
                            contentDescription = "Excluir ${cta.fileName}",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Rodapé exibindo o nome do arquivo salvo na pasta VIDEOS CTA (ex: CTA1.WEBM, CTA2.WEBM...)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = cta.fileName,
                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                        fontSize = 9.5.sp,
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
