package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SubtitlesOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SubtitleStyle
import com.example.engine.SubtitleEngine

/**
 * Barra Horizontal de Estilos de Legendas (Exibida ao clicar no botão "LEGENDAS" ao lado de "CTA"):
 * - Sem cabeçalho de texto extra acima da lista (exibe diretamente os modelos).
 * - Primeira opção: "Sem Legenda" (ID 0) para não aplicar nada.
 * - Seguido pelos 15 estilos profissionais (modelos de 2 linhas, modelos com animação de entrada
 *   e 5 modelos com fundo semi-transparente de cores diferentes).
 * - Ao clicar em um estilo, a pré-visualização com o texto de legenda de exemplo aparece
 *   centralizada no centro da tela da área de pré-visualização.
 */
@Composable
fun SubtitlesCarousel(
    selectedStyle: SubtitleStyle,
    isSubtitlesEnabledInMenu: Boolean,
    onSelectStyle: (SubtitleStyle) -> Unit,
    onOpenSubtitlesConfigInMenu: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val allOptions = SubtitleStyle.CAROUSEL_OPTIONS

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("subtitles_carousel_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .testTag("subtitles_horizontal_row"),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allOptions.forEach { style ->
                val isSelected = style.id == selectedStyle.id
                SubtitleStyleCardItem(
                    style = style,
                    isSelected = isSelected,
                    onClick = { onSelectStyle(style) }
                )
            }
        }
    }
}

@Composable
fun SubtitleStyleCardItem(
    style: SubtitleStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val demoBitmap = remember(style.id, style.name, style.backgroundColor, style.isTwoLineLayout) {
        SubtitleEngine.getStyleDemonstrationBitmap(style, width = 320, height = 148)
    }

    Card(
        modifier = modifier
            .width(156.dp)
            .height(116.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("subtitle_style_card_${style.id}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFF1F293D) else Color(0xFF141824)
        ),
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) Color(0xFFFFEA00) else Color(0xFF2B344B)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Imagem demonstrativa do estilo de legenda
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF0B0F19)),
                contentAlignment = Alignment.Center
            ) {
                if (style.id == 0) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SubtitlesOff,
                            contentDescription = "Sem Legenda",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "SEM LEGENDA",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                } else if (demoBitmap != null && !demoBitmap.isRecycled) {
                    Image(
                        bitmap = demoBitmap.asImageBitmap(),
                        contentDescription = "Demonstração ${style.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Badge superior esquerdo
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                ) {
                    Text(
                        text = if (style.id == 0) "OFF" else "#${style.id} • ${style.badge}",
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (style.id == 0) Color(0xFF94A3B8) else Color(0xFFFFEA00),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFFEA00))
                            .border(1.dp, Color.Black, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selecionado",
                            tint = Color.Black,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = style.name,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) Color(0xFFFFEA00) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = style.description,
                    fontSize = 8.5.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
