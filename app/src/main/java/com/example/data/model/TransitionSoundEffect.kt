package com.example.data.model

data class TransitionSoundEffect(
    val id: Int,
    val name: String,
    val description: String,
    val category: SoundCategory,
    val customFilePath: String? = null,
    val isCustom: Boolean = false
) {
    val isMute: Boolean get() = id == 0

    companion object {
        val NO_SOUND = TransitionSoundEffect(
            id = 0,
            name = "Sem Som",
            description = "Silencioso (Nenhum áudio de transição)",
            category = SoundCategory.NONE
        )

        // 4 Sons de Tecla/Botão
        val CLICK_MECHANICAL = TransitionSoundEffect(
            id = 1,
            name = "Clique Mecânico",
            description = "Snap nítido de switch mecânico (18ms)",
            category = SoundCategory.BUTTON_CLICK
        )
        val CLICK_TACTILE = TransitionSoundEffect(
            id = 2,
            name = "Clique Tátil",
            description = "Toque duplo tátil preciso (22ms)",
            category = SoundCategory.BUTTON_CLICK
        )
        val CLICK_METALLIC = TransitionSoundEffect(
            id = 3,
            name = "Clique Metálico",
            description = "Impacto metálico cristalino (25ms)",
            category = SoundCategory.BUTTON_CLICK
        )
        val CLICK_DEEP = TransitionSoundEffect(
            id = 4,
            name = "Clique Profundo",
            description = "Pressionar sólido e encorpado (32ms)",
            category = SoundCategory.BUTTON_CLICK
        )

        // 8 Sons de Transição Super Rápidos
        val WHOOSH_FAST = TransitionSoundEffect(
            id = 5,
            name = "Whoosh Ultra Rápido",
            description = "Deslocamento de ar veloz (110ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val SWOOSH_CINEMATIC = TransitionSoundEffect(
            id = 6,
            name = "Swoosh Cinema",
            description = "Crescendo de ar dinâmico (160ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val WHIP_SNAP = TransitionSoundEffect(
            id = 7,
            name = "Whip Transição",
            description = "Chicote rápido cortante (90ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val GLITCH_CUT = TransitionSoundEffect(
            id = 8,
            name = "Glitch Digital Cut",
            description = "Corte futurista com ruído tech (100ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val BUBBLE_POP = TransitionSoundEffect(
            id = 9,
            name = "Pop Bolha Rápido",
            description = "Pop enérgico de bolha (65ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val LASER_FAST = TransitionSoundEffect(
            id = 10,
            name = "Laser Zap Rápido",
            description = "Chirp descendente sci-fi (80ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val AIR_PUFF = TransitionSoundEffect(
            id = 11,
            name = "Air Puff Impact",
            description = "Sopro de ar com grave suave (130ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val PERCUSSIVE_SNAP = TransitionSoundEffect(
            id = 12,
            name = "Snap Percussivo",
            description = "Estalo seco e percussivo (55ms)",
            category = SoundCategory.FAST_TRANSITION
        )

        // +5 Novos Sons de Transição Personalizados (IDs 13 a 17)
        val CAMERA_SHUTTER = TransitionSoundEffect(
            id = 13,
            name = "Obturador Flash",
            description = "Clique duplo de câmera DSLR profissional (95ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val CINEMATIC_BOOM = TransitionSoundEffect(
            id = 14,
            name = "Bass Drop Impact",
            description = "Impacto sub-grave cinematográfico (180ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val MAGIC_SPARKLE = TransitionSoundEffect(
            id = 15,
            name = "Brilho Cristal Chime",
            description = "Arpejo mágico cristalino brilhante (150ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val VINYL_REWIND = TransitionSoundEffect(
            id = 16,
            name = "Rewind Tape Spin",
            description = "Efeito rápido de rebobinar fita/vinil (140ms)",
            category = SoundCategory.FAST_TRANSITION
        )
        val CYBER_RISER_HIT = TransitionSoundEffect(
            id = 17,
            name = "Cyber Riser & Hit",
            description = "Subida reversa rápida com impacto (165ms)",
            category = SoundCategory.FAST_TRANSITION
        )

        const val MAX_BUILT_IN_ID = 17

        val BUILT_IN_SOUNDS: List<TransitionSoundEffect> = listOf(
            NO_SOUND,
            CLICK_MECHANICAL,
            CLICK_TACTILE,
            CLICK_METALLIC,
            CLICK_DEEP,
            WHOOSH_FAST,
            SWOOSH_CINEMATIC,
            WHIP_SNAP,
            GLITCH_CUT,
            BUBBLE_POP,
            LASER_FAST,
            AIR_PUFF,
            PERCUSSIVE_SNAP,
            CAMERA_SHUTTER,
            CINEMATIC_BOOM,
            MAGIC_SPARKLE,
            VINYL_REWIND,
            CYBER_RISER_HIT
        )

        val DEFAULT: TransitionSoundEffect = CLICK_MECHANICAL

        fun getById(id: Int): TransitionSoundEffect {
            return BUILT_IN_SOUNDS.find { it.id == id } ?: DEFAULT
        }
    }
}

enum class SoundCategory(val label: String) {
    NONE("Silêncio"),
    BUTTON_CLICK("Clique de Botão"),
    FAST_TRANSITION("Transição Rápida"),
    CUSTOM("Personalizado")
}
