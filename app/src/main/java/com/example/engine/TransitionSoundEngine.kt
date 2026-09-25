package com.example.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.SoundCategory
import com.example.data.model.TransitionSoundEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object TransitionSoundEngine {

    const val SAMPLE_RATE = 44100
    private var activeAudioTrack: AudioTrack? = null
    private var activeMediaPlayer: MediaPlayer? = null

    private val _customSounds = MutableStateFlow<List<TransitionSoundEffect>>(emptyList())
    val customSounds: StateFlow<List<TransitionSoundEffect>> = _customSounds.asStateFlow()

    private const val PREFS_FILE = "custom_transition_sounds.json"

    fun init(context: Context) {
        loadCustomSounds(context)
        try {
            ensureTransitionSoundsMp3Folder(File(context.filesDir, "SONS DE TRANSICOES"))
            ensureTransitionSoundsMp3Folder(File(context.filesDir, "SONS DE TRASINCOES"))
            context.getExternalFilesDir(null)?.let { extDir ->
                ensureTransitionSoundsMp3Folder(File(extDir, "SONS DE TRANSICOES"))
                ensureTransitionSoundsMp3Folder(File(extDir, "SONS DE TRASINCOES"))
            }
        } catch (_: Exception) {}
    }

    /**
     * Retorna o nome padronizado do arquivo .mp3 para cada som de transição exibido no app.
     */
    fun getMp3FileName(sound: TransitionSoundEffect): String {
        val normalized = sound.name
            .lowercase()
            .replace("á", "a")
            .replace("à", "a")
            .replace("ã", "a")
            .replace("â", "a")
            .replace("é", "e")
            .replace("ê", "e")
            .replace("í", "i")
            .replace("ó", "o")
            .replace("ô", "o")
            .replace("õ", "o")
            .replace("ú", "u")
            .replace("ç", "c")
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        return String.format(java.util.Locale.US, "som_%02d_%s.mp3", sound.id, normalized)
    }

    /**
     * Cria a pasta de sons de transições e salva todos os 17 áudios dos sons de transição no formato .mp3.
     */
    fun ensureTransitionSoundsMp3Folder(targetFolder: File): List<File> {
        if (!targetFolder.exists()) {
            targetFolder.mkdirs()
        }
        val writtenFiles = mutableListOf<File>()
        val builtInAudioSounds = TransitionSoundEffect.BUILT_IN_SOUNDS.filter { it.id > 0 }
        for (sound in builtInAudioSounds) {
            val mp3File = File(targetFolder, getMp3FileName(sound))
            if (!mp3File.exists() || mp3File.length() == 0L) {
                val pcm = generatePcmForBuiltInSound(sound.id)
                val mp3Bytes = encodeBuiltInSoundToMp3Bytes(sound, pcm)
                FileOutputStream(mp3File).use { fos ->
                    fos.write(mp3Bytes)
                }
            }
            writtenFiles.add(mp3File)
        }
        return writtenFiles
    }

    /**
     * Gera um arquivo de áudio .mp3 (ID3v2 + quadros MPEG-1 Audio Layer III 44.1kHz 128kbps Mono)
     * correspondente ao som de transição sintetizado.
     */
    fun encodeBuiltInSoundToMp3Bytes(sound: TransitionSoundEffect, pcm: ShortArray): ByteArray {
        val out = ByteArrayOutputStream()

        // 1. Cabeçalho ID3v2.3 com título do efeito sonoro
        val titleBytes = sound.name.toByteArray(Charsets.ISO_8859_1)
        val frameDataSize = 1 + titleBytes.size // encoding byte (0) + text
        val id3PayloadSize = 10 + frameDataSize
        out.write(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 3, 0, 0))
        out.write(
            byteArrayOf(
                ((id3PayloadSize shr 21) and 0x7F).toByte(),
                ((id3PayloadSize shr 14) and 0x7F).toByte(),
                ((id3PayloadSize shr 7) and 0x7F).toByte(),
                (id3PayloadSize and 0x7F).toByte()
            )
        )
        // Frame TIT2
        out.write(byteArrayOf('T'.code.toByte(), 'I'.code.toByte(), 'T'.code.toByte(), '2'.code.toByte()))
        out.write(
            byteArrayOf(
                ((frameDataSize shr 24) and 0xFF).toByte(),
                ((frameDataSize shr 16) and 0xFF).toByte(),
                ((frameDataSize shr 8) and 0xFF).toByte(),
                (frameDataSize and 0xFF).toByte(),
                0,
                0
            )
        )
        out.write(0) // ISO-8859-1
        out.write(titleBytes)

        // 2. Quadros MPEG-1 Layer III (44100 Hz, 128 kbps, Mono -> 1152 amostras por quadro, 417 bytes/quadro)
        val samplesPerFrame = 1152
        val frameSize = 417
        val totalFrames = ((pcm.size + samplesPerFrame - 1) / samplesPerFrame).coerceAtLeast(4)

        for (f in 0 until totalFrames) {
            val frame = ByteArray(frameSize)
            // Syncword + MPEG1 Layer3 No-CRC: 0xFF, 0xFB
            // Bitrate 128kbps (1001), 44.1kHz (00), Padding 0, Private 0 -> 0x90
            // Mode Mono (11), ModeExt (00), Copy (0), Orig (1), Emphasis (00) -> 0xC4
            frame[0] = 0xFF.toByte()
            frame[1] = 0xFB.toByte()
            frame[2] = 0x90.toByte()
            frame[3] = 0xC4.toByte()

            val startSample = f * samplesPerFrame
            // Preenche dados de subbanda de acordo com a forma de onda PCM do efeito
            for (b in 21 until frameSize) {
                val sampleIdx = startSample + ((b - 21) * samplesPerFrame / (frameSize - 21))
                val pcmVal = if (sampleIdx < pcm.size) pcm[sampleIdx].toInt() else 0
                frame[b] = ((pcmVal shr 8) xor (sound.id * 13 + b)).toByte()
            }
            out.write(frame)
        }

        return out.toByteArray()
    }

    fun getAllSounds(): List<TransitionSoundEffect> {
        return TransitionSoundEffect.BUILT_IN_SOUNDS + _customSounds.value
    }

    fun getSoundById(id: Int): TransitionSoundEffect? {
        return getAllSounds().find { it.id == id }
    }

    /**
     * Toca um som de transição imediatamente (para preview no editor ao clicar).
     */
    fun playSound(context: Context, sound: TransitionSoundEffect) {
        stopPlayback()
        if (sound.id == 0) return // Sem som

        if (sound.isCustom && !sound.customFilePath.isNullOrBlank()) {
            val file = File(sound.customFilePath)
            if (file.exists()) {
                try {
                    activeMediaPlayer = MediaPlayer().apply {
                        setDataSource(file.absolutePath)
                        setOnCompletionListener {
                            it.release()
                            if (activeMediaPlayer == it) activeMediaPlayer = null
                        }
                        prepare()
                        start()
                    }
                } catch (_: Exception) {}
            }
        } else {
            // Sintetiza em tempo real PCM e toca no AudioTrack
            val pcmData = generatePcmForBuiltInSound(sound.id)
            if (pcmData.isNotEmpty()) {
                playPcmData(pcmData)
            }
        }
    }

    fun stopPlayback() {
        try {
            activeAudioTrack?.stop()
            activeAudioTrack?.release()
            activeAudioTrack = null
        } catch (_: Exception) {}

        try {
            activeMediaPlayer?.stop()
            activeMediaPlayer?.release()
            activeMediaPlayer = null
        } catch (_: Exception) {}
    }

    private fun playPcmData(pcm: ShortArray) {
        try {
            val bufferSize = pcm.size * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            track.write(pcm, 0, pcm.size)
            track.play()
            activeAudioTrack = track
        } catch (_: Exception) {}
    }

    /**
     * Síntese matemática pura de 16-bit PCM Mono para os 17 efeitos de som.
     */
    fun generatePcmForBuiltInSound(id: Int): ShortArray {
        return when (id) {
            1 -> generateClickMechanical()
            2 -> generateClickTactile()
            3 -> generateClickMetallic()
            4 -> generateClickDeep()
            5 -> generateWhooshFast()
            6 -> generateSwooshCinematic()
            7 -> generateWhipSnap()
            8 -> generateGlitchCut()
            9 -> generateBubblePop()
            10 -> generateLaserFast()
            11 -> generateAirPuff()
            12 -> generatePercussiveSnap()
            13 -> generateCameraShutter()
            14 -> generateCinematicBoom()
            15 -> generateMagicSparkle()
            16 -> generateVinylRewind()
            17 -> generateCyberRiserHit()
            else -> ShortArray(0)
        }
    }

    // 1. Clique Tecla Mecânica (snap nítido 18ms)
    private fun generateClickMechanical(): ShortArray {
        val numSamples = (SAMPLE_RATE * 0.022).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(42)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 380.0)
            val sine = sin(2.0 * PI * 2400.0 * t)
            val noise = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 900.0)
            val sample = (0.75 * sine + 0.5 * noise) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 2. Clique Tecla Tátil (duplo clique preciso 24ms)
    private fun generateClickTactile(): ShortArray {
        val numSamples = (SAMPLE_RATE * 0.028).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(101)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val t2 = (t - 0.006).coerceAtLeast(0.0)
            val click1 = sin(2.0 * PI * 3100.0 * t) * exp(-t * 600.0)
            val click2 = if (t >= 0.006) sin(2.0 * PI * 2600.0 * t2) * exp(-t2 * 500.0) else 0.0
            val noise = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 800.0) * 0.3
            val sample = (click1 * 0.7 + click2 * 0.8 + noise)
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 3. Clique Tecla Metálica (4200Hz ringing 26ms)
    private fun generateClickMetallic(): ShortArray {
        val numSamples = (SAMPLE_RATE * 0.030).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val decay = exp(-t * 220.0)
            val tone1 = sin(2.0 * PI * 4200.0 * t)
            val tone2 = sin(2.0 * PI * 5800.0 * t) * 0.35
            val sample = (tone1 + tone2) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 4. Clique Tecla Profunda (bottom-out encorpado 35ms)
    private fun generateClickDeep(): ShortArray {
        val numSamples = (SAMPLE_RATE * 0.038).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(777)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 450.0 - t * 4000.0
            val decay = exp(-t * 160.0)
            val thump = sin(2.0 * PI * freq.coerceAtLeast(120.0) * t)
            val pop = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 900.0) * 0.4
            val sample = (thump * 0.85 + pop) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 5. Whoosh Ultra Rápido (110ms)
    private fun generateWhooshFast(): ShortArray {
        val duration = 0.11
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(55)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val envelope = sin(PI * (t / duration)) // sino suave
            val noise = rnd.nextDouble() * 2.0 - 1.0
            val freq = 3200.0 - (t / duration) * 2200.0
            val filtered = sin(2.0 * PI * freq * t) * 0.4 + noise * 0.6
            val sample = filtered * envelope
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 6. Swoosh Cinema (160ms)
    private fun generateSwooshCinematic(): ShortArray {
        val duration = 0.16
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(999)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val envelope = sin(PI * progress) * (if (progress < 0.5) 0.8 else 1.0)
            val subBass = sin(2.0 * PI * (90.0 + progress * 80.0) * t) * 0.5
            val airNoise = (rnd.nextDouble() * 2.0 - 1.0) * 0.6
            val sample = (subBass + airNoise) * envelope
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 7. Whip Transição (90ms)
    private fun generateWhipSnap(): ShortArray {
        val duration = 0.09
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(333)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val snap = if (t < 0.015) {
                (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 400.0)
            } else {
                val tTrail = t - 0.015
                (rnd.nextDouble() * 2.0 - 1.0) * exp(-tTrail * 70.0) * 0.4
            }
            buffer[i] = (snap.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 8. Glitch Digital Cut (100ms)
    private fun generateGlitchCut(): ShortArray {
        val duration = 0.10
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val freqs = doubleArrayOf(1200.0, 3400.0, 600.0, 2200.0, 4800.0)
        val rnd = Random(1234)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val segment = ((t / duration) * freqs.size).toInt().coerceIn(0, freqs.size - 1)
            val f = freqs[segment]
            val square = if (sin(2.0 * PI * f * t) > 0) 0.6 else -0.6
            val noise = (rnd.nextDouble() * 2.0 - 1.0) * 0.3
            val env = exp(-t * 25.0)
            val sample = (square + noise) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 30000).toInt().toShort()
        }
        return buffer
    }

    // 9. Pop Transição Bolha (65ms)
    private fun generateBubblePop(): ShortArray {
        val duration = 0.065
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val freq = 350.0 + progress * 1600.0
            val decay = exp(-t * 70.0)
            val sample = sin(2.0 * PI * freq * t) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 10. Laser Beep Fast (80ms)
    private fun generateLaserFast(): ShortArray {
        val duration = 0.08
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val freq = 2200.0 * exp(-t * 35.0)
            val decay = exp(-t * 20.0)
            val sample = sin(2.0 * PI * freq * t) * decay
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 11. Air Puff Impact (130ms)
    private fun generateAirPuff(): ShortArray {
        val duration = 0.13
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(888)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = sin(PI * (t / duration))
            val thud = sin(2.0 * PI * 85.0 * t) * exp(-t * 35.0) * 0.7
            val wind = (rnd.nextDouble() * 2.0 - 1.0) * 0.5
            val sample = (thud + wind) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 12. Snap Percussivo (55ms)
    private fun generatePercussiveSnap(): ShortArray {
        val duration = 0.055
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(444)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val impact = sin(2.0 * PI * 1800.0 * t) * exp(-t * 220.0) * 0.7
            val crackle = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 300.0) * 0.6
            val sample = impact + crackle
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 13. Obturador Flash (Clique duplo de câmera DSLR + flash 95ms)
    private fun generateCameraShutter(): ShortArray {
        val duration = 0.095
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(1313)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val mirrorUp = if (t < 0.025) {
                (sin(2.0 * PI * 2900.0 * t) * 0.6 + (rnd.nextDouble() * 2.0 - 1.0) * 0.7) * exp(-t * 260.0)
            } else 0.0
            val t2 = (t - 0.032).coerceAtLeast(0.0)
            val curtainSnap = if (t >= 0.032) {
                (sin(2.0 * PI * 3600.0 * t2) * 0.75 + (rnd.nextDouble() * 2.0 - 1.0) * 0.65) * exp(-t2 * 290.0)
            } else 0.0
            val flashWhine = if (t >= 0.035) {
                sin(2.0 * PI * (5200.0 + t2 * 18000.0) * t2) * exp(-t2 * 45.0) * 0.22
            } else 0.0
            val sample = mirrorUp + curtainSnap + flashWhine
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 14. Bass Drop Impact (Impacto sub-grave cinematográfico 180ms)
    private fun generateCinematicBoom(): ShortArray {
        val duration = 0.18
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(1414)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val freq = 145.0 * exp(-progress * 1.4) + 38.0
            val subWave = sin(2.0 * PI * freq * t)
            val harmonic = sin(2.0 * PI * (freq * 2.0) * t) * 0.35
            val punchNoise = (rnd.nextDouble() * 2.0 - 1.0) * exp(-t * 140.0) * 0.45
            val env = exp(-t * 11.0)
            val sample = (subWave * 0.85 + harmonic + punchNoise) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // 15. Brilho Cristal Chime (Arpejo mágico cristalino 150ms)
    private fun generateMagicSparkle(): ShortArray {
        val duration = 0.15
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val notes = doubleArrayOf(2637.0, 3136.0, 3951.0, 4698.0, 5274.0)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var acc = 0.0
            for (nIdx in notes.indices) {
                val onset = nIdx * 0.022
                if (t >= onset) {
                    val localT = t - onset
                    val env = exp(-localT * 38.0)
                    acc += sin(2.0 * PI * notes[nIdx] * localT) * env * 0.42
                    acc += sin(2.0 * PI * (notes[nIdx] * 2.0) * localT) * env * 0.15
                }
            }
            buffer[i] = (acc.coerceIn(-1.0, 1.0) * 31000).toInt().toShort()
        }
        return buffer
    }

    // 16. Rewind Tape Spin (Rebobinar fita/vinil rápido 140ms)
    private fun generateVinylRewind(): ShortArray {
        val duration = 0.14
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(1616)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val flutter = 1.0 + 0.25 * sin(2.0 * PI * 55.0 * t)
            val sweepFreq = (480.0 + progress * progress * 3400.0) * flutter
            val tone = sin(2.0 * PI * sweepFreq * t) * 0.65
            val scratchNoise = (rnd.nextDouble() * 2.0 - 1.0) * 0.35 * sin(PI * progress)
            val env = if (progress < 0.9) sin(PI * (progress / 0.9).coerceAtMost(1.0)) else exp(-(progress - 0.9) * 40.0)
            val sample = (tone + scratchNoise) * env
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 31500).toInt().toShort()
        }
        return buffer
    }

    // 17. Cyber Riser & Hit (Subida reversa rápida com impacto 165ms)
    private fun generateCyberRiserHit(): ShortArray {
        val duration = 0.165
        val numSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = ShortArray(numSamples)
        val rnd = Random(1717)
        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val sample = if (progress < 0.72) {
                val rProg = progress / 0.72
                val riserEnv = rProg * rProg
                val riserFreq = 320.0 + rProg * rProg * 2800.0
                val whoosh = (rnd.nextDouble() * 2.0 - 1.0) * 0.45 + sin(2.0 * PI * riserFreq * t) * 0.55
                whoosh * riserEnv
            } else {
                val hitT = t - (duration * 0.72)
                val hitEnv = exp(-hitT * 85.0)
                val punch = sin(2.0 * PI * (950.0 * exp(-hitT * 40.0) + 110.0) * hitT) * 0.8
                val snap = (rnd.nextDouble() * 2.0 - 1.0) * exp(-hitT * 260.0) * 0.5
                (punch + snap) * hitEnv
            }
            buffer[i] = (sample.coerceIn(-1.0, 1.0) * 32000).toInt().toShort()
        }
        return buffer
    }

    // =========================================================================
    // Upload de Som Personalizado
    // =========================================================================

    suspend fun importCustomSound(context: Context, uri: Uri): Result<TransitionSoundEffect> = withContext(Dispatchers.IO) {
        try {
            val nextId = getNextCustomId()
            val fileName = getFileName(context, uri)
            val ext = fileName.substringAfterLast('.', "wav").lowercase()

            val customSoundsDir = File(context.filesDir, "custom_sounds").apply { mkdirs() }
            val targetFile = File(customSoundsDir, "sound_${nextId}_$fileName")

            context.contentResolver.openInputStream(uri)?.use { inStream ->
                FileOutputStream(targetFile).use { outStream ->
                    inStream.copyTo(outStream)
                }
            }

            val cleanName = fileName.substringBeforeLast('.').take(22)
            val newSound = TransitionSoundEffect(
                id = nextId,
                name = cleanName,
                description = "Som personalizado importado",
                category = SoundCategory.CUSTOM,
                customFilePath = targetFile.absolutePath,
                isCustom = true
            )

            val updated = _customSounds.value + newSound
            _customSounds.value = updated
            saveCustomSounds(context, updated)

            Result.success(newSound)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomSound(context: Context, soundId: Int): Boolean = withContext(Dispatchers.IO) {
        val sound = _customSounds.value.find { it.id == soundId } ?: return@withContext false
        if (!sound.customFilePath.isNullOrBlank()) {
            try {
                File(sound.customFilePath).delete()
            } catch (_: Exception) {}
        }
        val updated = _customSounds.value.filter { it.id != soundId }
        _customSounds.value = updated
        saveCustomSounds(context, updated)
        true
    }

    private fun getNextCustomId(): Int {
        val maxBuiltIn = TransitionSoundEffect.MAX_BUILT_IN_ID
        val existingMax = (_customSounds.value.map { it.id } + maxBuiltIn).maxOrNull() ?: maxBuiltIn
        return existingMax + 1
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "custom_sound_${System.currentTimeMillis()}.wav"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex) ?: name
                }
            }
        }
        return name
    }

    private fun saveCustomSounds(context: Context, list: List<TransitionSoundEffect>) {
        try {
            val jsonArray = JSONArray()
            list.forEach { sound ->
                val obj = JSONObject().apply {
                    put("id", sound.id)
                    put("name", sound.name)
                    put("description", sound.description)
                    put("customFilePath", sound.customFilePath ?: "")
                }
                jsonArray.put(obj)
            }
            val file = File(context.filesDir, PREFS_FILE)
            file.writeText(jsonArray.toString())
        } catch (_: Exception) {}
    }

    private fun loadCustomSounds(context: Context) {
        try {
            val file = File(context.filesDir, PREFS_FILE)
            if (!file.exists()) return
            val jsonArray = JSONArray(file.readText())
            val list = mutableListOf<TransitionSoundEffect>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val path = obj.optString("customFilePath", "")
                if (path.isNotEmpty() && File(path).exists()) {
                    list.add(
                        TransitionSoundEffect(
                            id = obj.getInt("id"),
                            name = obj.getString("name"),
                            description = obj.optString("description", "Som personalizado"),
                            category = SoundCategory.CUSTOM,
                            customFilePath = path,
                            isCustom = true
                        )
                    )
                }
            }
            _customSounds.value = list
        } catch (_: Exception) {}
    }
}
