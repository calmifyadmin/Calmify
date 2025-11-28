package com.lifo.humanoid.data.vrm

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.gltfio.FilamentAsset
import com.google.gson.JsonObject

/**
 * Maps VRM humanoid bones to Filament entities.
 * VRM defines a standard humanoid bone structure for avatar models.
 *
 * Reference: https://github.com/vrm-c/vrm-specification/blob/master/specification/0.0/schema/vrm.humanoid.bone.schema.json
 */
class VrmHumanoidBoneMapper {

    companion object {
        private const val TAG = "VrmHumanoidBoneMapper"
    }

    /**
     * VRM Humanoid bone names (VRM 0.x specification)
     */
    enum class HumanoidBone(val vrmName: String) {
        // Torso
        HIPS("hips"),
        SPINE("spine"),
        CHEST("chest"),
        UPPER_CHEST("upperChest"),
        NECK("neck"),

        // Head
        HEAD("head"),
        LEFT_EYE("leftEye"),
        RIGHT_EYE("rightEye"),
        JAW("jaw"),

        // Left Arm
        LEFT_SHOULDER("leftShoulder"),
        LEFT_UPPER_ARM("leftUpperArm"),
        LEFT_LOWER_ARM("leftLowerArm"),
        LEFT_HAND("leftHand"),

        // Right Arm
        RIGHT_SHOULDER("rightShoulder"),
        RIGHT_UPPER_ARM("rightUpperArm"),
        RIGHT_LOWER_ARM("rightLowerArm"),
        RIGHT_HAND("rightHand"),

        // Left Leg
        LEFT_UPPER_LEG("leftUpperLeg"),
        LEFT_LOWER_LEG("leftLowerLeg"),
        LEFT_FOOT("leftFoot"),
        LEFT_TOES("leftToes"),

        // Right Leg
        RIGHT_UPPER_LEG("rightUpperLeg"),
        RIGHT_LOWER_LEG("rightLowerLeg"),
        RIGHT_FOOT("rightFoot"),
        RIGHT_TOES("rightToes"),

        // Left Hand Fingers (optional)
        LEFT_THUMB_PROXIMAL("leftThumbProximal"),
        LEFT_THUMB_INTERMEDIATE("leftThumbIntermediate"),
        LEFT_THUMB_DISTAL("leftThumbDistal"),
        LEFT_INDEX_PROXIMAL("leftIndexProximal"),
        LEFT_INDEX_INTERMEDIATE("leftIndexIntermediate"),
        LEFT_INDEX_DISTAL("leftIndexDistal"),
        LEFT_MIDDLE_PROXIMAL("leftMiddleProximal"),
        LEFT_MIDDLE_INTERMEDIATE("leftMiddleIntermediate"),
        LEFT_MIDDLE_DISTAL("leftMiddleDistal"),
        LEFT_RING_PROXIMAL("leftRingProximal"),
        LEFT_RING_INTERMEDIATE("leftRingIntermediate"),
        LEFT_RING_DISTAL("leftRingDistal"),
        LEFT_LITTLE_PROXIMAL("leftLittleProximal"),
        LEFT_LITTLE_INTERMEDIATE("leftLittleIntermediate"),
        LEFT_LITTLE_DISTAL("leftLittleDistal"),

        // Right Hand Fingers (optional)
        RIGHT_THUMB_PROXIMAL("rightThumbProximal"),
        RIGHT_THUMB_INTERMEDIATE("rightThumbIntermediate"),
        RIGHT_THUMB_DISTAL("rightThumbDistal"),
        RIGHT_INDEX_PROXIMAL("rightIndexProximal"),
        RIGHT_INDEX_INTERMEDIATE("rightIndexIntermediate"),
        RIGHT_INDEX_DISTAL("rightIndexDistal"),
        RIGHT_MIDDLE_PROXIMAL("rightMiddleProximal"),
        RIGHT_MIDDLE_INTERMEDIATE("rightMiddleIntermediate"),
        RIGHT_MIDDLE_DISTAL("rightMiddleDistal"),
        RIGHT_RING_PROXIMAL("rightRingProximal"),
        RIGHT_RING_INTERMEDIATE("rightRingIntermediate"),
        RIGHT_RING_DISTAL("rightRingDistal"),
        RIGHT_LITTLE_PROXIMAL("rightLittleProximal"),
        RIGHT_LITTLE_INTERMEDIATE("rightLittleIntermediate"),
        RIGHT_LITTLE_DISTAL("rightLittleDistal");

        companion object {
            /**
             * Find HumanoidBone by VRM name (case-insensitive)
             */
            fun fromVrmName(name: String): HumanoidBone? {
                return entries.firstOrNull { it.vrmName.equals(name, ignoreCase = true) }
            }
        }
    }

    // Mapping: HumanoidBone -> Filament entity ID
    private val boneEntityMap = mutableMapOf<HumanoidBone, Int>()

    // Mapping: Node index -> Filament entity
    private val nodeToEntityMap = mutableMapOf<Int, Int>()

    // Mapping: Node name -> Filament entity
    private val nodeNameToEntityMap = mutableMapOf<String, Int>()

    // Store original transforms for reset
    private val originalTransforms = mutableMapOf<Int, FloatArray>()

    // Reference to Engine for transform operations
    private var engine: Engine? = null

