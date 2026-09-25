package com.example.engine

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class ImageImportResult {
    data class Success(val imported: List<Pair<String, String>>) : ImageImportResult()
    data class Error(val message: String) : ImageImportResult()
}

object ImageImportHelper {

    private val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/bmp",
        "image/heic",
        "video/mp4",
        "video/3gpp",
        "video/webm",
        "video/quicktime",
        "video/x-matroska",
        "video/avi"
    )

    private val ALLOWED_EXTENSIONS = setOf(
        "jpg",
        "jpeg",
        "png",
        "webp",
        "bmp",
        "heic",
        "mp4",
        "3gp",
        "webm",
        "mov",
        "mkv",
        "avi"
    )

    suspend fun importImages(
        context: Context,
        uris: List<Uri>,
        projectId: Long
    ): ImageImportResult = withContext(Dispatchers.IO) {
        if (uris.isEmpty()) {
            return@withContext ImageImportResult.Error("Nenhum arquivo selecionado.")
        }

        val destFolder = File(context.filesDir, "projects/$projectId/images").apply {
            mkdirs()
        }

        val result = mutableListOf<Pair<String, String>>()

        for (uri in uris) {
            val mimeType = context.contentResolver.getType(uri)?.lowercase()
            val fileName = getFileName(context, uri)
            val ext = fileName.substringAfterLast('.', "").lowercase()

            val isValidMime = mimeType != null && (
                mimeType in ALLOWED_MIME_TYPES ||
                mimeType.startsWith("image/") ||
                mimeType.startsWith("video/")
            )
            val isValidExt = ext in ALLOWED_EXTENSIONS

            if (!isValidMime && !isValidExt) {
                return@withContext ImageImportResult.Error("Arquivo inválido ($fileName). Selecione imagens ou vídeos suportados.")
            }

            try {
                val prefix = if (MediaHelper.isVideo(fileName)) "vid" else "img"
                val targetFile = File(
                    destFolder,
                    "${prefix}_${System.currentTimeMillis()}_${result.size + 1}_$fileName"
                )
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                result.add(Pair(targetFile.absolutePath, fileName))
            } catch (e: Exception) {
                return@withContext ImageImportResult.Error("Falha ao ler mídia: ${e.message}")
            }
        }

        ImageImportResult.Success(result)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var name = "media_${System.currentTimeMillis()}.jpg"
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
}
