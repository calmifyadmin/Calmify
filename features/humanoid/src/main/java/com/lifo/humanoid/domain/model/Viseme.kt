package com.lifo.humanoid.domain.model

/**
 * Represents a viseme - a visual representation of a phoneme.
 * Based on the standard 15-viseme system (Microsoft SAPI).
 * Each viseme maps to specific blend shapes for lip animation.
 */
enum class Viseme(val blendShapes: Map<String, Float>) {
    // Silence
    SILENCE(mapOf("mouthClosed" to 1.0f)),

    // Vowels
    AA(mapOf(
        "mouthOpen" to 1.0f,
        "jawOpen" to 0.8f
    )), // "ah" as in "father"

    E(mapOf(
        "mouthOpen" to 0.6f,
        "mouthWide" to 0.5f
    )), // "eh" as in "bed"

    I(mapOf(
        "mouthWide" to 0.7f,
        "mouthSmile" to 0.4f
    )), // "ee" as in "see"

    O(mapOf(
        "mouthOpen" to 0.7f,
        "mouthRound" to 1.0f
    )), // "oh" as in "go"

    U(mapOf(
        "mouthPucker" to 1.0f,
        "mouthRound" to 0.8f
    )), // "oo" as in "boot"

    // Consonants
    M_B_P(mapOf("mouthClosed" to 1.0f)), // Lips closed (m, b, p)

    F_V(mapOf(
        "mouthOpen" to 0.3f,
        "lipBite" to 0.7f
    )), // Lip-teeth (f, v)

    TH(mapOf(
        "tongueOut" to 0.5f,
        "mouthOpen" to 0.3f
    )), // Tongue-teeth (th)

    T_D_N_L(mapOf("mouthOpen" to 0.4f)), // Tongue-alveolar (t, d, n, l)

    S_Z(mapOf(
        "mouthWide" to 0.6f,
        "mouthOpen" to 0.2f
    )), // Sibilants (s, z)

    SH_ZH_CH_J(mapOf(
        "mouthRound" to 0.5f,
        "mouthOpen" to 0.4f
    )), // Palatals (sh, zh, ch, j)

    K_G_NG(mapOf("mouthOpen" to 0.5f)), // Velars (k, g, ng)

    R(mapOf(
        "mouthRound" to 0.6f,
        "mouthOpen" to 0.5f
    )), // R sound

    W(mapOf(
        "mouthPucker" to 0.8f,
        "mouthRound" to 0.9f
    )); // W sound

    companion object {
        /**
         * Get viseme from phoneme character
         * Simplified mapping - production version would use IPA phonemes
         */
        fun fromPhoneme(phoneme: Char): Viseme {
            return when (phoneme.lowercaseChar()) {
                'a' -> AA
                'e' -> E
                'i' -> I
                'o' -> O
                'u' -> U
                'm', 'b', 'p' -> M_B_P
                'f', 'v' -> F_V
                't', 'd', 'n', 'l' -> T_D_N_L
                's', 'z' -> S_Z
                'k', 'g' -> K_G_NG
                'r' -> R
                'w' -> W
                else -> SILENCE
            }
        }
    }
}
