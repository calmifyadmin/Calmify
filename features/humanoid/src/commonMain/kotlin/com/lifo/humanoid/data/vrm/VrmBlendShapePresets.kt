package com.lifo.humanoid.data.vrm

/**
 * VRM 0.x Standard Blend Shape Presets
 * Reference: https://github.com/vrm-c/vrm-specification
 *
 * Provides standardized mapping between emotions and VRM blend shape presets,
 * ensuring compatibility with various VRM avatar models.
 */
object VrmBlendShapePresets {

    /**
     * Mapping Emotion -> VRM Preset Names (lowercase for compatibility)
     * Multiple alternatives are provided as different VRM models may use different naming conventions
     */
    val EMOTION_TO_VRM = mapOf(
        "happy" to listOf("joy", "fun", "happy", "smile"),
        "sad" to listOf("sorrow", "sad"),
        "angry" to listOf("angry"),
        "surprised" to listOf("surprised", "surprise"),
        "neutral" to listOf("neutral"),
        "thinking" to listOf("thinking", "serious"),
        "excited" to listOf("fun", "excited", "joy"),
        "confused" to listOf("confused"),
        "worried" to listOf("worried", "sorrow"),
        "disappointed" to listOf("sorrow", "disappointed")
    )

    /**
     * VRM standard viseme presets for lip-sync
     */
    val VISEME_PRESETS = listOf("a", "i", "u", "e", "o")

    /**
     * VRM blink presets
     */
    val BLINK_PRESETS = mapOf(
        "both" to "blink",
        "left" to "blink_l",
        "right" to "blink_r"
    )

    /**
     * VRM look direction presets
     */
    val LOOK_PRESETS = mapOf(
        "up" to "lookup",
        "down" to "lookdown",
        "left" to "lookleft",
        "right" to "lookright"
    )

    /**
     * Standard VRM blend shape preset names
     */
    enum class StandardPreset(val presetName: String) {
        // Expressions
        NEUTRAL("neutral"),
        JOY("joy"),
        ANGRY("angry"),
        SORROW("sorrow"),
        FUN("fun"),
        SURPRISED("surprised"),

        // Lip sync
        A("a"),
        I("i"),
        U("u"),
        E("e"),
        O("o"),

        // Blink
        BLINK("blink"),
        BLINK_LEFT("blink_l"),
        BLINK_RIGHT("blink_r"),

        // Look at
        LOOK_UP("lookup"),
        LOOK_DOWN("lookdown"),
        LOOK_LEFT("lookleft"),
        LOOK_RIGHT("lookright")
    }

    /**
     * Find the first available preset from a list of candidates
     *
     * @param availablePresets Set of available preset names in the VRM model
     * @param candidates List of candidate names to search for (in priority order)
     * @return The first matching preset name, or null if none found
     */
    fun findAvailablePreset(
        availablePresets: Set<String>,
        candidates: List<String>
    ): String? {
        val normalizedAvailable = availablePresets.map { it.lowercase() }.toSet()
        return candidates.firstOrNull { it.lowercase() in normalizedAvailable }
    }

    /**
     * Get blend shape weights for an emotion, finding the best match in available presets
     *
     * @param emotionName The name of the emotion (e.g., "happy", "sad")
     * @param intensity The intensity of the emotion (0.0-1.0)
     * @param availablePresets Set of available preset names in the VRM model
     * @return Map of blend shape names to weights
     */
    fun getEmotionWeights(
        emotionName: String,
        intensity: Float,
        availablePresets: Set<String>
    ): Map<String, Float> {
        // "neutral" means no expression — don't activate any morph target.
        // The VRM "neutral" morph target resets the face to rest pose, but under
        // PBR lighting it deforms the geometry in ways that break the 3D look.
        if (emotionName.equals("neutral", ignoreCase = true)) {
            return emptyMap()
        }

        val candidates = EMOTION_TO_VRM[emotionName.lowercase()] ?: listOf(emotionName.lowercase())
        val matchedPreset = findAvailablePreset(availablePresets, candidates)

        return if (matchedPreset != null) {
            mapOf(matchedPreset to intensity.coerceIn(0f, 1f))
        } else {
            // Fallback: try the emotion name directly
            mapOf(emotionName.lowercase() to intensity.coerceIn(0f, 1f))
        }
    }

    /**
     * Get blink weight for a specific eye
     *
     * @param eye "both", "left", or "right"
     * @param weight The blink weight (0.0-1.0, where 1.0 is fully closed)
     * @return Map of blend shape names to weights
     */
    fun getBlinkWeights(eye: String, weight: Float): Map<String, Float> {
        val presetName = BLINK_PRESETS[eye.lowercase()] ?: BLINK_PRESETS["both"]!!
        return mapOf(presetName to weight.coerceIn(0f, 1f))
    }

    /**
     * Get viseme weights for lip-sync
     *
     * @param viseme The viseme character ("a", "i", "u", "e", "o")
     * @param weight The viseme weight (0.0-1.0)
     * @return Map of blend shape names to weights
     */
    fun getVisemeWeights(viseme: String, weight: Float): Map<String, Float> {
        val normalizedViseme = viseme.lowercase()
        return if (normalizedViseme in VISEME_PRESETS) {
            mapOf(normalizedViseme to weight.coerceIn(0f, 1f))
        } else {
            emptyMap()
        }
    }
}
