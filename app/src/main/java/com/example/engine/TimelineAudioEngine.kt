package com.example.engine

import android.content.ContentUris
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.PI
import kotlin.math.sin

data class TimelineAudioItem(
    val id: String,
    val displayName: String,
    val filePath: String,
    val uriString: String,
    val durationSeconds: Float,
    val sourceFolder: String = "Dispositivo"
) {
    val formattedDuration: String
        get() {
            val totalSecs = durationSeconds.coerceAtLeast(1f).toInt()
            val mins = totalSecs / 60
            val secs = totalSecs % 60
            return String.format(Locale.US, "%02d:%02d", mins, secs)
        }
}

object TimelineAudioEngine {

    private var mediaPlayer: MediaPlayer? = null

    private val _isPlayingTimelineAudio = MutableStateFlow(false)
    val isPlayingTimelineAudio: StateFlow<Boolean> = _isPlayingTimelineAudio.asStateFlow()

    private val _playingAudioPath = MutableStateFlow<String?>(null)
    val playingAudioPath: StateFlow<String?> = _playingAudioPath.asStateFlow()

    fun togglePlayPause(audioFilePath: String) {
        if (_isPlayingTimelineAudio.value && _playingAudioPath.value == audioFilePath) {
            pauseOrStopAudio()
        } else {
            playAudioFile(audioFilePath)
        }
    }

