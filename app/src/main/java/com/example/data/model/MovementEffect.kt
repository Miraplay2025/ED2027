package com.example.data.model

import android.graphics.Matrix
import androidx.compose.ui.graphics.GraphicsLayerScope
import kotlin.math.cos
import kotlin.math.sin

/**
 * Catálogo completo de movimentos e animações de câmera (IDs 0 a 26).
 * Contém os 11 originais + 16 novas animações hiper profissionais utilizadas por grandes editores:
 * 0: Sem Animação (Estático)
 * 1: Pan Left
 * 2: Pan Right
 * 3: Tilt Up
 * 4: Tilt Down
 * 5: Zoom In / Dolly In
 * 6: Zoom Out / Dolly Out
 * 7: Rotate & Zoom In
 * 8: Diagonal Pan
 * 9: Pulse Zoom (Ease Out)
 * 10: Horizontal Shake & Pan
 * 11: Dolly Zoom / Vertigo (Efeito Hitchcock)
 * 12: Dutch Angle Drift (Inclinação Holandesa)
 * 13: Orbit Sweep (Arco Orbital Cinemático)
 * 14: Crash Zoom In (Zoom de Impacto)
 * 15: Whip Pan Left (Chicote para Esquerda)
 * 16: Whip Pan Right (Chicote para Direita)
 * 17: Floating Gimbal Drift (Flutuação Steadycam)
 * 18: Crane Pedestal Up (Grua Ascendente)
 * 19: Crane Pedestal Down (Grua Descendente)
 * 20: Parallax 2.5D Push (Profundidade Tridimensional)
 * 21: Handheld Action Cam (Câmera Orgânica na Mão)
 * 22: Spiral Vortex In (Vórtice Helicoidal)
 * 23: Snap Zoom & Rebound (Zoom com Recuo Elástico)
 * 24: Cinematic Reveal Glide (Planagem de Revelação)
 * 25: Low Angle Hero Push (Empurrão Heróico em Contra-Plongée)
 * 26: Breathing Focus Drift (Respiração de Lente Anamórfica)
 */
