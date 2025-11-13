package com.lifo.humanoid.data.vrm

import com.google.android.filament.gltfio.FilamentAsset

/**
 * Represents a loaded VRM model with all its extensions and metadata.
 *
 * VRM is a 3D avatar format based on glTF 2.0 with additional extensions:
 * - Blend shapes for facial expressions
 * - Spring bones for physics simulation
 * - Humanoid bone structure
 * - Material properties
 */
data class VrmModel(
    val filamentAsset: FilamentAsset,
    val blendShapes: List<VrmBlendShape>,
    val springBones: List<SpringBoneData>,
    val metadata: VrmMetadata
) {
    /**
     * Get all available blend shape names
     */
    fun getBlendShapeNames(): List<String> = blendShapes.map { it.name }

    /**
     * Check if a specific blend shape exists
     */
    fun hasBlendShape(name: String): Boolean = blendShapes.any { it.name == name }
}

/**
 * Represents a VRM blend shape (morph target).
 */
data class VrmBlendShape(
    val name: String,
    val preset: BlendShapePreset?,
    val bindings: List<BlendShapeBinding>
) {
    /**
     * VRM standard blend shape presets
     */
    enum class BlendShapePreset {
        // Expressions
        NEUTRAL,
        JOY,
        ANGRY,
        SORROW,
        FUN,
        SURPRISED,

        // Lip sync
        A, I, U, E, O,

        // Blink
        BLINK,
        BLINK_LEFT,
        BLINK_RIGHT,

        // Look
        LOOK_UP,
        LOOK_DOWN,
        LOOK_LEFT,
        LOOK_RIGHT,

        UNKNOWN
    }
}

/**
 * Binding information for blend shapes
 */
data class BlendShapeBinding(
    val meshIndex: Int,
    val morphTargetIndex: Int,
    val weight: Float
)

/**
 * Spring bone physics data
 */
data class SpringBoneData(
    val boneName: String,
    val stiffness: Float = 0.5f,
    val gravityPower: Float = 0.1f,
    val gravityDir: Triple<Float, Float, Float> = Triple(0f, -1f, 0f),
    val dragForce: Float = 0.4f,
    val hitRadius: Float = 0.02f,
    val colliderGroups: List<String> = emptyList()
)

/**
 * VRM metadata information
 */
data class VrmMetadata(
    val version: String = "0.0",
    val title: String = "Unknown Avatar",
    val author: String = "Unknown",
    val contactInformation: String = "",
    val reference: String = "",
    val allowedUserName: String = "OnlyAuthor",
    val violentUsage: String = "Disallow",
    val sexualUsage: String = "Disallow",
    val commercialUsage: String = "Disallow",
    val licenseType: String = "Redistribution_Prohibited"
)
