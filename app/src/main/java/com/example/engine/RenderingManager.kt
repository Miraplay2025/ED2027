package com.example.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class RenderingState(
    val isRunning: Boolean = false,
    val progressPercent: Int = 0,
    val currentImageIndex: Int = 0,
    val totalImages: Int = 0,
    val remainingImages: Int = 0,
    val currentMovementName: String = "",
    val logs: List<String> = emptyList(),
    val isCompleted: Boolean = false,
    val isCancelled: Boolean = false,
    val errorMessage: String? = null,
    val outputFiles: List<String> = emptyList()
)

object RenderingManager {

    private val _state = MutableStateFlow(RenderingState())
    val state: StateFlow<RenderingState> = _state.asStateFlow()

    @Volatile
    var isCancelRequested: Boolean = false
        private set

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun startBatch(totalImages: Int) {
        isCancelRequested = false
        val initialLog = "[${currentTime()}] Iniciando lote de $totalImages imagens..."
        _state.value = RenderingState(
            isRunning = true,
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

    fun addOutputFile(filePath: String) {
        val current = _state.value.outputFiles
        _state.value = _state.value.copy(outputFiles = current + filePath)
    }

    fun completeBatch() {
        log("Processamento do lote concluído com sucesso!")
        _state.value = _state.value.copy(
            isRunning = false,
            progressPercent = 100,
            remainingImages = 0,
            isCompleted = true
        )
    }

    fun cancel() {
        isCancelRequested = true
        log("Cancelamento solicitado pelo usuário. Interrompendo processamento...")
        _state.value = _state.value.copy(
            isRunning = false,
            isCancelled = true
        )
    }

    fun fail(errorMsg: String) {
        log("Erro durante a renderização: $errorMsg")
        _state.value = _state.value.copy(
            isRunning = false,
            errorMessage = errorMsg
        )
    }

    fun reset() {
        isCancelRequested = false
        _state.value = RenderingState()
    }

    private fun currentTime(): String = timeFormat.format(Date())
}
