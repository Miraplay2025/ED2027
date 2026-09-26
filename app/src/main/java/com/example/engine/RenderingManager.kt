package com.example.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class Stage2UserDecision {
    data object IgnoreSubtitles : Stage2UserDecision()
    data object ApplyAnywayValidOnly : Stage2UserDecision()
    data class RetryWithCorrectedSubtitles(val correctedText: String) : Stage2UserDecision()
}

data class RenderingState(
    val isRunning: Boolean = false,
    val currentStage: Int = 1,
    val progressPercent: Int = 0,
    val currentImageIndex: Int = 0,
    val totalImages: Int = 0,
    val remainingImages: Int = 0,
    val currentMovementName: String = "",
    val logs: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val errorMessage: String? = null,
    val outputFiles: List<String> = emptyList(),
    val isPausedForSubtitleWarning: Boolean = false,
    val subtitleWarningMessage: String? = null,
    val invalidSubtitleTimesLabel: String? = null,
    val finalVideoDurationFormatted: String? = null,
    val pendingSubtitlesText: String = "",
    val subtitleCorrectionError: String? = null
)

object RenderingManager {

    private val _state = MutableStateFlow(RenderingState())
    val state: StateFlow<RenderingState> = _state.asStateFlow()

    @Volatile
    var isCancelRequested: Boolean = false
        private set

    @Volatile
    private var stage2DecisionDeferred: CompletableDeferred<Stage2UserDecision>? = null

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun startBatch(totalImages: Int) {
        isCancelRequested = false
        stage2DecisionDeferred?.cancel()
        stage2DecisionDeferred = null
        val initialLog = "[${currentTime()}] Iniciando Etapa 1: Lote de $totalImages mídias..."
        _state.value = RenderingState(
            isRunning = true,
            currentStage = 1,
            progressPercent = 0,
            currentImageIndex = 0,
            totalImages = totalImages,
            remainingImages = totalImages,
            logs = listOf(initialLog),
            isCompleted = false,
            isCancelled = false,
            errorMessage = null,
            outputFiles = emptyList()
        )
    }

    fun log(message: String) {
        val entry = if (message.startsWith("[")) message else "[${currentTime()}] $message"
        val currentLogs = _state.value.logs
        _state.value = _state.value.copy(logs = currentLogs + entry)
    }

    fun setStage(stage: Int, statusText: String) {
        _state.value = _state.value.copy(
            currentStage = stage,
            currentMovementName = statusText
        )
    }

    fun updateProgress(
        currentImageIndex: Int,
        totalImages: Int,
        currentMovementName: String,
        overallPercent: Int
    ) {
        val remaining = (totalImages - currentImageIndex).coerceAtLeast(0)
        _state.value = _state.value.copy(
            currentImageIndex = currentImageIndex,
            totalImages = totalImages,
            remainingImages = remaining,
            currentMovementName = currentMovementName,
            progressPercent = overallPercent.coerceIn(0, 100)
        )
    }

    suspend fun pauseAndAwaitStage2SubtitleDecision(
        warningMessage: String,
        invalidTimesLabel: String,
        videoDurationFormatted: String,
        currentSubtitlesText: String,
        correctionError: String? = null
    ): Stage2UserDecision {
        val deferred = CompletableDeferred<Stage2UserDecision>()
        stage2DecisionDeferred = deferred
        log("PAUSA ETAPA 2: $warningMessage")
        _state.value = _state.value.copy(
            currentStage = 2,
            isPausedForSubtitleWarning = true,
            subtitleWarningMessage = warningMessage,
            invalidSubtitleTimesLabel = invalidTimesLabel,
            finalVideoDurationFormatted = videoDurationFormatted,
            pendingSubtitlesText = currentSubtitlesText,
            subtitleCorrectionError = correctionError
        )
        val decision = deferred.await()
        _state.value = _state.value.copy(
            isPausedForSubtitleWarning = false,
            subtitleWarningMessage = null,
            invalidSubtitleTimesLabel = null,
            subtitleCorrectionError = null
        )
        return decision
    }

    fun submitStage2Decision(decision: Stage2UserDecision) {
        stage2DecisionDeferred?.complete(decision)
    }

    fun addOutputFile(filePath: String) {
        val current = _state.value.outputFiles
        _state.value = _state.value.copy(outputFiles = current + filePath)
    }

    fun completeBatch() {
        log("Processamento concluído com sucesso!")
        _state.value = _state.value.copy(
            isRunning = false,
            isPausedForSubtitleWarning = false,
            progressPercent = 100,
            remainingImages = 0,
            isCompleted = true
        )
    }

    fun cancel() {
        isCancelRequested = true
        stage2DecisionDeferred?.cancel()
        stage2DecisionDeferred = null
        log("Cancelamento solicitado pelo usuário. Interrompendo processamento...")
        _state.value = _state.value.copy(
            isRunning = false,
            isPausedForSubtitleWarning = false,
            isCancelled = true
        )
    }

    fun fail(errorMsg: String) {
        stage2DecisionDeferred?.cancel()
        stage2DecisionDeferred = null
        log("Erro durante a renderização: $errorMsg")
        _state.value = _state.value.copy(
            isRunning = false,
            isPausedForSubtitleWarning = false,
            errorMessage = errorMsg
        )
    }

    fun reset() {
        isCancelRequested = false
        stage2DecisionDeferred?.cancel()
        stage2DecisionDeferred = null
        _state.value = RenderingState()
    }

    private fun currentTime(): String = timeFormat.format(Date())
}
