package com.example.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import java.io.File

object MediaHelper {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "mov", "3gp", "avi", "ts"
    )

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "bmp", "heic"
    )

    fun isVideo(pathOrName: String): Boolean {
        val ext = pathOrName.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    fun isImage(pathOrName: String): Boolean {
        val ext = pathOrName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    /**
     * Extrai miniatura (frame inicial) de vídeo ou carrega imagem do disco.
     */
    fun loadInitialFrame(filePath: String, targetWidth: Int = 320, targetHeight: Int = 180): Bitmap? {
        val file = File(filePath)
        if (!file.exists()) return null

        return if (isVideo(filePath)) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(file.absolutePath)
                retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            } catch (_: Exception) {
                null
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }
        } else {
            try {
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, options)

                var sampleSize = 1
                while (options.outWidth / (sampleSize * 2) >= targetWidth &&
                    options.outHeight / (sampleSize * 2) >= targetHeight
                ) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
            } catch (_: Exception) {
                null
            }
        }
    }
}
