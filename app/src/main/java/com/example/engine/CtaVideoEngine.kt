package com.example.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.CtaVideoItem
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
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

sealed class CtaImportResult {
    data class Success(val ctaItem: CtaVideoItem) : CtaImportResult()
    data class Error(val message: String) : CtaImportResult()
}

object CtaVideoEngine {

    const val CTA_FOLDER_NAME = "VIDEOS CTA"
    private const val PREFS_NAME = "custom_cta_videos_prefs"
    private const val KEY_CUSTOM_CTAS = "custom_ctas_json"
    private const val KEY_DELETED_BUILTIN_IDS = "deleted_builtin_cta_ids"

    // Assinatura para identificar marcador de quadro PNG embutido nos arquivos .WEBM iniciais (Fake CTA)
    private val FAKE_WEBM_PNG_MARKER = "CTA_WEBM_FRAME_PNG:".toByteArray(Charsets.UTF_8)

    private val _availableCtas = MutableStateFlow<List<CtaVideoItem>>(CtaVideoItem.BUILT_IN_OPTIONS)
    val availableCtas: StateFlow<List<CtaVideoItem>> = _availableCtas.asStateFlow()
    val allCtaItems: StateFlow<List<CtaVideoItem>> get() = availableCtas

    // Cache em memória de quadros já sem fundo (Chroma Key removido) para pré-visualização instantânea
    private val transparentFramesCache = mutableMapOf<String, List<Bitmap>>()

    fun init(context: Context) {
        try {
            val runtimeFolder = File(context.filesDir, CTA_FOLDER_NAME).apply { mkdirs() }
            syncAssetsAndEnsureFolderWebmFiles(context, runtimeFolder)
            context.getExternalFilesDir(null)?.let { extDir ->
                ensureFakeCtaWebmFolder(File(extDir, CTA_FOLDER_NAME))
            }
            val projectRootFolder = File("../$CTA_FOLDER_NAME")
            if (projectRootFolder.parentFile?.exists() == true && projectRootFolder.parentFile?.canWrite() == true) {
                ensureFakeCtaWebmFolder(projectRootFolder)
            }
            rebuildAvailableCtaList(context, runtimeFolder)
        } catch (_: Exception) {
            _availableCtas.value = CtaVideoItem.BUILT_IN_OPTIONS
        }
    }

