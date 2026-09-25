package com.example.engine

import com.example.data.model.ParsedAnimationConfig

sealed class SyntaxParseResult {
    data class Success(val configs: List<ParsedAnimationConfig>) : SyntaxParseResult()
    data class Error(
        val message: String,
        val faultySnippet: String? = null
    ) : SyntaxParseResult()
}

sealed class TransitionValidationResult {
    data class Success(val transitionIds: List<Int>) : TransitionValidationResult()
    data class Error(
        val message: String,
        val faultyId: String? = null
    ) : TransitionValidationResult()
}

sealed class TransitionSoundValidationResult {
    data class Success(val soundIds: List<Int>) : TransitionSoundValidationResult()
    data class Error(
        val message: String,
        val faultyId: String? = null
    ) : TransitionSoundValidationResult()
}

data class RandomPromptResult(
    val movementSyntaxText: String,
    val transitionIdsText: String,
    val transitionSoundIdsText: String = "1, 5, 2, 7"
)

object SyntaxParser {

    /**
     * Valida e interpreta a sintaxe textual fornecida pelo usuário.
     * Exemplo de formato:
     * "IMAGEM 1 + MOVIMENTO 1 + 6.0s, MIDIA 2 + MOVIMENTO 0 + 4.0s"
     *
     * @param text O texto digitado pelo usuário.
     * @param totalProjectImages Quantidade total de mídias cadastradas no projeto.
     * @param videoMediaIndices Conjunto de índices (1-indexed) de mídias que são vídeos.
     */
    fun parseAndValidate(
        text: String,
        totalProjectImages: Int,
        videoMediaIndices: Set<Int> = emptySet()
    ): SyntaxParseResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return SyntaxParseResult.Error(
                "Campo de animação está vazio"
            )
        }

        if (totalProjectImages == 0) {
            return SyntaxParseResult.Error(
                "Adicione mídias ao projeto primeiro"
            )
        }

        // Separa itens por vírgula ou por quebra de linha
        val rawTokens = trimmed
            .split(Regex("[,;\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawTokens.isEmpty()) {
            return SyntaxParseResult.Error(
                "Nenhum comando válido foi informado"
            )
        }

        val parsedConfigs = mutableListOf<ParsedAnimationConfig>()
        val seenImageIndices = mutableSetOf<Int>()

        for (token in rawTokens) {
            val parts = token.split("+").map { it.trim() }
            if (parts.size != 3) {
                return SyntaxParseResult.Error(
                    message = "Formato inválido no comando informado",
                    faultySnippet = token
                )
            }

            val imagePart = parts[0]
            val movementPart = parts[1]
            val durationPart = parts[2]

            // 1. Validação de MÍDIA / IMAGEM
            val imageMatch = Regex("(?i)(?:IMAGEM|M[IÍ]DIA|VIDEO)?\\s*(\\d+)").matchEntire(imagePart)
            if (imageMatch == null) {
                return SyntaxParseResult.Error(
                    message = "Identificador de mídia é inválido",
                    faultySnippet = token
                )
            }
            val imageIndex = imageMatch.groupValues[1].toIntOrNull()
            if (imageIndex == null || imageIndex <= 0) {
                return SyntaxParseResult.Error(
                    message = "Número de imagem é inválido",
                    faultySnippet = token
                )
            }

            if (imageIndex > totalProjectImages) {
                return SyntaxParseResult.Error(
                    message = "Mídia $imageIndex não existe no projeto",
                    faultySnippet = token
                )
            }

            if (seenImageIndices.contains(imageIndex)) {
                return SyntaxParseResult.Error(
                    message = "Mídia $imageIndex foi configurada duplicada",
                    faultySnippet = token
                )
            }
            seenImageIndices.add(imageIndex)

            // 2. Validação de MOVIMENTO
            val movementMatch = Regex("(?i)MOVIMENTO?\\s*(\\d+)").matchEntire(movementPart)
            if (movementMatch == null) {
                return SyntaxParseResult.Error(
                    message = "Formato de movimento é inválido",
                    faultySnippet = token
                )
            }
            val movementId = movementMatch.groupValues[1].toIntOrNull()
            if (movementId == null) {
                return SyntaxParseResult.Error(
                    message = "Número de movimento é inválido",
                    faultySnippet = token
                )
            }

            if (movementId !in 0..com.example.data.model.MovementEffect.MAX_ID) {
                return SyntaxParseResult.Error(
                    message = "MOVIMENTO $movementId é inexistente",
                    faultySnippet = token
                )
            }

            // REGRA: Vídeos NÃO DEVEM suportar aplicação de animação de movimento!
            if (videoMediaIndices.contains(imageIndex) && movementId != 0) {
                return SyntaxParseResult.Error(
                    message = "Vídeos não suportam animação dinâmica",
                    faultySnippet = token
                )
            }

            // 3. Validação de DURAÇÃO (deve ter 's' no final)
            if (!durationPart.endsWith("s", ignoreCase = true)) {
                return SyntaxParseResult.Error(
                    message = "Duração deve terminar com s",
                    faultySnippet = token
                )
            }

            val durationNumberStr = durationPart.dropLast(1).trim().replace(',', '.')
            val durationVal = durationNumberStr.toFloatOrNull()
            if (durationVal == null || durationVal <= 0.1f) {
                return SyntaxParseResult.Error(
                    message = "Tempo de duração é inválido",
                    faultySnippet = token
                )
            }
            if (durationVal > 12.0f) {
                return SyntaxParseResult.Error(
                    message = "Duração máxima permitida é 12s",
                    faultySnippet = token
                )
            }

            parsedConfigs.add(
                ParsedAnimationConfig(
                    imageIndex = imageIndex,
                    movementId = movementId,
                    durationSeconds = durationVal,
                    rawText = token
                )
            )
        }

        // Validação de Cobertura Total: TODAS as mídias devem ter uma linha configurada
        if (parsedConfigs.size != totalProjectImages) {
            return SyntaxParseResult.Error(
                message = "Erro: apenas ${parsedConfigs.size} foram configuradas"
            )
        }

        val sortedConfigs = parsedConfigs.sortedBy { it.imageIndex }
        return SyntaxParseResult.Success(sortedConfigs)
    }

    /**
     * Gera texto padrão de sintaxe para facilitar a inicialização pelo usuário.
     */
    fun generateDefaultSyntax(
        totalImages: Int,
        defaultMovementId: Int = 1,
        defaultDurationSeconds: Float = 6.0f,
        videoMediaIndices: Set<Int> = emptySet()
    ): String {
        if (totalImages <= 0) return ""
        return (1..totalImages).joinToString(",\n") { index ->
            val mov = if (videoMediaIndices.contains(index)) {
                0 // Vídeo sempre começa com 0
            } else if (defaultMovementId == 1) {
                ((index - 1) % 10) + 1
            } else {
                defaultMovementId
            }
            val dur = String.format(java.util.Locale.US, "%.1fs", defaultDurationSeconds.coerceAtMost(12.0f))
            "IMAGEM $index + MOVIMENTO $mov + $dur"
        }
    }

    /**
     * Valida os IDs de transições (1 a 20, ou 0).
     */
    fun validateTransitionIds(text: String): TransitionValidationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return TransitionValidationResult.Error(
                "Informe os IDs das transições"
            )
        }

        val tokens = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return TransitionValidationResult.Error(
                "Informe os IDs das transições"
            )
        }

        val validIds = mutableListOf<Int>()
        for (token in tokens) {
            val id = token.toIntOrNull()
            if (id == null) {
                return TransitionValidationResult.Error(
                    message = "ID de transição é inválido",
                    faultyId = token
                )
            }
            if (id !in 0..20) {
                return TransitionValidationResult.Error(
                    message = "Transição deve ser entre 0-20",
                    faultyId = token
                )
            }
            validIds.add(id)
        }

        return TransitionValidationResult.Success(validIds)
    }

    /**
     * Valida os IDs de sons de transições fornecidos pelo usuário separados por vírgula.
     */
    fun validateTransitionSoundIds(
        text: String,
        availableSoundIds: Set<Int> = (0..com.example.data.model.TransitionSoundEffect.MAX_BUILT_IN_ID).toSet()
    ): TransitionSoundValidationResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return TransitionSoundValidationResult.Error(
                "Informe os IDs dos sons"
            )
        }

        val tokens = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return TransitionSoundValidationResult.Error(
                "Informe os IDs dos sons"
            )
        }

        val validIds = mutableListOf<Int>()
        for (token in tokens) {
            val id = token.toIntOrNull()
            if (id == null) {
                return TransitionSoundValidationResult.Error(
                    message = "ID de som é inválido",
                    faultyId = token
                )
            }
            if (id !in availableSoundIds && id != 0) {
                return TransitionSoundValidationResult.Error(
                    message = "ID de som não existe",
                    faultyId = token
                )
            }
            validIds.add(id)
        }

        return TransitionSoundValidationResult.Success(validIds)
    }

    /**
     * Gera prompts totalmente aleatórios com movimentos, durações (máximo 12 segundos), transições e sons.
     */
    fun generateRandomPrompts(
        totalImages: Int,
        videoMediaIndices: Set<Int> = emptySet(),
        availableSoundIds: List<Int> = (1..com.example.data.model.TransitionSoundEffect.MAX_BUILT_IN_ID).toList()
    ): RandomPromptResult {
        if (totalImages <= 0) {
            return RandomPromptResult(
                movementSyntaxText = "",
                transitionIdsText = "1, 4, 2, 8",
                transitionSoundIdsText = "1, 5, 13, 15"
            )
        }

        val random = java.util.Random()
        val syntaxLines = (1..totalImages).map { index ->
            val randomMov = if (videoMediaIndices.contains(index)) {
                0
            } else {
                random.nextInt(com.example.data.model.MovementEffect.MAX_ID + 1)
            }
            // Duração aleatória de cada mídia com máximo de 12.0 segundos (entre 3.0s e 12.0s)
            val randomDuration = (3.0f + (random.nextInt(91) / 10.0f)).coerceAtMost(12.0f)
            val durFormatted = String.format(java.util.Locale.US, "%.1fs", randomDuration)
            "IMAGEM $index + MOVIMENTO $randomMov + $durFormatted"
        }

        val transitionCount = (totalImages - 1).coerceAtLeast(1)
        val randomTransitionIds = (1..transitionCount).map {
            random.nextInt(20) + 1
        }

        val poolSounds = if (availableSoundIds.isEmpty()) {
            (1..com.example.data.model.TransitionSoundEffect.MAX_BUILT_IN_ID).toList()
        } else {
            availableSoundIds
        }
        val randomSoundIds = (1..transitionCount).map {
            poolSounds[random.nextInt(poolSounds.size)]
        }

        return RandomPromptResult(
            movementSyntaxText = syntaxLines.joinToString(",\n"),
            transitionIdsText = randomTransitionIds.joinToString(", "),
            transitionSoundIdsText = randomSoundIds.joinToString(", ")
        )
    }
}
