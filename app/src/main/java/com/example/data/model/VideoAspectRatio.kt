package com.example.data.model

/**
 * Proporções de Tela (Aspect Ratio) suportadas com rolagem horizontal:
 * - 16:9 (Paisagem / Widescreen / YouTube / TV)
 * - 9:16 (Vertical / Stories / TikTok / Reels)
 * - 1:1 (Quadrado / Feed Instagram)
 * - 4:5 (Retrato Vertical / Feed Instagram Portrait)
 * - 21:9 (Cinema Ultra-Widescreen)
 * - 4:3 (Formato Clássico)
 * - 3:4 (Retrato Clássico)
 */
enum class VideoAspectRatio(
    val label: String,
    val ratio: Float,
    val subtitle: String,
    val widthRatio: Int,
    val heightRatio: Int
) {
    RATIO_16_9(
        label = "16:9",
        ratio = 16f / 9f,
        subtitle = "Paisagem Widescreen",
        widthRatio = 16,
        heightRatio = 9
    ),
    RATIO_9_16(
        label = "9:16",
        ratio = 9f / 16f,
        subtitle = "Vertical (Reels/TikTok)",
        widthRatio = 9,
        heightRatio = 16
    ),
    RATIO_1_1(
        label = "1:1",
        ratio = 1.0f,
        subtitle = "Quadrado (Feed)",
        widthRatio = 1,
        heightRatio = 1
    ),
    RATIO_4_5(
        label = "4:5",
        ratio = 4f / 5f,
        subtitle = "Retrato Vertical (Feed)",
        widthRatio = 4,
        heightRatio = 5
    ),
    RATIO_21_9(
        label = "21:9",
        ratio = 21f / 9f,
        subtitle = "Cinema Ultra-Wide",
        widthRatio = 21,
        heightRatio = 9
    ),
    RATIO_4_3(
        label = "4:3",
        ratio = 4f / 3f,
        subtitle = "Clássico Paisagem",
        widthRatio = 4,
        heightRatio = 3
    ),
    RATIO_3_4(
        label = "3:4",
        ratio = 3f / 4f,
        subtitle = "Clássico Retrato",
        widthRatio = 3,
        heightRatio = 4
    );

    /**
     * Calcula dimensões de vídeo pares (múltiplas de 2/16 para H.264 MediaCodec)
     * a partir de uma resolução de base.
     */
    fun calculateDimensions(resolution: VideoResolution): Pair<Int, Int> {
        val baseDim = when (resolution) {
            VideoResolution.RES_240P -> 240
            VideoResolution.RES_360P -> 360
            VideoResolution.RES_480P -> 480
            VideoResolution.RES_720P -> 720
        }

        return when (this) {
            RATIO_16_9 -> {
                val h = baseDim
                val w = makeEven((h * 16f / 9f).toInt())
                Pair(w, h)
            }
            RATIO_9_16 -> {
                val w = baseDim
                val h = makeEven((w * 16f / 9f).toInt())
                Pair(w, h)
            }
            RATIO_1_1 -> {
                val s = baseDim
                Pair(s, s)
            }
            RATIO_4_5 -> {
                val h = baseDim
                val w = makeEven((h * 4f / 5f).toInt())
                Pair(w, h)
            }
            RATIO_21_9 -> {
                val h = baseDim
                val w = makeEven((h * 21f / 9f).toInt())
                Pair(w, h)
            }
            RATIO_4_3 -> {
                val h = baseDim
                val w = makeEven((h * 4f / 3f).toInt())
                Pair(w, h)
            }
            RATIO_3_4 -> {
                val w = baseDim
                val h = makeEven((w * 4f / 3f).toInt())
                Pair(w, h)
            }
        }
    }

    private fun makeEven(value: Int): Int {
        val adjusted = if (value % 2 != 0) value + 1 else value
        return adjusted
    }

    companion object {
        val ALL: List<VideoAspectRatio> = listOf(
            RATIO_16_9,
            RATIO_9_16,
            RATIO_1_1,
            RATIO_4_5,
            RATIO_21_9,
            RATIO_4_3,
            RATIO_3_4
        )
        val DEFAULT: VideoAspectRatio = RATIO_16_9

        fun detectFromDimensions(width: Int, height: Int): VideoAspectRatio {
            if (width <= 0 || height <= 0) return DEFAULT
            val imgRatio = width.toFloat() / height.toFloat()
            return ALL.minByOrNull { kotlin.math.abs(it.ratio - imgRatio) } ?: DEFAULT
        }
    }
}
