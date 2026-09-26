package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import com.example.data.model.CtaVideoItem
import com.example.data.model.MovementEffect
import com.example.data.model.ParsedSubtitleItem
import com.example.data.model.SubtitleStyle
import com.example.data.model.TransitionEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

data class RenderSequenceItem(
    val imageFile: File,
    val movementEffect: MovementEffect,
    val durationSeconds: Float,
    val transitionToNext: TransitionEffect = TransitionEffect.DEFAULT,
    val transitionSoundIdToNext: Int = 0
)

data class CtaRenderOverlayConfig(
    val ctaItem: CtaVideoItem,
    val startTimeSeconds: Float,
    val normalizedX: Float = 0.50f,
    val normalizedY: Float = 0.78f,
    val scale: Float = 0.38f,
    val durationSeconds: Float = 3.5f
)

data class LogoRenderOverlayConfig(
    val logoFile: File,
    val normalizedX: Float = 0.82f,
    val normalizedY: Float = 0.16f,
    val scale: Float = 0.22f
)

object VideoEncoder {

    private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
    private const val DEFAULT_FRAME_RATE = 30
    private const val I_FRAME_INTERVAL = 1
    private const val DEFAULT_BIT_RATE = 5_000_000 // 5.0 Mbps

    /**
     * Renderização de Vídeo Único (Exportação Unificada):
     * Processa a sequência completa de imagens e exporta um ÚNICO VÍDEO COMPLETO final,
     * unindo todas as imagens com suas durações, movimentos de câmera e transições suaves,
     * além das sobreposições opcionais de CTA (no tempo exato configurado) e Logo (do início ao fim).
     */
    suspend fun encodeUnifiedSequenceToVideo(
        sequence: List<RenderSequenceItem>,
        outputFile: File,
        targetWidth: Int = 1280,
        targetHeight: Int = 720,
        frameRate: Int = DEFAULT_FRAME_RATE,
        bitRate: Int = DEFAULT_BIT_RATE,
        transitionDurationSeconds: Float = 1.0f,
        ctaOverlay: CtaRenderOverlayConfig? = null,
        logoOverlay: LogoRenderOverlayConfig? = null,
        subtitles: List<ParsedSubtitleItem> = emptyList(),
        subtitleStyle: SubtitleStyle = SubtitleStyle.NO_SUBTITLE,
        timelineAudioFile: File? = null,
        onGlobalProgress: (currentFrame: Int, totalFrames: Int, currentImageIndex: Int, statusText: String) -> Unit = { _, _, _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Boolean = withContext(Dispatchers.Default) {
        if (sequence.isEmpty()) return@withContext false

        // 1. Calcula frames por imagem, frames de transição e duração visível contínua de cada cena
        val itemFrames = sequence.map { (it.durationSeconds * frameRate).toInt().coerceAtLeast(frameRate) }
        val totalGlobalFrames = itemFrames.sum().coerceAtLeast(1)
        val totalVideoDurationSeconds = totalGlobalFrames.toFloat() / frameRate.coerceAtLeast(1)

        // Prepara os quadros sem fundo (Chroma Key removido) do vídeo de CTA caso o tempo fornecido não ultrapasse o tempo do vídeo final
        val isCtaIncluded = ctaOverlay != null &&
            ctaOverlay.ctaItem.id != 0 &&
            ctaOverlay.startTimeSeconds >= 0f &&
            ctaOverlay.startTimeSeconds < totalVideoDurationSeconds
        val ctaStartFrame = if (isCtaIncluded && ctaOverlay != null) {
            (ctaOverlay.startTimeSeconds * frameRate).toInt().coerceAtLeast(0)
        } else -1
        val ctaDurationFrames = if (isCtaIncluded && ctaOverlay != null) {
            (ctaOverlay.durationSeconds * frameRate).toInt().coerceAtLeast(frameRate)
        } else 0
        val ctaW = if (isCtaIncluded && ctaOverlay != null) {
            (targetWidth * ctaOverlay.scale.coerceIn(0.16f, 0.85f)).toInt().coerceAtLeast(64)
        } else 0
        val ctaH = if (isCtaIncluded) {
            (ctaW * (200f / 360f)).toInt().coerceAtLeast(38)
        } else 0
        val ctaTransparentFrames: List<Bitmap> = if (isCtaIncluded && ctaOverlay != null) {
            CtaVideoEngine.getTransparentFramesForCta(ctaOverlay.ctaItem, ctaW, ctaH, 12)
        } else emptyList()

        // Prepara a imagem de Logo (exibida durante todo o vídeo do início ao fim na posição e tamanho ajustados)
        val scaledLogoBitmap: Bitmap? = if (logoOverlay != null && logoOverlay.logoFile.exists()) {
            val rawLogo = loadOptimizedBitmap(logoOverlay.logoFile, targetWidth, targetHeight)
            if (rawLogo != null) {
                val targetLogoMaxDim = (targetWidth * logoOverlay.scale.coerceIn(0.08f, 0.65f)).toInt().coerceAtLeast(32)
                val aspect = rawLogo.width.toFloat() / rawLogo.height.coerceAtLeast(1).toFloat()
                val lw: Int
                val lh: Int
                if (aspect >= 1f) {
                    lw = targetLogoMaxDim
                    lh = (targetLogoMaxDim / aspect).toInt().coerceAtLeast(16)
                } else {
                    lh = targetLogoMaxDim
                    lw = (targetLogoMaxDim * aspect).toInt().coerceAtLeast(16)
                }
                Bitmap.createScaledBitmap(rawLogo, lw, lh, true).also {
                    if (it != rawLogo) rawLogo.recycle()
                }
            } else null
        } else null

        // Calcula os frames de transição de saída de cada cena (0 até sequence.size - 2)
        val outTransFrames = IntArray(sequence.size) { idx ->
            val hasNextScene = idx < sequence.size - 1
            val transEffect = sequence[idx].transitionToNext
            if (hasNextScene && transEffect.id != 0) {
                val requestedFrames = (transitionDurationSeconds.coerceIn(0.4f, 6.0f) * frameRate).toInt().coerceAtLeast(1)
                requestedFrames.coerceIn(1, (itemFrames[idx] - 1).coerceAtLeast(1))
            } else {
                0
            }
        }

        // Frames de transição de entrada da cena (quando a cena começa a aparecer durante a transição anterior)
        val inTransFrames = IntArray(sequence.size) { idx ->
            if (idx > 0) outTransFrames[idx - 1] else 0
        }

        // Total de frames em que cada cena fica visível na tela (entrada + fase solo + saída)
        // Assim o movimento de câmera começa IMEDIATAMENTE no frame 0 da transição de entrada e
        // prossegue continuamente sem reiniciar e sem interferir na transição!
        val totalVisibleSceneFrames = IntArray(sequence.size) { idx ->
            (inTransFrames[idx] + itemFrames[idx]).coerceAtLeast(1)
        }

        val rawVideoFile = File(outputFile.parentFile, "raw_${System.currentTimeMillis()}_${outputFile.name}")
        if (rawVideoFile.exists()) {
            rawVideoFile.delete()
        }
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        var currentBitmap: Bitmap? = null
        var nextBitmap: Bitmap? = null
        var sceneLayer1: Bitmap? = null
        var sceneLayer2: Bitmap? = null

        try {
            val colorFormat = selectColorFormat()
            val format = MediaFormat.createVideoFormat(MIME_TYPE, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(rawVideoFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBitmap)
            // Buffers independentes para que o movimento de câmera de cada cena seja renderizado
            // de forma 100% isolada antes da aplicação do efeito de transição
            sceneLayer1 = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            sceneLayer2 = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvasScene1 = Canvas(sceneLayer1)
            val canvasScene2 = Canvas(sceneLayer2)
            val identityMatrix = Matrix()

            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val scenePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val matrixCurrent = Matrix()
            val matrixNext = Matrix()

            val argbArray = IntArray(targetWidth * targetHeight)
            val yuvArray = ByteArray(targetWidth * targetHeight * 3 / 2)

            var globalFrameIndex = 0

            // Helper para drenar buffers do encoder
            fun drainEncoder(endOfStream: Boolean) {
                var drainAttempts = 0
                while (drainAttempts < (if (endOfStream) 100 else 1)) {
                    val outIndex = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000 else 0)
                    if (outIndex >= 0) {
                        val encodedBuffer = encoder.getOutputBuffer(outIndex)
                        if (bufferInfo.size != 0 && muxerStarted && encodedBuffer != null) {
                            encodedBuffer.position(bufferInfo.offset)
                            encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            muxer.writeSampleData(videoTrackIndex, encodedBuffer, bufferInfo)
                        }
                        encoder.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            break
                        }
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                        val newFormat = encoder.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                        muxerStarted = true
                    } else {
                        if (endOfStream) drainAttempts++ else break
                    }
                }
            }

            // Helper para enviar um frame YUV ao encoder (aplicando antes o Logo contínuo e o vídeo de CTA no tempo definido)
            fun queueCurrentFrame(isLastFrame: Boolean): Boolean {
                // 1. Logo: aparece durante todo o vídeo do início ao fim na posição e tamanho ajustados pelo usuário
                if (scaledLogoBitmap != null && !scaledLogoBitmap.isRecycled && logoOverlay != null) {
                    val logoLeft = (logoOverlay.normalizedX.coerceIn(0.05f, 0.95f) * targetWidth - scaledLogoBitmap.width / 2f)
                        .coerceIn(0f, (targetWidth - scaledLogoBitmap.width).coerceAtLeast(0).toFloat())
                    val logoTop = (logoOverlay.normalizedY.coerceIn(0.05f, 0.95f) * targetHeight - scaledLogoBitmap.height / 2f)
                        .coerceIn(0f, (targetHeight - scaledLogoBitmap.height).coerceAtLeast(0).toFloat())
                    paint.alpha = 255
                    canvas.drawBitmap(scaledLogoBitmap, logoLeft, logoTop, paint)
                }

                // 2. CTA: aparece no tempo exato definido pelo usuário (se não ultrapassar o tempo do vídeo final)
                // na posição e tamanho ajustados pelo usuário, sem fundo (Chroma Key removido)
                if (isCtaIncluded && ctaOverlay != null && ctaTransparentFrames.isNotEmpty() &&
                    globalFrameIndex >= ctaStartFrame && globalFrameIndex < (ctaStartFrame + ctaDurationFrames)
                ) {
                    val ctaElapsedFrames = (globalFrameIndex - ctaStartFrame).coerceAtLeast(0)
                    val ctaFrameBitmap = ctaTransparentFrames[ctaElapsedFrames % ctaTransparentFrames.size]
                    if (!ctaFrameBitmap.isRecycled) {
                        val ctaLeft = (ctaOverlay.normalizedX.coerceIn(0.08f, 0.92f) * targetWidth - ctaFrameBitmap.width / 2f)
                            .coerceIn(0f, (targetWidth - ctaFrameBitmap.width).coerceAtLeast(0).toFloat())
                        val ctaTop = (ctaOverlay.normalizedY.coerceIn(0.08f, 0.92f) * targetHeight - ctaFrameBitmap.height / 2f)
                            .coerceIn(0f, (targetHeight - ctaFrameBitmap.height).coerceAtLeast(0).toFloat())
                        paint.alpha = 255
                        canvas.drawBitmap(ctaFrameBitmap, ctaLeft, ctaTop, paint)
                    }
                }

                // 3. Legendas (Etapa 2): aplicadas nos tempos específicos fornecidos, sem nunca incluir os tempos,
                // posicionadas no rodapé do vídeo com corte inteligente e responsivo para que 100% do texto fique visível
                if (subtitles.isNotEmpty()) {
                    val currentTimeSec = globalFrameIndex.toFloat() / frameRate.coerceAtLeast(1)
                    val activeSubtitle = subtitles.firstOrNull { sub ->
                        currentTimeSec >= sub.startTimeSeconds && currentTimeSec <= sub.endTimeSeconds
                    }
                    if (activeSubtitle != null) {
                        val effectiveStyle = SubtitleStyle.getEffectiveRenderStyle(subtitleStyle.id)
                        val elapsedInSubtitle = (currentTimeSec - activeSubtitle.startTimeSeconds).coerceAtLeast(0f)
                        val entryAnimProgress = (elapsedInSubtitle / 0.42f).coerceIn(0f, 1f)
                        SubtitleEngine.drawSubtitleOnCanvas(
                            canvas = canvas,
                            rawPhrase = activeSubtitle.text,
                            style = effectiveStyle,
                            canvasWidth = targetWidth,
                            canvasHeight = targetHeight,
                            centerInPreview = false,
                            entryProgress = entryAnimProgress
                        )
                    }
                }

                frameBitmap.getPixels(argbArray, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    convertArgbToYuv420Planar(argbArray, yuvArray, targetWidth, targetHeight)
                } else {
                    convertArgbToYuv420SemiPlanar(argbArray, yuvArray, targetWidth, targetHeight)
                }

                var queued = false
                var attempts = 0
                while (!queued && attempts < 60) {
                    if (isCancelled()) return false
                    val inIndex = encoder.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = encoder.getInputBuffer(inIndex)
                        inBuffer?.clear()
                        inBuffer?.put(yuvArray)

                        val ptsUs = (globalFrameIndex * 1_000_000L) / frameRate
                        val flags = if (isLastFrame) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0

                        encoder.queueInputBuffer(
                            inIndex,
                            0,
                            yuvArray.size,
                            ptsUs,
                            flags
                        )
                        queued = true
                    } else {
                        drainEncoder(false)
                        attempts++
                    }
                }
                drainEncoder(false)
                return queued
            }

            // Helper para desenhar uma cena (vídeo ou foto com movimento de câmera independente) em um Canvas próprio
            fun renderSceneWithCameraMovement(
                targetCanvas: Canvas,
                sceneItem: RenderSequenceItem,
                sceneBitmap: Bitmap,
                cameraProgress: Float,
                localFrameForVideo: Int,
                tempMatrix: Matrix
            ) {
                val isVideoMedia = MediaHelper.isVideo(sceneItem.imageFile.name)
                targetCanvas.drawColor(Color.BLACK)

                if (isVideoMedia) {
                    val retriever = MediaMetadataRetriever()
                    val frameTimeUs = (localFrameForVideo.coerceAtLeast(0) * 1_000_000L) / frameRate
                    val videoFrame = try {
                        retriever.setDataSource(sceneItem.imageFile.absolutePath)
                        retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    } catch (_: Exception) { null } finally {
                        try { retriever.release() } catch (_: Exception) {}
                    }

                    if (videoFrame != null) {
                        val scale = minOf(targetWidth.toFloat() / videoFrame.width, targetHeight.toFloat() / videoFrame.height)
                        val dx = (targetWidth - videoFrame.width * scale) / 2f
                        val dy = (targetHeight - videoFrame.height * scale) / 2f
                        tempMatrix.reset()
                        tempMatrix.postScale(scale, scale)
                        tempMatrix.postTranslate(dx, dy)
                        scenePaint.alpha = 255
                        targetCanvas.drawBitmap(videoFrame, tempMatrix, scenePaint)
                        videoFrame.recycle()
                    } else {
                        scenePaint.alpha = 255
                        targetCanvas.drawBitmap(sceneBitmap, tempMatrix, scenePaint)
                    }
                } else {
                    sceneItem.movementEffect.applyToMatrix(
                        matrix = tempMatrix,
                        progress = cameraProgress.coerceIn(0f, 1f),
                        canvasW = targetWidth.toFloat(),
                        canvasH = targetHeight.toFloat(),
                        bitmapW = sceneBitmap.width.toFloat(),
                        bitmapH = sceneBitmap.height.toFloat()
                    )
                    scenePaint.alpha = 255
                    targetCanvas.drawBitmap(sceneBitmap, tempMatrix, scenePaint)
                }
            }

            // Loop principal da sequência unificada
            for (i in sequence.indices) {
                if (isCancelled()) {
                    cleanup(encoder, muxer, muxerStarted)
                    currentBitmap?.recycle()
                    nextBitmap?.recycle()
                    sceneLayer1.recycle()
                    sceneLayer2.recycle()
                    frameBitmap.recycle()
                    outputFile.delete()
                    return@withContext false
                }

                val item = sequence[i]
                if (currentBitmap == null || currentBitmap.isRecycled) {
                    currentBitmap = loadOptimizedBitmap(item.imageFile, targetWidth * 2, targetHeight * 2)
                        ?: return@withContext false
                }

                val hasNext = i < sequence.size - 1
                if (hasNext && (nextBitmap == null || nextBitmap.isRecycled)) {
                    nextBitmap = loadOptimizedBitmap(sequence[i + 1].imageFile, targetWidth * 2, targetHeight * 2)
                }

                val totalItemFrames = itemFrames[i]
                val transitionEffect = item.transitionToNext
                val transitionFrames = if (hasNext && nextBitmap != null && transitionEffect.id != 0) {
                    outTransFrames[i]
                } else {
                    0
                }
                val pureCameraFrames = (totalItemFrames - transitionFrames).coerceAtLeast(0)
                val incomingFrames = inTransFrames[i]
                val currTotalVisible = totalVisibleSceneFrames[i]

                // Fase 1: Movimento de câmera da imagem atual (continua exatamente de onde a transição de entrada parou!)
                for (f in 0 until pureCameraFrames) {
                    if (isCancelled()) {
                        cleanup(encoder, muxer, muxerStarted)
                        currentBitmap.recycle()
                        nextBitmap?.recycle()
                        sceneLayer1.recycle()
                        sceneLayer2.recycle()
                        frameBitmap.recycle()
                        outputFile.delete()
                        return@withContext false
                    }

                    val localVisibleFrame = incomingFrames + f
                    val progress = localVisibleFrame.toFloat() / (currTotalVisible - 1).coerceAtLeast(1)

                    renderSceneWithCameraMovement(
                        targetCanvas = canvas,
                        sceneItem = item,
                        sceneBitmap = currentBitmap,
                        cameraProgress = progress,
                        localFrameForVideo = localVisibleFrame,
                        tempMatrix = matrixCurrent
                    )

                    val isVeryLastFrame = (i == sequence.size - 1) && (f == pureCameraFrames - 1)
                    if (!queueCurrentFrame(isVeryLastFrame)) {
                        cleanup(encoder, muxer, muxerStarted)
                        return@withContext false
                    }

                    globalFrameIndex++
                    onGlobalProgress(
                        globalFrameIndex,
                        totalGlobalFrames,
                        i,
                        "Renderizando Cena ${i + 1}/${sequence.size}: ${item.movementEffect.name}"
                    )
                }

                // Fase 2: Transição suave entre a imagem atual e a próxima imagem
                // CADA ELEMENTO É INDEPENDENTE: o movimento de câmera da próxima imagem já entra em ação
                // imediatamente no frame 0 da transição (sem esperar a transição acabar!) e sem interferir
                // no movimento da cena atual ou no efeito de transição.
                if (transitionFrames > 0 && nextBitmap != null) {
                    val nextTotalVisible = totalVisibleSceneFrames[i + 1]
                    for (tf in 0 until transitionFrames) {
                        if (isCancelled()) {
                            cleanup(encoder, muxer, muxerStarted)
                            currentBitmap.recycle()
                            nextBitmap.recycle()
                            sceneLayer1.recycle()
                            sceneLayer2.recycle()
                            frameBitmap.recycle()
                            outputFile.delete()
                            return@withContext false
                        }

                        val transProgress = tf.toFloat() / (transitionFrames - 1).coerceAtLeast(1)
                        val currVisibleFrame = incomingFrames + pureCameraFrames + tf
                        val currCamProgress = currVisibleFrame.toFloat() / (currTotalVisible - 1).coerceAtLeast(1)
                        val nextCamProgress = tf.toFloat() / (nextTotalVisible - 1).coerceAtLeast(1)

                        // Renderiza a Cena 1 com seu movimento de câmera em sua camada isolada
                        renderSceneWithCameraMovement(
                            targetCanvas = canvasScene1,
                            sceneItem = item,
                            sceneBitmap = currentBitmap,
                            cameraProgress = currCamProgress,
                            localFrameForVideo = currVisibleFrame,
                            tempMatrix = matrixCurrent
                        )

                        // Renderiza a Cena 2 com seu movimento de câmera já em ação em sua camada isolada
                        renderSceneWithCameraMovement(
                            targetCanvas = canvasScene2,
                            sceneItem = sequence[i + 1],
                            sceneBitmap = nextBitmap,
                            cameraProgress = nextCamProgress,
                            localFrameForVideo = tf,
                            tempMatrix = matrixNext
                        )

                        // Aplica o efeito de transição combinando as duas camadas já animadas de forma 100% independente
                        canvas.drawColor(Color.BLACK)
                        identityMatrix.reset()
                        transitionEffect.applyToCanvas(
                            progress = transProgress,
                            canvas = canvas,
                            paint = paint,
                            bitmap1 = sceneLayer1,
                            matrix1 = identityMatrix,
                            bitmap2 = sceneLayer2,
                            matrix2 = identityMatrix,
                            width = targetWidth.toFloat(),
                            height = targetHeight.toFloat()
                        )

                        val isVeryLastFrame = (i == sequence.size - 1) && (tf == transitionFrames - 1)
                        if (!queueCurrentFrame(isVeryLastFrame)) {
                            cleanup(encoder, muxer, muxerStarted)
                            return@withContext false
                        }

                        globalFrameIndex++
                        onGlobalProgress(
                            globalFrameIndex,
                            totalGlobalFrames,
                            i,
                            "Transição [${transitionEffect.id}] ${transitionEffect.name} (${i + 1} ➔ ${i + 2})"
                        )
                    }
                }

                // Prepara a próxima iteração: avança os bitmaps
                if (hasNext && nextBitmap != null) {
                    currentBitmap.recycle()
                    currentBitmap = nextBitmap
                    nextBitmap = null
                }
            }

            // Drena todos os buffers finais
            drainEncoder(true)

            cleanup(encoder, muxer, muxerStarted)
            currentBitmap?.recycle()
            nextBitmap?.recycle()
            sceneLayer1.recycle()
            sceneLayer2.recycle()
            frameBitmap.recycle()

            val hasTransitionSound = sequence.any { it.transitionSoundIdToNext != 0 }
            val hasTimelineAudio = timelineAudioFile != null && timelineAudioFile.exists()
            if (hasTransitionSound || hasTimelineAudio) {
                onGlobalProgress(
                    totalGlobalFrames,
                    totalGlobalFrames,
                    sequence.size - 1,
                    "Sincronizando áudio no vídeo final..."
                )
                applyTransitionAudioTrack(
                    tempVideoFile = rawVideoFile,
                    finalOutputFile = outputFile,
                    sequence = sequence,
                    itemFrames = itemFrames,
                    frameRate = frameRate,
                    transitionDurationSeconds = transitionDurationSeconds,
                    timelineAudioFile = timelineAudioFile
                )
            } else {
                rawVideoFile.copyTo(outputFile, overwrite = true)
                rawVideoFile.delete()
            }

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup(encoder, muxer, false)
            currentBitmap?.recycle()
            nextBitmap?.recycle()
            sceneLayer1?.recycle()
            sceneLayer2?.recycle()
            if (rawVideoFile.exists()) {
                rawVideoFile.delete()
            }
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return@withContext false
        }
    }