data class MovementEffect(
    val id: Int,
    val name: String,
    val description: String,
    val shortBadge: String = "ID $id"
) {
    /**
     * Aplica o efeito de câmera a um GraphicsLayer do Compose para preview em tempo real.
     */
    fun applyToGraphicsLayer(scope: GraphicsLayerScope, progress: Float) {
        val t = progress.coerceIn(0f, 1f)
        when (id) {
            0 -> {
                scope.scaleX = 1.0f
                scope.scaleY = 1.0f
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            1 -> {
                val scale = 1.20f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = (0.5f - t) * 120f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            2 -> {
                val scale = 1.20f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = (t - 0.5f) * 120f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            3 -> {
                val scale = 1.25f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = (0.5f - t) * 120f
                scope.rotationZ = 0f
            }
            4 -> {
                val scale = 1.25f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = (t - 0.5f) * 120f
                scope.rotationZ = 0f
            }
            5 -> {
                val scale = 1.0f + (t * 0.35f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            6 -> {
                val scale = 1.35f - (t * 0.35f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            7 -> {
                val scale = 1.08f + (t * 0.22f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = -1.5f + (t * 3.0f)
            }
            8 -> {
                val scale = 1.25f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = (t - 0.5f) * 100f
                scope.translationY = (t - 0.5f) * 100f
                scope.rotationZ = 0f
            }
            9 -> {
                val easeOut = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
                val scale = 1.0f + (easeOut * 0.35f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            10 -> {
                val scale = 1.22f
                val panOffset = (t - 0.5f) * 100f
                val microShake = sin(t * 30f * Math.PI.toFloat()) * 8f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = panOffset + microShake
                scope.translationY = sin(t * 20f * Math.PI.toFloat()) * 3f
                scope.rotationZ = sin(t * 15f * Math.PI.toFloat()) * 0.5f
            }
            11 -> {
                // 11: Dolly Zoom / Vertigo (Zoom rápido com compensação de escala)
                val vertigoScale = 1.0f + (t * t * 0.45f)
                scope.scaleX = vertigoScale
                scope.scaleY = vertigoScale
                scope.translationY = (t - 0.5f) * 20f
                scope.rotationZ = sin(t * Math.PI.toFloat()) * 0.8f
            }
            12 -> {
                // 12: Dutch Angle Drift (Rotação holandesa cinematográfica)
                val scale = 1.22f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.rotationZ = -3.5f + (t * 7.0f)
                scope.translationX = (0.5f - t) * 35f
                scope.translationY = sin(t * Math.PI.toFloat()) * 18f
            }
            13 -> {
                // 13: Orbit Sweep (Giro em arco orbital em torno do centro)
                val scale = 1.25f
                val angle = t * Math.PI.toFloat() * 1.5f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = cos(angle) * 45f
                scope.translationY = sin(angle) * 30f
                scope.rotationZ = sin(t * Math.PI.toFloat() * 2f) * 1.8f
            }
            14 -> {
                // 14: Crash Zoom In (Zoom rápido e impactante com desaceleração final)
                val easeCrash = if (t < 0.7f) {
                    (t / 0.7f) * (t / 0.7f) * 0.85f
                } else {
                    0.85f + ((t - 0.7f) / 0.3f) * 0.15f
                }
                val scale = 1.0f + (easeCrash * 0.50f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            15 -> {
                // 15: Whip Pan Left (Chicote veloz para a esquerda com snap)
                val scale = 1.30f
                scope.scaleX = scale
                scope.scaleY = scale
                val whipEase = t * t * t
                scope.translationX = (0.5f - whipEase) * 160f
                scope.translationY = 0f
                scope.rotationZ = -whipEase * 1.5f
            }
            16 -> {
                // 16: Whip Pan Right (Chicote veloz para a direita com snap)
                val scale = 1.30f
                scope.scaleX = scale
                scope.scaleY = scale
                val whipEase = t * t * t
                scope.translationX = (whipEase - 0.5f) * 160f
                scope.translationY = 0f
                scope.rotationZ = whipEase * 1.5f
            }
            17 -> {
                // 17: Floating Gimbal Drift (Flutuação de gimbal 3 eixos)
                val scale = 1.18f
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = sin(t * Math.PI.toFloat() * 1.5f) * 35f
                scope.translationY = cos(t * Math.PI.toFloat() * 2f) * 25f
                scope.rotationZ = sin(t * Math.PI.toFloat() * 1.2f) * 1.5f
            }
            18 -> {
                // 18: Crane Pedestal Up (Guindaste subindo suavemente)
                val scale = 1.20f + (t * 0.12f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = (0.6f - t) * 90f
                scope.rotationZ = 0f
            }
            19 -> {
                // 19: Crane Pedestal Down (Guindaste descendo suavemente)
                val scale = 1.20f + (t * 0.12f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = 0f
                scope.translationY = (t - 0.6f) * 90f
                scope.rotationZ = 0f
            }
            20 -> {
                // 20: Parallax 2.5D Push (Empurrão com profundidade tridimensional)
                val scale = 1.05f + (t * 0.30f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = (t - 0.5f) * 50f
                scope.translationY = (0.5f - t) * 35f
                scope.rotationZ = (t - 0.5f) * 2.0f
            }
            21 -> {
                // 21: Handheld Action Cam (Câmera na mão orgânica com passos)
                val scale = 1.22f
                scope.scaleX = scale
                scope.scaleY = scale
                val stepShake = sin(t * 16f * Math.PI.toFloat()) * 6f
                val bodyDrift = cos(t * 6f * Math.PI.toFloat()) * 14f
                scope.translationX = bodyDrift + stepShake
                scope.translationY = sin(t * 12f * Math.PI.toFloat()) * 8f
                scope.rotationZ = sin(t * 8f * Math.PI.toFloat()) * 1.2f
            }
            22 -> {
                // 22: Spiral Vortex In (Giro espiral helicoidal imersivo)
                val scale = 1.0f + (t * 0.42f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.rotationZ = t * 12f
                scope.translationX = sin(t * Math.PI.toFloat() * 2f) * 15f
                scope.translationY = cos(t * Math.PI.toFloat() * 2f) * 15f
            }
            23 -> {
                // 23: Snap Zoom & Rebound (Zoom com micro-recuo elástico)
                val rawScale = if (t < 0.6f) {
                    1.0f + (t / 0.6f) * 0.40f
                } else {
                    1.40f - sin((t - 0.6f) / 0.4f * Math.PI.toFloat()) * 0.08f
                }
                scope.scaleX = rawScale
                scope.scaleY = rawScale
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
            24 -> {
                // 24: Cinematic Reveal Glide (Planagem diagonal ascendente)
                val scale = 1.24f - (t * 0.08f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationX = (t - 0.5f) * 80f
                scope.translationY = (0.5f - t) * 60f
                scope.rotationZ = (0.5f - t) * 1.2f
            }
            25 -> {
                // 25: Low Angle Hero Push (Câmera baixa avançando com peso)
                val scale = 1.08f + (t * 0.32f)
                scope.scaleX = scale
                scope.scaleY = scale
                scope.translationY = (0.5f - t) * 70f
                scope.rotationZ = (t - 0.5f) * 1.0f
            }
            26 -> {
                // 26: Breathing Focus Drift (Respiração de foco anamórfico)
                val breath = sin(t * Math.PI.toFloat() * 2f)
                val scale = 1.15f + (breath * 0.06f)
                scope.scaleX = scale
                scope.scaleY = scale * (1.0f + breath * 0.02f)
                scope.translationX = sin(t * Math.PI.toFloat()) * 20f
                scope.translationY = cos(t * Math.PI.toFloat()) * 15f
                scope.rotationZ = breath * 0.6f
            }
            else -> {
                scope.scaleX = 1.0f
                scope.scaleY = 1.0f
                scope.translationX = 0f
                scope.translationY = 0f
                scope.rotationZ = 0f
            }
        }
    }

    /**
     * Aplica o efeito de câmera para a renderização de frames em vídeo (Matrix no Canvas).
     */
    fun applyToMatrix(
        matrix: Matrix,
        progress: Float,
        canvasW: Float,
        canvasH: Float,
        bitmapW: Float,
        bitmapH: Float
    ) {
        val t = progress.coerceIn(0f, 1f)
        matrix.reset()

        // Fit center
        val scaleFit = minOf(canvasW / bitmapW, canvasH / bitmapH)
        val initialW = bitmapW * scaleFit
        val initialH = bitmapH * scaleFit

        var effectScale = 1.0f
        var transXFraction = 0f
        var transYFraction = 0f
        var rotationDegrees = 0f

        when (id) {
            0 -> {
                effectScale = 1.0f
            }
            1 -> {
                effectScale = 1.25f
                transXFraction = (0.5f - t) * 0.25f
            }
            2 -> {
                effectScale = 1.25f
                transXFraction = (t - 0.5f) * 0.25f
            }
            3 -> {
                effectScale = 1.25f
                transYFraction = (0.5f - t) * 0.25f
            }
            4 -> {
                effectScale = 1.25f
                transYFraction = (t - 0.5f) * 0.25f
            }
            5 -> {
                effectScale = 1.0f + (t * 0.35f)
            }
            6 -> {
                effectScale = 1.35f - (t * 0.35f)
            }
            7 -> {
                effectScale = 1.10f + (t * 0.22f)
                rotationDegrees = -1.5f + (t * 3.0f)
            }
            8 -> {
                effectScale = 1.25f
                transXFraction = (t - 0.5f) * 0.20f
                transYFraction = (t - 0.5f) * 0.20f
            }
            9 -> {
                val easeOut = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t)
                effectScale = 1.0f + (easeOut * 0.35f)
            }
            10 -> {
                effectScale = 1.25f
                val pan = (t - 0.5f) * 0.20f
                val shake = (sin(t * 30f * Math.PI.toFloat()) * 0.02f)
                transXFraction = pan + shake
                transYFraction = (sin(t * 20f * Math.PI.toFloat()) * 0.008f)
                rotationDegrees = sin(t * 15f * Math.PI.toFloat()) * 0.6f
            }
            11 -> {
                // Dolly Zoom Vertigo
                effectScale = 1.0f + (t * t * 0.45f)
                transYFraction = (t - 0.5f) * 0.04f
                rotationDegrees = sin(t * Math.PI.toFloat()) * 0.8f
            }
            12 -> {
                // Dutch Angle Drift
                effectScale = 1.22f
                rotationDegrees = -3.5f + (t * 7.0f)
                transXFraction = (0.5f - t) * 0.08f
                transYFraction = sin(t * Math.PI.toFloat()) * 0.04f
            }
            13 -> {
                // Orbit Sweep
                effectScale = 1.25f
                val angle = t * Math.PI.toFloat() * 1.5f
                transXFraction = cos(angle) * 0.10f
                transYFraction = sin(angle) * 0.06f
                rotationDegrees = sin(t * Math.PI.toFloat() * 2f) * 1.8f
            }
            14 -> {
                // Crash Zoom In
                val easeCrash = if (t < 0.7f) {
                    (t / 0.7f) * (t / 0.7f) * 0.85f
                } else {
                    0.85f + ((t - 0.7f) / 0.3f) * 0.15f
                }
                effectScale = 1.0f + (easeCrash * 0.50f)
            }
            15 -> {
                // Whip Pan Left
                effectScale = 1.30f
                val whipEase = t * t * t
                transXFraction = (0.5f - whipEase) * 0.32f
                rotationDegrees = -whipEase * 1.5f
            }
            16 -> {
                // Whip Pan Right
                effectScale = 1.30f
                val whipEase = t * t * t
                transXFraction = (whipEase - 0.5f) * 0.32f
                rotationDegrees = whipEase * 1.5f
            }
            17 -> {
                // Floating Gimbal Drift
                effectScale = 1.18f
                transXFraction = sin(t * Math.PI.toFloat() * 1.5f) * 0.08f
                transYFraction = cos(t * Math.PI.toFloat() * 2f) * 0.06f
                rotationDegrees = sin(t * Math.PI.toFloat() * 1.2f) * 1.5f
            }
            18 -> {
                // Crane Pedestal Up
                effectScale = 1.20f + (t * 0.12f)
                transYFraction = (0.6f - t) * 0.18f
            }
            19 -> {
                // Crane Pedestal Down
                effectScale = 1.20f + (t * 0.12f)
                transYFraction = (t - 0.6f) * 0.18f
            }
            20 -> {
                // Parallax 2.5D Push
                effectScale = 1.05f + (t * 0.30f)
                transXFraction = (t - 0.5f) * 0.10f
                transYFraction = (0.5f - t) * 0.08f
                rotationDegrees = (t - 0.5f) * 2.0f
            }
            21 -> {
                // Handheld Action Cam
                effectScale = 1.22f
                val stepShake = sin(t * 16f * Math.PI.toFloat()) * 0.015f
                val bodyDrift = cos(t * 6f * Math.PI.toFloat()) * 0.035f
                transXFraction = bodyDrift + stepShake
                transYFraction = sin(t * 12f * Math.PI.toFloat()) * 0.02f
                rotationDegrees = sin(t * 8f * Math.PI.toFloat()) * 1.2f
            }
            22 -> {
                // Spiral Vortex In
                effectScale = 1.0f + (t * 0.42f)
                rotationDegrees = t * 12f
                transXFraction = sin(t * Math.PI.toFloat() * 2f) * 0.035f
                transYFraction = cos(t * Math.PI.toFloat() * 2f) * 0.035f
            }
            23 -> {
                // Snap Zoom & Rebound
                effectScale = if (t < 0.6f) {
                    1.0f + (t / 0.6f) * 0.40f
                } else {
                    1.40f - sin((t - 0.6f) / 0.4f * Math.PI.toFloat()) * 0.08f
                }
            }
            24 -> {
                // Cinematic Reveal Glide
                effectScale = 1.24f - (t * 0.08f)
                transXFraction = (t - 0.5f) * 0.16f
                transYFraction = (0.5f - t) * 0.12f
                rotationDegrees = (0.5f - t) * 1.2f
            }
            25 -> {
                // Low Angle Hero Push
                effectScale = 1.08f + (t * 0.32f)
                transYFraction = (0.5f - t) * 0.14f
                rotationDegrees = (t - 0.5f) * 1.0f
            }
            26 -> {
                // Breathing Focus Drift
                val breath = sin(t * Math.PI.toFloat() * 2f)
                effectScale = 1.15f + (breath * 0.06f)
                transXFraction = sin(t * Math.PI.toFloat()) * 0.04f
                transYFraction = cos(t * Math.PI.toFloat()) * 0.03f
                rotationDegrees = breath * 0.6f
            }
            else -> {
                effectScale = 1.0f
            }
        }

        val totalScale = scaleFit * effectScale
        val scaledW = bitmapW * totalScale
        val scaledH = bitmapH * totalScale

        val centerX = canvasW / 2f
        val centerY = canvasH / 2f

        val transX = (centerX - scaledW / 2f) + (transXFraction * canvasW)
        val transY = (centerY - scaledH / 2f) + (transYFraction * canvasH)

        matrix.postScale(totalScale, totalScale)
        matrix.postTranslate(transX, transY)
        if (rotationDegrees != 0f) {
            matrix.postRotate(rotationDegrees, centerX, centerY)
        }
    }

    companion object {
        const val MAX_ID = 26

        val ALL_EFFECTS = listOf(
            MovementEffect(0, "Sem Animação", "Imagem estática sem qualquer deslocamento ou zoom.", "0 • Estático"),
            MovementEffect(1, "Pan Left", "Desliza suavemente da direita para a esquerda.", "1 • Pan Left"),
            MovementEffect(2, "Pan Right", "Desliza suavemente da esquerda para a direita.", "2 • Pan Right"),
            MovementEffect(3, "Tilt Up", "Deslocamento vertical de baixo para cima.", "3 • Tilt Up"),
            MovementEffect(4, "Tilt Down", "Deslocamento vertical de cima para baixo.", "4 • Tilt Down"),
            MovementEffect(5, "Zoom In / Dolly In", "Aproximação contínua em direção ao centro da imagem.", "5 • Zoom In"),
            MovementEffect(6, "Zoom Out / Dolly Out", "Afastamento suave a partir do centro, revelando a cena.", "6 • Zoom Out"),
            MovementEffect(7, "Rotate & Zoom In", "Animação profissional: Rotação leve com zoom contínuo.", "7 • Rotate Zoom"),
            MovementEffect(8, "Diagonal Pan", "Movimento suave em diagonal da ponta superior à inferior.", "8 • Diagonal"),
            MovementEffect(9, "Pulse Zoom", "Aproximação rápida no centro com suave desaceleração.", "9 • Pulse"),
            MovementEffect(10, "Horizontal Shake & Pan", "Movimento pan dinâmico com micro-estabilização de ação.", "10 • Dynamic"),
            MovementEffect(11, "Dolly Zoom (Vertigo)", "Efeito clássico Hitchcock com distorção perceptiva de profundidade.", "11 • Vertigo"),
            MovementEffect(12, "Dutch Angle Drift", "Inclinação holandesa oblíqua com suave flutuação cinematográfica.", "12 • Dutch"),
            MovementEffect(13, "Orbit Sweep 360", "Movimento suave em arco orbital ao redor do ponto de interesse.", "13 • Orbit"),
            MovementEffect(14, "Crash Zoom In", "Zoom veloz de alto impacto dramático estilo blockbuster.", "14 • Crash"),
            MovementEffect(15, "Whip Pan Left", "Chicote rápido para esquerda com snap e desaceleração precisa.", "15 • Whip L"),
            MovementEffect(16, "Whip Pan Right", "Chicote rápido para direita com snap e desaceleração precisa.", "16 • Whip R"),
            MovementEffect(17, "Floating Gimbal Drift", "Flutuação ultra estável de gimbal 3 eixos simulando steadycam.", "17 • Gimbal"),
            MovementEffect(18, "Crane Pedestal Up", "Movimento vertical de grua cinematográfica subindo na cena.", "18 • Crane Up"),
            MovementEffect(19, "Crane Pedestal Down", "Movimento vertical de grua descendo com ângulo imersivo.", "19 • Crane Down"),
            MovementEffect(20, "Parallax 2.5D Push", "Empurrão dimensional com perspectiva de profundidade.", "20 • Parallax"),
            MovementEffect(21, "Handheld Action Cam", "Câmera orgânica na mão com ritmo natural de passos.", "21 • Handheld"),
            MovementEffect(22, "Spiral Vortex In", "Giro helicoidal envolvente com aproximação progressiva.", "22 • Spiral"),
            MovementEffect(23, "Snap Zoom & Rebound", "Zoom rápido com micro-recuo elástico no ponto focal.", "23 • Snap"),
            MovementEffect(24, "Cinematic Reveal Glide", "Deslize diagonal ascendente revelando amplitude visual.", "24 • Glide"),
            MovementEffect(25, "Low Angle Hero Push", "Câmera baixa avançando com peso heróico sobre o sujeito.", "25 • Hero Push"),
            MovementEffect(26, "Breathing Focus Drift", "Respiração óptica suave simulando lentes de cinema anamórficas.", "26 • Breathing")
        )

        fun findById(id: Int): MovementEffect? {
            return ALL_EFFECTS.firstOrNull { it.id == id }
        }

        fun getOrDefault(id: Int): MovementEffect {
            return findById(id) ?: ALL_EFFECTS[5] // Zoom In por padrão
        }
    }
}
