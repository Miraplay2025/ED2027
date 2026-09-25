package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.documentfile.provider.DocumentFile
import com.example.MainActivity
import com.example.R
import com.example.data.db.AppDatabase
import com.example.data.local.AppPreferences
import com.example.data.model.MovementEffect
import com.example.data.model.ParsedAnimationConfig
import com.example.data.model.TransitionEffect
import com.example.engine.RenderSequenceItem
import com.example.engine.RenderingManager
import com.example.engine.VideoEncoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class VideoRenderingService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "channel_app_animador_render"

    private var isNotificationHiddenByUser = false
    private var lastNotificationContent = "Renderizando vídeo..."
    private var lastCurrent = 0
    private var lastTotal = 1
    private var lastPercent = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        when (intent.action) {
            ACTION_CANCEL -> {
                RenderingManager.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DISMISS_NOTIFICATION -> {
                isNotificationHiddenByUser = true
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(NOTIFICATION_ID)
                if (!RenderingManager.state.value.isRunning) {
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_APP_FOREGROUND -> {
                isAppInForeground = true
                return START_NOT_STICKY
            }
            ACTION_APP_BACKGROUND -> {
                isAppInForeground = false
                if (RenderingManager.state.value.isRunning && !isNotificationHiddenByUser) {
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    manager.notify(
                        NOTIFICATION_ID,
                        buildNotification(lastNotificationContent, lastCurrent, lastTotal, lastPercent)
                    )
                }
                return START_NOT_STICKY
            }
        }

        val projectId = intent.getLongExtra(EXTRA_PROJECT_ID, -1L)
        val configs = intent.getParcelableArrayListExtra<RenderConfigParcel>(EXTRA_CONFIGS)
        val customOutputDirUri = intent.getStringExtra(EXTRA_CUSTOM_DIR_URI)
        val videoWidth = intent.getIntExtra(EXTRA_VIDEO_WIDTH, 1280)
        val videoHeight = intent.getIntExtra(EXTRA_VIDEO_HEIGHT, 720)
        val videoFps = intent.getIntExtra(EXTRA_VIDEO_FPS, 30)
        val videoBitrate = intent.getIntExtra(EXTRA_VIDEO_BITRATE, 5_000_000)
        val resolutionLabel = intent.getStringExtra(EXTRA_RESOLUTION_LABEL) ?: "${videoWidth}x${videoHeight}"
        val transitionIds = intent.getIntegerArrayListExtra(EXTRA_TRANSITION_IDS) ?: arrayListOf(1)
        val transitionSoundIds = intent.getIntegerArrayListExtra(EXTRA_TRANSITION_SOUND_IDS) ?: arrayListOf<Int>()
        val transitionDurationSeconds = intent.getFloatExtra(EXTRA_TRANSITION_DURATION, 1.0f).coerceIn(0.4f, 6.0f)

        if (projectId == -1L || configs.isNullOrEmpty()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Garante que o diretório padrão exista fisicamente antes de iniciar qualquer renderização
        AppPreferences.getInstance(applicationContext).ensureDefaultDirectory()

        isNotificationHiddenByUser = false
        startForeground(NOTIFICATION_ID, buildNotification("Iniciando renderização de vídeo unificado...", 0, configs.size, 0))

        serviceScope.launch {
            processBatch(
                projectId = projectId,
                configs = configs,
                transitionIds = transitionIds,
                transitionSoundIds = transitionSoundIds,
                transitionDurationSeconds = transitionDurationSeconds,
                customOutputDirUri = customOutputDirUri,
                videoWidth = videoWidth,
                videoHeight = videoHeight,
                videoFps = videoFps,
                videoBitrate = videoBitrate,
                resolutionLabel = resolutionLabel
            )
        }

        return START_NOT_STICKY
    }

    private suspend fun processBatch(
        projectId: Long,
        configs: List<RenderConfigParcel>,
        transitionIds: List<Int>,
        transitionSoundIds: List<Int>,
        transitionDurationSeconds: Float,
        customOutputDirUri: String?,
        videoWidth: Int,
        videoHeight: Int,
        videoFps: Int,
        videoBitrate: Int,
        resolutionLabel: String
    ) {
        val totalImages = configs.size
        RenderingManager.startBatch(totalImages)

        val bitrateFormatted = String.format(java.util.Locale.US, "%.1f Mbps", videoBitrate / 1_000_000f)
        val transDurFormatted = String.format(java.util.Locale.US, "%.1fs", transitionDurationSeconds)
        RenderingManager.log("Iniciando Exportação de Vídeo Único: $resolutionLabel | $videoFps FPS | $bitrateFormatted")
        if (transitionIds.isNotEmpty()) {
            RenderingManager.log("Transições configuradas: [${transitionIds.joinToString(", ")}] • Duração: $transDurFormatted")
        }

        val db = AppDatabase.getInstance(applicationContext)
        val images = db.projectDao().getImagesForProjectSync(projectId)
        val imagesByOrder = images.associateBy { it.orderIndex }

        // Diretório padrão conforme requisito 7: /Movies/AppAnimador/ ou preferência persistida
        val effectiveOutputDirUri = customOutputDirUri ?: AppPreferences.getInstance(applicationContext).getDefaultOutputDirUri()
        val defaultOutputDir = AppPreferences.getInstance(applicationContext).ensureDefaultDirectory()

        // Constrói os itens da sequência completa de vídeo unificado
        val soundPool = if (transitionSoundIds.isNotEmpty()) transitionSoundIds else listOf(0)
        val sequenceItems = mutableListOf<RenderSequenceItem>()
        for ((index, item) in configs.withIndex()) {
            val projectImage = imagesByOrder[item.imageIndex]
            if (projectImage == null) {
                RenderingManager.log("Aviso: Mídia #${item.imageIndex} não encontrada no banco.")
                continue
            }

            val imageFile = File(projectImage.filePath)
            if (!imageFile.exists()) {
                RenderingManager.log("Aviso: Arquivo físico não encontrado em ${imageFile.absolutePath}")
                continue
            }

            val effect = MovementEffect.findById(item.movementId) ?: MovementEffect.ALL_EFFECTS[0]
            val transId = if (transitionIds.isNotEmpty()) {
                transitionIds[index % transitionIds.size]
            } else {
                1
            }
            val transEffect = TransitionEffect.getOrCut(transId)
            val hasNext = index < configs.size - 1
            val chosenSoundId = if (hasNext && soundPool.isNotEmpty()) soundPool.random() else 0

            sequenceItems.add(
                RenderSequenceItem(
                    imageFile = imageFile,
                    movementEffect = effect,
                    durationSeconds = item.durationSeconds,
                    transitionToNext = transEffect,
                    transitionSoundIdToNext = chosenSoundId
                )
            )
        }

        if (sequenceItems.isEmpty()) {
            RenderingManager.log("Nenhuma imagem válida encontrada para gerar o vídeo.")
            RenderingManager.completeBatch()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        RenderingManager.log("Processando sequência de ${sequenceItems.size} cenas em um único arquivo de vídeo...")

        val tempOutputFile = File(cacheDir, "video_unificado_${System.currentTimeMillis()}.mp4")

        val success = VideoEncoder.encodeUnifiedSequenceToVideo(
            sequence = sequenceItems,
            outputFile = tempOutputFile,
            targetWidth = videoWidth,
            targetHeight = videoHeight,
            frameRate = videoFps,
            bitRate = videoBitrate,
            transitionDurationSeconds = transitionDurationSeconds,
            onGlobalProgress = { currentFrame, totalFrames, currentImgIndex, statusText ->
                val overallPercent = ((currentFrame.toFloat() / totalFrames) * 100).toInt().coerceIn(0, 100)
                updateNotification(
                    "Exportando vídeo único ($overallPercent%)",
                    currentImgIndex + 1,
                    sequenceItems.size,
                    overallPercent
                )
                RenderingManager.updateProgress(
                    currentImageIndex = currentImgIndex + 1,
                    totalImages = sequenceItems.size,
                    currentMovementName = statusText,
                    overallPercent = overallPercent
                )
            },
            isCancelled = { RenderingManager.isCancelRequested }
        )

        if (success && tempOutputFile.exists() && tempOutputFile.length() > 0) {
            val fileName = "video_completo_animado_${System.currentTimeMillis()}.mp4"
            val savedLocation = saveVideoToDestination(
                tempFile = tempOutputFile,
                fileName = fileName,
                customOutputDirUri = effectiveOutputDirUri,
                defaultDir = defaultOutputDir
            )

            RenderingManager.addOutputFile(savedLocation)
            RenderingManager.log("VÍDEO ÚNICO FINAL gerado com sucesso!")
            RenderingManager.log("Salvo em: $savedLocation")
            tempOutputFile.delete()
        } else {
            if (RenderingManager.isCancelRequested) {
                RenderingManager.log("Renderização cancelada pelo usuário.")
            } else {
                RenderingManager.log("Falha na renderização do vídeo único.")
            }
            tempOutputFile.delete()
        }

        if (RenderingManager.isCancelRequested) {
            updateNotification("Renderização cancelada", 0, totalImages, 100)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Marca projeto com data do último render
        val project = db.projectDao().getProjectSync(projectId)
        if (project != null) {
            db.projectDao().updateProject(project.copy(lastRenderedAt = System.currentTimeMillis()))
        }

        RenderingManager.completeBatch()
        stopForeground(STOP_FOREGROUND_DETACH)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildSuccessNotification("Vídeo final renderizado e salvo com sucesso!"))
        stopSelf()
    }

    private fun saveVideoToDestination(
        tempFile: File,
        fileName: String,
        customOutputDirUri: String?,
        defaultDir: File
    ): String {
        // Se usuário definiu pasta personalizada via SAF
        if (!customOutputDirUri.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customOutputDirUri)
                val pickedDir = DocumentFile.fromTreeUri(applicationContext, treeUri)
                if (pickedDir != null && pickedDir.canWrite()) {
                    var targetDoc = pickedDir.findFile(fileName)
                    if (targetDoc != null) {
                        targetDoc.delete()
                    }
                    targetDoc = pickedDir.createFile("video/mp4", fileName)
                    if (targetDoc != null) {
                        contentResolver.openOutputStream(targetDoc.uri)?.use { outStream ->
                            FileInputStream(tempFile).use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                        return targetDoc.uri.toString()
                    }
                }
            } catch (e: Exception) {
                RenderingManager.log("Aviso: Falha ao salvar no diretório SAF personalizado: ${e.message}. Salvando no diretório padrão.")
            }
        }

        // Diretório padrão: /Movies/AppAnimador/
        try {
            defaultDir.mkdirs()
            val destinationFile = File(defaultDir, fileName)
            tempFile.copyTo(destinationFile, overwrite = true)
            MediaScannerConnection.scanFile(
                applicationContext,
                arrayOf(destinationFile.absolutePath),
                arrayOf("video/mp4"),
                null
            )
            return destinationFile.absolutePath
        } catch (e: Exception) {
            // Em versões de Android com Scoped Storage estrito (MediaStore insert)
            return try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AppAnimador")
                    }
                }
                val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    contentResolver.openOutputStream(uri)?.use { outStream ->
                        FileInputStream(tempFile).use { inStream ->
                            inStream.copyTo(outStream)
                        }
                    }
                    uri.toString()
                } else {
                    val appMovies = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AppAnimador").apply { mkdirs() }
                    val fb = File(appMovies, fileName)
                    tempFile.copyTo(fb, overwrite = true)
                    fb.absolutePath
                }
            } catch (_: Exception) {
                val appMovies = File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "AppAnimador").apply { mkdirs() }
                val fb = File(appMovies, fileName)
                tempFile.copyTo(fb, overwrite = true)
                fb.absolutePath
            }
        }
    }

    private fun buildNotification(
        content: String,
        current: Int,
        total: Int,
        percent: Int
    ): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(this, VideoRenderingService::class.java).apply {
            action = ACTION_CANCEL
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, VideoRenderingService::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Editor Automático — Renderizando Vídeo")
            .setContentText(content)
            .setSubText("$percent%")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Parar", cancelPendingIntent)
            .addAction(android.R.drawable.ic_menu_view, "Ocultar", dismissPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun buildSuccessNotification(content: String): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(this, VideoRenderingService::class.java).apply {
            action = ACTION_DISMISS_NOTIFICATION
        }
        val dismissPendingIntent = PendingIntent.getService(
            this,
            2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Oculta a barra de processamento (setProgress(0, 0, false)) e exibe o texto de sucesso
        // com o botão X ao lado para clicar e fechar a notificação
        val customRemoteViews = android.widget.RemoteViews(packageName, R.layout.notification_render_complete).apply {
            setTextViewText(R.id.txt_notification_title, "100% Concluído — Vídeo Salvo!")
            setTextViewText(R.id.txt_notification_message, content)
            setOnClickPendingIntent(R.id.btn_close_notification, dismissPendingIntent)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("100% Concluído — Vídeo Salvo!")
            .setContentText(content)
            .setProgress(0, 0, false)
            .setCustomContentView(customRemoteViews)
            .setCustomBigContentView(customRemoteViews)
            .setOngoing(false)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "✕ Fechar", dismissPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun updateNotification(content: String, current: Int, total: Int, percent: Int) {
        lastNotificationContent = content
        lastCurrent = current
        lastTotal = total
        lastPercent = percent

        if (!isNotificationHiddenByUser) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification(content, current, total, percent))
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Renderização de Vídeo",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Progresso contínuo de renderização de vídeos em segundo plano"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val ACTION_START = "com.example.service.action.START"
        const val ACTION_CANCEL = "com.example.service.action.CANCEL"
        const val ACTION_DISMISS_NOTIFICATION = "com.example.service.action.DISMISS_NOTIFICATION"
        const val ACTION_APP_FOREGROUND = "com.example.service.action.APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "com.example.service.action.APP_BACKGROUND"

        const val EXTRA_PROJECT_ID = "extra_project_id"
        const val EXTRA_CONFIGS = "extra_configs"
        const val EXTRA_CUSTOM_DIR_URI = "extra_custom_dir_uri"
        const val EXTRA_VIDEO_WIDTH = "extra_video_width"
        const val EXTRA_VIDEO_HEIGHT = "extra_video_height"
        const val EXTRA_VIDEO_FPS = "extra_video_fps"
        const val EXTRA_VIDEO_BITRATE = "extra_video_bitrate"
        const val EXTRA_RESOLUTION_LABEL = "extra_resolution_label"
        const val EXTRA_TRANSITION_IDS = "extra_transition_ids"
        const val EXTRA_TRANSITION_SOUND_IDS = "extra_transition_sound_ids"
        const val EXTRA_TRANSITION_DURATION = "extra_transition_duration"

        @Volatile
        var isAppInForeground: Boolean = false

        fun notifyAppForeground(context: Context) {
            isAppInForeground = true
            try {
                val intent = Intent(context, VideoRenderingService::class.java).apply {
                    action = ACTION_APP_FOREGROUND
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun notifyAppBackground(context: Context) {
            isAppInForeground = false
            try {
                val intent = Intent(context, VideoRenderingService::class.java).apply {
                    action = ACTION_APP_BACKGROUND
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun start(
            context: Context,
            projectId: Long,
            configs: List<ParsedAnimationConfig>,
            customDirUri: String?,
            videoWidth: Int = 1280,
            videoHeight: Int = 720,
            videoFps: Int = 30,
            videoBitrateBps: Int = 5_000_000,
            resolutionLabel: String = "720p (1280x720) [HD]",
            transitionIds: List<Int> = listOf(1),
            transitionSoundIds: List<Int> = emptyList(),
            transitionDurationSeconds: Float = 1.0f
        ) {
            val parcelList = ArrayList(configs.map {
                RenderConfigParcel(it.imageIndex, it.movementId, it.durationSeconds)
            })
            val intent = Intent(context, VideoRenderingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_ID, projectId)
                putParcelableArrayListExtra(EXTRA_CONFIGS, parcelList)
                putIntegerArrayListExtra(EXTRA_TRANSITION_IDS, ArrayList(transitionIds))
                putIntegerArrayListExtra(EXTRA_TRANSITION_SOUND_IDS, ArrayList(transitionSoundIds))
                putExtra(EXTRA_TRANSITION_DURATION, transitionDurationSeconds.coerceIn(0.4f, 6.0f))
                putExtra(EXTRA_CUSTOM_DIR_URI, customDirUri)
                putExtra(EXTRA_VIDEO_WIDTH, videoWidth)
                putExtra(EXTRA_VIDEO_HEIGHT, videoHeight)
                putExtra(EXTRA_VIDEO_FPS, videoFps)
                putExtra(EXTRA_VIDEO_BITRATE, videoBitrateBps)
                putExtra(EXTRA_RESOLUTION_LABEL, resolutionLabel)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun dismissNotification(context: Context) {
            try {
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.cancel(1001)
                val intent = Intent(context, VideoRenderingService::class.java).apply {
                    action = ACTION_DISMISS_NOTIFICATION
                }
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun cancel(context: Context) {
            val intent = Intent(context, VideoRenderingService::class.java).apply {
                action = ACTION_CANCEL
            }
            context.startService(intent)
        }
    }
}