    suspend fun encodeImageToVideo(
        imageFile: File,
        movementEffect: MovementEffect,
        durationSeconds: Float,
        outputFile: File,
        targetWidth: Int = 1280,
        targetHeight: Int = 720,
        frameRate: Int = DEFAULT_FRAME_RATE,
        bitRate: Int = DEFAULT_BIT_RATE,
        onFrameProgress: (frame: Int, totalFrames: Int) -> Unit = { _, _ -> },
        isCancelled: () -> Boolean = { false }
    ): Boolean = withContext(Dispatchers.Default) {
        val totalFrames = (durationSeconds * frameRate).toInt().coerceAtLeast(1)

        // 1. Decodifica o bitmap com respeito ao Aspect Ratio e orientação EXIF
        val sourceBitmap = loadOptimizedBitmap(imageFile, targetWidth * 2, targetHeight * 2)
            ?: return@withContext false

        outputFile.parentFile?.mkdirs()
        if (outputFile.exists()) {
            outputFile.delete()
        }

        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null

        try {
            val colorFormat = selectColorFormat()
            val format = MediaFormat.createVideoFormat(MIME_TYPE, targetWidth, targetHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
            }

            encoder = MediaCodec.createEncoderByType(MIME_TYPE)
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            var videoTrackIndex = -1
            var muxerStarted = false

            val bufferInfo = MediaCodec.BufferInfo()
            val frameBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frameBitmap)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
            val matrix = Matrix()

            val argbArray = IntArray(targetWidth * targetHeight)
            val yuvArray = ByteArray(targetWidth * targetHeight * 3 / 2)

            var frameIndex = 0

            while (frameIndex < totalFrames) {
                if (isCancelled()) {
                    cleanup(encoder, muxer, muxerStarted)
                    sourceBitmap.recycle()
                    frameBitmap.recycle()
                    outputFile.delete()
                    return@withContext false
                }

                val progress = frameIndex.toFloat() / (totalFrames - 1).coerceAtLeast(1)

                // Renderiza o quadro com a matriz calculada do efeito de câmera
                canvas.drawColor(Color.BLACK)
                movementEffect.applyToMatrix(
                    matrix = matrix,
                    progress = progress,
                    canvasW = targetWidth.toFloat(),
                    canvasH = targetHeight.toFloat(),
                    bitmapW = sourceBitmap.width.toFloat(),
                    bitmapH = sourceBitmap.height.toFloat()
                )
                canvas.drawBitmap(sourceBitmap, matrix, paint)

                // Converte frame para YUV
                frameBitmap.getPixels(argbArray, 0, targetWidth, 0, 0, targetWidth, targetHeight)
                if (colorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                    convertArgbToYuv420Planar(argbArray, yuvArray, targetWidth, targetHeight)
                } else {
                    convertArgbToYuv420SemiPlanar(argbArray, yuvArray, targetWidth, targetHeight)
                }

                // Envia para o encoder
                val inputBufferIndex = encoder.dequeueInputBuffer(10_000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = encoder.getInputBuffer(inputBufferIndex)
                    inputBuffer?.clear()
                    inputBuffer?.put(yuvArray)

                    val ptsUs = (frameIndex * 1_000_000L) / frameRate
                    val isLastFrame = frameIndex == totalFrames - 1
                    val flags = if (isLastFrame) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0

                    encoder.queueInputBuffer(
                        inputBufferIndex,
                        0,
                        yuvArray.size,
                        ptsUs,
                        flags
                    )
                    frameIndex++
                    onFrameProgress(frameIndex, totalFrames)
                }

                // Drena saídas disponíveis do encoder
                var outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                while (outputBufferIndex >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                        bufferInfo.size = 0
                    }

                    if (bufferInfo.size != 0 && muxerStarted && encodedBuffer != null) {
                        encodedBuffer.position(bufferInfo.offset)
                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedBuffer, bufferInfo)
                    }

                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                    outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 0)
                }

                if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) {
                        throw RuntimeException("Formato de saída mudou mais de uma vez")
                    }
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                }
            }

            // Drena os frames finais até BUFFER_FLAG_END_OF_STREAM
            var eosReached = false
            var drainAttempts = 0
            while (!eosReached && drainAttempts < 100) {
                val outputBufferIndex = encoder.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outputBufferIndex >= 0) {
                    val encodedBuffer = encoder.getOutputBuffer(outputBufferIndex)
                    if (bufferInfo.size != 0 && muxerStarted && encodedBuffer != null) {
                        encodedBuffer.position(bufferInfo.offset)
                        encodedBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        muxer.writeSampleData(videoTrackIndex, encodedBuffer, bufferInfo)
                    }
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        eosReached = true
                    }
                    encoder.releaseOutputBuffer(outputBufferIndex, false)
                } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED && !muxerStarted) {
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = muxer.addTrack(newFormat)
                    muxer.start()
                    muxerStarted = true
                } else {
                    drainAttempts++
                }
            }

            cleanup(encoder, muxer, muxerStarted)
            sourceBitmap.recycle()
            frameBitmap.recycle()
            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            cleanup(encoder, muxer, false)
            if (sourceBitmap != null && !sourceBitmap.isRecycled) {
                sourceBitmap.recycle()
            }
            if (outputFile.exists()) {
                outputFile.delete()
            }
            return@withContext false
        }
    }

    private fun cleanup(encoder: MediaCodec?, muxer: MediaMuxer?, muxerStarted: Boolean) {
        try {
            encoder?.stop()
        } catch (_: Exception) {}
        try {
            encoder?.release()
        } catch (_: Exception) {}
        try {
            if (muxerStarted) {
                muxer?.stop()
            }
        } catch (_: Exception) {}
        try {
            muxer?.release()
        } catch (_: Exception) {}
    }

    private fun selectColorFormat(): Int {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (info in codecList.codecInfos) {
            if (!info.isEncoder) continue
            val types = info.supportedTypes
            for (type in types) {
                if (type.equals(MIME_TYPE, ignoreCase = true)) {
                    val caps = info.getCapabilitiesForType(type)
                    for (format in caps.colorFormats) {
                        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                            return format
                        }
                    }
                    for (format in caps.colorFormats) {
                        if (format == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                            return format
                        }
                    }
                }
            }
        }
        return MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar
    }

    private fun convertArgbToYuv420SemiPlanar(
        argb: IntArray,
        yuv: ByteArray,
        width: Int,
        height: Int
    ) {
        val frameSize = width * height
        var yIndex = 0
        var uvIndex = frameSize

        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[index++]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uvIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[uvIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    }

    private fun convertArgbToYuv420Planar(
        argb: IntArray,
        yuv: ByteArray,
        width: Int,
        height: Int
    ) {
        val frameSize = width * height
        val qFrameSize = frameSize / 4
        var yIndex = 0
        var uIndex = frameSize
        var vIndex = frameSize + qFrameSize

        var index = 0
        for (j in 0 until height) {
            for (i in 0 until width) {
                val pixel = argb[index++]
                val r = (pixel shr 16) and 0xff
                val g = (pixel shr 8) and 0xff
                val b = pixel and 0xff

                val y = ((66 * r + 129 * g + 25 * b + 128) shr 8) + 16
                val u = ((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128
                val v = ((112 * r - 94 * g - 18 * b + 128) shr 8) + 128

                yuv[yIndex++] = y.coerceIn(0, 255).toByte()
                if (j % 2 == 0 && i % 2 == 0) {
                    yuv[uIndex++] = u.coerceIn(0, 255).toByte()
                    yuv[vIndex++] = v.coerceIn(0, 255).toByte()
                }
            }
        }
    }

    private fun loadOptimizedBitmap(file: File, maxW: Int, maxH: Int): Bitmap? {
        if (MediaHelper.isVideo(file.name)) {
            val retriever = MediaMetadataRetriever()
            return try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        }

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        var sampleSize = 1
        while (options.outWidth / sampleSize > maxW || options.outHeight / sampleSize > maxH) {
            sampleSize *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOptions) ?: return null

        // Corrige orientação EXIF
        return try {
            val exif = ExifInterface(file.absolutePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                else -> return decoded
            }
            val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
            if (rotated != decoded) {
                decoded.recycle()
            }
            rotated
        } catch (_: Exception) {
            decoded
        }
    }

    fun probeCreatedVideoDurationSeconds(videoFile: File, fallbackDurationSec: Float): Float {
        if (!videoFile.exists() || videoFile.length() <= 0L) return fallbackDurationSec
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoFile.absolutePath)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (durationMs != null && durationMs > 0L) {
                (durationMs / 1000f).coerceAtLeast(fallbackDurationSec)
            } else {
                fallbackDurationSec
            }
        } catch (_: Exception) {
            fallbackDurationSec
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun applyTransitionAudioTrack(
        tempVideoFile: File,
        finalOutputFile: File,
        sequence: List<RenderSequenceItem>,
        itemFrames: List<Int>,
        frameRate: Int,
        transitionDurationSeconds: Float = 1.0f,
        timelineAudioFile: File? = null
    ) {
        try {
            val totalFrames = itemFrames.sum()
            val sampleRate = 44100
            val totalSamples = ((totalFrames.toDouble() / frameRate) * sampleRate).toInt().coerceAtLeast(sampleRate)
            val audioPcm = ShortArray(totalSamples)

            // 1. Se o usuário adicionou um áudio na linha do tempo, inclui e preenche o áudio no vídeo final
            if (timelineAudioFile != null && timelineAudioFile.exists()) {
                val timelineSamples = TimelineAudioEngine.extractPcmSamplesForMix(
                    audioFile = timelineAudioFile,
                    targetSampleCount = totalSamples,
                    targetSampleRate = sampleRate
                )
                for (i in timelineSamples.indices) {
                    if (i < audioPcm.size) {
                        audioPcm[i] = timelineSamples[i]
                    }
                }
            }

            // 2. Mixa os sons de transição nas trocas de cena
            var accumulatedFrames = 0
            for (i in sequence.indices) {
                val totalItemFrames = itemFrames[i]
                val hasNext = i < sequence.size - 1
                val transFrames = if (hasNext && sequence[i].transitionToNext.id != 0) {
                    (transitionDurationSeconds.coerceIn(0.4f, 6.0f) * frameRate).toInt().coerceIn(1, (totalItemFrames - 1).coerceAtLeast(1))
                } else 0
                val pureCameraFrames = (totalItemFrames - transFrames).coerceAtLeast(0)
                val transitionStartFrame = accumulatedFrames + pureCameraFrames

                val soundId = sequence[i].transitionSoundIdToNext
                if (soundId != 0 && hasNext) {
                    val soundPcm = TransitionSoundEngine.generatePcmForBuiltInSound(soundId)
                    if (soundPcm.isNotEmpty()) {
                        val sampleOffset = ((transitionStartFrame.toDouble() / frameRate) * sampleRate).toInt()
                        for (s in soundPcm.indices) {
                            val targetIdx = sampleOffset + s
                            if (targetIdx < audioPcm.size) {
                                val mixed = audioPcm[targetIdx].toInt() + soundPcm[s].toInt()
                                audioPcm[targetIdx] = mixed.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                            }
                        }
                    }
                }
                accumulatedFrames += totalItemFrames
            }

            muxVideoAndAudio(tempVideoFile, finalOutputFile, audioPcm, sampleRate)
        } catch (_: Exception) {
            tempVideoFile.copyTo(finalOutputFile, overwrite = true)
            tempVideoFile.delete()
        }
    }

    private fun muxVideoAndAudio(
        videoSourceFile: File,
        destinationFile: File,
        audioPcm: ShortArray,
        sampleRate: Int
    ) {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(videoSourceFile.absolutePath)
        } catch (e: Exception) {
            videoSourceFile.copyTo(destinationFile, overwrite = true)
            videoSourceFile.delete()
            return
        }

        var videoTrackIndex = -1
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("video/")) {
                videoTrackIndex = i
                extractor.selectTrack(i)
                break
            }
        }

        if (videoTrackIndex == -1) {
            extractor.release()
            videoSourceFile.copyTo(destinationFile, overwrite = true)
            videoSourceFile.delete()
            return
        }

        val videoFormat = extractor.getTrackFormat(videoTrackIndex)
        val muxer = MediaMuxer(destinationFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outVideoTrack = muxer.addTrack(videoFormat)

        // AAC Audio Encoder
        val audioFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
        }

        var audioEncoder: MediaCodec? = null
        try {
            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            audioEncoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            audioEncoder.start()

            val audioBufferInfo = MediaCodec.BufferInfo()
            var outAudioTrack = -1
            var muxerStarted = false

            val pcmBytes = ByteArray(audioPcm.size * 2)
            ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(audioPcm)

            var pcmOffset = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = audioEncoder.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inBuf = audioEncoder.getInputBuffer(inIdx)
                        inBuf?.clear()
                        val remaining = pcmBytes.size - pcmOffset
                        val chunkSize = minOf(remaining, inBuf?.remaining() ?: 4096)
                        if (chunkSize > 0 && inBuf != null) {
                            inBuf.put(pcmBytes, pcmOffset, chunkSize)
                            val ptsUs = (pcmOffset.toLong() * 1_000_000L) / (sampleRate * 2L)
                            audioEncoder.queueInputBuffer(inIdx, 0, chunkSize, ptsUs, 0)
                            pcmOffset += chunkSize
                        } else {
                            audioEncoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        }
                    }
                }

                val outIdx = audioEncoder.dequeueOutputBuffer(audioBufferInfo, 10_000)
                if (outIdx >= 0) {
                    val outBuf = audioEncoder.getOutputBuffer(outIdx)
                    if (muxerStarted && audioBufferInfo.size > 0 && outBuf != null) {
                        outBuf.position(audioBufferInfo.offset)
                        outBuf.limit(audioBufferInfo.offset + audioBufferInfo.size)
                        muxer.writeSampleData(outAudioTrack, outBuf, audioBufferInfo)
                    }
                    audioEncoder.releaseOutputBuffer(outIdx, false)
                    if (audioBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val actualAudioFormat = audioEncoder.outputFormat
                    outAudioTrack = muxer.addTrack(actualAudioFormat)
                    muxer.start()
                    muxerStarted = true

                    // Transfere os samples de vídeo para o muxer agora que iniciou
                    val videoBuf = ByteBuffer.allocate(1024 * 1024)
                    val videoBufInfo = MediaCodec.BufferInfo()
                    while (true) {
                        videoBufInfo.offset = 0
                        videoBufInfo.size = extractor.readSampleData(videoBuf, 0)
                        if (videoBufInfo.size < 0) break
                        videoBufInfo.presentationTimeUs = extractor.sampleTime
                        videoBufInfo.flags = extractor.sampleFlags
                        muxer.writeSampleData(outVideoTrack, videoBuf, videoBufInfo)
                        extractor.advance()
                    }
                }
            }

            audioEncoder.stop()
            audioEncoder.release()
            muxer.stop()
            muxer.release()
            extractor.release()
            videoSourceFile.delete()
        } catch (_: Exception) {
            try { audioEncoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { muxer.release() } catch (_: Exception) {}
            videoSourceFile.copyTo(destinationFile, overwrite = true)
            videoSourceFile.delete()
        }
    }
}
