package com.example.data.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Representa os 30 Efeitos de Transições Profissionais especificados pelo usuário,
 * além da opção 0 = Sem Transição.
 */
data class TransitionEffect(
    val id: Int,
    val name: String,
    val category: String,
    val description: String
) {
    /**
     * Aplica o efeito de transição entre bitmap1 e bitmap2 em um Canvas.
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
        paint.colorFilter = null
        paint.shader = null
        paint.style = Paint.Style.FILL

        when (id) {
            0 -> {
                // 0 = Sem Transição (Corte Direto)
                paint.alpha = 255
                if (t < 0.5f) {
                    canvas.drawBitmap(bitmap1, matrix1, paint)
                } else {
                    canvas.drawBitmap(bitmap2, matrix2, paint)
                }
            }
            1 -> {
                // 1 = Blur Dissolve Cinematográfico (Desfoque gaussiano rápido uniforme + opacidade)
                renderGaussianBlurDissolve(canvas, paint, bitmap1, matrix1, bitmap2, matrix2, t, 14f)
            }
            2 -> {
                // 2 = Chicote Horizontal (Whip Pan): arrasto lateral ultraveloz para a direita com motion blur horizontal intenso
                val ease = t * t * (3f - 2f * t)
                val shift1 = ease * width
                val shift2 = (ease - 1f) * width
                val motionStreak = sin(t * PI.toFloat()) * (width * 0.08f)

                val m1 = Matrix(matrix1).apply { postTranslate(shift1, 0f) }
                val m2 = Matrix(matrix2).apply { postTranslate(shift2, 0f) }

                for (s in -2..2) {
                    val streakOffset = s * (motionStreak / 2f)
                    paint.alpha = if (s == 0) 170 else 65
                    val ms1 = Matrix(m1).apply { postTranslate(streakOffset, 0f) }
                    val ms2 = Matrix(m2).apply { postTranslate(streakOffset, 0f) }
                    canvas.drawBitmap(bitmap1, ms1, paint)
                    canvas.drawBitmap(bitmap2, ms2, paint)
                }
            }
            3 -> {
                // 3 = Impacto de Zoom (Punch In): avanço rápido centralizado + desfoque radial de tunelamento
                val ease = t * t * (3f - 2f * t)
                val scale1 = 1.0f + 0.75f * ease
                val scale2 = 0.55f + 0.45f * ease
                val peak = sin(t * PI.toFloat())

                val m1 = Matrix(matrix1).apply { postScale(scale1, scale1, cx, cy) }
                val m2 = Matrix(matrix2).apply { postScale(scale2, scale2, cx, cy) }

                val a1 = ((1f - ease) * 255).toInt()
                if (a1 > 0) {
                    paint.alpha = (a1 * 0.45f).toInt()
                    val m1Tunnel = Matrix(m1).apply { postScale(1f + 0.06f * peak, 1f + 0.06f * peak, cx, cy) }
                    canvas.drawBitmap(bitmap1, m1Tunnel, paint)
                    paint.alpha = a1
                    canvas.drawBitmap(bitmap1, m1, paint)
                }
                val a2 = (ease * 255).toInt()
                if (a2 > 0) {
                    paint.alpha = (a2 * 0.45f).toInt()
                    val m2Tunnel = Matrix(m2).apply { postScale(1f - 0.05f * peak, 1f - 0.05f * peak, cx, cy) }
                    canvas.drawBitmap(bitmap2, m2Tunnel, paint)
                    paint.alpha = a2
                    canvas.drawBitmap(bitmap2, m2, paint)
                }
            }
            4 -> {
                // 4 = Separação de Canais RGB (Glitch): camadas R, G e B com deslocamentos horizontais e tremura
                val activeBmp = if (t < 0.5f) bitmap1 else bitmap2
                val activeMat = if (t < 0.5f) matrix1 else matrix2
                val glitchIntensity = sin(t * PI.toFloat())
                val offsetPx = glitchIntensity * (width * 0.045f)
                val jitterY = sin(t * 28f) * glitchIntensity * (height * 0.012f)

                paint.alpha = 255
                canvas.drawBitmap(activeBmp, activeMat, paint)

                if (glitchIntensity > 0.05f) {
                    val chanAlpha = (glitchIntensity * 145).toInt().coerceIn(0, 255)
                    // Canal Vermelho
                    paint.alpha = chanAlpha
                    paint.colorFilter = LightingColorFilter(0xFFFF0000.toInt(), 0)
                    val mR = Matrix(activeMat).apply { postTranslate(-offsetPx, jitterY) }
                    canvas.drawBitmap(activeBmp, mR, paint)

                    // Canal Verde
                    paint.colorFilter = LightingColorFilter(0xFF00FF00.toInt(), 0)
                    val mG = Matrix(activeMat).apply { postTranslate(0f, -jitterY) }
                    canvas.drawBitmap(activeBmp, mG, paint)

                    // Canal Azul
                    paint.colorFilter = LightingColorFilter(0xFF0000FF.toInt(), 0)
                    val mB = Matrix(activeMat).apply { postTranslate(offsetPx, jitterY * 0.5f) }
                    canvas.drawBitmap(activeBmp, mB, paint)
                    paint.colorFilter = null
                }
            }
            5 -> {
                // 5 = Clarão de Lente Orgânico (Light Leak): onda de luz âmbar e dourado a partir do canto superior esquerdo
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)

                val leakIntensity = sin(t * PI.toFloat())
                if (leakIntensity > 0.01f) {
                    val leakAlpha = (leakIntensity * 225).toInt().coerceIn(0, 255)
                    val radius = hypot(width, height) * (0.45f + 0.65f * leakIntensity)
                    val leakPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            width * 0.12f,
                            height * 0.12f,
                            radius.coerceAtLeast(10f),
                            intArrayOf(
                                Color.argb(leakAlpha, 255, 215, 64),
                                Color.argb((leakAlpha * 0.8f).toInt(), 255, 143, 0),
                                Color.argb(0, 255, 111, 0)
                            ),
                            floatArrayOf(0f, 0.55f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, width, height, leakPaint)
                }
            }
            6 -> {
                // 6 = Deslocamento Vertical Fluido (Push Up) com desaceleração elástica
                val c1 = 1.70158f
                val c3 = c1 + 1f
                val inv = t - 1f
                val elasticProgress = (1f + c3 * inv * inv * inv + c1 * inv * inv).coerceIn(0f, 1.06f)

                val dy1 = -elasticProgress * height
                val dy2 = (1f - elasticProgress) * height

                paint.alpha = 255
                val m1 = Matrix(matrix1).apply { postTranslate(0f, dy1) }
                val m2 = Matrix(matrix2).apply { postTranslate(0f, dy2) }
                canvas.drawBitmap(bitmap1, m1, paint)
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            7 -> {
                // 7 = Exposição Fotográfica Estourada: superexposição até branco puro e recuperação de contraste
                val activeBmp = if (t < 0.5f) bitmap1 else bitmap2
                val activeMat = if (t < 0.5f) matrix1 else matrix2
                val burn = sin(t * PI.toFloat())
                val scaleContrast = 1f + burn * 1.8f
                val translateBright = burn * 220f

                val cm = ColorMatrix(
                    floatArrayOf(
                        scaleContrast, 0f, 0f, 0f, translateBright,
                        0f, scaleContrast, 0f, 0f, translateBright,
                        0f, 0f, scaleContrast, 0f, translateBright,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
                paint.alpha = 255
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(activeBmp, activeMat, paint)
                paint.colorFilter = null

                val whiteFlash = (burn * burn * 245).toInt().coerceIn(0, 255)
                if (whiteFlash > 0) {
                    paint.color = Color.argb(whiteFlash, 255, 255, 255)
                    canvas.drawRect(0f, 0f, width, height, paint)
                }
            }
            8 -> {
                // 8 = Zoom Invertido com Recuo (Punch Out): encolhe para o centro e nova mídia expande do centro
                paint.color = Color.BLACK
                paint.alpha = 255
                canvas.drawRect(0f, 0f, width, height, paint)

                if (t < 0.5f) {
                    val p = t / 0.5f
                    val s1 = (1f - 0.82f * p).coerceAtLeast(0.12f)
                    val m1 = Matrix(matrix1).apply { postScale(s1, s1, cx, cy) }
                    paint.alpha = ((1f - p * 0.5f) * 255).toInt()
                    canvas.drawBitmap(bitmap1, m1, paint)
                } else {
                    val p = (t - 0.5f) / 0.5f
                    val ease = 1f - (1f - p) * (1f - p)
                    val s2 = (0.12f + 0.88f * ease).coerceIn(0.12f, 1f)
                    val m2 = Matrix(matrix2).apply { postScale(s2, s2, cx, cy) }
                    paint.alpha = 255
                    canvas.drawBitmap(bitmap2, m2, paint)
                }
            }
            9 -> {
                // 9 = Distorção de Lente Anamórfica: curvatura horizontal + desfoque direcional puramente horizontal
                val peak = sin(t * PI.toFloat())
                val sx = 1f + 0.36f * peak
                val sy = (1f - 0.16f * peak).coerceAtLeast(0.75f)
                val horizBlur = peak * (width * 0.03f)

                val activeBmp = if (t < 0.5f) bitmap1 else bitmap2
                val activeMat = if (t < 0.5f) matrix1 else matrix2
                val baseM = Matrix(activeMat).apply { postScale(sx, sy, cx, cy) }

                for (i in -2..2) {
                    paint.alpha = if (i == 0) 210 else 80
                    val mi = Matrix(baseM).apply { postTranslate(i * (horizBlur / 2f), 0f) }
                    canvas.drawBitmap(activeBmp, mi, paint)
                }
            }
            10 -> {
                // 10 = Divisão por Cortina Minimalista (Split Wipe): metade esquerda sai pela esquerda, metade direita pela direita
                paint.alpha = 255
                canvas.drawBitmap(bitmap2, matrix2, paint)

                val ease = t * t * (3f - 2f * t)
                val offsetHalf = ease * cx

                val saveLeft = canvas.save()
                canvas.clipRect(0f, 0f, cx - offsetHalf, height)
                val mLeft = Matrix(matrix1).apply { postTranslate(-offsetHalf, 0f) }
                canvas.drawBitmap(bitmap1, mLeft, paint)
                canvas.restoreToCount(saveLeft)

                val saveRight = canvas.save()
                canvas.clipRect(cx + offsetHalf, 0f, width, height)
                val mRight = Matrix(matrix1).apply { postTranslate(offsetHalf, 0f) }
                canvas.drawBitmap(bitmap1, mRight, paint)
                canvas.restoreToCount(saveRight)
            }
            11 -> {
                // 11 = Preenchimento por Vetor Radial: rotação de 360 graus no sentido horário
                paint.alpha = 255
                canvas.drawBitmap(bitmap1, matrix1, paint)

                val radius = hypot(width, height)
                val sweepAngle = t * 360f
                val oval = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
                val path = Path().apply {
                    moveTo(cx, cy)
                    arcTo(oval, -90f, sweepAngle)
                    close()
                }
                val save = canvas.save()
                canvas.clipPath(path)
                canvas.drawBitmap(bitmap2, matrix2, paint)
                canvas.restoreToCount(save)
            }
            12 -> {
                // 12 = Desvanecimento Texturizado Monocromático: perde saturação até P&B e depois reduz opacidade
                val sat = (1f - (t * 2f).coerceIn(0f, 1f))
                val alpha2 = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)

                val satMatrix = ColorMatrix().apply { setSaturation(sat) }
                paint.alpha = 255
                paint.colorFilter = ColorMatrixColorFilter(satMatrix)
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.colorFilter = null

                if (alpha2 > 0f) {
                    paint.alpha = (alpha2 * 255).toInt()
                    canvas.drawBitmap(bitmap2, matrix2, paint)
                }
            }
            13 -> {
                // 13 = Chicote Diagonal Invertido: guinada rápida do canto inferior esquerdo para o superior direito
                val ease = t * t * (3f - 2f * t)
                val dx1 = ease * width
                val dy1 = -ease * height
                val dx2 = (ease - 1f) * width
                val dy2 = (1f - ease) * height
                val trail = sin(t * PI.toFloat()) * (width * 0.04f)

                val m1 = Matrix(matrix1).apply { postTranslate(dx1, dy1) }
                val m2 = Matrix(matrix2).apply { postTranslate(dx2, dy2) }

                for (s in -1..1) {
                    paint.alpha = if (s == 0) 200 else 85
                    val ms1 = Matrix(m1).apply { postTranslate(s * trail, -s * trail) }
                    val ms2 = Matrix(m2).apply { postTranslate(s * trail, -s * trail) }
                    canvas.drawBitmap(bitmap1, ms1, paint)
                    canvas.drawBitmap(bitmap2, ms2, paint)
                }
            }
            14 -> {
                // 14 = Interferência de Ruído Analógico (VCR): barra horizontal de estática desce de cima para baixo
                val barY = height * t
                val barHalfH = (height * 0.045f).coerceAtLeast(10f)

                paint.alpha = 255
                // Abaixo da barra: primeira mídia
                val saveBottom = canvas.save()
                canvas.clipRect(0f, barY, width, height)
                canvas.drawBitmap(bitmap1, matrix1, paint)
                canvas.restoreToCount(saveBottom)

                // Acima da barra: segunda mídia
                val saveTop = canvas.save()
                canvas.clipRect(0f, 0f, width, barY)
                canvas.drawBitmap(bitmap2, matrix2, paint)
                canvas.restoreToCount(saveTop)

                // Barra horizontal de estática cinzenta e distorção magnética
                val vcrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        0f, barY - barHalfH, 0f, barY + barHalfH,
                        intArrayOf(
                            Color.argb(0, 180, 180, 180),
                            Color.argb(210, 210, 210, 210),
                            Color.argb(235, 110, 110, 110),
                            Color.argb(0, 180, 180, 180)
                        ),
                        null,
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(0f, barY - barHalfH, width, barY + barHalfH, vcrPaint)
            }
            15 -> {
                // 15 = Sombra Projetada por Deslocamento (Slide): desliza da direita para a esquerda com sombra difusa
                paint.alpha = 255
                canvas.drawBitmap(bitmap1, matrix1, paint)

                val ease = 1f - (1f - t) * (1f - t)
                val newLeft = width * (1f - ease)
                val shadowWidth = (width * 0.09f).coerceAtLeast(24f)

                // Sombra preta suave e difusa projetada sobre a imagem antiga
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    shader = LinearGradient(
                        newLeft - shadowWidth, 0f, newLeft, 0f,
                        Color.argb(0, 0, 0, 0),
                        Color.argb(185, 0, 0, 0),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawRect(newLeft - shadowWidth, 0f, newLeft, height, shadowPaint)

                val m2 = Matrix(matrix2).apply { postTranslate(newLeft, 0f) }
                canvas.drawBitmap(bitmap2, m2, paint)
            }
            16 -> {
                // 16 = Desfoque de Inclinação Seletiva (Tilt-Shift): topo e base desfocados avançando para o centro
                val activeBmp = if (t < 0.5f) bitmap1 else bitmap2
                val activeMat = if (t < 0.5f) matrix1 else matrix2
                val peak = sin(t * PI.toFloat())

                paint.alpha = 255
                canvas.drawBitmap(activeBmp, activeMat, paint)

                val blurOffset = peak * 12f
                val bandH = height * (0.25f + 0.25f * peak)

                val saveBands = canvas.save()
                val bandPath = Path().apply {
                    addRect(0f, 0f, width, bandH, Path.Direction.CW)
                    addRect(0f, height - bandH, width, height, Path.Direction.CW)
                }
                canvas.clipPath(bandPath)
                paint.alpha = 120
                canvas.drawBitmap(activeBmp, Matrix(activeMat).apply { postTranslate(blurOffset, 0f) }, paint)
                canvas.drawBitmap(activeBmp, Matrix(activeMat).apply { postTranslate(-blurOffset, blurOffset * 0.6f) }, paint)
                canvas.restoreToCount(saveBands)
            }
            17 -> {
                // 17 = Inversão de Luminância por Canal: inversão instantânea estilo raio-X no ponto de corte
                val activeBmp = if (t < 0.5f) bitmap1 else bitmap2
                val activeMat = if (t < 0.5f) matrix1 else matrix2
                val distFromCut = abs(t - 0.5f)

                paint.alpha = 255
                if (distFromCut < 0.16f) {
                    val invStrength = 1f - (distFromCut / 0.16f)
                    val k = 1f - 2f * invStrength
                    val offset = 255f * invStrength
                    val invMatrix = ColorMatrix(
                        floatArrayOf(
                            k, 0f, 0f, 0f, offset,
                            0f, k, 0f, 0f, offset,
                            0f, 0f, k, 0f, offset,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                    paint.colorFilter = ColorMatrixColorFilter(invMatrix)
                }
                canvas.drawBitmap(activeBmp, activeMat, paint)
                paint.colorFilter = null
            }
            18 -> {
                // 18 = Crescimento por Máscara de Brilho (Luma Fade)
                paint.alpha = 255
                canvas.drawBitmap(bitmap1, matrix1, paint)

                val lumaCurve = t * t * (3f - 2f * t)
                val boost = (1f - lumaCurve) * 65f
                val cm = ColorMatrix(
                    floatArrayOf(
                        1.15f, 0f, 0f, 0f, boost,
                        0f, 1.15f, 0f, 0f, boost,
                        0f, 0f, 1.15f, 0f, boost,
                        0f, 0f, 0f, lumaCurve, 0f
                    )
                )
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap2, matrix2, paint)
                paint.colorFilter = null
            }
            19 -> {
                // 19 = Onda de Choque Concêntrica: anel de distorção circular a partir do centro
                paint.alpha = 255
                canvas.drawBitmap(bitmap1, matrix1, paint)

                val maxR = hypot(cx, cy) * 1.15f
                val currentR = maxR * t
                val ringThickness = (maxR * 0.14f).coerceAtLeast(18f)

                // Anel deformado na borda da onda
                val saveRing = canvas.save()
                val ringPath = Path().apply {
                    addCircle(cx, cy, currentR + ringThickness * 0.5f, Path.Direction.CW)
                }
                canvas.clipPath(ringPath)
                val waveMatrix = Matrix(matrix1).apply { postScale(1.08f, 1.08f, cx, cy) }
                canvas.drawBitmap(bitmap1, waveMatrix, paint)
                canvas.restoreToCount(saveRing)

                // Interior do anel com a nova mídia nítida
                val saveInner = canvas.save()
                val innerPath = Path().apply {
                    addCircle(cx, cy, (currentR - ringThickness * 0.3f).coerceAtLeast(0f), Path.Direction.CW)
                }
                canvas.clipPath(innerPath)
                canvas.drawBitmap(bitmap2, matrix2, paint)
                canvas.restoreToCount(saveInner)
            }
            20 -> {
                // 20 = Corte Seco com Flash Esmeralda: sobreposição monocromática esmeralda escura no corte
                paint.alpha = 255
                if (t < 0.5f) {
                    canvas.drawBitmap(bitmap1, matrix1, paint)
                } else {
                    canvas.drawBitmap(bitmap2, matrix2, paint)
                }

                val flashPeak = (1f - (abs(t - 0.5f) / 0.25f)).coerceIn(0f, 1f)
                if (flashPeak > 0f) {
                    val emeraldAlpha = (flashPeak * 145).toInt().coerceIn(0, 255)
                    paint.color = Color.argb(emeraldAlpha, 6, 95, 70) // tonalidade esmeralda escura
                    canvas.drawRect(0f, 0f, width, height, paint)
                }
            }
            21 -> {
                // 21 = Transição por Losango Concêntrico: losango geométrico expande do centro às bordas
                paint.alpha = 255
                canvas.drawBitmap(bitmap1, matrix1, paint)

                val rx = width * t * 1.05f
                val ry = height * t * 1.05f
                val diamondPath = Path().apply {
                    moveTo(cx, cy - ry)
                    lineTo(cx + rx, cy)
                    lineTo(cx, cy + ry)
                    lineTo(cx - rx, cy)
                    close()
                }
                val save = canvas.save()
                canvas.clipPath(diamondPath)
                canvas.drawBitmap(bitmap2, matrix2, paint)
                canvas.restoreToCount(save)
            }
            22 -> {
                // 22 = Queima de Película Orgânica (Film Burn): manchas laranja vivo, vermelho e amarelo nas bordas
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)

                val burn = sin(t * PI.toFloat())
                if (burn > 0.02f) {
                    val burnAlpha = (burn * 235).toInt().coerceIn(0, 255)
                    val burnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = LinearGradient(
                            0f, 0f, width, height,
                            intArrayOf(
                                Color.argb(burnAlpha, 213, 0, 0),
                                Color.argb(burnAlpha, 255, 109, 0),
                                Color.argb((burnAlpha * 0.85f).toInt(), 255, 214, 0),
                                Color.argb(burnAlpha, 255, 61, 0)
                            ),
                            floatArrayOf(0f, 0.35f, 0.7f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, width, height, burnPaint)
                }
            }
            23 -> {
                // 23 = Cortina de Barras Diagonais: faixas paralelas a 45 graus deslizando da esquerda para a direita
                paint.alpha = 255
                canvas.drawBitmap(bitmap1, matrix1, paint)

                val numBars = 8
                val span = width + height
                val barStep = span / numBars
                val fillW = barStep * t * 1.05f
                val barsPath = Path()

                for (i in 0..numBars) {
                    val startX = -height + i * barStep
                    barsPath.moveTo(startX, height)
                    barsPath.lineTo(startX + height, 0f)
                    barsPath.lineTo(startX + height + fillW, 0f)
                    barsPath.lineTo(startX + fillW, height)
                    barsPath.close()
                }

                val save = canvas.save()
                canvas.clipPath(barsPath)
                canvas.drawBitmap(bitmap2, matrix2, paint)
                canvas.restoreToCount(save)
            }
            24 -> {
                // 24 = Grão de Prata e Obturador Mecânico: trepidação vertical 35mm + piscar preto horizontal
                val activeBmp = if (t < 0.5f) bitmap1 else bitmap2
                val activeMat = if (t < 0.5f) matrix1 else matrix2
                val jitter = if (t < 0.5f) sin(t * 50f) * (height * 0.022f) else 0f

                val m = Matrix(activeMat).apply { postTranslate(0f, jitter) }
                paint.alpha = 255
                canvas.drawBitmap(activeBmp, m, paint)

                val shutterDist = abs(t - 0.5f)
                if (shutterDist < 0.14f) {
                    val closeFactor = 1f - (shutterDist / 0.14f)
                    val barH = (height * 0.5f) * closeFactor
                    paint.color = Color.BLACK
                    canvas.drawRect(0f, 0f, width, barH, paint)
                    canvas.drawRect(0f, height - barH, width, height, paint)
                }
            }
            25 -> {
                // 25 = Revelação por Círculo Íris (Iris Wipe): círculo fecha no preto e abre revelando a nova mídia
                paint.color = Color.BLACK
                paint.alpha = 255
                canvas.drawRect(0f, 0f, width, height, paint)

                val maxR = hypot(cx, cy)
                val currentR = if (t < 0.5f) {
                    maxR * (1f - t / 0.5f)
                } else {
                    maxR * ((t - 0.5f) / 0.5f)
                }.coerceAtLeast(2f)

                val irisPath = Path().apply {
                    addCircle(cx, cy, currentR, Path.Direction.CW)
                }
                val save = canvas.save()
                canvas.clipPath(irisPath)
                if (t < 0.5f) {
                    canvas.drawBitmap(bitmap1, matrix1, paint)
                } else {
                    canvas.drawBitmap(bitmap2, matrix2, paint)
                }
                canvas.restoreToCount(save)
            }
            26 -> {
                // 26 = Vinheta de Época Desbotada: bordas escurecem em tom sépia difuso e expandem do núcleo
                paint.alpha = ((1f - t) * 255).toInt()
                canvas.drawBitmap(bitmap1, matrix1, paint)
                paint.alpha = (t * 255).toInt()
                canvas.drawBitmap(bitmap2, matrix2, paint)

                val peak = sin(t * PI.toFloat())
                if (peak > 0.02f) {
                    val vigAlpha = (peak * 230).toInt().coerceIn(0, 255)
                    val radius = hypot(cx, cy) * (1.05f - 0.55f * peak).coerceAtLeast(0.25f)
                    val sepiaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            cx, cy, radius,
                            intArrayOf(
                                Color.argb((vigAlpha * 0.25f).toInt(), 215, 190, 150),
                                Color.argb((vigAlpha * 0.75f).toInt(), 93, 64, 55),
                                Color.argb(vigAlpha, 38, 22, 16)
                            ),
                            floatArrayOf(0f, 0.6f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, width, height, sepiaPaint)
                }
            }
            27 -> {
                // 27 = Grade de Blocos Isométricos: grade de quadrados girando 180 graus sobre o eixo vertical
                val cols = 4
                val rows = 4
                val cellW = width / cols
                val cellH = height / rows
                paint.color = Color.BLACK
                paint.alpha = 255
                canvas.drawRect(0f, 0f, width, height, paint)

                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val delay = ((r + c).toFloat() / (rows + cols)) * 0.35f
                        val localT = ((t - delay) / 0.65f).coerceIn(0f, 1f)
                        val angleCos = cos(localT * PI.toFloat())
                        val scaleX = abs(angleCos).coerceAtLeast(0.04f)
                        val left = c * cellW
                        val top = r * cellH
                        val cellCenterX = left + cellW / 2f
                        val cellCenterY = top + cellH / 2f

                        val save = canvas.save()
                        canvas.clipRect(left, top, left + cellW, top + cellH)
                        canvas.scale(scaleX, 1f, cellCenterX, cellCenterY)
                        if (localT < 0.5f) {
                            canvas.drawBitmap(bitmap1, matrix1, paint)
                        } else {
                            canvas.drawBitmap(bitmap2, matrix2, paint)
                        }
                        canvas.restoreToCount(save)
                    }
                }
            }
            28 -> {
                // 28 = Distorção de Calor e Vapor (Mirage): ondulação e refração horizontal contínua
                val bands = 16
                val bandH = height / bands
                val miragePeak = sin(t * PI.toFloat())

                for (i in 0 until bands) {
                    val top = i * bandH
                    val bottom = top + bandH + 1f
                    val waveDx = sin(i * 0.9f + t * 14f) * miragePeak * (width * 0.055f)

                    val save = canvas.save()
                    canvas.clipRect(0f, top, width, bottom)
                    paint.alpha = ((1f - t) * 255).toInt()
                    canvas.drawBitmap(bitmap1, Matrix(matrix1).apply { postTranslate(waveDx, 0f) }, paint)
                    paint.alpha = (t * 255).toInt()
                    canvas.drawBitmap(bitmap2, Matrix(matrix2).apply { postTranslate(-waveDx, 0f) }, paint)
                    canvas.restoreToCount(save)
                }
            }
            29 -> {
                // 29 = Obturador de Lâminas Helicoidais: lâminas em espiral fechando e abrindo com rotação
                paint.color = Color.BLACK
                paint.alpha = 255
                canvas.drawRect(0f, 0f, width, height, paint)

                val numBlades = 6
                val maxR = hypot(cx, cy) * 1.2f
                val openFactor = if (t < 0.5f) (1f - t / 0.5f) else ((t - 0.5f) / 0.5f)
                val rotDeg = sin(t * PI.toFloat()) * 42f
                val polyRadius = (maxR * openFactor).coerceAtLeast(4f)

                val polyPath = Path()
                for (i in 0 until numBlades) {
                    val angleRad = Math.toRadians((i * (360.0 / numBlades)) + rotDeg)
                    val px = cx + (polyRadius * cos(angleRad)).toFloat()
                    val py = cy + (polyRadius * sin(angleRad)).toFloat()
                    if (i == 0) polyPath.moveTo(px, py) else polyPath.lineTo(px, py)
                }
                polyPath.close()

                val save = canvas.save()
                canvas.clipPath(polyPath)
                if (t < 0.5f) {
                    canvas.drawBitmap(bitmap1, matrix1, paint)
                } else {
                    canvas.drawBitmap(bitmap2, matrix2, paint)
                }
                canvas.restoreToCount(save)
            }
            30 -> {
                // 30 = Efeito de Dissolução por Halogéneo: desfoque horizontal + brilho esbranquiçado nos tons médios
                val peak = sin(t * PI.toFloat())
                val horizBlur = peak * 10f

                val a1 = ((1f - t) * 255).toInt()
                if (a1 > 0) {
                    paint.alpha = (a1 * 0.5f).toInt()
                    canvas.drawBitmap(bitmap1, Matrix(matrix1).apply { postTranslate(horizBlur, 0f) }, paint)
                    canvas.drawBitmap(bitmap1, Matrix(matrix1).apply { postTranslate(-horizBlur, 0f) }, paint)
                    paint.alpha = a1
                    canvas.drawBitmap(bitmap1, matrix1, paint)
                }

                val a2 = (t * 255).toInt()
                if (a2 > 0) {
                    paint.alpha = a2
                    canvas.drawBitmap(bitmap2, matrix2, paint)
                }

                if (peak > 0.01f) {
                    val haloAlpha = (peak * 195).toInt().coerceIn(0, 255)
                    val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            cx, cy, hypot(cx, cy).coerceAtLeast(10f),
                            intArrayOf(
                                Color.argb(haloAlpha, 255, 252, 235),
                                Color.argb((haloAlpha * 0.65f).toInt(), 255, 244, 214),
                                Color.argb(0, 255, 255, 255)
                            ),
                            floatArrayOf(0f, 0.55f, 1f),
                            Shader.TileMode.CLAMP
                        )
                    }
                    canvas.drawRect(0f, 0f, width, height, haloPaint)
                }
            }
        }
        paint.alpha = 255
        paint.colorFilter = null
        paint.shader = null
    }

    private fun renderGaussianBlurDissolve(
        canvas: Canvas,
        paint: Paint,
        bitmap1: Bitmap,
        matrix1: Matrix,
        bitmap2: Bitmap,
        matrix2: Matrix,
        t: Float,
        maxOffset: Float
    ) {
        val blur1 = t * maxOffset
        val blur2 = (1f - t) * maxOffset

        val alpha1 = ((1f - t) * 255).toInt()
        if (alpha1 > 0) {
            if (blur1 > 1f) {
                paint.alpha = (alpha1 * 0.38f).toInt()
                canvas.drawBitmap(bitmap1, Matrix(matrix1).apply { postTranslate(blur1, 0f) }, paint)
                canvas.drawBitmap(bitmap1, Matrix(matrix1).apply { postTranslate(-blur1, 0f) }, paint)
                canvas.drawBitmap(bitmap1, Matrix(matrix1).apply { postTranslate(0f, blur1) }, paint)
                canvas.drawBitmap(bitmap1, Matrix(matrix1).apply { postTranslate(0f, -blur1) }, paint)
            }
            paint.alpha = alpha1
            canvas.drawBitmap(bitmap1, matrix1, paint)
        }

        val alpha2 = (t * 255).toInt()
        if (alpha2 > 0) {
            if (blur2 > 1f) {
                paint.alpha = (alpha2 * 0.38f).toInt()
                canvas.drawBitmap(bitmap2, Matrix(matrix2).apply { postTranslate(blur2, 0f) }, paint)
                canvas.drawBitmap(bitmap2, Matrix(matrix2).apply { postTranslate(-blur2, 0f) }, paint)
                canvas.drawBitmap(bitmap2, Matrix(matrix2).apply { postTranslate(0f, blur2) }, paint)
                canvas.drawBitmap(bitmap2, Matrix(matrix2).apply { postTranslate(0f, -blur2) }, paint)
            }
            paint.alpha = alpha2
            canvas.drawBitmap(bitmap2, matrix2, paint)
        }
    }

    companion object {
        const val MAX_ID = 30

        val NO_TRANSITION = TransitionEffect(
            id = 0,
            name = "Sem Transição",
            category = "Corte",
            description = "Corte direto entre cenas sem interpolação"
        )

        /**
         * Os 30 Efeitos de Transições Profissionais especificados (mais 0 = Sem Transição):
         */
        val ALL_TRANSITIONS: List<TransitionEffect> = listOf(
            NO_TRANSITION,
            TransitionEffect(
                id = 1,
                name = "Blur Dissolve Cinematográfico",
                category = "Suave",
                description = "A mídia atual sofre um desfoque gaussiano rápido e uniforme enquanto perde opacidade. Simultaneamente, a nova mídia surge por trás, partindo de um estado totalmente desfocado e transparente até atingir nitidez absoluta e opacidade total."
            ),
            TransitionEffect(
                id = 2,
                name = "Chicote Horizontal (Whip Pan)",
                category = "Movimento",
                description = "A tela sofre um arrasto lateral ultraveloz para a direita, gerando um desfoque de movimento horizontal linear tão intenso que transforma a imagem em faixas coloridas borradas, revelando a nova mídia assim que o movimento cessa abruptamente."
            ),
            TransitionEffect(
                id = 3,
                name = "Impacto de Zoom (Punch In)",
                category = "Escala",
                description = "A imagem atual avança rapidamente em direção ao espectador através de uma expansão de escala centralizada. Durante o avanço, as bordas sofrem um desfoque radial acentuado, criando uma sensação de tunelamento até que a nova mídia assuma o plano principal."
            ),
            TransitionEffect(
                id = 4,
                name = "Separação de Canais RGB (Glitch)",
                category = "Estilizado",
                description = "A imagem divide-se momentaneamente nas suas três camadas de cor primárias: vermelho, verde e azul. Estas camadas sofrem pequenos deslocamentos horizontais desalinhados e tremuras estáticas antes de se fundirem perfeitamente na imagem da nova mídia."
            ),
            TransitionEffect(
                id = 5,
                name = "Clarão de Lente Orgânico (Light Leak)",
                category = "Iluminação",
                description = "Uma onda de luz quente, com tons de âmbar e dourado, invade o ecrã a partir do canto superior esquerdo através do modo de mistura de ecrã (screen). A luz intensifica-se até cobrir quase toda a imagem e, ao dissipar-se, revela a nova mídia."
            ),
            TransitionEffect(
                id = 6,
                name = "Deslocamento Vertical Fluido (Push Up)",
                category = "Posição",
                description = "A mídia atual é empurrada para cima e sai do enquadramento enquanto a nova mídia entra pelo limite inferior do ecrã. O movimento segue uma desaceleração elástica, onde a nova imagem passa ligeiramente do ponto final e estabiliza com suavidade."
            ),
            TransitionEffect(
                id = 7,
                name = "Exposição Fotográfica Estourada",
                category = "Iluminação",
                description = "Os realces e os pontos brancos da imagem atual aumentam de intensidade de forma exponencial, simulando uma superesposição de câmara que queima os detalhes até deixar o ecrã totalmente branco. A partir do branco puro, a nova mídia surge recuperando o contraste."
            ),
            TransitionEffect(
                id = 8,
                name = "Zoom Invertido com Recuo (Punch Out)",
                category = "Escala",
                description = "A mídia atual encolhe rapidamente em direção ao centro da tela, revelando que estava inserida dentro de uma moldura infinita. A nova mídia expande-se a partir do ponto central microscópico, preenchendo o ecrã inteiro com um efeito de sucção reversa."
            ),
            TransitionEffect(
                id = 9,
                name = "Distorção de Lente Anamórfica",
                category = "Estilizado",
                description = "As bordas da imagem atual sofrem uma curvatura esférica extrema, simulando uma lente olho de peixe combinada com um desfoque direcional puramente horizontal. A imagem distorce-se até ao limite antes de se endireitar instantaneamente na nova mídia."
            ),
            TransitionEffect(
                id = 10,
                name = "Divisão por Cortina Minimalista (Split Wipe)",
                category = "Máscara",
                description = "A imagem atual divide-se ao meio por uma linha vertical invisível. A metade esquerda desloca-se para fora do ecrã pelo lado esquerdo, e a metade direita pelo lado direito, revelando a nova mídia que já se encontrava estática no fundo."
            ),
            TransitionEffect(
                id = 11,
                name = "Preenchimento por Vetor Radial",
                category = "Máscara",
                description = "Uma linha reta invisível fixa no centro do ecrã faz uma rotação de 360 graus no sentido dos ponteiros do relógio. À medida que avança, vai revelando a nova mídia num movimento circular contínuo e limpo, sem qualquer tipo de desfoque ou textura."
            ),
            TransitionEffect(
                id = 12,
                name = "Desvanecimento Texturizado Monocromático",
                category = "Suave",
                description = "A mídia atual perde toda a saturação de cor até ficar completamente a preto e branco. Logo de seguida, a opacidade diminui gradualmente para revelar as cores vibrantes da nova mídia que surge por baixo."
            ),
            TransitionEffect(
                id = 13,
                name = "Chicote Diagonal Invertido",
                category = "Movimento",
                description = "A câmara simula uma guinada rápida na diagonal, do canto inferior esquerdo para o canto superior direito. A imagem transforma-se num rasto de linhas diagonais desfocadas que guiam o olhar do espectador diretamente para o ponto central da nova mídia."
            ),
            TransitionEffect(
                id = 14,
                name = "Interferência de Ruído Analógico (VCR)",
                category = "Estilizado",
                description = "Uma barra horizontal de estática cinzenta e ruído granulado cruza verticalmente o ecrã de cima para baixo. À passagem dessa linha de interferência, a imagem da primeira mídia é substituída pela imagem da segunda mídia, com uma ligeira distorção magnética na borda da linha."
            ),
            TransitionEffect(
                id = 15,
                name = "Sombra Projetada por Deslocamento (Slide)",
                category = "Posição",
                description = "A nova mídia desliza horizontalmente da direita para a esquerda, sobrepondo-se à mídia anterior. A borda esquerda desta nova mídia possui uma sombra preta suave e difusa projetada sobre a imagem antiga, criando uma ilusão tridimensional de camadas físicas."
            ),
            TransitionEffect(
                id = 16,
                name = "Desfoque de Inclinação Seletiva (Tilt-Shift)",
                category = "Suave",
                description = "As áreas superior e inferior do ecrã ficam imediatamente desfocadas, mantendo apenas uma faixa central nítida. Esse desfoque espalha-se rapidamente em direção ao centro até cobrir toda a tela, limpando-se logo a seguir a partir do centro para revelar a nova mídia."
            ),
            TransitionEffect(
                id = 17,
                name = "Inversão de Luminância por Canal",
                category = "Estilizado",
                description = "O ponto de corte entre as mídias é marcado por uma inversão instantânea das zonas escuras e claras (efeito negativo) focado apenas nas áreas de maior contraste. O ecrã pisca visualmente nesta estética de raio-X antes de estabilizar a nova imagem."
            ),
            TransitionEffect(
                id = 18,
                name = "Crescimento por Máscara de Brilho (Luma Fade)",
                category = "Máscara",
                description = "A nova mídia começa a surgir primeiro através das zonas mais claras e iluminadas da mídia atual. O efeito espalha-se progressivamente para os tons médios e, finalmente, preenche as áreas de sombra escura, completando a transição de forma orgânica."
            ),
            TransitionEffect(
                id = 19,
                name = "Onda de Choque Concéntrica",
                category = "Distorção",
                description = "Um anel de distorção circular propaga-se a partir do centro do ecrã em direção às extremidades, simulando uma gota a cair na água. À medida que o anel expande, vai deformando os píxeis da mídia antiga e deixando a nova mídia perfeitamente nítida no seu interior."
            ),
            TransitionEffect(
                id = 20,
                name = "Corte Seco com Flash Esmeralda",
                category = "Iluminação",
                description = "No momento exato da troca de mídias, ocorre uma sobreposição monocromática de tonalidade esmeralda escura com opacidade reduzida que dura apenas uma fração de segundo. Este filtro de cor funciona como um pico de energia visual que suaviza o corte direto."
            ),
            TransitionEffect(
                id = 21,
                name = "Transição por Losango Concêntrico",
                category = "Máscara Geométrica",
                description = "Um pequeno losango geométrico perfeito surge no centro exato do ecrã e expande-se simetricamente em direção às bordas. O interior do losango revela a nova mídia completamente nítida, enquanto a área exterior mantém a mídia anterior intocada até ser totalmente preenchida."
            ),
            TransitionEffect(
                id = 22,
                name = "Queima de Película Orgânica (Film Burn)",
                category = "Cinematográfica Clássica",
                description = "Manchas orgânicas de luz texturizada em tons de laranja vivo, vermelho e amarelo surgem nas bordas do enquadramento, simulando o derretimento físico de uma película de rolo sob o calor do projetor. A queima consome a imagem atual até que a nova mídia seja revelada sob as cinzas digitais."
            ),
            TransitionEffect(
                id = 23,
                name = "Cortina de Barras Diagonais",
                category = "Máscara Geométrica",
                description = "Múltiplas faixas geométricas paralelas, inclinadas num ângulo preciso de 45 graus, cruzam o ecrã deslizando rapidamente da esquerda para a direita. Cada barra funciona como uma máscara opaca que traz consigo uma fatia da nova mídia, completando a imagem quando todas se unem."
            ),
            TransitionEffect(
                id = 24,
                name = "Grão de Prata e Obturador Mecânico",
                category = "Cinematográfica Clássica",
                description = "A imagem atual sofre um aumento súbito de grão cinematográfico áspero e uma ligeira trepidação vertical que simula uma falha no motor do obturador de uma câmara de 35mm. Um piscar preto horizontal rápido corta a imagem, abrindo-se de imediato na nova mídia já estabilizada."
            ),
            TransitionEffect(
                id = 25,
                name = "Revelação por Círculo Íris (Iris Wipe)",
                category = "Máscara Geométrica / Clássica",
                description = "Uma máscara circular perfeita fecha-se em torno do ponto de interesse principal da primeira mídia, isolando-o num fundo preto absoluto, simulando a abertura de uma lente antiga. O círculo abre-se novamente a partir desse mesmo ponto, mas agora revelando a nova mídia por completo."
            ),
            TransitionEffect(
                id = 26,
                name = "Vinheta de Época Desbotada",
                category = "Cinematográfica Clássica",
                description = "As bordas do ecrã escurecem rapidamente num tom sépia difuso, criando uma vinheta circular profunda que comprime a primeira imagem. O centro sofre uma perda de contraste e desbota para um tom envelhecido antes de expandir a nova mídia a partir do núcleo de luz."
            ),
            TransitionEffect(
                id = 27,
                name = "Grade de Blocos Isométricos",
                category = "Máscara Geométrica / Outros",
                description = "O ecrã divide-se numa grade invisível de quadrados simétricos. Cada quadrado roda 180 graus sobre o seu próprio eixo vertical de forma escalonada, como se fosse um painel giratório físico, onde a face frontal exibe a mídia antiga e a face traseira revela a nova imagem."
            ),
            TransitionEffect(
                id = 28,
                name = "Distorção de Calor e Vapor (Mirage)",
                category = "Outros Estilos",
                description = "A imagem atual começa a ondular e a sofrer refração horizontal contínua, simulando o efeito visual do ar quente sobre o asfalto ou uma miragem no deserto. As ondas de distorção tornam-se tão intensas que liquefazem a imagem, transmutando as formas na nova mídia."
            ),
            TransitionEffect(
                id = 29,
                name = "Obturador de Lâminas Helicoidais",
                category = "Máscara Geométrica",
                description = "Várias lâminas triangulares geométricas partem das extremidades do ecrã e rodam em espiral em direção ao centro, fechando o plano como o diafragma de uma lente fotográfica. Ao atingirem o centro, as lâminas invertem o movimento de rotação, abrindo-se para revelar a nova mídia."
            ),
            TransitionEffect(
                id = 30,
                name = "Efeito de Dissolução por Halogéneo",
                category = "Outro Estilo",
                description = "A primeira mídia sofre um desfoque horizontal combinado com um brilho esbranquiçado intenso focado exclusivamente nos tons médios, imitando a luz de uma lâmpada de halogéneo de estúdio a acender. Esse pico de iluminação difusa dissipa-se rapidamente para revelar a composição da nova mídia."
            )
        )

        val DEFAULT: TransitionEffect = ALL_TRANSITIONS[1] // Blur Dissolve Cinematográfico

        fun findById(id: Int): TransitionEffect? =
            ALL_TRANSITIONS.firstOrNull { it.id == id }

        fun getOrCut(id: Int): TransitionEffect =
            findById(id) ?: NO_TRANSITION
    }
}