    fun playAudioFile(audioFilePath: String) {
        pauseOrStopAudio()
        val file = File(audioFilePath)
        if (!file.exists()) return
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    _isPlayingTimelineAudio.value = false
                    _playingAudioPath.value = null
                }
                setOnErrorListener { _, _, _ ->
                    _isPlayingTimelineAudio.value = false
                    _playingAudioPath.value = null
                    true
                }
                prepare()
                start()
            }
            mediaPlayer = player
            _playingAudioPath.value = audioFilePath
            _isPlayingTimelineAudio.value = true
        } catch (_: Exception) {
            _isPlayingTimelineAudio.value = false
            _playingAudioPath.value = null
        }
    }

    fun pauseOrStopAudio() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
        _isPlayingTimelineAudio.value = false
        _playingAudioPath.value = null
    }

    /**
     * Lista todos os áudios que estão no dispositivo do usuário + biblioteca de trilhas do próprio dispositivo/app.
     */
    suspend fun loadAllDeviceAudios(context: Context): List<TimelineAudioItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TimelineAudioItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. Garante trilhas reais WAV no diretório de áudios do dispositivo para que sempre haja opções válidas
        val studioAudioDir = ensureDeviceStudioAudioFiles(context)
        studioAudioDir.listFiles()?.sortedBy { it.name }?.forEach { file ->
            if (file.isFile && isSupportedAudioExtension(file.name) && seenPaths.add(file.absolutePath)) {
                val dur = probeAudioDurationSeconds(file) ?: 18f
                list.add(
                    TimelineAudioItem(
                        id = file.absolutePath,
                        displayName = file.name,
                        filePath = file.absolutePath,
                        uriString = Uri.fromFile(file).toString(),
                        durationSeconds = dur,
                        sourceFolder = "Áudios do Dispositivo"
                    )
                )
            }
        }

        // 2. Consulta MediaStore.Audio.Media do dispositivo do usuário
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA
            )
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "audio_$id.mp3"
                    val durationMs = if (durCol != -1) cursor.getLong(durCol) else 0L
                    val path = if (dataCol != -1) cursor.getString(dataCol) else null
                    val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                    if (path != null && File(path).exists()) {
                        if (seenPaths.add(path)) {
                            list.add(
                                TimelineAudioItem(
                                    id = contentUri.toString(),
                                    displayName = name,
                                    filePath = path,
                                    uriString = contentUri.toString(),
                                    durationSeconds = if (durationMs > 0) durationMs / 1000f else (probeAudioDurationSeconds(File(path)) ?: 15f),
                                    sourceFolder = File(path).parentFile?.name ?: "Memória Interna"
                                )
                            )
                        }
                    } else {
                        list.add(
                            TimelineAudioItem(
                                id = contentUri.toString(),
                                displayName = name,
                                filePath = path ?: "",
                                uriString = contentUri.toString(),
                                durationSeconds = if (durationMs > 0) durationMs / 1000f else 15f,
                                sourceFolder = "Biblioteca de Áudio"
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Varre pastas públicas de Música, Downloads, Podcasts e Notificações no dispositivo
        val dirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RINGTONES),
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        )

        for (dir in dirs) {
            try {
                if (dir.exists()) {
                    dir.walkTopDown().maxDepth(3).forEach { file ->
                        if (file.isFile && isSupportedAudioExtension(file.name) && seenPaths.add(file.absolutePath)) {
                            val dur = probeAudioDurationSeconds(file) ?: 15f
                            list.add(
                                TimelineAudioItem(
                                    id = file.absolutePath,
                                    displayName = file.name,
                                    filePath = file.absolutePath,
                                    uriString = Uri.fromFile(file).toString(),
                                    durationSeconds = dur,
                                    sourceFolder = dir.name
                                )
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        list
    }

    /**
     * Importa e valida um áudio selecionado pelo usuário (via item da lista ou seletor de arquivos).
     */
    suspend fun importAndValidateAudio(
        context: Context,
        uri: Uri,
        suggestedName: String? = null
    ): Result<TimelineAudioItem> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(context.filesDir, "timeline_uploaded_audio").apply { mkdirs() }
            val resolvedName = suggestedName ?: resolveDisplayName(context, uri) ?: "trilha_audio_${System.currentTimeMillis()}.wav"
            val cleanName = resolvedName.replace(Regex("[^a-zA-Z0-9._\\- ]"), "_")
            val targetFile = File(destDir, "timeline_${System.currentTimeMillis()}_$cleanName")

            val copied = if (uri.scheme == "file") {
                val src = File(uri.path ?: "")
                if (src.exists()) {
                    src.copyTo(targetFile, overwrite = true)
                    true
                } else false
            } else {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                    true
                } ?: false
            }

            if (!copied || !targetFile.exists() || targetFile.length() < 44L) {
                targetFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Arquivo de áudio inválido ou vazio."))
            }

            val durationSec = probeAudioDurationSeconds(targetFile)
            if (durationSec == null || durationSec <= 0.05f) {
                targetFile.delete()
                return@withContext Result.failure(IllegalArgumentException("Selecione um arquivo de áudio válido."))
            }

            val item = TimelineAudioItem(
                id = targetFile.absolutePath,
                displayName = resolvedName,
                filePath = targetFile.absolutePath,
                uriString = Uri.fromFile(targetFile).toString(),
                durationSeconds = durationSec,
                sourceFolder = "Áudio na Linha do Tempo"
            )
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.lastPathSegment
        }
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameIndex)
                } else null
            }
        } catch (_: Exception) {
            uri.lastPathSegment
        }
    }

    fun isSupportedAudioExtension(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.US)
        return lower.endsWith(".mp3") ||
            lower.endsWith(".wav") ||
            lower.endsWith(".m4a") ||
            lower.endsWith(".aac") ||
            lower.endsWith(".ogg") ||
            lower.endsWith(".flac")
    }

    fun probeAudioDurationSeconds(file: File): Float? {
        if (!file.exists() || file.length() < 44L) return null
        // 1. Verifica cabeçalho WAV RIFF primeiro (rápido e funciona também em testes JVM)
        try {
            FileInputStream(file).use { fis ->
                val header = ByteArray(44)
                if (fis.read(header) == 44) {
                    val riff = String(header, 0, 4, Charsets.US_ASCII)
                    val wave = String(header, 8, 4, Charsets.US_ASCII)
                    if (riff == "RIFF" && wave == "WAVE") {
                        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                        val byteRate = bb.getInt(28)
                        if (byteRate > 0) {
                            val dataBytes = (file.length() - 44L).coerceAtLeast(1L)
                            return (dataBytes.toFloat() / byteRate.toFloat()).coerceAtLeast(0.5f)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Fallback para MediaMetadataRetriever (MP3, M4A, AAC, OGG, etc.)
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durMs = durStr?.toLongOrNull() ?: return null
            if (durMs > 0L) durMs / 1000f else null
        } catch (_: Exception) {
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    /**
     * Decodifica os samples PCM (44100 Hz Mono) do arquivo de áudio da linha do tempo
     * para mixar no vídeo final renderizado.
     */
    fun extractPcmSamplesForMix(audioFile: File, targetSampleCount: Int, targetSampleRate: Int = 44100): ShortArray {
        if (!audioFile.exists() || targetSampleCount <= 0) return ShortArray(0)

        // 1. Leitura direta se for WAV PCM 16-bit
        try {
            FileInputStream(audioFile).use { fis ->
                val header = ByteArray(44)
                if (fis.read(header) == 44) {
                    val riff = String(header, 0, 4, Charsets.US_ASCII)
                    val wave = String(header, 8, 4, Charsets.US_ASCII)
                    if (riff == "RIFF" && wave == "WAVE") {
                        val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                        val channels = bb.getShort(22).toInt().coerceAtLeast(1)
                        val fileSampleRate = bb.getInt(24).coerceAtLeast(8000)
                        val payloadBytes = fis.readBytes()
                        val shortBuf = ByteBuffer.wrap(payloadBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        val rawShorts = ShortArray(shortBuf.remaining())
                        shortBuf.get(rawShorts)

                        val monoFrameCount = (rawShorts.size / channels).coerceAtLeast(1)
                        val result = ShortArray(targetSampleCount)
                        val rateRatio = fileSampleRate.toDouble() / targetSampleRate.toDouble()
                        for (i in 0 until targetSampleCount) {
                            val srcFrame = ((i * rateRatio).toInt()) % monoFrameCount
                            val sampleIdx = (srcFrame * channels).coerceIn(0, (rawShorts.size - 1).coerceAtLeast(0))
                            result[i] = rawShorts[sampleIdx]
                        }
                        return result
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Decodificação via MediaExtractor + MediaCodec para MP3 / AAC / M4A / OGG
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        try {
            extractor.setDataSource(audioFile.absolutePath)
            var audioTrackIdx = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIdx = i
                    format = f
                    extractor.selectTrack(i)
                    break
                }
            }
            if (audioTrackIdx >= 0 && format != null) {
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return ShortArray(0)
                val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                } else 1
                val srcRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(8000)
                } else targetSampleRate

                decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()

                val decodedMono = ArrayList<Short>(minOf(targetSampleCount, 44100 * 60))
                val info = MediaCodec.BufferInfo()
                var inputEOS = false
                var outputEOS = false
                var loops = 0

                while (!outputEOS && decodedMono.size < targetSampleCount && loops < 6000) {
                    loops++
                    if (!inputEOS) {
                        val inIdx = decoder.dequeueInputBuffer(5_000)
                        if (inIdx >= 0) {
                            val inBuf = decoder.getInputBuffer(inIdx)
                            if (inBuf != null) {
                                val sampleSize = extractor.readSampleData(inBuf, 0)
                                if (sampleSize < 0) {
                                    decoder.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                    inputEOS = true
                                } else {
                                    decoder.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                                    extractor.advance()
                                }
                            }
                        }
                    }

                    val outIdx = decoder.dequeueOutputBuffer(info, 5_000)
                    if (outIdx >= 0) {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val shortBuffer = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val chunk = ShortArray(shortBuffer.remaining())
                            shortBuffer.get(chunk)
                            var idx = 0
                            while (idx < chunk.size && decodedMono.size < targetSampleCount * 2) {
                                decodedMono.add(chunk[idx])
                                idx += channels
                            }
                        }
                        decoder.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputEOS = true
                        }
                    }
                }

                if (decodedMono.isNotEmpty()) {
                    val out = ShortArray(targetSampleCount)
                    val ratio = srcRate.toDouble() / targetSampleRate.toDouble()
                    for (i in 0 until targetSampleCount) {
                        val srcIdx = ((i * ratio).toInt()) % decodedMono.size
                        out[i] = decodedMono[srcIdx]
                    }
                    return out
                }
            }
        } catch (_: Exception) {
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
        }

        return ShortArray(0)
    }

    private fun ensureDeviceStudioAudioFiles(context: Context): File {
        val dir = File(context.filesDir, "audios_dispositivo_studio").apply { mkdirs() }
        val presets = listOf(
            Triple("Trilha_Cinematografica_Epica.wav", 220.0, 277.18),
            Triple("Batida_LoFi_Criadores_Pro.wav", 261.63, 329.63),
            Triple("Ambiente_ Corporativo_Moderno.wav", 293.66, 369.99),
            Triple("Energia_Reels_Dinamico.wav", 329.63, 440.0),
            Triple("Acustico_Suave_Documentario.wav", 196.0, 246.94)
        )

        for ((fileName, freqA, freqB) in presets) {
            val file = File(dir, fileName)
            if (!file.exists() || file.length() < 100L) {
                writeSynthesizedStudioWav(file, durationSeconds = 8, freq1 = freqA, freq2 = freqB)
            }
        }
        return dir
    }

    private fun writeSynthesizedStudioWav(
        outputFile: File,
        durationSeconds: Int,
        freq1: Double,
        freq2: Double
    ) {
        try {
            val sampleRate = 22050
            val totalSamples = sampleRate * durationSeconds
            val pcmData = ByteArray(totalSamples * 2)
            val buffer = ByteBuffer.wrap(pcmData).order(ByteOrder.LITTLE_ENDIAN)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / sampleRate.toDouble()
                val env = (0.55 + 0.35 * sin(2.0 * PI * 0.5 * t)).coerceIn(0.1, 1.0)
                val wave = (sin(2.0 * PI * freq1 * t) * 0.45 +
                    sin(2.0 * PI * freq2 * t) * 0.35 +
                    sin(2.0 * PI * (freq1 * 0.5) * t) * 0.20) * env
                val sample = (wave * 11000).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                buffer.putShort(sample)
            }

            FileOutputStream(outputFile).use { fos ->
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
                val byteRate = sampleRate * 2
                val dataSize = pcmData.size
                header.put("RIFF".toByteArray(Charsets.US_ASCII))
                header.putInt(36 + dataSize)
                header.put("WAVE".toByteArray(Charsets.US_ASCII))
                header.put("fmt ".toByteArray(Charsets.US_ASCII))
                header.putInt(16)
                header.putShort(1) // PCM
                header.putShort(1) // Mono
                header.putInt(sampleRate)
                header.putInt(byteRate)
                header.putShort(2) // BlockAlign
                header.putShort(16) // BitsPerSample
                header.put("data".toByteArray(Charsets.US_ASCII))
                header.putInt(dataSize)
                fos.write(header.array())
                fos.write(pcmData)
            }
        } catch (_: Exception) {}
    }
}
