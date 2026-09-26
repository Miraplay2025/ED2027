package com.example.data.model

data class ParsedSubtitleItem(
    val startTimeSeconds: Float,
    val endTimeSeconds: Float,
    val startTimeFormatted: String,
    val endTimeFormatted: String,
    val text: String,
    val rawToken: String
)

enum class SubtitleBackgroundType(val rawValue: String) {
    TRANSPARENT("transparent"),
    BOX("box"),
    BAR("bar"),
    TEXT_WIDTH_BOX("text_width_box")
}

enum class SubtitleFontWeight(val rawValue: String) {
    LIGHT("Light"),
    NORMAL("Normal"),
    BOLD("Bold")
}

enum class SubtitleEntryAnimation(val rawValue: String, val label: String) {
    NONE("none", "Sem Animação"),
    FADE_IN("fade-in", "Fade-In"),
    SLIDE_UP("slide-up", "Slide-Up"),
    SCALE_UP("scale-up", "Scale-Up")
}

data class SubtitleStyle(
    val id: Int,
    val name: String,
    val badge: String,
    val description: String,
    val maxLines: Int = 1,
    val fontFamily: String = "Inter",
    val fontWeight: SubtitleFontWeight = SubtitleFontWeight.NORMAL,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundType: SubtitleBackgroundType = SubtitleBackgroundType.TRANSPARENT,
    val backgroundColorHex: String = "none",
    val backgroundOpacity: Float = 0.0f,
    val backgroundColor: Int? = null,
    val textShadowSpec: String = "none",
    val hasTextShadow: Boolean = false,
    val shadowDx: Float = 0f,
    val shadowDy: Float = 0f,
    val shadowRadius: Float = 0f,
    val shadowColor: Int = 0x00000000,
    val borderRadiusPx: Float = 0f,
    val isUppercase: Boolean = false,
    val entryAnimation: SubtitleEntryAnimation = SubtitleEntryAnimation.NONE,
    val exitAnimation: String = "none"
) {
    val isTwoLineLayout: Boolean
        get() = maxLines >= 2

    companion object {
        const val DEFAULT_STYLE_ID = 100

        private fun argbFromHexAndOpacity(hex: String, opacity: Float): Int? {
            if (hex.equals("none", ignoreCase = true) || opacity <= 0f) return null
            val clean = hex.trim().removePrefix("#")
            val rgb = clean.toIntOrNull(16) ?: return null
            val alpha = (opacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
            return (alpha shl 24) or (rgb and 0x00FFFFFF)
        }

        val NO_SUBTITLE = SubtitleStyle(
            id = 0,
            name = "Sem Legenda",
            badge = "OFF",
            description = "Não aplicar nenhuma legenda",
            maxLines = 1,
            fontFamily = "Inter",
            fontWeight = SubtitleFontWeight.NORMAL,
            textColor = 0xFFFFFFFF.toInt(),
            backgroundType = SubtitleBackgroundType.TRANSPARENT,
            backgroundColorHex = "none",
            backgroundOpacity = 0.0f,
            backgroundColor = null,
            textShadowSpec = "none",
            hasTextShadow = false,
            borderRadiusPx = 0f,
            isUppercase = false,
            entryAnimation = SubtitleEntryAnimation.NONE,
            exitAnimation = "none"
        )

        /**
         * Opção de Legenda Padrão exibida exatamente ao lado da opção "Sem Legenda":
         * "nome": "Legenda Padrão com Fundo",
         * "max_lines": 2,
         * "font_family": "Arial",
         * "text_color": "#FFFFFF",
         * "background_type": "box",
         * "background_color": "#000000",
         * "background_opacity": 0.5,
         * "entry_animation": "none",
         * "exit_animation": "none"
         */
        val DEFAULT_STYLE = SubtitleStyle(
            id = DEFAULT_STYLE_ID,
            name = "Legenda Padrão com Fundo",
            badge = "PADRÃO • 2 LINHAS",
            description = "Arial • Box #000000 (50%) • 2 Linhas",
            maxLines = 2,
            fontFamily = "Arial",
            fontWeight = SubtitleFontWeight.NORMAL,
            textColor = 0xFFFFFFFF.toInt(),
            backgroundType = SubtitleBackgroundType.BOX,
            backgroundColorHex = "#000000",
            backgroundOpacity = 0.5f,
            backgroundColor = argbFromHexAndOpacity("#000000", 0.5f),
            textShadowSpec = "none",
            hasTextShadow = false,
            borderRadiusPx = 4f,
            isUppercase = false,
            entryAnimation = SubtitleEntryAnimation.NONE,
            exitAnimation = "none"
        )

        /**
         * Os 15 Modelos Visuais de Legendas especificados:
         */
        val ALL_15_MODELS: List<SubtitleStyle> = listOf(
            SubtitleStyle(
                id = 1,
                name = "Minimalista Profissional",
                badge = "1 LINHA • FADE-IN",
                description = "Inter • Transparente • Sombra suave",
                maxLines = 1,
                fontFamily = "Inter",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.TRANSPARENT,
                backgroundColorHex = "none",
                backgroundOpacity = 0.0f,
                backgroundColor = null,
                textShadowSpec = "0px 2px 4px rgba(0, 0, 0, 0.3)",
                hasTextShadow = true,
                shadowDx = 0f,
                shadowDy = 2f,
                shadowRadius = 4f,
                shadowColor = 0x4D000000,
                borderRadiusPx = 0f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.FADE_IN
            ),
            SubtitleStyle(
                id = 2,
                name = "Corporativo Elegante",
                badge = "2 LINHAS • BOX",
                description = "Roboto • Azul Marinho (#001F3F 40%)",
                maxLines = 2,
                fontFamily = "Roboto",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#001F3F",
                backgroundOpacity = 0.4f,
                backgroundColor = argbFromHexAndOpacity("#001F3F", 0.4f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 4f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.NONE
            ),
            SubtitleStyle(
                id = 3,
                name = "Documentário Premium",
                badge = "2 LINHAS • BAR",
                description = "Open Sans • Barra Preta (#000000 50%)",
                maxLines = 2,
                fontFamily = "Open Sans",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BAR,
                backgroundColorHex = "#000000",
                backgroundOpacity = 0.5f,
                backgroundColor = argbFromHexAndOpacity("#000000", 0.5f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 0f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.NONE
            ),
            SubtitleStyle(
                id = 4,
                name = "Shorts Clean",
                badge = "1 LINHA • SLIDE-UP",
                description = "Montserrat Bold • Box Preto 30%",
                maxLines = 1,
                fontFamily = "Montserrat",
                fontWeight = SubtitleFontWeight.BOLD,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#000000",
                backgroundOpacity = 0.3f,
                backgroundColor = argbFromHexAndOpacity("#000000", 0.3f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 6f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.SLIDE_UP
            ),
            SubtitleStyle(
                id = 5,
                name = "Cinema Moderno",
                badge = "2 LINHAS • CINEMA",
                description = "Arial • #E5E5E5 • Sombra suave",
                maxLines = 2,
                fontFamily = "Arial",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFFE5E5E5.toInt(),
                backgroundType = SubtitleBackgroundType.TRANSPARENT,
                backgroundColorHex = "none",
                backgroundOpacity = 0.0f,
                backgroundColor = null,
                textShadowSpec = "1px 1px 3px rgba(0, 0, 0, 0.4)",
                hasTextShadow = true,
                shadowDx = 1f,
                shadowDy = 1f,
                shadowRadius = 3f,
                shadowColor = 0x66000000,
                borderRadiusPx = 0f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.NONE
            ),
            SubtitleStyle(
                id = 6,
                name = "Entrevista Executiva",
                badge = "1 LINHA • FADE-IN",
                description = "Poppins • Box #2C2C2C 45% • Raio 12px",
                maxLines = 1,
                fontFamily = "Poppins",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#2C2C2C",
                backgroundOpacity = 0.45f,
                backgroundColor = argbFromHexAndOpacity("#2C2C2C", 0.45f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 12f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.FADE_IN
            ),
            SubtitleStyle(
                id = 7,
                name = "Estilo Tech / Premium",
                badge = "2 LINHAS • TECH",
                description = "Ubuntu • Box #1C1C1E 50% • Raio 8px",
                maxLines = 2,
                fontFamily = "Ubuntu",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#1C1C1E",
                backgroundOpacity = 0.5f,
                backgroundColor = argbFromHexAndOpacity("#1C1C1E", 0.5f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 8f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.NONE
            ),
            SubtitleStyle(
                id = 8,
                name = "Masterclass Educacional",
                badge = "2 LINHAS • CLARO",
                description = "Inter • Texto #000000 • Box #FFFFFF 60%",
                maxLines = 2,
                fontFamily = "Inter",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFF000000.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#FFFFFF",
                backgroundOpacity = 0.6f,
                backgroundColor = argbFromHexAndOpacity("#FFFFFF", 0.6f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 6f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.NONE
            ),
            SubtitleStyle(
                id = 9,
                name = "Vlog de Luxo",
                badge = "1 LINHA • FADE-IN",
                description = "Montserrat Light • Dourado #D4AF37",
                maxLines = 1,
                fontFamily = "Montserrat",
                fontWeight = SubtitleFontWeight.LIGHT,
                textColor = 0xFFD4AF37.toInt(),
                backgroundType = SubtitleBackgroundType.TRANSPARENT,
                backgroundColorHex = "none",
                backgroundOpacity = 0.0f,
                backgroundColor = null,
                textShadowSpec = "0px 1px 2px rgba(0, 0, 0, 0.2)",
                hasTextShadow = true,
                shadowDx = 0f,
                shadowDy = 1f,
                shadowRadius = 2f,
                shadowColor = 0x33000000,
                borderRadiusPx = 0f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.FADE_IN
            ),
            SubtitleStyle(
                id = 10,
                name = "Finanças / Business",
                badge = "2 LINHAS • BUSINESS",
                description = "Segoe UI • Box Verde #0B3C26 40%",
                maxLines = 2,
                fontFamily = "Segoe UI",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#0B3C26",
                backgroundOpacity = 0.4f,
                backgroundColor = argbFromHexAndOpacity("#0B3C26", 0.4f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 4f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.NONE
            ),
            SubtitleStyle(
                id = 11,
                name = "Noticiário Clean",
                badge = "2 LINHAS • NEWS",
                description = "Helvetica • Box Preto 55% • Raio 0px",
                maxLines = 2,
                fontFamily = "Helvetica",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#000000",
                backgroundOpacity = 0.55f,
                backgroundColor = argbFromHexAndOpacity("#000000", 0.55f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 0f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.NONE
            ),
            SubtitleStyle(
                id = 12,
                name = "Social Clip Bold",
                badge = "1 LINHA • SCALE-UP",
                description = "Bebas Neue Uppercase • Text Width Box 50%",
                maxLines = 1,
                fontFamily = "Bebas Neue",
                fontWeight = SubtitleFontWeight.BOLD,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.TEXT_WIDTH_BOX,
                backgroundColorHex = "#000000",
                backgroundOpacity = 0.5f,
                backgroundColor = argbFromHexAndOpacity("#000000", 0.5f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 2f,
                isUppercase = true,
                entryAnimation = SubtitleEntryAnimation.SCALE_UP
            ),
            SubtitleStyle(
                id = 13,
                name = "Estilo Estúdio / Podcast",
                badge = "1 LINHA • SLIDE-UP",
                description = "Poppins Bold • Box Vinho #58111A 50%",
                maxLines = 1,
                fontFamily = "Poppins",
                fontWeight = SubtitleFontWeight.BOLD,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#58111A",
                backgroundOpacity = 0.5f,
                backgroundColor = argbFromHexAndOpacity("#58111A", 0.5f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 6f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.SLIDE_UP
            ),
            SubtitleStyle(
                id = 14,
                name = "Minimal Dark",
                badge = "2 LINHAS • DARK",
                description = "Roboto Light • Box #121212 50% • Raio 4px",
                maxLines = 2,
                fontFamily = "Roboto",
                fontWeight = SubtitleFontWeight.LIGHT,
                textColor = 0xFFFFFFFF.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#121212",
                backgroundOpacity = 0.5f,
                backgroundColor = argbFromHexAndOpacity("#121212", 0.5f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 4f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.NONE
            ),
            SubtitleStyle(
                id = 15,
                name = "Creative Agency",
                badge = "1 LINHA • FADE-IN",
                description = "Montserrat • Texto #1A1A1A • Pílula 20px",
                maxLines = 1,
                fontFamily = "Montserrat",
                fontWeight = SubtitleFontWeight.NORMAL,
                textColor = 0xFF1A1A1A.toInt(),
                backgroundType = SubtitleBackgroundType.BOX,
                backgroundColorHex = "#FFFFFF",
                backgroundOpacity = 0.5f,
                backgroundColor = argbFromHexAndOpacity("#FFFFFF", 0.5f),
                textShadowSpec = "none",
                hasTextShadow = false,
                borderRadiusPx = 20f,
                isUppercase = false,
                entryAnimation = SubtitleEntryAnimation.FADE_IN
            )
        )

        /**
         * Na barra horizontal e no menu:
         * 1º Sem Legenda -> ao lado: Legenda Padrão com Fundo -> seguido pelos 15 modelos visuais.
         */
        val CAROUSEL_OPTIONS: List<SubtitleStyle> = listOf(NO_SUBTITLE, DEFAULT_STYLE) + ALL_15_MODELS

        val MENU_MODELS: List<SubtitleStyle> = listOf(DEFAULT_STYLE) + ALL_15_MODELS

        fun findById(id: Int): SubtitleStyle {
            if (id == 0) return NO_SUBTITLE
            if (id == DEFAULT_STYLE_ID) return DEFAULT_STYLE
            return ALL_15_MODELS.find { it.id == id } ?: DEFAULT_STYLE
        }

        fun getEffectiveRenderStyle(id: Int): SubtitleStyle {
            if (id == DEFAULT_STYLE_ID) return DEFAULT_STYLE
            return ALL_15_MODELS.find { it.id == id } ?: DEFAULT_STYLE
        }
    }
}
