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
                "A caixa de texto de sintaxe está vazia. Por favor configure as instruções para as mídias."
            )
        }

        if (totalProjectImages == 0) {
            return SyntaxParseResult.Error(
                "O projeto não possui mídias importadas. Adicione fotos ou vídeos antes de configurar a sintaxe."
            )
        }

        // Separa itens por vírgula ou por quebra de linha
        val rawTokens = trimmed
            .split(Regex("[,;\\n]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (rawTokens.isEmpty()) {
            return SyntaxParseResult.Error(
                "Nenhum comando detectado na sintaxe informada."
            )
        }

        val parsedConfigs = mutableListOf<ParsedAnimationConfig>()
        val seenImageIndices = mutableSetOf<Int>()

        for (token in rawTokens) {
            val parts = token.split("+").map { it.trim() }
            if (parts.size != 3) {
                return SyntaxParseResult.Error(
                    message = "Formato inválido no trecho: '$token'. O formato exigido é: IMAGEM X + MOVIMENTO Y + Z.Zs",
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
                    message = "Identificador de mídia inválido em '$imagePart'. Esperado: 'IMAGEM X' (ex: IMAGEM 1).",
                    faultySnippet = token
                )
            }
            val imageIndex = imageMatch.groupValues[1].toIntOrNull()
            if (imageIndex == null || imageIndex <= 0) {
                return SyntaxParseResult.Error(
                    message = "Número de imagem inválido em '$imagePart'.",
                    faultySnippet = token
                )
            }

            if (imageIndex > totalProjectImages) {
                return SyntaxParseResult.Error(
                    message = "Validação de Existência falhou: Mídia $imageIndex não existe no projeto. O projeto possui apenas $totalProjectImages mídia(s).",
                    faultySnippet = token
                )
            }

            if (seenImageIndices.contains(imageIndex)) {
                return SyntaxParseResult.Error(
                    message = "Validação de Duplicidade falhou: Mídia $imageIndex foi configurada mais de uma vez.",
                    faultySnippet = token
                )
            }
            seenImageIndices.add(imageIndex)

            // 2. Validação de MOVIMENTO
            val movementMatch = Regex("(?i)MOVIMENTO?\\s*(\\d+)").matchEntire(movementPart)
            if (movementMatch == null) {
                return SyntaxParseResult.Error(
                    message = "Identificador de movimento inválido em '$movementPart'. Esperado: 'MOVIMENTO Y' (ex: MOVIMENTO 1).",
                    faultySnippet = token
                )
            }
            val movementId = movementMatch.groupValues[1].toIntOrNull()
            if (movementId == null) {
                return SyntaxParseResult.Error(
                    message = "Número de movimento inválido em '$movementPart'.",
                    faultySnippet = token
                )
            }

            if (movementId !in 0..com.example.data.model.MovementEffect.MAX_ID) {
                return SyntaxParseResult.Error(
                    message = "Validação de Existência de Movimento falhou: MOVIMENTO $movementId é inexistente. Só existem animações de 0 a ${com.example.data.model.MovementEffect.MAX_ID}.",
                    faultySnippet = token
                )
            }

            // REGRA: Vídeos NÃO DEVEM suportar aplicação de animação de movimento!
            if (videoMediaIndices.contains(imageIndex) && movementId != 0) {
                return SyntaxParseResult.Error(
                    message = "Vídeos não suportam aplicação de animação de movimento: A Mídia $imageIndex é um arquivo de vídeo e foi configurada com MOVIMENTO $movementId. Altere para 'MOVIMENTO 0' (estático) para esta mídia.",
                    faultySnippet = token
                )
            }

            // 3. Validação de DURAÇÃO (deve ter 's' no final)
            if (!durationPart.endsWith("s", ignoreCase = true)) {
                return SyntaxParseResult.Error(
                    message = "Validação de Duração falhou na Mídia $imageIndex: o valor '$durationPart' deve conter o sufixo 's' (ex: 4.0s ou 6.0s).",
                    faultySnippet = token
                )
            }

            val durationNumberStr = durationPart.dropLast(1).trim()
            val durationVal = durationNumberStr.toFloatOrNull()
            if (durationVal == null || durationVal <= 0.1f) {
                return SyntaxParseResult.Error(
                    message = "Validação de Duração falhou na Mídia $imageIndex: tempo inválido '$durationPart'. O tempo deve ser numérico maior que 0.1s.",
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
            val missing = (1..totalProjectImages).filter { it !in seenImageIndices }
            return SyntaxParseResult.Error(
                message = "Validação de Cobertura Total falhou: O projeto possui $totalProjectImages mídia(s), mas apenas ${parsedConfigs.size} foram configuradas. Mídias ausentes: ${missing.joinToString { "MÍDIA $it" }}."
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
            val dur = String.format(java.util.Locale.US, "%.1fs", defaultDurationSeconds)
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
                "O campo de IDs de transições está vazio. Informe os IDs de 1 a 20 separados por vírgula (ex: 1, 4, 2, 8)."
            )
        }

        val tokens = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return TransitionValidationResult.Error(
                "Nenhum ID de transição encontrado. Informe IDs de 1 a 20 separados por vírgula."
            )
        }

        val validIds = mutableListOf<Int>()
        for (token in tokens) {
            val id = token.toIntOrNull()
            if (id == null) {
                return TransitionValidationResult.Error(
                    message = "ID de transição inválido: '$token'. Informe apenas números de 1 a 20 (ou 0 para sem transição) separados por vírgula.",
                    faultyId = token
                )
            }
            if (id !in 0..20) {
                return TransitionValidationResult.Error(
                    message = "ID de transição inexistente: '$token'. Os IDs válidos são de 1 a 20 inspirados no CapCut (ou 0 para sem transição).",
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
                "O campo de IDs de sons de transição está vazio. Informe os IDs separados por vírgula (ex: 1, 3, 5, 8)."
            )
        }

        val tokens = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isEmpty()) {
            return TransitionSoundValidationResult.Error(
                "Nenhum ID de som de transição encontrado. Informe os IDs numéricos separados por vírgula."
            )
        }

        val validIds = mutableListOf<Int>()
        for (token in tokens) {
            val id = token.toIntOrNull()
            if (id == null) {
                return TransitionSoundValidationResult.Error(
                    message = "ID de som inválido: '$token'. Digite apenas números separados por vírgula.",
                    faultyId = token
                )
            }
            if (id !in availableSoundIds && id != 0) {
                val availableSorted = availableSoundIds.sorted().joinToString(", ")
                return TransitionSoundValidationResult.Error(
                    message = "ID de som inexistente: '$token'. Os IDs válidos disponíveis são: 0 (Sem som), $availableSorted.",
                    faultyId = token
                )
            }
            validIds.add(id)
        }

        return TransitionSoundValidationResult.Success(validIds)
    }

    /**
     * Gera prompts totalmente aleatórios com movimentos, durações, transições e sons.
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
            val randomDuration = 5.0f + (random.nextInt(51) / 10.0f)
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
