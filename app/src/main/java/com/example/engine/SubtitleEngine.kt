package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import com.example.data.model.ParsedSubtitleItem
import com.example.data.model.SubtitleBackgroundType
import com.example.data.model.SubtitleEntryAnimation
import com.example.data.model.SubtitleFontWeight
import com.example.data.model.SubtitleStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

sealed class SubtitleValidationResult {
    data class Success(val items: List<ParsedSubtitleItem>) : SubtitleValidationResult()
    data class Error(
        val message: String,
        val faultyPart: String
    ) : SubtitleValidationResult()
}

sealed class Stage2SubtitleCheckResult {
    data class AllValid(val validSubtitles: List<ParsedSubtitleItem>) : Stage2SubtitleCheckResult()
    data class HasInvalidTimes(
        val validSubtitles: List<ParsedSubtitleItem>,
        val invalidSubtitles: List<ParsedSubtitleItem>,
        val invalidTimesLabel: String,
        val videoDurationFormatted: String
    ) : Stage2SubtitleCheckResult()
}

object SubtitleEngine {

    const val PLACEHOLDER_EXAMPLE =
        "00:00 + EXEMPLO DE TEXTO DA LEGENDA = 00:13, 00:14 + EXEMPLO DE TEXTO DA LEGENDA = 00:20..."

    private val demoBitmapCache = ConcurrentHashMap<Int, Bitmap>()

    /**
     * Valida e interpreta o campo de legendas no formato:
     * 00:00 + EXEMPLO DE TEXTO DA LEGENDA = 00:13, 00:14 + EXEMPLO DE TEXTO DA LEGENDA = 00:20
     *
     * Regras:
     * - Separa as legendas por vírgula.
     * - Nunca inclui os tempos no texto final da legenda.
     * - Caso duas legendas ou mais tenham o mesmo tempo de exibição (início) ou final (ou sobreposição de mesmo tempo),
     *   exibe erro com NO MÁXIMO 5 PALAVRAS na mesma linha mostrando a parte específica que tem esse erro.
     */
    fun parseAndValidateSubtitles(rawInput: String): SubtitleValidationResult {
        val cleanedInput = rawInput.trim().removeSuffix("...").trim()
        if (cleanedInput.isEmpty()) {
            return SubtitleValidationResult.Error(
                message = "Preencha o campo legendas",
                faultyPart = "vazio"
            )
        }

        val rawSegments = cleanedInput
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawSegments.isEmpty()) {
            return SubtitleValidationResult.Error(
                message = "Preencha o campo legendas",
                faultyPart = "vazio"
            )
        }

        val parsedItems = mutableListOf<ParsedSubtitleItem>()
        val seenStartTimes = mutableMapOf<Int, String>()
        val seenEndTimes = mutableMapOf<Int, String>()
        val seenAllTimeBoundaries = mutableMapOf<Int, String>()

