package com.lifo.humanoid.domain.model

/**
 * Represents a viseme - a visual representation of a phoneme.
 * Based on the standard 15-viseme system (Microsoft SAPI).
 * Each viseme maps to specific blend shapes for lip animation.
 */
enum class Viseme(val blendShapes: Map<String, Float>) {
    // Silence — no morph target active
    SILENCE(emptyMap()),

    // Vowels — VRM standard blend shape names: a, i, u, e, o
    AA(mapOf("a" to 1.0f)),   // "ah" as in "father"
    E(mapOf("e" to 1.0f)),    // "eh" as in "bed"
    I(mapOf("i" to 1.0f)),    // "ee" as in "see"
    O(mapOf("o" to 1.0f)),    // "oh" as in "go"
    U(mapOf("u" to 1.0f)),    // "oo" as in "boot"

    // Consonants — approximate using VRM vowel shapes
    M_B_P(emptyMap()),                    // Lips closed (m, b, p) — no morph active
    F_V(mapOf("i" to 0.4f, "a" to 0.2f)),          // Lip-teeth (f, v)
    TH(mapOf("i" to 0.3f, "e" to 0.2f)),            // Tongue-teeth (th)
    T_D_N_L(mapOf("a" to 0.4f)),                    // Tongue-alveolar (t, d, n, l)
    S_Z(mapOf("i" to 0.5f)),                         // Sibilants (s, z)
    SH_ZH_CH_J(mapOf("u" to 0.4f, "o" to 0.3f)),   // Palatals (sh, zh, ch, j)
    K_G_NG(mapOf("a" to 0.4f, "e" to 0.2f)),        // Velars (k, g, ng)
    R(mapOf("o" to 0.5f, "u" to 0.3f)),             // R sound
    W(mapOf("u" to 0.8f, "o" to 0.5f));             // W sound

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
