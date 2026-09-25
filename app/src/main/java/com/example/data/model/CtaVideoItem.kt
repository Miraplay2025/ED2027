package com.example.data.model

import android.graphics.Color

data class CtaVideoItem(
    val id: Int,
    val name: String,
    val fileName: String,
    val badgeText: String,
    val description: String,
    val filePath: String? = null,
    val isCustom: Boolean = false,
    val keyColor: Int = Color.GREEN,
    val accentColorHex: Int = 0xFFFF1744.toInt(),
    val secondaryColorHex: Int = 0xFFFFD600.toInt()
) {
    companion object {
        val NO_CTA = CtaVideoItem(
            id = 0,
            name = "SEM CTA",
            fileName = "SEM CTA",
            badgeText = "NENHUM",
            description = "Não aplicar nenhum vídeo de CTA no projeto.",
            filePath = null,
            isCustom = false,
            keyColor = Color.TRANSPARENT
        )
        val NONE = NO_CTA

        /**
         * Os 10 vídeos de CTA da pasta "VIDEOS CTA" (CTA1.WEBM a CTA10.WEBM),
         * precedidos pela opção 0 = "SEM CTA".
         */
        val DEFAULT_FOLDER_CTAS: List<CtaVideoItem> = listOf(
            CtaVideoItem(
                id = 1,
                name = "CTA1.WEBM",
                fileName = "CTA1.WEBM",
                badgeText = "INSCREVA-SE",
                description = "Botão animado de Inscreva-se com fundo Chroma Key.",
                accentColorHex = 0xFFFF1744.toInt(),
                secondaryColorHex = 0xFFFFFFFF.toInt()
            ),
            CtaVideoItem(
                id = 2,
                name = "CTA2.WEBM",
                fileName = "CTA2.WEBM",
                badgeText = "DEIXE O LIKE",
                description = "Animação de Curtir / Like dinâmico para engajamento.",
                accentColorHex = 0xFF00B0FF.toInt(),
                secondaryColorHex = 0xFFFFEA00.toInt()
            ),
            CtaVideoItem(
                id = 3,
                name = "CTA3.WEBM",
                fileName = "CTA3.WEBM",
                badgeText = "ATIVE O SININHO",
                description = "Sininho de notificações pulsando em destaque.",
                accentColorHex = 0xFFFFAB00.toInt(),
                secondaryColorHex = 0xFFFFFFFF.toInt()
            ),
            CtaVideoItem(
                id = 4,
                name = "CTA4.WEBM",
                fileName = "CTA4.WEBM",
                badgeText = "COMPARTILHE",
                description = "Chamada visual para compartilhar o vídeo.",
                accentColorHex = 0xFF00E676.toInt(),
                secondaryColorHex = 0xFF1DE9B6.toInt()
            ),
            CtaVideoItem(
                id = 5,
                name = "CTA5.WEBM",
                fileName = "CTA5.WEBM",
                badgeText = "SIGA O PERFIL",
                description = "CTA moderno para seguir nas redes sociais.",
                accentColorHex = 0xFFD500F9.toInt(),
                secondaryColorHex = 0xFF00E5FF.toInt()
            ),
            CtaVideoItem(
                id = 6,
                name = "CTA6.WEBM",
                fileName = "CTA6.WEBM",
                badgeText = "COMENTE AQUI",
                description = "Balão animado convidando o público a comentar.",
                accentColorHex = 0xFF2979FF.toInt(),
                secondaryColorHex = 0xFFFFFFFF.toInt()
            ),
            CtaVideoItem(
                id = 7,
                name = "CTA7.WEBM",
                fileName = "CTA7.WEBM",
                badgeText = "LINK NA BIO",
                description = "Seta e botão apontando para o Link na Bio/Descrição.",
                accentColorHex = 0xFFFF3D00.toInt(),
                secondaryColorHex = 0xFFFFD600.toInt()
            ),
            CtaVideoItem(
                id = 8,
                name = "CTA8.WEBM",
                fileName = "CTA8.WEBM",
                badgeText = "COMPRE AGORA",
                description = "Selos de oferta e chamada direta para conversão.",
                accentColorHex = 0xFF00C853.toInt(),
                secondaryColorHex = 0xFFFFEA00.toInt()
            ),
            CtaVideoItem(
                id = 9,
                name = "CTA9.WEBM",
                fileName = "CTA9.WEBM",
                badgeText = "SAIBA MAIS",
                description = "Banner interativo de Saiba Mais com brilho animado.",
                accentColorHex = 0xFF651FFF.toInt(),
                secondaryColorHex = 0xFF00E5FF.toInt()
            ),
            CtaVideoItem(
                id = 10,
                name = "CTA10.WEBM",
                fileName = "CTA10.WEBM",
                badgeText = "ASSISTA ATÉ O FIM",
                description = "Alerta visual dinâmico de retenção de audiência.",
                accentColorHex = 0xFFFF1744.toInt(),
                secondaryColorHex = 0xFFFF9100.toInt()
            )
        )

        val BUILT_IN_OPTIONS: List<CtaVideoItem> = listOf(NO_CTA) + DEFAULT_FOLDER_CTAS
    }
}
