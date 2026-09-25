package com.example.engine

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

sealed class ZipExtractResult {
    data class Success(val extractedFiles: List<Pair<String, String>>) : ZipExtractResult()
    data class Error(val message: String) : ZipExtractResult()
}

object ZipExtractor {

    private val ALLOWED_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    /**
     * Extrai imagens de um arquivo ZIP mantendo a ordem alfabética/numérica original.
     * Se contiver arquivos não permitidos ou zero imagens, retorna erro estrito conforme especificação:
     * "Arquivo ZIP inválido ou sem imagens suportadas."
     */
    suspend fun extractZip(
        context: Context,
        zipUri: Uri,
        projectId: Long
    ): ZipExtractResult = withContext(Dispatchers.IO) {
        val destFolder = File(context.filesDir, "projects/$projectId/images").apply {
            mkdirs()
        }

        // Leitura inicial para verificação de integridade e conteúdo
        val inputStream: InputStream? = try {
            context.contentResolver.openInputStream(zipUri)
        } catch (e: Exception) {
            return@withContext ZipExtractResult.Error("Arquivo ZIP inválido ou sem imagens suportadas.")
        }

        if (inputStream == null) {
            return@withContext ZipExtractResult.Error("Arquivo ZIP inválido ou sem imagens suportadas.")
        }

        // Buffer temporário em memória / temp dir para validação
        val tempDir = File(context.cacheDir, "zip_temp_${System.currentTimeMillis()}").apply {
            mkdirs()
        }

        try {
            val entries = mutableListOf<String>()
            ZipInputStream(inputStream).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val name = File(entry.name).name
                        // Ignora arquivos de sistema do macOS (__MACOSX, .DS_Store)
                        if (!name.startsWith(".") && !entry.name.contains("__MACOSX")) {
                            val ext = name.substringAfterLast('.', "").lowercase()
                            // Se contiver arquivos não permitidos
                            if (ext !in ALLOWED_EXTENSIONS) {
                                tempDir.deleteRecursively()
                                return@withContext ZipExtractResult.Error("Arquivo ZIP inválido ou sem imagens suportadas.")
                            }

                            // Extrai temporariamente para validação
                            val tempFile = File(tempDir, name)
                            FileOutputStream(tempFile).use { fos ->
                                zis.copyTo(fos)
                            }
                            entries.add(name)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            if (entries.isEmpty()) {
                tempDir.deleteRecursively()
                return@withContext ZipExtractResult.Error("Arquivo ZIP inválido ou sem imagens suportadas.")
            }

            // Ordenação estrita alfabética / numérica natural
            val sortedEntries = entries.sortedWith(NaturalOrderComparator)

            val finalFiles = mutableListOf<Pair<String, String>>()
            sortedEntries.forEachIndexed { index, fileName ->
                val srcFile = File(tempDir, fileName)
                val targetFile = File(destFolder, "img_${System.currentTimeMillis()}_${index + 1}_$fileName")
                srcFile.copyTo(targetFile, overwrite = true)
                finalFiles.add(Pair(targetFile.absolutePath, fileName))
            }

            tempDir.deleteRecursively()
            ZipExtractResult.Success(finalFiles)
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            ZipExtractResult.Error("Arquivo ZIP inválido ou sem imagens suportadas.")
        }
    }

    /**
     * Comparador de ordem natural (ex: img1.jpg, img2.jpg, img10.jpg).
     */
    private val NaturalOrderComparator = Comparator<String> { a, b ->
        val regex = Regex("(\\d+)|(\\D+)")
        val aMatcher = regex.findAll(a).iterator()
        val bMatcher = regex.findAll(b).iterator()

        while (aMatcher.hasNext() && bMatcher.hasNext()) {
            val aToken = aMatcher.next().value
            val bToken = bMatcher.next().value

            val aIsNum = aToken.all { it.isDigit() }
            val bIsNum = bToken.all { it.isDigit() }

            if (aIsNum && bIsNum) {
                val aNum = aToken.toLongOrNull() ?: 0L
                val bNum = bToken.toLongOrNull() ?: 0L
                val cmp = aNum.compareTo(bNum)
                if (cmp != 0) return@Comparator cmp
            } else {
                val cmp = aToken.compareTo(bToken, ignoreCase = true)
                if (cmp != 0) return@Comparator cmp
            }
        }

        a.compareTo(b, ignoreCase = true)
    }
}
