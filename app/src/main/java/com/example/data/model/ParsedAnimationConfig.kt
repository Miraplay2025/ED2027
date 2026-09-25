package com.example.data.model

data class ParsedAnimationConfig(
    val imageIndex: Int, // 1-based (e.g. 1 for IMAGEM 1)
    val movementId: Int, // 0 to 10
    val durationSeconds: Float, // e.g. 6.0f
    val rawText: String
)