        for (segment in rawSegments) {
            val plusIdx = segment.indexOf('+')
            val equalsIdx = segment.lastIndexOf('=')

            if (plusIdx <= 0 || equalsIdx <= plusIdx + 1 || equalsIdx >= segment.length - 1) {
                val shortToken = compactPartForError(segment)
                return SubtitleValidationResult.Error(
                    message = "Formato inválido em $shortToken",
                    faultyPart = shortToken
                )
            }

            val startRaw = segment.substring(0, plusIdx).trim()
            val textRaw = segment.substring(plusIdx + 1, equalsIdx).trim()
            val endRaw = segment.substring(equalsIdx + 1).trim()

            val startSeconds = parseTimestampToSeconds(startRaw)
            if (startSeconds == null || startSeconds < 0f) {
                val badStart = compactPartForError(startRaw)
                return SubtitleValidationResult.Error(
                    message = "Início inválido em $badStart",
                    faultyPart = badStart
                )
            }

            val endSeconds = parseTimestampToSeconds(endRaw)
            if (endSeconds == null || endSeconds <= 0f) {
                val badEnd = compactPartForError(endRaw)
                return SubtitleValidationResult.Error(
                    message = "Fim inválido em $badEnd",
                    faultyPart = badEnd
                )
            }

            if (textRaw.isEmpty()) {
                val shortStart = formatSecondsToMmSs(startSeconds)
                return SubtitleValidationResult.Error(
                    message = "Texto vazio em $shortStart",
                    faultyPart = shortStart
                )
            }

            if (endSeconds <= startSeconds) {
                val shortEnd = formatSecondsToMmSs(endSeconds)
                return SubtitleValidationResult.Error(
                    message = "Fim menor em $shortEnd",
                    faultyPart = shortEnd
                )
            }

            val startKey = Math.round(startSeconds * 100f)
            val endKey = Math.round(endSeconds * 100f)
            val startFormatted = formatSecondsToMmSs(startSeconds)
            val endFormatted = formatSecondsToMmSs(endSeconds)

            if (seenStartTimes.containsKey(startKey)) {
                return SubtitleValidationResult.Error(
                    message = "Início repetido em $startFormatted",
                    faultyPart = startFormatted
                )
            }

            if (seenEndTimes.containsKey(endKey)) {
                return SubtitleValidationResult.Error(
                    message = "Fim repetido em $endFormatted",
                    faultyPart = endFormatted
                )
            }

            if (seenAllTimeBoundaries.containsKey(startKey)) {
                return SubtitleValidationResult.Error(
                    message = "Tempo repetido em $startFormatted",
                    faultyPart = startFormatted
                )
            }

            if (seenAllTimeBoundaries.containsKey(endKey)) {
                return SubtitleValidationResult.Error(
                    message = "Tempo repetido em $endFormatted",
                    faultyPart = endFormatted
                )
            }

            val overlapping = parsedItems.firstOrNull { existing ->
                startSeconds < existing.endTimeSeconds && endSeconds > existing.startTimeSeconds
            }
            if (overlapping != null) {
                return SubtitleValidationResult.Error(
                    message = "Tempo repetido em $startFormatted",
                    faultyPart = startFormatted
                )
            }

            seenStartTimes[startKey] = startFormatted
            seenEndTimes[endKey] = endFormatted
            seenAllTimeBoundaries[startKey] = startFormatted
            seenAllTimeBoundaries[endKey] = endFormatted

            parsedItems.add(
                ParsedSubtitleItem(
                    startTimeSeconds = startSeconds,
                    endTimeSeconds = endSeconds,
                    startTimeFormatted = startFormatted,
                    endTimeFormatted = endFormatted,
                    text = textRaw,
                    rawToken = segment
                )
            )
        }

