package com.example.data.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.PI
import kotlin.math.sin

/**
 * Representa as 20 Transições Suaves inspiradas no CapCut (focadas em dissolução, fumaça,
 * borrão, desfoque e zoom suave), além da opção 0 = Sem Transição.
 *
 * REGRAS ATENDIDAS:
 * - Sem transições direcionais/deslizantes (sem deslizar esquerda, direita, cima, baixo).
 * - Sem colorações agressivas ou piscadas violentas.
 * - Inclui a opção "0 = Sem Transição".
 * - Cada transição possui um ID numérico visível (0 a 20).
 */
data class TransitionEffect(
    val id: Int,
    val name: String,
    val category: String,
    val description: String
) {
    /**
     * Aplica a transição suave entre bitmap1 e bitmap2 em um Canvas.
     * @param progress Valor de 0.0f a 1.0f
     * @param canvas Canvas de destino
     * @param paint Paint para renderização
     * @param bitmap1 Imagem de origem
     * @param matrix1 Matriz da câmera para imagem 1
     * @param bitmap2 Imagem de destino
     * @param matrix2 Matriz da câmera para imagem 2
     * @param width Largura do quadro
     * @param height Altura do quadro
     */
    fun applyToCanvas(
        progress: Float,
        canvas: Canvas,
        paint: Paint,
        bitmap1: Bitmap,
        matrix1: Matrix,
        bitmap2: Bitmap,
        matrix2: Matrix,
        width: Float,
        height: Float
    ) {
        val t = progress.coerceIn(0.0f, 1.0f)
        val cx = width / 2f
        val cy = height / 2f

        when (id) {
            0 -> {
                // 0 = Sem Transição (Corte Direto)
                if (t < 0.5f) {
                    paint.alpha = 255
                    canvas.drawBitmap(bitmap1, matrix1, paint)
                } else {
                    paint.alpha = 255
                    canvas.drawBitmap(bitmap2, matrix2, paint)
                }
            }
            1 -> {
                // 1 = Dissolvência Suave (Cross Dissolve)
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)
            }
            2 -> {
                // 2 = Fumaça Suave (Smoke Mist)
                val curve1 = (1f - t * t).coerceIn(0f, 1f)
                val curve2 = (t * t).coerceIn(0f, 1f)
                paint.alpha = (curve1 * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)

                val mistScale = 1.0f + 0.04f * sin(t * Math.PI.toFloat())
                val m2 = Matrix(matrix2).apply { postScale(mistScale, mistScale, cx, cy) }
                paint.alpha = (curve2 * 255).toInt()
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            3 -> {
                // 3 = Desfoque Gaussiano (Gaussian Blur Dissolve)
                renderMultiSampleBlur(canvas, paint, bitmap1, matrix1, bitmap2, matrix2, t, cx, cy, 6f)
            }
            4 -> {
                // 4 = Zoom Suave In (Smooth Zoom In)
                val scale1 = 1.0f + 0.15f * t
                val m1 = Matrix(matrix1).apply { postScale(scale1, scale1, cx, cy) }
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, m1, paint)

                val scale2 = 0.90f + 0.10f * t
                val m2 = Matrix(matrix2).apply { postScale(scale2, scale2, cx, cy) }
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            5 -> {
                // 5 = Zoom Suave Out (Smooth Zoom Out)
                val scale1 = 1.0f - 0.12f * t
                val m1 = Matrix(matrix1).apply { postScale(scale1, scale1, cx, cy) }
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, m1, paint)

                val scale2 = 1.15f - 0.15f * (1f - t)
                val m2 = Matrix(matrix2).apply { postScale(scale2, scale2, cx, cy) }
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            6 -> {
                // 6 = Borrão Radial (Radial Blur)
                renderRadialBlur(canvas, paint, bitmap1, matrix1, bitmap2, matrix2, t, cx, cy)
            }
            7 -> {
                // 7 = Brilho Suave (Soft Glow / White Mist Dissolve)
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)

                val glowAlpha = (sin(t * Math.PI.toFloat()) * 65).toInt()
                if (glowAlpha > 0) {
                    paint.color = Color.argb(glowAlpha, 255, 255, 255)
                    canvas.drawRect(0f, 0f, width, height, paint)
                }
            }
            8 -> {
                // 8 = Névoa Difusa (Diffuse Fog Veil)
                val fogFade = (sin(t * Math.PI.toFloat()) * 40).toInt()
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)

                if (fogFade > 0) {
                    paint.color = Color.argb(fogFade, 240, 244, 250)
                    canvas.drawRect(0f, 0f, width, height, paint)
                }
            }
            9 -> {
                // 9 = Desfoque Bokeh / Lente (Bokeh Lens Defocus)
                renderMultiSampleBlur(canvas, paint, bitmap1, matrix1, bitmap2, matrix2, t, cx, cy, 9f)
            }
            10 -> {
                // 10 = Zoom Cruzado (Cross-Zoom CapCut)
                val s1 = 1.0f + 0.20f * (t * t)
                val m1 = Matrix(matrix1).apply { postScale(s1, s1, cx, cy) }
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, m1, paint)

                val s2 = 0.85f + 0.15f * (1f - (1f - t) * (1f - t))
                val m2 = Matrix(matrix2).apply { postScale(s2, s2, cx, cy) }
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            11 -> {
                // 11 = Borrão Espiral Suave (Soft Swirl Blur)
                val rot1 = 2.5f * sin(t * Math.PI.toFloat())
                val m1 = Matrix(matrix1).apply { postRotate(rot1, cx, cy) }
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, m1, paint)

                val rot2 = -2.5f * sin((1f - t) * Math.PI.toFloat())
                val m2 = Matrix(matrix2).apply { postRotate(rot2, cx, cy) }
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            12 -> {
                // 12 = Ondulação Suave (Soft Ripple Dissolve)
                val rippleScale = 1.0f + 0.05f * sin(t * Math.PI.toFloat() * 2f)
                val m1 = Matrix(matrix1).apply { postScale(rippleScale, rippleScale, cx, cy) }
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, m1, paint)

                val m2 = Matrix(matrix2).apply { postScale(1f / rippleScale, 1f / rippleScale, cx, cy) }
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            13 -> {
                // 13 = Vapor Suave (Soft Vapor)
                val sCurve = t * t * (3f - 2f * t)
                val vaporShift = 0.03f * sin(t * Math.PI.toFloat())
                val m1 = Matrix(matrix1).apply { postScale(1f + vaporShift, 1f + vaporShift, cx, cy) }
                paint.alpha = ((1f - sCurve) * 255).toInt()
                canvas.drawBitmap(bitmap1, m1, paint)

                paint.alpha = (sCurve * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)
            }
            14 -> {
                // 14 = Desfoque Dinâmico (Dynamic Focus Shift)
                renderMultiSampleBlur(canvas, paint, bitmap1, matrix1, bitmap2, matrix2, t, cx, cy, 7f)
            }
            15 -> {
                // 15 = Zoom com Desfoque (Zoom Blur)
                val zoom = 1.0f + 0.08f * sin(t * Math.PI.toFloat())
                val m1 = Matrix(matrix1).apply { postScale(zoom, zoom, cx, cy) }
                val m2 = Matrix(matrix2).apply { postScale(zoom, zoom, cx, cy) }
                renderMultiSampleBlur(canvas, paint, bitmap1, m1, bitmap2, m2, t, cx, cy, 5f)
            }
            16 -> {
                // 16 = Dissolvência Fílmica (Exponential Film Dissolve)
                val filmAlpha = t * t * (3f - 2f * t) // S-Curve
                paint.alpha = ((1f - filmAlpha) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (filmAlpha * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)
            }
            17 -> {
                // 17 = Foco Suave / Dreamy (Soft Dream Glow)
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)

                val dreamAlpha = (sin(t * Math.PI.toFloat()) * 35).toInt()
                if (dreamAlpha > 0) {
                    paint.color = Color.argb(dreamAlpha, 255, 245, 235)
                    canvas.drawRect(0f, 0f, width, height, paint)
                }
            }
            18 -> {
                // 18 = Pulsação Suave (Soft Pulse Zoom)
                val pulse = 1.0f + 0.04f * sin(t * Math.PI.toFloat())
                val m1 = Matrix(matrix1).apply { postScale(pulse, pulse, cx, cy) }
                val m2 = Matrix(matrix2).apply { postScale(pulse, pulse, cx, cy) }
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, m1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            19 -> {
                // 19 = Névoa Cinematográfica (Cinema Mist Blend)
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)

                val mistIntensity = (sin(t * Math.PI.toFloat()) * 30).toInt()
                if (mistIntensity > 0) {
                    paint.color = Color.argb(mistIntensity, 220, 230, 245)
                    canvas.drawRect(0f, 0f, width, height, paint)
                }
            }
            20 -> {
                // 20 = Fusão Atmosférica (Atmospheric Blur Merge)
                val blendScale = 1.0f + 0.03f * (0.5f - kotlin.math.abs(t - 0.5f))
                val m1 = Matrix(matrix1).apply { postScale(blendScale, blendScale, cx, cy) }
                val m2 = Matrix(matrix2).apply { postScale(blendScale, blendScale, cx, cy) }
                renderMultiSampleBlur(canvas, paint, bitmap1, m1, bitmap2, m2, t, cx, cy, 4f)
            }
        }
        paint.alpha = 255
    }

    private fun renderMultiSampleBlur(
        canvas: Canvas,
        paint: Paint,
        bitmap1: Bitmap,
        matrix1: Matrix,
        bitmap2: Bitmap,
        matrix2: Matrix,
        t: Float,
        cx: Float,
        cy: Float,
        maxOffset: Float
    ) {
        val peak = sin(t * Math.PI.toFloat())
        val offset = peak * maxOffset

        // Renderiza bitmap1 com blur e fade out
        val alpha1 = ((1f - t) * 255).toInt()
        if (alpha1 > 0) {
            if (offset > 1f) {
                paint.alpha = (alpha1 * 0.4f).toInt()
                val m1a = Matrix(matrix1).apply { postTranslate(offset, 0f) }
                val m1b = Matrix(matrix1).apply { postTranslate(-offset, 0f) }
                val m1c = Matrix(matrix1).apply { postTranslate(0f, offset) }
                canvas.drawBitmap(bitmap1, m1a, paint)
                canvas.drawBitmap(bitmap1, m1b, paint)
                canvas.drawBitmap(bitmap1, m1c, paint)
            }
            paint.alpha = alpha1
            canvas.drawBitmap(bitmap1, matrix1, paint)
        }

        // Renderiza bitmap2 com blur e fade in
        val alpha2 = (t * 255).toInt()
        if (alpha2 > 0) {
            if (offset > 1f) {
                paint.alpha = (alpha2 * 0.4f).toInt()
                val m2a = Matrix(matrix2).apply { postTranslate(0f, -offset) }
                val m2b = Matrix(matrix2).apply { postTranslate(offset * 0.7f, offset * 0.7f) }
                canvas.drawBitmap(bitmap2, m2a, paint)
                canvas.drawBitmap(bitmap2, m2b, paint)
            }
            paint.alpha = alpha2
            canvas.drawBitmap(bitmap2, matrix2, paint)
        }
    }

    private fun renderRadialBlur(
        canvas: Canvas,
        paint: Paint,
        bitmap1: Bitmap,
        matrix1: Matrix,
        bitmap2: Bitmap,
        matrix2: Matrix,
        t: Float,
        cx: Float,
        cy: Float
    ) {
        val peak = sin(t * Math.PI.toFloat())
        val sOffset = 0.04f * peak

        val a1 = ((1f - t) * 255).toInt()
        if (a1 > 0) {
            paint.alpha = (a1 * 0.5f).toInt()
            val m1 = Matrix(matrix1).apply { postScale(1f + sOffset, 1f + sOffset, cx, cy) }
            canvas.drawBitmap(bitmap1, m1, paint)
            paint.alpha = a1
            canvas.drawBitmap(bitmap1, matrix1, paint)
        }

        val a2 = (t * 255).toInt()
        if (a2 > 0) {
            paint.alpha = (a2 * 0.5f).toInt()
            val m2 = Matrix(matrix2).apply { postScale(1f - sOffset, 1f - sOffset, cx, cy) }
            canvas.drawBitmap(bitmap2, m2, paint)
            paint.alpha = a2
            canvas.drawBitmap(bitmap2, matrix2, paint)
        }
    }

    companion object {
        val NO_TRANSITION = TransitionEffect(
            id = 0,
            name = "Sem Transição",
            category = "Corte",
            description = "Corte direto entre cenas sem interpolação"
        )

        /**
         * As 20 Transições Suaves inspiradas no CapCut
         */
        val ALL_TRANSITIONS: List<TransitionEffect> = listOf(
            NO_TRANSITION,
            TransitionEffect(1, "Dissolvência Suave", "Dissolução", "Mistura linear contínua e elegante entre cenas"),
            TransitionEffect(2, "Fumaça Suave", "Fumaça / Névoa", "Transição estilo vapor e névoa sutil atmosférica"),
            TransitionEffect(3, "Desfoque Gaussiano", "Borrão / Desfoque", "Desfoque difuso e macio no ápice da passagem"),
            TransitionEffect(4, "Zoom Suave In", "Zoom Suave", "Aproximação suave da imagem para uma fusão moderna"),
            TransitionEffect(5, "Zoom Suave Out", "Zoom Suave", "Afastamento progressivo com troca de planos suave"),
            TransitionEffect(6, "Borrão Radial", "Borrão / Desfoque", "Desfoque radial concêntrico de alta velocidade suave"),
            TransitionEffect(7, "Brilho Suave", "Dissolução", "Luz translúcida suave na transição entre quadros"),
            TransitionEffect(8, "Névoa Difusa", "Fumaça / Névoa", "Névoa matinal sutil desvanecendo entre imagens"),
            TransitionEffect(9, "Desfoque Bokeh", "Borrão / Desfoque", "Efeito cinematográfico de lente fora de foco"),
            TransitionEffect(10, "Zoom Cruzado", "Zoom Suave", "Transição icônica estilo CapCut com fusão em escala"),
            TransitionEffect(11, "Borrão Espiral Suave", "Borrão / Desfoque", "Micro-rotação com borrão circular harmonioso"),
            TransitionEffect(12, "Ondulação Suave", "Dissolução", "Pulsar sutil em onda aquática translúcida"),
            TransitionEffect(13, "Vapor Suave", "Fumaça / Névoa", "Dissolução fluida simulando vapor leve"),
            TransitionEffect(14, "Desfoque Dinâmico", "Borrão / Desfoque", "Transferência fluida de nitidez focal"),
            TransitionEffect(15, "Zoom com Desfoque", "Zoom Suave", "Combinação elegante de aproximação e blur"),
            TransitionEffect(16, "Dissolvência Fílmica", "Dissolução", "Curva S cinematográfica com tom orgânico"),
            TransitionEffect(17, "Foco Suave", "Dissolução", "Aura aconchegante e difusa estilo sonho"),
            TransitionEffect(18, "Pulsação Suave", "Zoom Suave", "Micro-pulsação rítmica e transição limpa"),
            TransitionEffect(19, "Névoa Cinematográfica", "Fumaça / Névoa", "Mistura difusa com estética anamórfica de cinema"),
            TransitionEffect(20, "Fusão Atmosférica", "Borrão / Desfoque", "Mescla atmosférica rica em profundidade")
        )

        val DEFAULT: TransitionEffect = ALL_TRANSITIONS[1] // Dissolvência Suave

        fun findById(id: Int): TransitionEffect? =
            ALL_TRANSITIONS.firstOrNull { it.id == id }

        fun getOrCut(id: Int): TransitionEffect =
            findById(id) ?: NO_TRANSITION
    }
}
