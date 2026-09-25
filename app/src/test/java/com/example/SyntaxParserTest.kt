package com.example

import com.example.engine.SyntaxParseResult
import com.example.engine.SyntaxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxParserTest {

    @Test
    fun testValidSyntaxParsing() {
        val input = "IMAGEM 1 + MOVIMENTO 1 + 6.0s, IMAGEM 2 + MOVIMENTO 2 + 4.0s, IMAGEM 3 + MOVIMENTO 5 + 3.5s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 3)

        assertTrue(result is SyntaxParseResult.Success)
        val configs = (result as SyntaxParseResult.Success).configs
        assertEquals(3, configs.size)

        assertEquals(1, configs[0].imageIndex)
        assertEquals(1, configs[0].movementId)
        assertEquals(6.0f, configs[0].durationSeconds, 0.01f)

        assertEquals(2, configs[1].imageIndex)
        assertEquals(2, configs[1].movementId)
        assertEquals(4.0f, configs[1].durationSeconds, 0.01f)

        assertEquals(3, configs[2].imageIndex)
        assertEquals(5, configs[2].movementId)
        assertEquals(3.5f, configs[2].durationSeconds, 0.01f)
    }

    @Test
    fun testMissingDurationSuffixError() {
        val input = "IMAGEM 1 + MOVIMENTO 1 + 6.0"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 1)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("'s'"))
    }

    @Test
    fun testInvalidMovementRangeError() {
        val input = "IMAGEM 1 + MOVIMENTO 35 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 1)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("0 a 26"))
    }

    @Test
    fun testNonExistentImageIndexError() {
        val input = "IMAGEM 4 + MOVIMENTO 1 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 2)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("não existe no projeto"))
    }

    @Test
    fun testMissingImagesCoverageError() {
        val input = "IMAGEM 1 + MOVIMENTO 1 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 3)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("Cobertura Total"))
    }

    @Test
    fun testTransitionValidationValidIds() {
        val input = "0, 1, 5, 12, 20"
        val result = SyntaxParser.validateTransitionIds(input)
        assertTrue(result is com.example.engine.TransitionValidationResult.Success)
        val ids = (result as com.example.engine.TransitionValidationResult.Success).transitionIds
        assertEquals(listOf(0, 1, 5, 12, 20), ids)
    }

    @Test
    fun testTransitionValidationOutOfRange() {
        val input = "1, 21, 5"
        val result = SyntaxParser.validateTransitionIds(input)
        assertTrue(result is com.example.engine.TransitionValidationResult.Error)
        val error = (result as com.example.engine.TransitionValidationResult.Error).message
        assertTrue(error.contains("20"))
    }

    @Test
    fun testTransitionValidationInvalidLetters() {
        val input = "1, abc, 5"
        val result = SyntaxParser.validateTransitionIds(input)
        assertTrue(result is com.example.engine.TransitionValidationResult.Error)
    }

    @Test
    fun testRandomPromptsGeneration() {
        val imageCount = 4
        val result = SyntaxParser.generateRandomPrompts(imageCount)

        assertTrue(result.movementSyntaxText.isNotEmpty())
        assertTrue(result.transitionIdsText.isNotEmpty())

        // Valida que a sintaxe gerada passa na validação
        val parseResult = SyntaxParser.parseAndValidate(result.movementSyntaxText, totalProjectImages = imageCount)
        assertTrue(parseResult is SyntaxParseResult.Success)

        val configs = (parseResult as SyntaxParseResult.Success).configs
        assertEquals(imageCount, configs.size)

        // Verifica limites de movimento (0-26) e duração (5.0s-10.0s)
        for (cfg in configs) {
            assertTrue("Movimento deve estar entre 0 e 26", cfg.movementId in 0..26)
            assertTrue("Duração deve estar entre 5.0 e 10.0s", cfg.durationSeconds in 5.0f..10.01f)
        }

        // Valida que as transições geradas passam na validação
        val transResult = SyntaxParser.validateTransitionIds(result.transitionIdsText)
        assertTrue(transResult is com.example.engine.TransitionValidationResult.Success)
    }

    @Test
    fun testBuiltInTransitionSoundsIncludes5NewCapCutSounds() {
        // 0 (Sem Som) + 17 sons embutidos = 18 itens no catálogo padrão
        assertEquals(18, com.example.data.model.TransitionSoundEffect.BUILT_IN_SOUNDS.size)
        for (id in 13..17) {
            val sound = com.example.data.model.TransitionSoundEffect.getById(id)
            assertEquals(id, sound.id)
            val pcm = com.example.engine.TransitionSoundEngine.generatePcmForBuiltInSound(id)
            assertTrue("PCM for sound ID $id should not be empty", pcm.isNotEmpty())
        }
        val validNewSounds = SyntaxParser.validateTransitionSoundIds("13, 14, 15, 16, 17")
        assertTrue(validNewSounds is com.example.engine.TransitionSoundValidationResult.Success)
        val ids = (validNewSounds as com.example.engine.TransitionSoundValidationResult.Success).soundIds
        assertEquals(listOf(13, 14, 15, 16, 17), ids)
    }
}

