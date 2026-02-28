package com.lifo.humanoid.data.vrm

import android.content.Context
import com.google.android.filament.gltfio.FilamentAsset
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loads VRM models and parses VRM-specific extensions.
 *
 * VRM files are glTF 2.0 files with additional "VRM" extension data.
 * This loader:
 * 1. Extracts the base glTF structure (handled by Filament's gltfio)
 * 2. Parses VRM extensions from JSON
 * 3. Extracts blend shapes, spring bones, and metadata
 *
 * Specification: https://github.com/vrm-c/vrm-specification
 */
class VrmLoader(private val context: Context) {

    private val gson = Gson()

    /**
     * Load a VRM file from app assets and create a VrmModel.
     * The FilamentAsset loading is handled by FilamentRenderer.
     *
     * @param assetPath Path to VRM file in assets (e.g., "models/avatar.vrm")
     * @param optimizeBones Whether to optimize bone count (default: true)
     * @return Pair of ByteBuffer (for Filament) and VRM extensions data
     */
    suspend fun loadVrmFromAssets(
        assetPath: String,
        optimizeBones: Boolean = true
    ): Pair<ByteBuffer, VrmExtensions>? {
        return try {
            println("[VrmLoader] Attempting to load VRM from assets: $assetPath")

            // Read VRM file from assets
            val originalBuffer = context.assets.open(assetPath).use { inputStream ->
                val bytes = inputStream.readBytes()
                println("[VrmLoader] Successfully read ${bytes.size} bytes from $assetPath")

                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    put(bytes)
                    rewind()
                }
            }

            // Optimize bones if requested
            val buffer = if (optimizeBones) {
                println("[VrmLoader] ╔═══════════════════════════════════════════════════════════╗")
                println("[VrmLoader] ║      Optimizing VRM for Filament 256 Bone Limit         ║")
                println("[VrmLoader] ╚═══════════════════════════════════════════════════════════╝")

                val optimizer = GltfBoneOptimizer(context)
                val result = optimizer.optimize(originalBuffer, maxBonesPerSkin = 256)

                if (result.bonesSaved > 0) {
                    println("[VrmLoader] Bone optimization successful:")
                    println("[VrmLoader]   Original bones: ${result.originalBoneCount}")
                    println("[VrmLoader]   Optimized bones: ${result.optimizedBoneCount}")
                    println("[VrmLoader]   Bones saved: ${result.bonesSaved}")
                    println("[VrmLoader]   Skins optimized: ${result.skinsOptimized}")
                    result.optimizedBuffer
                } else {
                    println("[VrmLoader] Model already within bone limit (${result.originalBoneCount} bones)")
                    originalBuffer
                }
            } else {
                println("[VrmLoader] Bone optimization disabled")
                originalBuffer
            }

            println("[VrmLoader] Created ByteBuffer with ${buffer.capacity()} bytes, position=${buffer.position()}, limit=${buffer.limit()}")

            // Parse VRM extensions (from original buffer to preserve original data)
            println("[VrmLoader] Parsing VRM extensions...")
            originalBuffer.position(0) // Reset position for parsing
            val vrmExtensions = parseVrmExtensions(originalBuffer)
            println("[VrmLoader] VRM extensions parsed successfully: ${vrmExtensions.blendShapes.size} blend shapes, ${vrmExtensions.springBones.size} spring bones")

            // Reset final buffer position
            buffer.position(0)

            Pair(buffer, vrmExtensions)
        } catch (e: Exception) {
            println("[VrmLoader] ERROR: Failed to load VRM from assets: $assetPath: ${e.message}")
            null
        }
    }

    /**
     * Parse VRM extension data from glTF JSON.
     *
     * VRM extensions are stored in the glTF JSON under "extensions.VRM"
     */
    private fun parseVrmExtensions(buffer: ByteBuffer): VrmExtensions {
        return try {
            // glTF binary structure:
            // - 12 bytes header (magic, version, length)
            // - JSON chunk
            // - Binary buffer chunk

            buffer.position(0)

            // Read header
            val magic = buffer.int
            println("[VrmLoader] glTF magic: 0x${magic.toString(16)} (expected 0x46546c67)")
            if (magic != 0x46546C67) { // "glTF" in ASCII
                throw IllegalArgumentException("Not a valid glTF file - magic is 0x${magic.toString(16)}")
            }

            val version = buffer.int
            val length = buffer.int
            println("[VrmLoader] glTF version: $version, length: $length")

            // Read JSON chunk header
            val jsonLength = buffer.int
            val jsonType = buffer.int
            println("[VrmLoader] JSON chunk: length=$jsonLength, type=0x${jsonType.toString(16)} (expected 0x4e4f534a)")

            if (jsonType != 0x4E4F534A) { // "JSON" in ASCII
                throw IllegalArgumentException("Expected JSON chunk, got 0x${jsonType.toString(16)}")
            }

            // Read JSON data
            val jsonBytes = ByteArray(jsonLength)
            buffer.get(jsonBytes)
            val jsonString = String(jsonBytes, Charsets.UTF_8)
            println("[VrmLoader] JSON string length: ${jsonString.length}, first 100 chars: ${jsonString.take(100)}")

            // Parse JSON
            val rootObject = gson.fromJson(jsonString, JsonObject::class.java)

            // Extract VRM extension
            val extensionsObject = rootObject.getAsJsonObject("extensions")
            val vrmObject = extensionsObject?.getAsJsonObject("VRM")

            if (vrmObject != null) {
                println("[VrmLoader] Found VRM extension in glTF file")
                parseVrmObject(vrmObject, rootObject)
            } else {
                println("[VrmLoader] WARNING: No VRM extension found - this is a standard glTF file")
                // Not a VRM file, return empty extensions
                VrmExtensions()
            }

        } catch (e: Exception) {
            println("[VrmLoader] ERROR: Failed to parse VRM extensions: ${e.message}")
            VrmExtensions() // Return empty extensions on error
        }
    }

    /**
     * Parse VRM extension object into structured data
     */
    private fun parseVrmObject(vrmObject: JsonObject, rootObject: JsonObject? = null): VrmExtensions {
        // Parse metadata
        val metadata = vrmObject.getAsJsonObject("meta")?.let { parseMetadata(it) }
            ?: VrmMetadata()

        // Parse blend shape master
        val blendShapes = vrmObject.getAsJsonObject("blendShapeMaster")
            ?.getAsJsonArray("blendShapeGroups")
            ?.map { parseBlendShape(it.asJsonObject) }
            ?: emptyList()

        // Parse spring bones
        val springBones = vrmObject.getAsJsonObject("secondaryAnimation")
            ?.getAsJsonArray("boneGroups")
            ?.map { parseSpringBone(it.asJsonObject) }
            ?.flatten()
            ?: emptyList()

        // Parse humanoid bone mapping (bone name → glTF node index)
        val humanoidBoneNodeIndices = mutableMapOf<String, Int>()
        vrmObject.getAsJsonObject("humanoid")
            ?.getAsJsonArray("humanBones")
            ?.forEach { boneElement ->
                try {
                    val boneObj = boneElement.asJsonObject
                    val boneName = boneObj.get("bone")?.asString
                    val nodeIndex = boneObj.get("node")?.asInt
                    if (boneName != null && nodeIndex != null) {
                        humanoidBoneNodeIndices[boneName] = nodeIndex
                    }
                } catch (e: Exception) {
                    println("[VrmLoader] WARNING: Failed to parse humanoid bone element: ${e.message}")
                }
            }
        // Resolve eye bone node indices to actual glTF node NAMES
        // CRITICAL: Filament entity array indices ≠ glTF node indices!
        // We must search by name, not by index.
        val nodesArray = rootObject?.getAsJsonArray("nodes")
        var leftEyeNodeName: String? = null
        var rightEyeNodeName: String? = null

        humanoidBoneNodeIndices["leftEye"]?.let { nodeIdx ->
            leftEyeNodeName = nodesArray?.get(nodeIdx)?.asJsonObject?.get("name")?.asString
        }
        humanoidBoneNodeIndices["rightEye"]?.let { nodeIdx ->
            rightEyeNodeName = nodesArray?.get(nodeIdx)?.asJsonObject?.get("name")?.asString
        }

        println("[VrmLoader] Parsed ${humanoidBoneNodeIndices.size} humanoid bones. " +
                "leftEye: idx=${humanoidBoneNodeIndices["leftEye"]} name='$leftEyeNodeName', " +
                "rightEye: idx=${humanoidBoneNodeIndices["rightEye"]} name='$rightEyeNodeName'")

        // Parse firstPerson lookAt type
        val lookAtTypeName = vrmObject.getAsJsonObject("firstPerson")
            ?.get("lookAtTypeName")?.asString ?: "Bone"
        println("[VrmLoader] VRM lookAt type: $lookAtTypeName")

        return VrmExtensions(
            metadata = metadata,
            blendShapes = blendShapes,
            springBones = springBones,
            humanoidBoneNodeIndices = humanoidBoneNodeIndices,
            leftEyeNodeName = leftEyeNodeName,
            rightEyeNodeName = rightEyeNodeName,
            lookAtTypeName = lookAtTypeName
        )
    }

    /**
     * Parse VRM metadata
     */
    private fun parseMetadata(metaObject: JsonObject): VrmMetadata {
        return VrmMetadata(
            version = metaObject.get("version")?.asString ?: "0.0",
            title = metaObject.get("title")?.asString ?: "Unknown Avatar",
            author = metaObject.get("author")?.asString ?: "Unknown",
            contactInformation = metaObject.get("contactInformation")?.asString ?: "",
            reference = metaObject.get("reference")?.asString ?: "",
            allowedUserName = metaObject.get("allowedUserName")?.asString ?: "OnlyAuthor",
            violentUsage = metaObject.get("violentUssageName")?.asString ?: "Disallow",
            sexualUsage = metaObject.get("sexualUssageName")?.asString ?: "Disallow",
            commercialUsage = metaObject.get("commercialUssageName")?.asString ?: "Disallow",
            licenseType = metaObject.get("licenseName")?.asString ?: "Redistribution_Prohibited"
        )
    }

    /**
     * Parse blend shape definition
     */
    private fun parseBlendShape(blendShapeObject: JsonObject): VrmBlendShape {
        val name = blendShapeObject.get("name")?.asString ?: "Unknown"
        val presetName = blendShapeObject.get("presetName")?.asString ?: "unknown"

        val preset = try {
            VrmBlendShape.BlendShapePreset.valueOf(presetName.uppercase())
        } catch (e: Exception) {
            VrmBlendShape.BlendShapePreset.UNKNOWN
        }

        val bindings = blendShapeObject.getAsJsonArray("binds")
            ?.map { bindObject ->
                val obj = bindObject.asJsonObject
                BlendShapeBinding(
                    meshIndex = obj.get("mesh")?.asInt ?: 0,
                    morphTargetIndex = obj.get("index")?.asInt ?: 0,
                    weight = obj.get("weight")?.asFloat ?: 1.0f
                )
            }
            ?: emptyList()

        return VrmBlendShape(name, preset, bindings)
    }

    /**
     * Parse spring bone group
     */
    private fun parseSpringBone(boneGroupObject: JsonObject): List<SpringBoneData> {
        val stiffness = boneGroupObject.get("stiffiness")?.asFloat ?: 0.5f // Note: typo in VRM spec
        val gravityPower = boneGroupObject.get("gravityPower")?.asFloat ?: 0.1f
        val dragForce = boneGroupObject.get("dragForce")?.asFloat ?: 0.4f
        val hitRadius = boneGroupObject.get("hitRadius")?.asFloat ?: 0.02f

        val gravityDir = boneGroupObject.getAsJsonObject("gravityDir")?.let {
            Triple(
                it.get("x")?.asFloat ?: 0f,
                it.get("y")?.asFloat ?: -1f,
                it.get("z")?.asFloat ?: 0f
            )
        } ?: Triple(0f, -1f, 0f)

        val colliderGroups = boneGroupObject.getAsJsonArray("colliderGroups")
            ?.map { it.asInt.toString() }
            ?: emptyList()

        // Get bones in this group
        val bones = boneGroupObject.getAsJsonArray("bones")
            ?.map { it.asInt }
            ?: emptyList()

        // Create spring bone data for each bone
        return bones.map { boneIndex ->
            SpringBoneData(
                boneName = "bone_$boneIndex", // TODO: Resolve actual bone name from node index
                stiffness = stiffness,
                gravityPower = gravityPower,
                gravityDir = gravityDir,
                dragForce = dragForce,
                hitRadius = hitRadius,
                colliderGroups = colliderGroups
            )
        }
    }

    /**
     * Build complete VrmModel from FilamentAsset and VRM extensions
     */
    fun buildVrmModel(
        filamentAsset: FilamentAsset,
        vrmExtensions: VrmExtensions
    ): VrmModel {
        return VrmModel(
            filamentAsset = filamentAsset,
            blendShapes = vrmExtensions.blendShapes,
            springBones = vrmExtensions.springBones,
            metadata = vrmExtensions.metadata
        )
    }
}

/**
 * Container for parsed VRM extension data
 */
data class VrmExtensions(
    val metadata: VrmMetadata = VrmMetadata(),
    val blendShapes: List<VrmBlendShape> = emptyList(),
    val springBones: List<SpringBoneData> = emptyList(),
    /**
     * VRM humanoid bone mapping: bone name (e.g. "leftEye") → glTF node index.
     * Parsed from VRM.humanoid.humanBones.
     */
    val humanoidBoneNodeIndices: Map<String, Int> = emptyMap(),
    /**
     * Actual glTF node name for left eye bone (resolved from humanoid data + nodes array).
     * e.g., "J_Adj_L_FaceEye" for VRoid models.
     */
    val leftEyeNodeName: String? = null,
    /**
     * Actual glTF node name for right eye bone.
     * e.g., "J_Adj_R_FaceEye" for VRoid models.
     */
    val rightEyeNodeName: String? = null,
    /**
     * VRM lookAt type: "Bone" or "BlendShape".
     * Parsed from VRM.firstPerson.lookAtTypeName.
     */
    val lookAtTypeName: String = "Bone"
)