        return SubtitleValidationResult.Success(parsedItems.sortedBy { it.startTimeSeconds })
    }

    /**
     * Etapa 2: Verifica se todas as legendas possuem tempos que existem dentro da duração do vídeo completo criado.
     */
    fun checkSubtitlesInFinalVideoDuration(
        subtitles: List<ParsedSubtitleItem>,
        videoDurationSeconds: Float
    ): Stage2SubtitleCheckResult {
        val safeDuration = videoDurationSeconds.coerceAtLeast(0.1f)
        val validList = mutableListOf<ParsedSubtitleItem>()
        val invalidList = mutableListOf<ParsedSubtitleItem>()

        for (item in subtitles) {
            if (item.startTimeSeconds >= 0f &&
                item.startTimeSeconds < safeDuration &&
                item.endTimeSeconds <= safeDuration + 0.05f
            ) {
                validList.add(item)
            } else {
                invalidList.add(item)
            }
        }

        return if (invalidList.isEmpty()) {
            Stage2SubtitleCheckResult.AllValid(validList)
        } else {
            val invalidSummary = invalidList.joinToString(", ") { "${it.startTimeFormatted}=${it.endTimeFormatted}" }
            Stage2SubtitleCheckResult.HasInvalidTimes(
                validSubtitles = validList,
                invalidSubtitles = invalidList,
                invalidTimesLabel = invalidSummary,
                videoDurationFormatted = formatSecondsToMmSs(safeDuration)
            )
        }
    }

    fun parseTimestampToSeconds(raw: String): Float? {
        val cleaned = raw.trim().lowercase(Locale.US).removeSuffix("s").trim()
        if (cleaned.isEmpty()) return null

        if (cleaned.contains(":")) {
            val parts = cleaned.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].trim().toIntOrNull() ?: return null
                val seconds = parts[1].trim().replace(',', '.').toFloatOrNull() ?: return null
                if (minutes < 0 || seconds < 0f || seconds >= 60f) return null
                return minutes * 60f + seconds
            } else if (parts.size == 3) {
                val hours = parts[0].trim().toIntOrNull() ?: return null
                val minutes = parts[1].trim().toIntOrNull() ?: return null
                val seconds = parts[2].trim().replace(',', '.').toFloatOrNull() ?: return null
                if (hours < 0 || minutes < 0 || minutes >= 60 || seconds < 0f || seconds >= 60f) return null
                return hours * 3600f + minutes * 60f + seconds
            }
            return null
        }

        return cleaned.replace(',', '.').toFloatOrNull()
    }

    fun formatSecondsToMmSs(seconds: Float): String {
        val totalSecs = seconds.coerceAtLeast(0f).toInt()
        val mins = totalSecs / 60
        val secs = totalSecs % 60
        return String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    private fun compactPartForError(raw: String): String {
        val singleToken = raw.trim().split(Regex("\\s+")).firstOrNull()?.take(12) ?: "item"
        return singleToken.ifEmpty { "item" }
    }

    /**
     * Configura o Paint tipográfico de acordo com a família de fonte e peso de cada um dos 15 modelos.
     */
    private fun configurePaintTypography(paint: Paint, style: SubtitleStyle) {
        val familyLower = style.fontFamily.lowercase(Locale.US)
        val baseFamilyName = when {
            familyLower.contains("bebas") -> "sans-serif-condensed"
            familyLower.contains("montserrat") || familyLower.contains("poppins") -> "sans-serif-medium"
            familyLower.contains("ubuntu") -> "sans-serif"
            familyLower.contains("open sans") || familyLower.contains("inter") || familyLower.contains("segoe") -> "sans-serif"
            familyLower.contains("arial") || familyLower.contains("helvetica") -> "sans-serif"
            else -> "sans-serif"
        }

        val androidStyle = when (style.fontWeight) {
            SubtitleFontWeight.BOLD -> Typeface.BOLD
            SubtitleFontWeight.LIGHT -> Typeface.NORMAL
            SubtitleFontWeight.NORMAL -> Typeface.NORMAL
        }

        val baseTypeface = Typeface.create(baseFamilyName, androidStyle)
        paint.typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val numericWeight = when {
                familyLower.contains("bebas") -> 700
                style.fontWeight == SubtitleFontWeight.BOLD -> 700
                style.fontWeight == SubtitleFontWeight.LIGHT -> 300
                else -> 450
            }
            Typeface.create(baseTypeface, numericWeight, false)
        } else {
            baseTypeface
        }

        paint.isFakeBoldText = style.fontWeight == SubtitleFontWeight.BOLD || familyLower.contains("bebas")
        paint.textScaleX = when {
            familyLower.contains("bebas") -> 0.86f
            familyLower.contains("montserrat") -> 1.03f
            else -> 1.0f
        }
        paint.letterSpacing = when {
            familyLower.contains("bebas") -> 0.06f
            style.fontWeight == SubtitleFontWeight.LIGHT -> 0.04f
            familyLower.contains("montserrat") || familyLower.contains("poppins") -> 0.02f
            else -> 0.01f
        }
    }

    /**
     * Quebra um texto de legenda de forma responsiva e inteligente por palavras inteiras,
     * respeitando `maxLines` (1 ou 2 linhas) e garantindo que NUNCA corte uma palavra ao meio.
     */
    fun wrapSubtitleLinesSmart(
        text: String,
        paint: Paint,
        maxWidthPx: Float,
        preferTwoLines: Boolean = false
    ): List<String> {
        val normalized = text.trim().replace(Regex("\\s+"), " ")
        if (normalized.isEmpty()) return emptyList()

        val words = normalized.split(" ")
        if (!preferTwoLines) {
            // Modelos de 1 linha (max_lines = 1): mantém em 1 linha única se couber na largura disponível
            if (paint.measureText(normalized) <= maxWidthPx) {
                return listOf(normalized)
            }
        } else if (words.size >= 2) {
            // Modelos de 2 linhas (max_lines = 2): divide equilibradamente em 2 linhas
            val midIndex = (words.size + 1) / 2
            val firstHalf = words.subList(0, midIndex).joinToString(" ")
            val secondHalf = words.subList(midIndex, words.size).joinToString(" ")
            if (paint.measureText(firstHalf) <= maxWidthPx && paint.measureText(secondHalf) <= maxWidthPx) {
                return listOf(firstHalf, secondHalf)
            }
        }

        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
            val candidateWidth = paint.measureText(candidate)
            if (candidateWidth <= maxWidthPx || currentLine.isEmpty()) {
                currentLine.clear()
                currentLine.append(candidate)
            } else {
                lines.add(currentLine.toString())
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return lines
    }

    /**
     * Desenha a legenda no Canvas conforme as especificações visuais dos 15 modelos:
     * - Na área de pré-visualização (`centerInPreview = true`): exibe NO CENTRO DA TELA e centralizado!
     * - No vídeo final renderizado (`centerInPreview = false`): exibe no RODAPÉ do vídeo!
     * - Suporta `background_type`: `transparent`, `box`, `bar`, `text_width_box`
     * - Suporta `entry_animation`: `none`, `fade-in`, `slide-up`, `scale-up`
     * - Suporta `max_lines`: 1 ou 2 linhas, `border_radius`, `text_shadow` e `background_opacity`.
     */
    fun drawSubtitleOnCanvas(
        canvas: Canvas,
        rawPhrase: String,
        style: SubtitleStyle,
        canvasWidth: Int,
        canvasHeight: Int,
        centerInPreview: Boolean = false,
        entryProgress: Float = 1.0f
    ) {
        if (style.id == 0) return
        val cleanPhrase = rawPhrase.trim()
        if (cleanPhrase.isEmpty()) return

        val progress = entryProgress.coerceIn(0f, 1f)
        val displayPhrase = if (style.isUppercase) cleanPhrase.uppercase(Locale.getDefault()) else cleanPhrase
        val maxTextWidth = (canvasWidth * 0.84f).coerceAtLeast(120f)
        val maxBlockHeight = (canvasHeight * 0.34f).coerceAtLeast(60f)

        val scaleFactor = (canvasHeight / 480f).coerceIn(0.45f, 2.5f)
        var fontSizePx = (canvasHeight * if (style.maxLines == 1) 0.044f else 0.040f).coerceIn(14f, 64f)
        val minFontSizePx = (canvasHeight * 0.020f).coerceAtLeast(10f)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            textSize = fontSizePx
        }
        configurePaintTypography(textPaint, style)

        var wrappedLines = wrapSubtitleLinesSmart(
            text = displayPhrase,
            paint = textPaint,
            maxWidthPx = maxTextWidth,
            preferTwoLines = style.maxLines >= 2
        )

        // Ajusta responsivamente o tamanho da fonte para respeitar max_lines (1 ou 2) e largura disponível sem cortar palavras
        while (fontSizePx > minFontSizePx) {
            textPaint.textSize = fontSizePx
            wrappedLines = wrapSubtitleLinesSmart(
                text = displayPhrase,
                paint = textPaint,
                maxWidthPx = maxTextWidth,
                preferTwoLines = style.maxLines >= 2
            )
            val maxLineMeasured = wrappedLines.maxOfOrNull { textPaint.measureText(it) } ?: 0f
            val lineHeight = fontSizePx * 1.26f
            val totalBlockHeight = wrappedLines.size * lineHeight
            val fitsTargetLines = wrappedLines.size <= style.maxLines
            if (fitsTargetLines && maxLineMeasured <= maxTextWidth && totalBlockHeight <= maxBlockHeight) {
                break
            }
            fontSizePx -= 1.2f
        }

        textPaint.textSize = fontSizePx
        wrappedLines = wrapSubtitleLinesSmart(
            text = displayPhrase,
            paint = textPaint,
            maxWidthPx = maxTextWidth,
            preferTwoLines = style.maxLines >= 2
        )
        if (wrappedLines.isEmpty()) return

        val lineHeight = fontSizePx * 1.28f
        val totalTextHeight = wrappedLines.size * lineHeight
        val maxLineW = (wrappedLines.maxOfOrNull { textPaint.measureText(it) } ?: 80f).coerceAtMost(maxTextWidth)

        // Espaçamento horizontal e vertical conforme o background_type do modelo
        val padH = when (style.backgroundType) {
            SubtitleBackgroundType.TEXT_WIDTH_BOX -> (fontSizePx * 0.28f).coerceAtLeast(6f)
            SubtitleBackgroundType.BAR -> (fontSizePx * 0.90f).coerceAtLeast(18f)
            SubtitleBackgroundType.BOX -> (fontSizePx * 0.65f).coerceAtLeast(14f)
            SubtitleBackgroundType.TRANSPARENT -> (fontSizePx * 0.35f).coerceAtLeast(8f)
        }
        val padV = when (style.backgroundType) {
            SubtitleBackgroundType.TEXT_WIDTH_BOX -> (fontSizePx * 0.22f).coerceAtLeast(5f)
            SubtitleBackgroundType.BAR -> (fontSizePx * 0.42f).coerceAtLeast(10f)
            SubtitleBackgroundType.BOX -> (fontSizePx * 0.38f).coerceAtLeast(9f)
            SubtitleBackgroundType.TRANSPARENT -> (fontSizePx * 0.25f).coerceAtLeast(6f)
        }

        val boxWidth = when (style.backgroundType) {
            SubtitleBackgroundType.BAR -> canvasWidth.toFloat()
            SubtitleBackgroundType.TEXT_WIDTH_BOX -> (maxLineW + padH * 2f).coerceAtMost(canvasWidth * 0.94f)
            else -> (maxLineW + padH * 2f).coerceAtMost(canvasWidth * 0.92f)
        }
        val boxHeight = totalTextHeight + padV * 2f
        val centerX = canvasWidth / 2f

        val boxTop: Float
        val boxBottom: Float
        if (centerInPreview) {
            // Na tela de pré-visualização: centralizado no centro da tela
            val centerY = canvasHeight / 2f
            boxTop = (centerY - boxHeight / 2f).coerceAtLeast(6f)
            boxBottom = (centerY + boxHeight / 2f).coerceAtMost(canvasHeight - 6f)
        } else {
            // No vídeo final renderizado: posicionado no rodapé do vídeo
            val bottomMargin = (canvasHeight * 0.065f).coerceAtLeast(18f)
            boxBottom = canvasHeight - bottomMargin
            boxTop = (boxBottom - boxHeight).coerceAtLeast(8f)
        }

        val boxLeft = if (style.backgroundType == SubtitleBackgroundType.BAR) {
            0f
        } else {
            (centerX - boxWidth / 2f).coerceAtLeast(6f)
        }
        val boxRight = if (style.backgroundType == SubtitleBackgroundType.BAR) {
            canvasWidth.toFloat()
        } else {
            (centerX + boxWidth / 2f).coerceAtMost(canvasWidth - 6f)
        }
        val boxCenterY = (boxTop + boxBottom) / 2f

        // Calcula transformações da Animação de Entrada (none, fade-in, slide-up, scale-up)
        var scaleX = 1f
        var scaleY = 1f
        var translateY = 0f
        var alphaMultiplier = 1f

        when (style.entryAnimation) {
            SubtitleEntryAnimation.NONE -> {
                // Sem animação de entrada
            }
            SubtitleEntryAnimation.FADE_IN -> {
                if (progress < 1f) {
                    val ease = 1f - (1f - progress) * (1f - progress)
                    alphaMultiplier = ease.coerceIn(0.08f, 1f)
                }
            }
            SubtitleEntryAnimation.SLIDE_UP -> {
                if (progress < 1f) {
                    val easeOut = 1f - (1f - progress) * (1f - progress)
                    translateY = (1f - easeOut) * (boxHeight * 0.65f)
                    alphaMultiplier = easeOut.coerceIn(0.12f, 1f)
                }
            }
            SubtitleEntryAnimation.SCALE_UP -> {
                if (progress < 1f) {
                    val easeOut = 1f - (1f - progress) * (1f - progress)
                    val s = 0.70f + 0.30f * easeOut
                    scaleX = s
                    scaleY = s
                    alphaMultiplier = easeOut.coerceIn(0.15f, 1f)
                }
            }
        }

        val saveCount = canvas.save()
        try {
            canvas.translate(0f, translateY)
            if (scaleX != 1f || scaleY != 1f) {
                canvas.scale(scaleX, scaleY, centerX, boxCenterY)
            }

            // 1. Renderiza o fundo se background_type != transparent e backgroundColor != null
            if (style.backgroundType != SubtitleBackgroundType.TRANSPARENT && style.backgroundColor != null) {
                val baseBgAlpha = Color.alpha(style.backgroundColor)
                val finalBgAlpha = (baseBgAlpha * alphaMultiplier).toInt().coerceIn(0, 255)
                val bgColorWithAlpha = (style.backgroundColor and 0x00FFFFFF) or (finalBgAlpha shl 24)

                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.style = Paint.Style.FILL
                    color = bgColorWithAlpha
                }

                val boxRect = RectF(boxLeft, boxTop, boxRight, boxBottom)
                val radiusPx = (style.borderRadiusPx * scaleFactor * 1.35f).coerceAtLeast(0f)

                if (radiusPx <= 0.5f || style.backgroundType == SubtitleBackgroundType.BAR) {
                    canvas.drawRect(boxRect, bgPaint)
                } else {
                    canvas.drawRoundRect(boxRect, radiusPx, radiusPx, bgPaint)
                }
            }

            // 2. Renderiza cada linha do texto
            val fontMetrics = textPaint.fontMetrics
            val firstBaselineY = boxTop + padV - fontMetrics.ascent

            val baseTextAlpha = Color.alpha(style.textColor)
            val finalTextAlpha = (baseTextAlpha * alphaMultiplier).toInt().coerceIn(0, 255)
            val activeTextColor = (style.textColor and 0x00FFFFFF) or (finalTextAlpha shl 24)

            val fillPaint = Paint(textPaint).apply {
                this.style = Paint.Style.FILL
                color = activeTextColor
                if (style.hasTextShadow) {
                    val baseShadowAlpha = Color.alpha(style.shadowColor)
                    val finalShadowAlpha = (baseShadowAlpha * alphaMultiplier).toInt().coerceIn(0, 255)
                    val shadowWithAlpha = (style.shadowColor and 0x00FFFFFF) or (finalShadowAlpha shl 24)
                    setShadowLayer(
                        (style.shadowRadius * scaleFactor).coerceAtLeast(1f),
                        style.shadowDx * scaleFactor,
                        style.shadowDy * scaleFactor,
                        shadowWithAlpha
                    )
                } else {
                    clearShadowLayer()
                }
            }

            for ((lineIndex, lineText) in wrappedLines.withIndex()) {
                val baselineY = firstBaselineY + lineIndex * lineHeight
                canvas.drawText(lineText, centerX, baselineY, fillPaint)
            }
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * Gera uma imagem (Bitmap) de demonstração visual para cada um dos 15 modelos de legenda
     * representando fielmente a fonte, quantidade de linhas (1 ou 2), cor de texto, fundo e opacidade.
     */
    fun getStyleDemonstrationBitmap(style: SubtitleStyle, width: Int = 320, height: Int = 150): Bitmap? {
        demoBitmapCache[style.id]?.let { cached ->
            if (!cached.isRecycled) return cached
        }
        return try {
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ?: return null
            val canvas = Canvas(bmp)

            // Cenário de fundo de estúdio suave para evidenciar tanto fundos claros (#FFFFFF) quanto escuros e transparentes
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(0xFF1E293B.toInt(), 0xFF334155.toInt(), 0xFF0F172A.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val bokehPaint1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x2438BDF8
                this.style = Paint.Style.FILL
            }
            val bokehPaint2 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x20F59E0B
                this.style = Paint.Style.FILL
            }
            canvas.drawCircle(width * 0.28f, height * 0.45f, height * 0.34f, bokehPaint1)
            canvas.drawCircle(width * 0.74f, height * 0.55f, height * 0.30f, bokehPaint2)

            if (style.id == 0) {
                val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFF94A3B8.toInt()
                    textSize = height * 0.16f
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText("SEM LEGENDA", width / 2f, height * 0.56f, offPaint)
            } else {
                val demoPhrase = if (style.maxLines >= 2) {
                    "Legenda profissional em duas linhas"
                } else {
                    "Legenda profissional"
                }
                drawSubtitleOnCanvas(
                    canvas = canvas,
                    rawPhrase = demoPhrase,
                    style = style,
                    canvasWidth = width,
                    canvasHeight = height,
                    centerInPreview = true,
                    entryProgress = 1.0f
                )
            }

            demoBitmapCache[style.id] = bmp
            bmp
        } catch (_: Throwable) {
            null
        }
    }
}
