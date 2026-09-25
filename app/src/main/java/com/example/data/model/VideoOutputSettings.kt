package com.example.data.model

/**
 * Resoluções de Vídeo especificadas para o aplicativo:
 * - 240p (320x240)
 * - 360p / 340p (640x360)
 * - 480p (854x480)
 * - 720p (1280x720) [HD]
 */
enum class VideoResolution(
    val label: String,
    val width: Int,
    val height: Int,
    val subtitle: String
) {
    RES_240P(
        label = "240p (320x240)",
        width = 320,
        height = 240,
        subtitle = "Ultra leve • Processamento ultra rápido"
    ),
    RES_360P(
        label = "360p / 340p (640x360)",
        width = 640,
        height = 360,
        subtitle = "Econômico • Ideal para redes sociais"
    ),
    RES_480P(
        label = "480p (854x480)",
        width = 854,
        height = 480,
        subtitle = "Definição padrão SD • Bom equilíbrio"
    ),
    RES_720P(
        label = "720p (1280x720) [HD]",
        width = 1280,
        height = 720,
        subtitle = "Alta definição HD • Qualidade recomendada"
    );

    companion object {
        val ALL: List<VideoResolution> = listOf(RES_240P, RES_360P, RES_480P, RES_720P)
        val DEFAULT: VideoResolution = RES_720P

        fun fromLabel(label: String): VideoResolution =
            ALL.firstOrNull { it.label == label } ?: DEFAULT
    }
}

/**
 * Configurações de Taxa de Quadros (FPS):
 * - 24 FPS (Cinematográfico)
 * - 30 FPS (Padrão)
 * - 60 FPS (Fluidez Máxima)
 */
enum class VideoFps(
    val fps: Int,
    val label: String,
    val subtitle: String
) {
    FPS_24(24, "24 FPS", "Cinematográfico"),
    FPS_30(30, "30 FPS", "Padrão"),
    FPS_60(60, "60 FPS", "Fluidez Máxima");

    companion object {
        val ALL: List<VideoFps> = listOf(FPS_24, FPS_30, FPS_60)
        val DEFAULT: VideoFps = FPS_30

        fun fromFps(fps: Int): VideoFps =
            ALL.firstOrNull { it.fps == fps } ?: DEFAULT
    }
}

/**
 * Presets de Taxa de Bits (Bitrate):
 * - 1 Mbps
 * - 2.5 Mbps
 * - 5 Mbps
 * - 8 Mbps
 */
enum class VideoBitratePreset(
    val mbps: Float,
    val label: String,
    val subtitle: String
) {
    MBPS_1(1.0f, "1 Mbps", "Economia de espaço"),
    MBPS_2_5(2.5f, "2.5 Mbps", "Equilibrado"),
    MBPS_5(5.0f, "5 Mbps", "Recomendado HD"),
    MBPS_8(8.0f, "8 Mbps", "Alta Qualidade");

    val bps: Int get() = (mbps * 1_000_000).toInt()

    companion object {
        val ALL: List<VideoBitratePreset> = listOf(MBPS_1, MBPS_2_5, MBPS_5, MBPS_8)
        val DEFAULT: VideoBitratePreset = MBPS_5

        fun fromMbps(mbps: Float): VideoBitratePreset? =
            ALL.firstOrNull { kotlin.math.abs(it.mbps - mbps) < 0.05f }
    }
}

/**
 * Configuração consolidada de saída de vídeo
 */
data class VideoOutputConfig(
    val resolution: VideoResolution = VideoResolution.DEFAULT,
    val aspectRatio: VideoAspectRatio = VideoAspectRatio.DEFAULT,
    val fps: Int = VideoFps.DEFAULT.fps,
    val bitrateMbps: Float = VideoBitratePreset.DEFAULT.mbps
) {
    private val dimensions: Pair<Int, Int> get() = aspectRatio.calculateDimensions(resolution)
    val width: Int get() = dimensions.first
    val height: Int get() = dimensions.second
    val bitrateBps: Int get() = (bitrateMbps * 1_000_000).toInt()
    val resolutionLabel: String get() = "${resolution.label} (${aspectRatio.label})"
}