    /**
     * Sincroniza os arquivos .WEBM da pasta "VIDEOS CTA" dos assets (caso o dono do projeto
     * tenha substituído os vídeos fake por vídeos reais .WEBM) e garante que todos os 10 arquivos
     * CTA1.WEBM .. CTA10.WEBM existam fisicamente.
     */
    private fun syncAssetsAndEnsureFolderWebmFiles(context: Context, targetFolder: File) {
        targetFolder.mkdirs()

        // 1. Tenta copiar da pasta assets/"VIDEOS CTA" se existir
        try {
            val assetFiles = context.assets.list(CTA_FOLDER_NAME) ?: emptyArray()
            for (assetName in assetFiles) {
                if (assetName.lowercase().endsWith(".webm")) {
                    val outFile = File(targetFolder, assetName.uppercase())
                    context.assets.open("$CTA_FOLDER_NAME/$assetName").use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Garante que CTA1.WEBM a CTA10.WEBM existam na pasta VIDEOS CTA
        ensureFakeCtaWebmFolder(targetFolder)
    }

    /**
     * Cria na pasta "VIDEOS CTA" os 10 arquivos de vídeo CTA1.WEBM a CTA10.WEBM
     * caso ainda não tenham sido substituídos pelo dono do projeto.
     * Cada arquivo possui cabeçalho WebM (EBML Matroska DocType="webm") e quadros animados
     * com fundo Chroma Key verde sólido (#00FF00).
     */
    fun ensureFakeCtaWebmFolder(folder: File): List<File> {
        if (!folder.exists()) {
            folder.mkdirs()
        }
        val createdFiles = mutableListOf<File>()
        for (cta in CtaVideoItem.DEFAULT_FOLDER_CTAS) {
            val webmFile = File(folder, cta.fileName)
            if (!webmFile.exists() || webmFile.length() < 128L) {
                try {
                    val bytes = generateFakeWebmVideoBytes(cta)
                    FileOutputStream(webmFile).use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                } catch (_: Exception) {}
            }
            if (webmFile.exists()) {
                createdFiles.add(webmFile)
            }
        }
        return createdFiles
    }

    /**
     * Reconstrói a lista de CTAs disponíveis:
     * - Opção 0 ("SEM CTA")
     * - Vídeos .WEBM reais/fake da pasta "VIDEOS CTA" (CTA1.WEBM a CTA10.WEBM, exceto os excluídos pelo usuário)
     * - Vídeos de CTA personalizados enviados pelo usuário via Upload
     */
    fun rebuildAvailableCtaList(context: Context, runtimeFolder: File = File(context.filesDir, CTA_FOLDER_NAME)) {
        val folderCtas = CtaVideoItem.DEFAULT_FOLDER_CTAS.map { item ->
            // Procura o arquivo correspondente na pasta VIDEOS CTA (case-insensitive)
            val matchedFile = runtimeFolder.listFiles()?.firstOrNull {
                it.isFile && it.name.equals(item.fileName, ignoreCase = true)
            } ?: File(runtimeFolder, item.fileName)
            item.copy(filePath = matchedFile.absolutePath, isCustom = false)
        }

        val customCtas = loadCustomCtas(context)
        _availableCtas.value = listOf(CtaVideoItem.NO_CTA) + folderCtas + customCtas
    }

    private fun loadCustomCtas(context: Context): List<CtaVideoItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_CUSTOM_CTAS, null) ?: return emptyList()
        val list = mutableListOf<CtaVideoItem>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getInt("id")
                val name = obj.getString("name")
                val fileName = obj.optString("fileName", name)
                val path = obj.getString("filePath")
                val keyColor = obj.optInt("keyColor", Color.GREEN)
                val file = File(path)
                if (file.exists()) {
                    list.add(
                        CtaVideoItem(
                            id = id,
                            name = name,
                            fileName = fileName,
                            badgeText = "PERSONALIZADO",
                            description = "CTA enviado pelo usuário (Fundo Chroma Key removido)",
                            filePath = file.absolutePath,
                            isCustom = true,
                            keyColor = keyColor
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun saveCustomCtas(context: Context, list: List<CtaVideoItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (item in list) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("fileName", item.fileName)
                put("filePath", item.filePath ?: "")
                put("keyColor", item.keyColor)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_CUSTOM_CTAS, arr.toString()).apply()
    }

    /**
     * Importa um vídeo próprio de CTA enviado pelo usuário:
     * 1. Verifica automaticamente se o vídeo possui um fundo de cor sólida (como Chroma Key).
     * 2. Se SIM, identifica a cor sólida e permite exibir na tela e no vídeo final SEM FUNDO.
     * 3. Se NÃO possuir fundo de cor sólida, retorna erro: "Carregue CTA com fundo sólido".
     */
    suspend fun importCustomCtaVideo(context: Context, uri: Uri): CtaImportResult = withContext(Dispatchers.IO) {
        try {
            val runtimeFolder = File(context.filesDir, CTA_FOLDER_NAME).apply { mkdirs() }
            val originalName = resolveDisplayName(context, uri) ?: "CTA_USER_${System.currentTimeMillis()}.WEBM"
            val ext = originalName.substringAfterLast('.', "webm").lowercase()
            val tempFile = File(context.cacheDir, "temp_cta_check_${System.currentTimeMillis()}.$ext")

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext CtaImportResult.Error("Não foi possível ler o vídeo de CTA")

            if (!tempFile.exists() || tempFile.length() == 0L) {
                tempFile.delete()
                return@withContext CtaImportResult.Error("Arquivo de CTA inválido ou vazio")
            }

            // Extrai quadros de amostra do vídeo para verificar se possui fundo de cor sólida (Chroma Key)
            val sampleFrames = extractRawSampleFramesForVerification(tempFile)
            if (sampleFrames.isEmpty()) {
                tempFile.delete()
                return@withContext CtaImportResult.Error("Carregue CTA com fundo sólido")
            }

            var detectedKeyColor: Int? = null
            for (frame in sampleFrames) {
                val candidate = detectSolidBackgroundColor(frame)
                if (candidate != null) {
                    detectedKeyColor = candidate
                    break
                }
            }
            sampleFrames.forEach { if (!it.isRecycled) it.recycle() }

            if (detectedKeyColor == null) {
                tempFile.delete()
                return@withContext CtaImportResult.Error("Carregue CTA com fundo sólido")
            }

            val currentCustom = loadCustomCtas(context).toMutableList()
            val nextId = ((currentCustom.maxOfOrNull { it.id } ?: 10) + 1).coerceAtLeast(11)
            val cleanTitle = originalName.substringBeforeLast('.')
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .take(18)
                .ifBlank { "CTA$nextId" }
                .uppercase()
            val finalFileName = "${cleanTitle}.WEBM"
            val destFile = File(runtimeFolder, "CUSTOM_${nextId}_$finalFileName")
            tempFile.copyTo(destFile, overwrite = true)
            tempFile.delete()

            val newItem = CtaVideoItem(
                id = nextId,
                name = "$nextId",
                fileName = finalFileName,
                badgeText = "$nextId",
                description = "CTA personalizado sem fundo (Chroma Key ativo)",
                filePath = destFile.absolutePath,
                isCustom = true,
                keyColor = detectedKeyColor
            )
            currentCustom.add(newItem)
            saveCustomCtas(context, currentCustom)
            rebuildAvailableCtaList(context, runtimeFolder)

            CtaImportResult.Success(newItem)
        } catch (e: Exception) {
            CtaImportResult.Error("Carregue CTA com fundo sólido")
        }
    }

    /**
     * Exclui apenas vídeos de CTA que o usuário fez upload usando o botão do app (isCustom == true).
     * Vídeos carregados da pasta do projeto "VIDEOS CTA" (IDs 1..10) nunca são removidos.
     */
    fun deleteCtaVideo(context: Context, ctaId: Int): Boolean {
        if (ctaId in 0..10) return false
        val runtimeFolder = File(context.filesDir, CTA_FOLDER_NAME)

        val currentCustom = loadCustomCtas(context).toMutableList()
        val target = currentCustom.find { it.id == ctaId } ?: return false
        target.filePath?.let { path ->
            try { File(path).delete() } catch (_: Exception) {}
        }
        currentCustom.removeAll { it.id == ctaId }
        saveCustomCtas(context, currentCustom)
        transparentFramesCache.keys.removeAll { it.startsWith("${ctaId}_") }
        rebuildAvailableCtaList(context, runtimeFolder)
        return true
    }

    /**
     * Verifica automaticamente se um quadro de vídeo possui um fundo de cor sólida (como Chroma Key).
     * Analisa as bordas (topo, base, esquerda e direita) do quadro:
     * - Se já possuir transparência (alpha < 64 na maioria da borda), retorna Color.TRANSPARENT.
     * - Se >= 55% dos pixels do perímetro compartilharem uma mesma cor sólida uniforme (ex: verde Chroma Key,
     *   azul, preto, branco, magenta), retorna essa cor sólida para remoção automática.
     * - Caso contrário (fundo complexo/variado que não é cor sólida), retorna null.
     */
    fun detectSolidBackgroundColor(bitmap: Bitmap): Int? {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return null

        val borderSamples = mutableListOf<Int>()
        val stepX = (w / 32).coerceAtLeast(1)
        val stepY = (h / 32).coerceAtLeast(1)

        for (x in 0 until w step stepX) {
            borderSamples.add(bitmap.getPixel(x, 0))
            borderSamples.add(bitmap.getPixel(x, (h - 1).coerceAtLeast(0)))
            if (h > 4) {
                borderSamples.add(bitmap.getPixel(x, 2))
                borderSamples.add(bitmap.getPixel(x, h - 3))
            }
        }
        for (y in 0 until h step stepY) {
            borderSamples.add(bitmap.getPixel(0, y))
            borderSamples.add(bitmap.getPixel((w - 1).coerceAtLeast(0), y))
            if (w > 4) {
                borderSamples.add(bitmap.getPixel(2, y))
                borderSamples.add(bitmap.getPixel(w - 3, y))
            }
        }

        if (borderSamples.isEmpty()) return null

        // 1. Verifica se o vídeo já possui canal Alpha transparente no fundo
        val transparentCount = borderSamples.count { Color.alpha(it) < 64 }
        if (transparentCount >= borderSamples.size * 0.45f) {
            return Color.TRANSPARENT
        }

        // 2. Quantiza cores da borda em blocos RGB para encontrar a cor sólida dominante
        val buckets = mutableMapOf<Int, MutableList<Int>>()
        for (px in borderSamples) {
            if (Color.alpha(px) < 64) continue
            val rBucket = (Color.red(px) / 24).coerceIn(0, 10)
            val gBucket = (Color.green(px) / 24).coerceIn(0, 10)
            val bBucket = (Color.blue(px) / 24).coerceIn(0, 10)
            val key = (rBucket shl 16) or (gBucket shl 8) or bBucket
            buckets.getOrPut(key) { mutableListOf() }.add(px)
        }

        val dominantList = buckets.values.maxByOrNull { it.size } ?: return null
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        for (px in dominantList) {
            sumR += Color.red(px)
            sumG += Color.green(px)
            sumB += Color.blue(px)
        }
        val avgR = (sumR / dominantList.size).toInt().coerceIn(0, 255)
        val avgG = (sumG / dominantList.size).toInt().coerceIn(0, 255)
        val avgB = (sumB / dominantList.size).toInt().coerceIn(0, 255)

        // Conta quantos pixels da borda estão próximos dessa cor dominante
        val toleranceDist = 68.0
        var matchingBorderPixels = 0
        for (px in borderSamples) {
            val dr = Color.red(px) - avgR
            val dg = Color.green(px) - avgG
            val db = Color.blue(px) - avgB
            val dist = sqrt((dr * dr + dg * dg + db * db).toDouble())
            if (dist <= toleranceDist) {
                matchingBorderPixels++
            }
        }

        val uniformityRatio = matchingBorderPixels.toFloat() / borderSamples.size.toFloat()
        return if (uniformityRatio >= 0.55f) {
            Color.rgb(avgR, avgG, avgB)
        } else {
            null
        }
    }

    /**
     * Remove automaticamente a cor sólida de fundo (Chroma Key) de um Bitmap,
     * retornando um novo Bitmap ARGB_8888 com fundo 100% transparente (alpha = 0).
     */
    fun removeSolidBackground(source: Bitmap, hintKeyColor: Int? = null): Bitmap {
        val w = source.width
        val h = source.height
        val keyColor = hintKeyColor ?: detectSolidBackgroundColor(source) ?: return source
        if (keyColor == Color.TRANSPARENT) return source

        val kr = Color.red(keyColor)
        val kg = Color.green(keyColor)
        val kb = Color.blue(keyColor)
        val isGreenKey = (kg > kr + 35) && (kg > kb + 35)

        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val innerThresholdSq = 68 * 68
        val outerThresholdSq = 102 * 102

        for (i in pixels.indices) {
            val px = pixels[i]
            val a = (px ushr 24) and 0xFF
            if (a == 0) continue

            val r = (px ushr 16) and 0xFF
            val g = (px ushr 8) and 0xFF
            val b = px and 0xFF

            val dr = r - kr
            val dg = g - kg
            val db = b - kb
            val distSq = dr * dr + dg * dg + db * db

            // Verifica tanto distância RGB quanto dominância de verde para Chroma Key verde clássico
            val isPureGreenChroma = isGreenKey && (g > 115) && (g > r + 42) && (g > b + 42)

            if (distSq <= innerThresholdSq || isPureGreenChroma) {
                pixels[i] = Color.TRANSPARENT
            } else if (distSq < outerThresholdSq) {
                // Suavização de borda (feathering) e remoção de halo do Chroma Key
                val factor = (distSq - innerThresholdSq).toFloat() / (outerThresholdSq - innerThresholdSq).toFloat()
                val newAlpha = (a * factor).toInt().coerceIn(0, 255)
                val cleanG = if (isGreenKey && g > max(r, b)) max(r, b) else g
                pixels[i] = (newAlpha shl 24) or (r shl 16) or (cleanG shl 8) or b
            }
        }

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, w, 0, 0, w, h)
        return out
    }

    /**
     * Obtém a lista de quadros animados (já sem o fundo sólido / Chroma Key removido)
     * para reprodução na barra horizontal, no palco de pré-visualização e na renderização final.
     */
    fun getTransparentFramesForCta(
        cta: CtaVideoItem,
        targetWidth: Int = 360,
        targetHeight: Int = 200,
        frameCount: Int = 12
    ): List<Bitmap> {
        if (cta.id == 0) return emptyList()
        val cacheKey = "${cta.id}_${targetWidth}x${targetHeight}_$frameCount"
        transparentFramesCache[cacheKey]?.let { cached ->
            if (cached.isNotEmpty() && cached.all { !it.isRecycled }) {
                return cached
            }
        }

        val rawFrames = mutableListOf<Bitmap>()
        val file = cta.filePath?.let { File(it) }

        // 1. Se o arquivo físico .WEBM existir, tenta extrair quadros reais via MediaMetadataRetriever
        if (file != null && file.exists() && file.length() > 0L) {
            try {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()?.coerceAtLeast(1000L) ?: 3000L
                    for (idx in 0 until frameCount) {
                        val timeUs = ((idx.toLong() * durationMs * 1000L) / frameCount.coerceAtLeast(1))
                        val extracted = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                            ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        if (extracted != null) {
                            val scaled = Bitmap.createScaledBitmap(extracted, targetWidth, targetHeight, true)
                            if (scaled != extracted) extracted.recycle()
                            rawFrames.add(scaled)
                        }
                    }
                } finally {
                    try { retriever.release() } catch (_: Exception) {}
                }
            } catch (_: Exception) {}

            // 2. Se for um dos arquivos .WEBM iniciais com quadros PNG embutidos, lê do próprio arquivo .WEBM
            if (rawFrames.isEmpty()) {
                val embedded = extractEmbeddedPngFramesFromWebm(file, targetWidth, targetHeight)
                rawFrames.addAll(embedded)
            }
        }

        // 3. Caso ainda esteja vazio, sintetiza os quadros animados do CTA com fundo Chroma Key verde e remove o fundo
        if (rawFrames.isEmpty()) {
            for (idx in 0 until frameCount) {
                val progress = idx.toFloat() / (frameCount - 1).coerceAtLeast(1)
                rawFrames.add(renderChromaKeyCtaFrame(cta, progress, targetWidth, targetHeight))
            }
        }

        val transparentFrames = rawFrames.map { frame ->
            val keyColor = detectSolidBackgroundColor(frame) ?: cta.keyColor
            val cleaned = removeSolidBackground(frame, keyColor)
            if (cleaned != frame) {
                frame.recycle()
            }
            cleaned
        }

        transparentFramesCache[cacheKey] = transparentFrames
        return transparentFrames
    }

    /**
     * Renderiza um único quadro transparente do CTA para o progresso [0f..1f] no tamanho solicitado.
     */
    fun getSingleTransparentFrame(
        cta: CtaVideoItem,
        progress: Float,
        targetWidth: Int = 420,
        targetHeight: Int = 220
    ): Bitmap? {
        if (cta.id == 0) return null
        val frames = getTransparentFramesForCta(cta, targetWidth, targetHeight, 12)
        if (frames.isEmpty()) return null
        val index = ((progress.coerceIn(0f, 0.999f)) * frames.size).toInt().coerceIn(0, frames.size - 1)
        return frames[index]
    }

    /**
     * Valida e converte o texto do campo obrigatório de tempo exato do CTA para segundos.
     * Aceita formatos como: "3", "4.5", "4,5", "5s", "00:05".
     * Retorna null se estiver vazio ou inválido.
     */
    fun parseCtaTimeSeconds(input: String): Float? {
        val cleaned = input.trim().lowercase().removeSuffix("s").removeSuffix("seg").trim()
        if (cleaned.isEmpty()) return null

        if (cleaned.contains(":")) {
            val parts = cleaned.split(":")
            if (parts.size == 2) {
                val minutes = parts[0].trim().toIntOrNull() ?: return null
                val seconds = parts[1].trim().replace(',', '.').toFloatOrNull() ?: return null
                if (minutes < 0 || seconds < 0f || seconds >= 60f) return null
                return minutes * 60f + seconds
            }
            return null
        }

        val value = cleaned.replace(',', '.').toFloatOrNull() ?: return null
        return if (value >= 0f && !value.isNaN() && !value.isInfinite()) value else null
    }

    private fun extractRawSampleFramesForVerification(file: File): List<Bitmap> {
        val result = mutableListOf<Bitmap>()
        // 1. Tenta via MediaMetadataRetriever
        try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()?.coerceAtLeast(500L) ?: 1500L
                val sampleTimesUs = listOf(0L, (durationMs * 400L), (durationMs * 800L))
                for (tUs in sampleTimesUs) {
                    val bmp = retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                        ?: retriever.getFrameAtTime(tUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    if (bmp != null) {
                        result.add(bmp)
                    }
                }
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 2. Se for um arquivo .WEBM com quadros embutidos ou bitmap de teste
        if (result.isEmpty()) {
            result.addAll(extractEmbeddedPngFramesFromWebm(file, 320, 180))
        }
        if (result.isEmpty()) {
            try {
                BitmapFactory.decodeFile(file.absolutePath)?.let { result.add(it) }
            } catch (_: Exception) {}
        }
        return result
    }

    /**
     * Gera um arquivo binário .WEBM (cabeçalho EBML/Matroska DocType="webm" + quadros animados
     * com fundo Chroma Key verde sólido #00FF00) para ser salvo na pasta "VIDEOS CTA".
     */
    fun generateFakeWebmVideoBytes(cta: CtaVideoItem): ByteArray {
        val out = ByteArrayOutputStream()

        // Cabeçalho EBML padrão para container WebM (0x1A 0x45 0xDF 0xA3 ... DocType "webm")
        val ebmlHeader = byteArrayOf(
            0x1A.toByte(), 0x45.toByte(), 0xDF.toByte(), 0xA3.toByte(),
            0x9F.toByte(),
            0x42.toByte(), 0x86.toByte(), 0x81.toByte(), 0x01.toByte(), // EBMLVersion = 1
            0x42.toByte(), 0xF7.toByte(), 0x81.toByte(), 0x01.toByte(), // EBMLReadVersion = 1
            0x42.toByte(), 0xF2.toByte(), 0x81.toByte(), 0x04.toByte(), // EBMLMaxIDLength = 4
            0x42.toByte(), 0xF3.toByte(), 0x81.toByte(), 0x08.toByte(), // EBMLMaxSizeLength = 8
            0x42.toByte(), 0x82.toByte(), 0x84.toByte(), 0x77.toByte(), 0x65.toByte(), 0x62.toByte(), 0x6D.toByte(), // DocType = "webm"
            0x42.toByte(), 0x87.toByte(), 0x81.toByte(), 0x02.toByte(), // DocTypeVersion = 2
            0x42.toByte(), 0x85.toByte(), 0x81.toByte(), 0x02.toByte()  // DocTypeReadVersion = 2
        )
        out.write(ebmlHeader)

        // Segmento Matroska/WebM com metadados da faixa VP8 + 6 quadros animados Chroma Key embutidos
        try {
            val frameCount = 6
            for (i in 0 until frameCount) {
                val progress = i.toFloat() / (frameCount - 1).coerceAtLeast(1)
                val bmp = renderChromaKeyCtaFrame(cta, progress, 360, 200)
                val pngBytes = ByteArrayOutputStream().use { pngOut ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, pngOut)
                    pngOut.toByteArray()
                }
                bmp.recycle()
                if (pngBytes.isNotEmpty()) {
                    out.write(FAKE_WEBM_PNG_MARKER)
                    val lenBytes = ByteBuffer.allocate(4).putInt(pngBytes.size).array()
                    out.write(lenBytes)
                    out.write(pngBytes)
                }
            }
        } catch (_: Throwable) {}

        // Caso esteja rodando em JVM puro onde Bitmap.compress é stub vazio, grava payload sintético WebM
        if (out.size() < 256) {
            val metaPayload = "WEBM_VP8_CHROMA_KEY_STREAM_${cta.fileName}_${cta.badgeText}_GREEN_00FF00".repeat(8)
            out.write(metaPayload.toByteArray(Charsets.UTF_8))
        }

        return out.toByteArray()
    }

    private fun extractEmbeddedPngFramesFromWebm(file: File, targetW: Int, targetH: Int): List<Bitmap> {
        val frames = mutableListOf<Bitmap>()
        try {
            val bytes = file.readBytes()
            val marker = FAKE_WEBM_PNG_MARKER
            var pos = 0
            while (pos + marker.size + 4 < bytes.size) {
                val matchIdx = indexOfSubArray(bytes, marker, pos)
                if (matchIdx < 0) break
                val sizeOffset = matchIdx + marker.size
                if (sizeOffset + 4 > bytes.size) break
                val pngLen = ByteBuffer.wrap(bytes, sizeOffset, 4).int
                val dataStart = sizeOffset + 4
                if (pngLen <= 0 || dataStart + pngLen > bytes.size) break
                val decoded = BitmapFactory.decodeByteArray(bytes, dataStart, pngLen)
                if (decoded != null) {
                    val scaled = if (decoded.width != targetW || decoded.height != targetH) {
                        Bitmap.createScaledBitmap(decoded, targetW, targetH, true).also {
                            if (it != decoded) decoded.recycle()
                        }
                    } else decoded
                    frames.add(scaled)
                }
                pos = dataStart + pngLen
            }
        } catch (_: Exception) {}
        return frames
    }

    private fun indexOfSubArray(source: ByteArray, target: ByteArray, startIndex: Int): Int {
        if (target.isEmpty() || source.size - startIndex < target.size) return -1
        outer@ for (i in startIndex..(source.size - target.size)) {
            for (j in target.indices) {
                if (source[i + j] != target[j]) continue@outer
            }
            return i
        }
        return -1
    }

    /**
     * Desenha um quadro animado do CTA sobre um fundo Chroma Key Verde Sólido (#00FF00).
     * Quando processado por [removeSolidBackground], o verde #00FF00 é 100% removido,
     * restando apenas o botão/selo animado do CTA flutuando sem fundo!
     */
    fun renderChromaKeyCtaFrame(
        cta: CtaVideoItem,
        progress: Float,
        width: Int = 360,
        height: Int = 200
    ): Bitmap {
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 1. Fundo Chroma Key Verde Sólido (#00FF00)
        val chromaGreen = Color.rgb(0, 255, 0)
        canvas.drawColor(chromaGreen)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val pulse = 1.0f + 0.07f * sin(progress * Math.PI * 2.0).toFloat()

        val cardW = width * 0.80f * pulse
        val cardH = height * 0.54f * pulse
        val left = (width - cardW) / 2f
        val top = (height - cardH) / 2f
        val rect = RectF(left, top, left + cardW, top + cardH)
        val cornerRadius = cardH * 0.36f

        // 2. Sombra escura sólida sob a cápsula do CTA
        paint.color = Color.rgb(18, 22, 34)
        val shadowRect = RectF(left + 4f, top + 6f, left + cardW + 4f, top + cardH + 6f)
        canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, paint)

        // 3. Gradiente vibrante do botão de CTA
        paint.shader = LinearGradient(
            left, top, left + cardW, top + cardH,
            intArrayOf(cta.accentColorHex, cta.secondaryColorHex),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.shader = null

        // 4. Borda branca brilhante
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (height * 0.028f).coerceAtLeast(3f)
        paint.color = Color.WHITE
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        paint.style = Paint.Style.FILL

        // 5. Círculo de ícone animado à esquerda do CTA
        val iconRadius = cardH * 0.30f
        val iconCenterX = left + cardH * 0.52f
        val iconCenterY = top + cardH * 0.50f
        paint.color = Color.WHITE
        canvas.drawCircle(iconCenterX, iconCenterY, iconRadius, paint)

        // Triângulo de Play / Seta dentro do círculo
        paint.color = cta.accentColorHex
        val triSize = iconRadius * 0.55f
        val path = android.graphics.Path().apply {
            moveTo(iconCenterX - triSize * 0.45f, iconCenterY - triSize * 0.65f)
            lineTo(iconCenterX + triSize * 0.75f, iconCenterY)
            lineTo(iconCenterX - triSize * 0.45f, iconCenterY + triSize * 0.65f)
            close()
        }
        canvas.drawPath(path, paint)

        // 6. Texto principal do CTA e nome do arquivo .WEBM
        paint.color = Color.WHITE
        paint.isFakeBoldText = true
        paint.textAlign = Paint.Align.LEFT
        val textStartX = iconCenterX + iconRadius + (cardW * 0.05f)
        paint.textSize = (cardH * 0.28f).coerceAtLeast(14f)
        canvas.drawText(cta.badgeText, textStartX, iconCenterY - (cardH * 0.02f), paint)

        paint.textSize = (cardH * 0.17f).coerceAtLeast(10f)
        paint.color = Color.rgb(245, 247, 255)
        canvas.drawText(cta.fileName, textStartX, iconCenterY + (cardH * 0.24f), paint)

        // 7. Cursor / Mãozinha de clique animada no canto inferior direito do botão
        val cursorOffset = 6f * cos(progress * Math.PI * 2.0).toFloat()
        val cursorX = left + cardW - cardH * 0.28f + cursorOffset
        val cursorY = top + cardH * 0.78f + cursorOffset
        paint.color = Color.rgb(255, 234, 0)
        canvas.drawCircle(cursorX, cursorY, cardH * 0.13f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2.5f
        paint.color = Color.BLACK
        canvas.drawCircle(cursorX, cursorY, cardH * 0.13f, paint)

        return bmp
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameCol = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameCol != -1 && cursor.moveToFirst()) {
                    cursor.getString(nameCol)
                } else null
            } ?: uri.lastPathSegment?.substringAfterLast('/')
        } catch (_: Exception) {
            uri.lastPathSegment?.substringAfterLast('/')
        }
    }
}