    /**
     * Initialize the bone mapper with Engine, asset, and node names.
     * This builds a mapping from node names to entities and attempts to
     * match VRM humanoid bone names automatically.
     *
     * @param engine The Filament Engine instance
     * @param asset The loaded FilamentAsset
     * @param nodeNames List of node names from the asset
     */
    fun initialize(engine: Engine, asset: FilamentAsset, nodeNames: List<String>) {
        this.engine = engine
        nodeNameToEntityMap.clear()

        // Build node name to entity mapping
        nodeNames.forEachIndexed { index, name ->
            if (index < asset.entities.size) {
                val entity = asset.entities[index]
                nodeNameToEntityMap[name] = entity
                nodeToEntityMap[index] = entity

                // Try to match to humanoid bone by name
                val humanoidBone = HumanoidBone.fromVrmName(name)
                if (humanoidBone != null) {
                    boneEntityMap[humanoidBone] = entity
                    Log.d(TAG, "Auto-mapped bone ${humanoidBone.name} from node name '$name' -> entity $entity")
                }
            }
        }

        // Store original transforms for all entities
        val tm = engine.transformManager
        asset.entities.forEach { entity ->
            val instance = tm.getInstance(entity)
            if (instance != 0) {
                val transform = FloatArray(16)
                tm.getTransform(instance, transform)
                originalTransforms[entity] = transform.copyOf()
            }
        }

        Log.d(TAG, "Initialized with ${nodeNameToEntityMap.size} nodes, ${boneEntityMap.size} auto-mapped bones")
    }

    /**
     * Get entity by node name
     */
    fun getEntityByNodeName(nodeName: String): Int? = nodeNameToEntityMap[nodeName]

    /**
     * Get all node names
     */
    fun getNodeNames(): Set<String> = nodeNameToEntityMap.keys.toSet()

    /**
     * Build the mapping from VRM humanoid extension data
     *
     * @param asset The loaded FilamentAsset
     * @param vrmHumanoidData The VRM humanoid extension JSON object
     * @return Map of HumanoidBone to entity IDs
     */
    fun buildMapping(
        asset: FilamentAsset,
        vrmHumanoidData: JsonObject?
    ): Map<HumanoidBone, Int> {
        boneEntityMap.clear()
        nodeToEntityMap.clear()

        if (vrmHumanoidData == null) {
            Log.w(TAG, "No VRM humanoid data provided")
            return emptyMap()
        }

        // Build node to entity mapping
        asset.entities.forEachIndexed { index, entity ->
            nodeToEntityMap[index] = entity
        }

        // Parse humanBones array from VRM extension
        val humanBones = vrmHumanoidData.getAsJsonArray("humanBones")
        if (humanBones == null) {
            Log.w(TAG, "No humanBones array in VRM humanoid data")
            return emptyMap()
        }

        humanBones.forEach { boneElement ->
            try {
                val boneObj = boneElement.asJsonObject
                val boneName = boneObj.get("bone")?.asString ?: return@forEach
                val nodeIndex = boneObj.get("node")?.asInt ?: return@forEach

                val humanoidBone = HumanoidBone.fromVrmName(boneName)
                if (humanoidBone != null) {
                    val entity = nodeToEntityMap[nodeIndex]
                    if (entity != null) {
                        boneEntityMap[humanoidBone] = entity
                        Log.d(TAG, "Mapped bone $boneName (${humanoidBone.name}) -> entity $entity (node $nodeIndex)")
                    } else {
                        Log.w(TAG, "No entity found for node index $nodeIndex (bone: $boneName)")
                    }
                } else {
                    Log.w(TAG, "Unknown humanoid bone name: $boneName")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing bone element", e)
            }
        }

        Log.d(TAG, "Total bones mapped: ${boneEntityMap.size}")
        return boneEntityMap.toMap()
    }

    /**
     * Get the Filament entity for a specific humanoid bone
     */
    fun getBoneEntity(bone: HumanoidBone): Int? = boneEntityMap[bone]

    /**
     * Check if a specific bone is mapped
     */
    fun hasBone(bone: HumanoidBone): Boolean = boneEntityMap.containsKey(bone)

    /**
     * Get all mapped bones
     */
    fun getMappedBones(): Set<HumanoidBone> = boneEntityMap.keys.toSet()

    /**
     * Get the entity map (bone -> entity)
     */
    fun getBoneEntityMap(): Map<HumanoidBone, Int> = boneEntityMap.toMap()

    /**
     * Store original transform for a bone (for reset functionality)
     */
    fun storeOriginalTransform(entity: Int, transform: FloatArray) {
        originalTransforms[entity] = transform.copyOf()
    }

    /**
     * Get stored original transform
     */
    fun getOriginalTransform(entity: Int): FloatArray? = originalTransforms[entity]?.copyOf()

    /**
     * Clear all stored original transforms
     */
    fun clearOriginalTransforms() {
        originalTransforms.clear()
    }

    /**
     * Check if we have essential bones for idle animation
     */
    fun hasEssentialBones(): Boolean {
        return hasBone(HumanoidBone.HIPS) &&
                hasBone(HumanoidBone.SPINE) &&
                hasBone(HumanoidBone.HEAD)
    }

    /**
     * Check if we have arm bones for gesture animations
     */
    fun hasArmBones(): Boolean {
        return hasBone(HumanoidBone.LEFT_UPPER_ARM) &&
                hasBone(HumanoidBone.LEFT_LOWER_ARM) &&
                hasBone(HumanoidBone.RIGHT_UPPER_ARM) &&
                hasBone(HumanoidBone.RIGHT_LOWER_ARM)
    }
}
