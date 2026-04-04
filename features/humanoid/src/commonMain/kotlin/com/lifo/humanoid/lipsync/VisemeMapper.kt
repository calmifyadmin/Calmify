package com.lifo.humanoid.lipsync

import com.lifo.humanoid.domain.model.Viseme

/**
 * Maps ARPAbet phonemes to VRM Visemes.
 * Based on the 15-viseme system (Microsoft SAPI) adapted for VRM.
 *
 * Reference: https://docs.microsoft.com/en-us/azure/cognitive-services/speech-service/how-to-speech-synthesis-viseme
 */
object VisemeMapper {

    /**
     * Mapping ARPAbet phoneme -> Viseme
     */
    private val PHONEME_TO_VISEME = mapOf(
        // Silence
        "SIL" to Viseme.SILENCE,

        // Vowels
        "AA" to Viseme.AA,   // father, hot
        "AE" to Viseme.AA,   // bat, had
        "AH" to Viseme.AA,   // but, hut
        "AO" to Viseme.O,    // bought, caught
        "AW" to Viseme.O,    // cow, how (diphthong)
        "AY" to Viseme.AA,   // bite, my (diphthong)
        "EH" to Viseme.E,    // bet, red
        "ER" to Viseme.E,    // bird, her
        "EY" to Viseme.E,    // bait, say
        "IH" to Viseme.I,    // bit, sit
        "IY" to Viseme.I,    // beat, see
        "OW" to Viseme.O,    // boat, go
        "OY" to Viseme.O,    // boy, coin (diphthong)
        "UH" to Viseme.U,    // book, put
        "UW" to Viseme.U,    // boot, too

        // Bilabial consonants (lips together)
        "B" to Viseme.M_B_P,
        "M" to Viseme.M_B_P,
        "P" to Viseme.M_B_P,

        // Labiodental consonants (lower lip to upper teeth)
        "F" to Viseme.F_V,
        "V" to Viseme.F_V,

        // Dental consonants (tongue to teeth)
        "DH" to Viseme.TH,  // the, this
        "TH" to Viseme.TH,  // think, bath

        // Alveolar consonants (tongue to alveolar ridge)
        "D" to Viseme.T_D_N_L,
        "L" to Viseme.T_D_N_L,
        "N" to Viseme.T_D_N_L,
        "T" to Viseme.T_D_N_L,

        // Sibilants
        "S" to Viseme.S_Z,
        "Z" to Viseme.S_Z,

        // Palatal/Post-alveolar consonants
        "CH" to Viseme.SH_ZH_CH_J,
        "JH" to Viseme.SH_ZH_CH_J,
        "SH" to Viseme.SH_ZH_CH_J,
        "ZH" to Viseme.SH_ZH_CH_J,

        // Velar consonants
        "G" to Viseme.K_G_NG,
        "K" to Viseme.K_G_NG,
        "NG" to Viseme.K_G_NG,

        // R sound
        "R" to Viseme.R,

        // W sound
        "W" to Viseme.W,

        // Glottal and other
        "HH" to Viseme.SILENCE,  // H is nearly invisible
        "Y" to Viseme.I          // Y as in "yes"
    )

    /**
     * VRM blend shape names for each viseme
     */
    /**
     * VRM blend shape names for each viseme
     */
    private val VISEME_TO_VRM = mapOf(
        Viseme.SILENCE to emptyMap<String, Float>(),
        Viseme.AA to mapOf("a" to 1.0f),
        Viseme.E to mapOf("e" to 1.0f),
        Viseme.I to mapOf("i" to 1.0f),
        Viseme.O to mapOf("o" to 1.0f),
        Viseme.U to mapOf("u" to 1.0f),
        Viseme.M_B_P to mapOf("a" to 0.2f),  // Slight mouth closed
        Viseme.F_V to mapOf("i" to 0.4f, "a" to 0.2f),
        Viseme.TH to mapOf("i" to 0.3f, "e" to 0.2f),
        Viseme.T_D_N_L to mapOf("a" to 0.4f),
        Viseme.S_Z to mapOf("i" to 0.5f),
        Viseme.SH_ZH_CH_J to mapOf("u" to 0.4f, "o" to 0.3f),
        Viseme.K_G_NG to mapOf("a" to 0.4f, "e" to 0.2f),
        Viseme.R to mapOf("o" to 0.5f, "u" to 0.3f),
        Viseme.W to mapOf("u" to 0.8f, "o" to 0.5f)
    )

    /**
     * Convert a phoneme to the corresponding viseme
     *
     * @param phoneme ARPAbet phoneme string
     * @return Corresponding Viseme
     */
    fun phonemeToViseme(phoneme: String): Viseme {
        // Remove stress markers (e.g., "AH1" -> "AH")
        val cleanPhoneme = phoneme.replace(Regex("[0-2]"), "")
        return PHONEME_TO_VISEME[cleanPhoneme] ?: Viseme.SILENCE
    }

    /**
     * Convert a list of phoneme timings to viseme timings
     *
     * @param phonemes List of PhonemeTiming
     * @return List of VisemeTiming
     */
    fun phonemesToVisemes(phonemes: List<PhonemeTiming>): List<VisemeTiming> {
        return phonemes.map { pt ->
            VisemeTiming(
                viseme = phonemeToViseme(pt.phoneme),
                startTimeMs = pt.startTimeMs,
                durationMs = pt.durationMs
            )
        }
    }

    /**
     * Get VRM blend shape weights for a viseme
     *
     * @param viseme The viseme
     * @param intensity Overall intensity (0.0-1.0)
     * @return Map of blend shape names to weights
     */
    fun getVisemeBlendShapes(viseme: Viseme, intensity: Float = 1.0f): Map<String, Float> {
        val baseWeights = VISEME_TO_VRM[viseme] ?: emptyMap()
        return baseWeights.mapValues { (_, weight) -> weight * intensity }
    }

    /**
     * Get blend shapes from viseme's built-in definition
     */
    fun getVisemeBuiltInBlendShapes(viseme: Viseme, intensity: Float = 1.0f): Map<String, Float> {
        return viseme.blendShapes.mapValues { (_, weight) -> weight * intensity }
    }
}

/**
 * Represents a viseme with timing information
 */
data class VisemeTiming(
    val viseme: Viseme,
    val startTimeMs: Long,
    val durationMs: Long
)

/**
 * Represents a phoneme with timing information
 */
data class PhonemeTiming(
    val phoneme: String,
    val startTimeMs: Long,
    val durationMs: Long
)
