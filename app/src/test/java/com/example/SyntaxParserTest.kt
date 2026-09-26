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
        assertTrue(error.contains("terminar com s"))
        assertTrue(error.trim().split(Regex("\\s+")).size <= 6)
    }

    @Test
    fun testInvalidMovementRangeError() {
        val input = "IMAGEM 1 + MOVIMENTO 35 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 1)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("inexistente"))
        assertTrue(error.trim().split(Regex("\\s+")).size <= 6)
    }

    @Test
    fun testNonExistentImageIndexError() {
        val input = "IMAGEM 4 + MOVIMENTO 1 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 2)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("não existe"))
        assertTrue(error.trim().split(Regex("\\s+")).size <= 6)
    }

    @Test
    fun testMissingImagesCoverageError() {
        val input = "IMAGEM 1 + MOVIMENTO 1 + 4.0s"
        val result = SyntaxParser.parseAndValidate(input, totalProjectImages = 3)

        assertTrue(result is SyntaxParseResult.Error)
        val error = (result as SyntaxParseResult.Error).message
        assertTrue(error.contains("foram configuradas"))
        assertTrue(error.trim().split(Regex("\\s+")).size <= 6)
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
        assertTrue(error.trim().split(Regex("\\s+")).size <= 6)
    }

    @Test
    fun testTransitionValidationInvalidLetters() {
        val input = "1, abc, 5"
        val result = SyntaxParser.validateTransitionIds(input)
        assertTrue(result is com.example.engine.TransitionValidationResult.Error)
        val error = (result as com.example.engine.TransitionValidationResult.Error).message
        assertTrue(error.trim().split(Regex("\\s+")).size <= 6)
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

        // Verifica limites de movimento (0-26) e duração (no máximo 12.0s)
        for (cfg in configs) {
            assertTrue("Movimento deve estar entre 0 e 26", cfg.movementId in 0..26)
            assertTrue("Duração deve ser no máximo 12.0s", cfg.durationSeconds in 3.0f..12.01f)
        }

        // Valida que as transições geradas passam na validação
        val transResult = SyntaxParser.validateTransitionIds(result.transitionIdsText)
        assertTrue(transResult is com.example.engine.TransitionValidationResult.Success)
    }

    @Test
    fun testBuiltInTransitionSoundsAndMp3FolderExport() {
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

        // Cria e salva em uma única pasta SONS DE TRANSICOES do projeto todos os 17 áudios em formato .mp3
        val projectRootFolder = java.io.File("../SONS DE TRANSICOES")
        val assetsFolder = java.io.File("src/main/assets/SONS DE TRANSICOES")

        val files1 = com.example.engine.TransitionSoundEngine.ensureTransitionSoundsMp3Folder(projectRootFolder)
        val files2 = com.example.engine.TransitionSoundEngine.ensureTransitionSoundsMp3Folder(assetsFolder)

        assertEquals(17, files1.size)
        assertEquals(17, files2.size)
        assertTrue(files1.all { it.exists() && it.name.endsWith(".mp3") && it.length() > 100L })

        // Valida os 30 efeitos de transição (0 = Sem Transição + 30 efeitos = 31 itens)
        assertEquals(31, com.example.data.model.TransitionEffect.ALL_TRANSITIONS.size)
        assertEquals("Blur Dissolve Cinematográfico", com.example.data.model.TransitionEffect.findById(1)?.name)
        assertEquals("Efeito de Dissolução por Halogéneo", com.example.data.model.TransitionEffect.findById(30)?.name)
        assertTrue(SyntaxParser.validateTransitionIds("1, 15, 21, 30") is com.example.engine.TransitionValidationResult.Success)
        assertTrue(SyntaxParser.validateTransitionIds("31") is com.example.engine.TransitionValidationResult.Error)
    }

    @Test
    fun testCtaVideosFolderAndWebmCreation() {
        val projectRootCtaFolder = java.io.File("../VIDEOS CTA")
        val assetsCtaFolder = java.io.File("src/main/assets/VIDEOS CTA")

        val rootFiles = com.example.engine.CtaVideoEngine.ensureFakeCtaWebmFolder(projectRootCtaFolder)
        val assetFiles = com.example.engine.CtaVideoEngine.ensureFakeCtaWebmFolder(assetsCtaFolder)

        assertEquals(10, rootFiles.size)
        assertEquals(10, assetFiles.size)
        assertTrue(rootFiles.any { it.name == "CTA1.WEBM" && it.exists() && it.length() > 100L })
        assertTrue(rootFiles.any { it.name == "CTA2.WEBM" && it.exists() })
        assertTrue(rootFiles.any { it.name == "CTA3.WEBM" && it.exists() })

        // Primeiro item da lista é SEM CTA (id = 0)
        assertEquals(0, com.example.data.model.CtaVideoItem.BUILT_IN_OPTIONS.first().id)
        assertEquals("SEM CTA", com.example.data.model.CtaVideoItem.BUILT_IN_OPTIONS.first().name)

        // Valida parser de tempo obrigatório de CTA
        assertEquals(5.0f, com.example.engine.CtaVideoEngine.parseCtaTimeSeconds("5s") ?: -1f, 0.01f)
        assertEquals(12.5f, com.example.engine.CtaVideoEngine.parseCtaTimeSeconds("12,5") ?: -1f, 0.01f)
        assertEquals(65.0f, com.example.engine.CtaVideoEngine.parseCtaTimeSeconds("01:05") ?: -1f, 0.01f)
        assertEquals(null, com.example.engine.CtaVideoEngine.parseCtaTimeSeconds(""))
    }

    @Test
    fun testSubtitlesParsingAndFiveWordDuplicateTimeError() {
        val validInput = "00:00 + EXEMPLO DE TEXTO DA LEGENDA = 00:13, 00:14 + SEGUNDO TEXTO DE LEGENDA = 00:20"
        val res = com.example.engine.SubtitleEngine.parseAndValidateSubtitles(validInput)
        assertTrue(res is com.example.engine.SubtitleValidationResult.Success)
        val items = (res as com.example.engine.SubtitleValidationResult.Success).items
        assertEquals(2, items.size)
        assertEquals(0f, items[0].startTimeSeconds, 0.01f)
        assertEquals(13f, items[0].endTimeSeconds, 0.01f)
        assertEquals("EXEMPLO DE TEXTO DA LEGENDA", items[0].text)
        assertEquals(14f, items[1].startTimeSeconds, 0.01f)
        assertEquals(20f, items[1].endTimeSeconds, 0.01f)
        assertEquals("SEGUNDO TEXTO DE LEGENDA", items[1].text)

        // Duas legendas com o mesmo tempo de início -> erro com no máximo 5 palavras na mesma linha
        val duplicateStart = "00:00 + TEXTO UM = 00:10, 00:00 + TEXTO DOIS = 00:15"
        val dupStartRes = com.example.engine.SubtitleEngine.parseAndValidateSubtitles(duplicateStart)
        assertTrue(dupStartRes is com.example.engine.SubtitleValidationResult.Error)
        val dupStartMsg = (dupStartRes as com.example.engine.SubtitleValidationResult.Error).message
        assertTrue("Error must not contain line breaks", !dupStartMsg.contains("\n"))
        assertTrue("Error must have at most 5 words: '$dupStartMsg'", dupStartMsg.trim().split(Regex("\\s+")).size <= 5)
        assertTrue("Error must show specific faulty time", dupStartMsg.contains("00:00"))

        // Duas legendas com o mesmo tempo final -> erro com no máximo 5 palavras na mesma linha
        val duplicateEnd = "00:00 + TEXTO UM = 00:13, 00:05 + TEXTO DOIS = 00:13"
        val dupEndRes = com.example.engine.SubtitleEngine.parseAndValidateSubtitles(duplicateEnd)
        assertTrue(dupEndRes is com.example.engine.SubtitleValidationResult.Error)
        val dupEndMsg = (dupEndRes as com.example.engine.SubtitleValidationResult.Error).message
        assertTrue("Error must not contain line breaks", !dupEndMsg.contains("\n"))
        assertTrue("Error must have at most 5 words: '$dupEndMsg'", dupEndMsg.trim().split(Regex("\\s+")).size <= 5)
        assertTrue("Error must show specific faulty time", dupEndMsg.contains("00:13"))

        // 15 modelos de legendas profissionais + Sem Legenda + Legenda Padrão com Fundo ao lado da opção Sem Legenda
        assertEquals(15, com.example.data.model.SubtitleStyle.ALL_15_MODELS.size)
        assertEquals(1, com.example.data.model.SubtitleStyle.ALL_15_MODELS.first().id)
        assertEquals("Minimalista Profissional", com.example.data.model.SubtitleStyle.ALL_15_MODELS.first().name)
        assertEquals("Creative Agency", com.example.data.model.SubtitleStyle.ALL_15_MODELS.last().name)
        assertEquals(17, com.example.data.model.SubtitleStyle.CAROUSEL_OPTIONS.size)
        assertEquals(0, com.example.data.model.SubtitleStyle.CAROUSEL_OPTIONS[0].id)
        assertEquals("Sem Legenda", com.example.data.model.SubtitleStyle.CAROUSEL_OPTIONS[0].name)
        assertEquals("Legenda Padrão com Fundo", com.example.data.model.SubtitleStyle.CAROUSEL_OPTIONS[1].name)
        assertEquals(2, com.example.data.model.SubtitleStyle.CAROUSEL_OPTIONS[1].maxLines)
        assertEquals("Arial", com.example.data.model.SubtitleStyle.CAROUSEL_OPTIONS[1].fontFamily)

        // Verifica propriedades dos novos modelos (1 e 2 linhas, animações fade-in, slide-up, scale-up e fundos semi-transparentes)
        assertTrue(com.example.data.model.SubtitleStyle.ALL_15_MODELS.any { it.maxLines == 1 })
        assertTrue(com.example.data.model.SubtitleStyle.ALL_15_MODELS.any { it.maxLines == 2 })
        assertTrue(com.example.data.model.SubtitleStyle.ALL_15_MODELS.any { it.entryAnimation == com.example.data.model.SubtitleEntryAnimation.FADE_IN })
        assertTrue(com.example.data.model.SubtitleStyle.ALL_15_MODELS.any { it.entryAnimation == com.example.data.model.SubtitleEntryAnimation.SLIDE_UP })
        assertTrue(com.example.data.model.SubtitleStyle.ALL_15_MODELS.any { it.entryAnimation == com.example.data.model.SubtitleEntryAnimation.SCALE_UP })

        // Etapa 2: validação contra a duração do vídeo final criado
        val stage2Check = com.example.engine.SubtitleEngine.checkSubtitlesInFinalVideoDuration(items, 15.0f)
        assertTrue(stage2Check is com.example.engine.Stage2SubtitleCheckResult.HasInvalidTimes)
        val invalidInfo = stage2Check as com.example.engine.Stage2SubtitleCheckResult.HasInvalidTimes
        assertEquals(1, invalidInfo.validSubtitles.size)
        assertEquals(1, invalidInfo.invalidSubtitles.size)
    }
}

