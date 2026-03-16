package com.lifo.humanoid.animation

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Loader for VRM Animation (.vrma) files.
 *
 * VRMA is the VRM Animation format based on glTF 2.0 with VRMC_vrm_animation extension.
 * It contains humanoid bone animations that can be applied to any VRM model.
 *
 * Reference: https://github.com/vrm-c/vrm-specification/tree/master/specification/VRMC_vrm_animation-1.0
 */
class VrmaAnimationLoader(private val context: Context) {

    private val gson = Gson()

    /**
     * Available animation assets
     */
    enum class AnimationAsset(val fileName: String, val displayName: String) {
        // Idle animations (per rotazione automatica)
        IDLE_LOOP("idle_loop.vrma", "Idle Loop"),
        IDLE_BASIC("idle_basic.vrma", "Idle Basic"),
        IDLE_LOOK_FINGERS("idle_look_fingers.vrma", "Idle Look Fingers"),
        IDLE_LOOKING_AROUND("idle_looking_around.vrma", "Idle Looking Around"),
        IDLE_VARIANT("idle_variant.vrma", "Idle Variant"),

        // Emotion animations
        ANGRY("angry.vrma", "Angry"),
        SAD("sad.vrma", "Sad"),
        DANCING_HAPPY("dancing_happy.vrma", "Dancing Happy"),

        // Gesture animations
        HELLO("models/hello.vrma", "Hello"),
        GREETING("greeting.vrma", "Greeting"),
        I_AGREE("i_agree.vrma", "I Agree"),
        I_DONT_KNOW("i_dont_know.vrma", "I Don't Know"),
        I_DONT_THINK_SO("i_dont_think_so.vrma", "I Don't Think So"),
        YES_WITH_HEAD("yes_with_head.vrma", "Yes"),
        NO_WITH_HEAD("no_with_head.vrma", "No"),
        POINTING_THING("pointing_thing.vrma", "Pointing"),
        YOU_ARE_CRAZY("you_are_crazy.vrma", "You Are Crazy"),

        // Action animations
        DANCE("dance.vrma", "Dance"),
        PEACE_SIGN("peaceSign.vrma", "Peace Sign"),
        SHOOT("shoot.vrma", "Shoot"),
        SHOW_FULL_BODY("showFullBody.vrma", "Show Full Body");

        // RIMOSSI (buggati): modelPose.vrma, spin.vrma, squat.vrma

        /**
         * Check if this animation is an idle animation (for automatic rotation)
         */
        fun isIdle(): Boolean = fileName.startsWith("idle_")
    }

    companion object {
        // glTF magic number "glTF" in ASCII
        private const val GLTF_MAGIC = 0x46546C67

        // JSON chunk type "JSON" in ASCII
        private const val JSON_CHUNK_TYPE = 0x4E4F534A

        // Binary chunk type "BIN\0" in ASCII
        private const val BIN_CHUNK_TYPE = 0x004E4942

        /**
         * Get all idle animations for rotation
         */
        fun getIdleAnimations(): List<AnimationAsset> =
            AnimationAsset.entries.filter { it.isIdle() }
    }

    /**
     * Load a VRMA animation from assets
     *
     * @param asset The animation asset to load
     * @return VrmaAnimation containing animation data, or null if loading fails
     */
    suspend fun loadAnimation(asset: AnimationAsset): VrmaAnimation? {
        return loadAnimationFromPath(asset.fileName)
    }

    /**
     * Load a VRMA animation from a specific asset path
     *
     * @param assetPath Path to the .vrma file in assets
     * @return VrmaAnimation containing animation data, or null if loading fails
     */
    suspend fun loadAnimationFromPath(assetPath: String): VrmaAnimation? {
        return try {
            println("[VrmaAnimationLoader] Loading VRMA animation: $assetPath")

            // Read file from assets
            val buffer = context.assets.open(assetPath).use { inputStream ->
                val bytes = inputStream.readBytes()
                println("[VrmaAnimationLoader] Read ${bytes.size} bytes from $assetPath")

                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    put(bytes)
                    rewind()
                }
            }

            // Parse glTF structure
            parseVrmaFile(buffer, assetPath)
        } catch (e: Exception) {
            println("[VrmaAnimationLoader] ERROR: Failed to load VRMA animation: $assetPath: ${e.message}")
            null
        }
    }

    /**
     * Parse a VRMA file (glTF 2.0 binary format)
     */
    private fun parseVrmaFile(buffer: ByteBuffer, fileName: String): VrmaAnimation? {
        buffer.position(0)

        // Read glTF header
        val magic = buffer.int
        if (magic != GLTF_MAGIC) {
            println("[VrmaAnimationLoader] ERROR: Invalid glTF magic: 0x${magic.toString(16)}")
            return null
        }

        val version = buffer.int
        val length = buffer.int
        println("[VrmaAnimationLoader] glTF version: $version, total length: $length")

        // Read JSON chunk
        val jsonChunkLength = buffer.int
        val jsonChunkType = buffer.int
        if (jsonChunkType != JSON_CHUNK_TYPE) {
            println("[VrmaAnimationLoader] ERROR: Expected JSON chunk, got: 0x${jsonChunkType.toString(16)}")
            return null
        }

        val jsonBytes = ByteArray(jsonChunkLength)
        buffer.get(jsonBytes)
        val jsonString = String(jsonBytes, Charsets.UTF_8)

        // Parse JSON
        val gltf = gson.fromJson(jsonString, JsonObject::class.java)

        // Read binary chunk if present
        var binaryData: ByteBuffer? = null
        if (buffer.remaining() >= 8) {
            val binChunkLength = buffer.int
            val binChunkType = buffer.int
            if (binChunkType == BIN_CHUNK_TYPE) {
                val binBytes = ByteArray(binChunkLength)
                buffer.get(binBytes)
                binaryData = ByteBuffer.wrap(binBytes).order(ByteOrder.LITTLE_ENDIAN)
                println("[VrmaAnimationLoader] Read binary chunk: $binChunkLength bytes")
            }
        }

        // Extract animation data
        return extractAnimationData(gltf, binaryData, fileName)
    }

    /**
     * Extract animation data from glTF JSON and binary buffer
     */
    private fun extractAnimationData(
        gltf: JsonObject,
        binaryData: ByteBuffer?,
        fileName: String
    ): VrmaAnimation? {
        // Get VRMC_vrm_animation extension
        val extensions = gltf.getAsJsonObject("extensions")
        val vrmAnimExt = extensions?.getAsJsonObject("VRMC_vrm_animation")

        val isVrma = vrmAnimExt != null
        println("[VrmaAnimationLoader] VRMC_vrm_animation extension present: $isVrma")

        // Parse animations array
        val animations = gltf.getAsJsonArray("animations")
        if (animations == null || animations.size() == 0) {
            println("[VrmaAnimationLoader] WARNING: No animations found in file")
            return null
        }

        val firstAnimation = animations[0].asJsonObject
        val animationName = firstAnimation.get("name")?.asString ?: fileName.removeSuffix(".vrma")

        // Parse channels and samplers
        val channels = firstAnimation.getAsJsonArray("channels") ?: return null
        val samplers = firstAnimation.getAsJsonArray("samplers") ?: return null

        // Parse accessors and buffer views
        val accessors = gltf.getAsJsonArray("accessors") ?: return null
        val bufferViews = gltf.getAsJsonArray("bufferViews") ?: return null

        // Parse nodes for bone names
        val nodes = gltf.getAsJsonArray("nodes")

        // Build animation tracks
        val tracks = mutableListOf<AnimationTrack>()
        var maxDuration = 0f

        channels.forEach { channelElement ->
            val channel = channelElement.asJsonObject
            val targetObj = channel.getAsJsonObject("target") ?: return@forEach

            val nodeIndex = targetObj.get("node")?.asInt ?: return@forEach
            val path = targetObj.get("path")?.asString ?: return@forEach
            val samplerIndex = channel.get("sampler")?.asInt ?: return@forEach

            // Get node name (bone name)
            val nodeName = nodes?.get(nodeIndex)?.asJsonObject?.get("name")?.asString
                ?: "node_$nodeIndex"

            // Get sampler data
            val sampler = samplers[samplerIndex].asJsonObject
            val inputAccessorIndex = sampler.get("input")?.asInt ?: return@forEach
            val outputAccessorIndex = sampler.get("output")?.asInt ?: return@forEach
            val interpolation = sampler.get("interpolation")?.asString ?: "LINEAR"

            // Parse keyframe times
            val inputAccessor = accessors[inputAccessorIndex].asJsonObject
            val times = parseAccessorData(inputAccessor, bufferViews, binaryData)
            if (times.isEmpty()) return@forEach

            // Update max duration
            val lastTime = times.last()
            if (lastTime > maxDuration) maxDuration = lastTime

            // Parse keyframe values
            val outputAccessor = accessors[outputAccessorIndex].asJsonObject
            val values = parseAccessorData(outputAccessor, bufferViews, binaryData)
            if (values.isEmpty()) return@forEach

            // Determine value count per keyframe based on path
            val valuesPerKey = when (path) {
                "translation" -> 3
                "rotation" -> 4
                "scale" -> 3
                "weights" -> outputAccessor.get("count")?.asInt?.div(times.size) ?: 1
                else -> 3
            }

            // Build keyframes
            val keyframes = times.mapIndexed { index, time ->
                val startIdx = index * valuesPerKey
                val endIdx = (startIdx + valuesPerKey).coerceAtMost(values.size)
                val keyValues = if (startIdx < values.size) {
                    values.subList(startIdx, endIdx).toFloatArray()
                } else {
                    FloatArray(valuesPerKey) { 0f }
                }
                AnimationKeyframe(time, keyValues)
            }

            tracks.add(
                AnimationTrack(
                    nodeName = nodeName,
                    nodeIndex = nodeIndex,
                    path = AnimationPath.fromString(path),
                    interpolation = InterpolationType.fromString(interpolation),
                    keyframes = keyframes
                )
            )
        }

        // Parse humanoid bone mapping from VRMC_vrm_animation extension
        val humanoidBoneMap = parseHumanoidMapping(vrmAnimExt, nodes)

        // Extract rest pose data for animation retargeting (following amica pattern)
        val restPoseData = extractRestPoseData(gltf, nodes, humanoidBoneMap, bufferViews, binaryData)

        // Use fileName for display name if animation name is generic
        val displayName = if (animationName == "animation" || animationName.isBlank()) {
            fileName.removeSuffix(".vrma")
        } else {
            animationName
        }

        // Determine if animation should loop based on fileName (more reliable than internal name)
        val shouldLoop = fileName.contains("loop", ignoreCase = true) ||
                fileName.contains("idle", ignoreCase = true)

        // Log detailed debug info for animation
        println("[VrmaAnimationLoader] === Animation Debug Info ===")
        println("[VrmaAnimationLoader] Animation: '$displayName' from file '$fileName'")
        println("[VrmaAnimationLoader] Duration: ${maxDuration}s, Looping: $shouldLoop")
        println("[VrmaAnimationLoader] Tracks: ${tracks.size}")
        println("[VrmaAnimationLoader] Humanoid bone mapping: ${humanoidBoneMap.size} entries")
        humanoidBoneMap.forEach { (vrmBoneName, animNodeName) ->
            println("[VrmaAnimationLoader]   $vrmBoneName -> $animNodeName")
        }

        // Log rest pose data
        if (restPoseData.first != null) {
            println("[VrmaAnimationLoader] Rest hips position: [${restPoseData.first!!.joinToString()}]")
        }
        println("[VrmaAnimationLoader] Bone world quaternions: ${restPoseData.second.size} bones")

        // Log track details
        tracks.take(10).forEach { track ->
            println("[VrmaAnimationLoader] Track: nodeName='${track.nodeName}', path=${track.path}, keyframes=${track.keyframes.size}")
        }
        if (tracks.size > 10) {
            println("[VrmaAnimationLoader] ... and ${tracks.size - 10} more tracks")
        }
        println("[VrmaAnimationLoader] === End Animation Debug ===")

        return VrmaAnimation(
            name = displayName,
            durationSeconds = maxDuration,
            tracks = tracks,
            humanoidBoneMapping = humanoidBoneMap,
            isLooping = shouldLoop,
            restHipsPosition = restPoseData.first,
            boneWorldQuaternions = restPoseData.second.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Parse accessor data from binary buffer
     */
    private fun parseAccessorData(
        accessor: JsonObject,
        bufferViews: com.google.gson.JsonArray,
        binaryData: ByteBuffer?
    ): List<Float> {
        if (binaryData == null) return emptyList()

        val bufferViewIndex = accessor.get("bufferView")?.asInt ?: return emptyList()
        val componentType = accessor.get("componentType")?.asInt ?: return emptyList()
        val count = accessor.get("count")?.asInt ?: return emptyList()
        val type = accessor.get("type")?.asString ?: return emptyList()
        val byteOffset = accessor.get("byteOffset")?.asInt ?: 0

        val bufferView = bufferViews[bufferViewIndex].asJsonObject
        val viewOffset = bufferView.get("byteOffset")?.asInt ?: 0
        val viewLength = bufferView.get("byteLength")?.asInt ?: return emptyList()

        // Determine components per element
        val componentsPerElement = when (type) {
            "SCALAR" -> 1
            "VEC2" -> 2
            "VEC3" -> 3
            "VEC4" -> 4
            "MAT4" -> 16
            else -> 1
        }

        val totalValues = count * componentsPerElement
        val result = mutableListOf<Float>()

        val startPosition = viewOffset + byteOffset
        binaryData.position(startPosition)

        for (i in 0 until totalValues) {
            val value = when (componentType) {
                5126 -> binaryData.float // FLOAT
                5123 -> binaryData.short.toInt().toFloat() // UNSIGNED_SHORT
                5121 -> (binaryData.get().toInt() and 0xFF).toFloat() // UNSIGNED_BYTE
                5122 -> binaryData.short.toFloat() // SHORT
                5120 -> binaryData.get().toFloat() // BYTE
                else -> 0f
            }
            result.add(value)
        }

        return result
    }

    /**
     * Parse humanoid bone mapping from VRMC_vrm_animation extension
     */
    private fun parseHumanoidMapping(
        vrmAnimExt: JsonObject?,
        nodes: com.google.gson.JsonArray?
    ): Map<String, String> {
        if (vrmAnimExt == null || nodes == null) return emptyMap()

        val mapping = mutableMapOf<String, String>()

        val humanoid = vrmAnimExt.getAsJsonObject("humanoid")
        val humanBones = humanoid?.getAsJsonObject("humanBones")

        humanBones?.entrySet()?.forEach { (boneName, value) ->
            val nodeIndex = value.asJsonObject?.get("node")?.asInt
            if (nodeIndex != null && nodeIndex < nodes.size()) {
                val nodeName = nodes[nodeIndex].asJsonObject?.get("name")?.asString
                if (nodeName != null) {
                    mapping[boneName] = nodeName
                    println("[VrmaAnimationLoader] Humanoid bone mapping: $boneName -> $nodeName (node $nodeIndex)")
                }
            }
        }

        return mapping
    }

    /**
     * Extract rest pose data from animation skeleton for retargeting.
     *
     * Following amica-master VRMAnimationLoaderPlugin.ts pattern:
     * - Extract hips rest position for translation scaling
     * - Calculate world quaternions for each bone in the animation's rest pose
     * - Calculate hipsParent world quaternion for the retargeting formula
     *
     * @return Pair of (hipsPosition, boneWorldQuaternions map)
     */
    private fun extractRestPoseData(
        gltf: JsonObject,
        nodes: com.google.gson.JsonArray?,
        humanoidBoneMap: Map<String, String>,
        bufferViews: com.google.gson.JsonArray,
        binaryData: ByteBuffer?
    ): Pair<FloatArray?, Map<String, FloatArray>> {
        if (nodes == null) return Pair(null, emptyMap())

        val boneWorldQuaternions = mutableMapOf<String, FloatArray>()
        var hipsPosition: FloatArray? = null

        // Build node hierarchy for world transform calculation
        val nodeTransforms = mutableMapOf<Int, NodeTransform>()
        val nodeParents = mutableMapOf<Int, Int>()

        // Parse all node transforms
        nodes.forEachIndexed { index, nodeElement ->
            val node = nodeElement.asJsonObject

            // Extract local transform
            val translation = node.getAsJsonArray("translation")?.let { arr ->
                floatArrayOf(arr[0].asFloat, arr[1].asFloat, arr[2].asFloat)
            } ?: floatArrayOf(0f, 0f, 0f)

            val rotation = node.getAsJsonArray("rotation")?.let { arr ->
                floatArrayOf(arr[0].asFloat, arr[1].asFloat, arr[2].asFloat, arr[3].asFloat)
            } ?: floatArrayOf(0f, 0f, 0f, 1f)

            val scale = node.getAsJsonArray("scale")?.let { arr ->
                floatArrayOf(arr[0].asFloat, arr[1].asFloat, arr[2].asFloat)
            } ?: floatArrayOf(1f, 1f, 1f)

            nodeTransforms[index] = NodeTransform(translation, rotation, scale)

            // Build parent relationships from children arrays
            node.getAsJsonArray("children")?.forEach { childElement ->
                val childIndex = childElement.asInt
                nodeParents[childIndex] = index
            }
        }

        // Calculate world transforms for each humanoid bone
        humanoidBoneMap.forEach { (vrmBoneName, animNodeName) ->
            // Find node index for this bone
            val nodeIndex = nodes.indexOfFirst { nodeElement ->
                nodeElement.asJsonObject?.get("name")?.asString == animNodeName
            }

            if (nodeIndex >= 0) {
                // Calculate world quaternion by walking up the parent chain
                val worldQuat = calculateWorldQuaternion(nodeIndex, nodeTransforms, nodeParents)
                boneWorldQuaternions[vrmBoneName] = worldQuat

                println("[VrmaAnimationLoader] Bone '$vrmBoneName' world quaternion: [${worldQuat.joinToString { "%.4f".format(it) }}]")

                // For hips, also calculate and store the parent's world quaternion
                if (vrmBoneName == "hips") {
                    val worldPos = calculateWorldPosition(nodeIndex, nodeTransforms, nodeParents)
                    hipsPosition = worldPos
                    println("[VrmaAnimationLoader] Hips world position: [${worldPos.joinToString()}]")

                    // CRITICAL: Calculate hipsParent world quaternion (following amica pattern)
                    val hipsParentIndex = nodeParents[nodeIndex]
                    val hipsParentWorldQuat = if (hipsParentIndex != null) {
                        calculateWorldQuaternion(hipsParentIndex, nodeTransforms, nodeParents)
                    } else {
                        // No parent - use identity quaternion
                        floatArrayOf(0f, 0f, 0f, 1f)
                    }
                    boneWorldQuaternions["hipsParent"] = hipsParentWorldQuat
                    println("[VrmaAnimationLoader] HipsParent world quaternion: [${hipsParentWorldQuat.joinToString { "%.4f".format(it) }}]")
                }
            }
        }

        println("[VrmaAnimationLoader] Extracted rest pose data for ${boneWorldQuaternions.size} bones (including hipsParent)")
        return Pair(hipsPosition, boneWorldQuaternions)
    }

    /**
     * Calculate world quaternion for a node by walking up parent chain.
     */
    private fun calculateWorldQuaternion(
        nodeIndex: Int,
        nodeTransforms: Map<Int, NodeTransform>,
        nodeParents: Map<Int, Int>
    ): FloatArray {
        var worldQuat = floatArrayOf(0f, 0f, 0f, 1f) // Identity
        var currentIndex: Int? = nodeIndex

        // Build chain from node to root
        val chain = mutableListOf<Int>()
        while (currentIndex != null) {
            chain.add(currentIndex)
            currentIndex = nodeParents[currentIndex]
        }

        // Apply rotations from root to node (reverse order)
        chain.reversed().forEach { idx ->
            val transform = nodeTransforms[idx]
            if (transform != null) {
                worldQuat = multiplyQuaternions(worldQuat, transform.rotation)
            }
        }

        return normalizeQuaternion(worldQuat)
    }

    /**
     * Calculate world position for a node.
     */
    private fun calculateWorldPosition(
        nodeIndex: Int,
        nodeTransforms: Map<Int, NodeTransform>,
        nodeParents: Map<Int, Int>
    ): FloatArray {
        var worldPos = floatArrayOf(0f, 0f, 0f)
        var currentIndex: Int? = nodeIndex

        // Build chain from node to root
        val chain = mutableListOf<Int>()
        while (currentIndex != null) {
            chain.add(currentIndex)
            currentIndex = nodeParents[currentIndex]
        }

        // Apply transforms from root to node (reverse order)
        // Simplified: just sum translations (proper implementation would apply full transforms)
        chain.reversed().forEach { idx ->
            val transform = nodeTransforms[idx]
            if (transform != null) {
                worldPos[0] += transform.translation[0]
                worldPos[1] += transform.translation[1]
                worldPos[2] += transform.translation[2]
            }
        }

        return worldPos
    }

    /**
     * Multiply two quaternions: result = q1 * q2
     * Format: [x, y, z, w]
     */
    private fun multiplyQuaternions(q1: FloatArray, q2: FloatArray): FloatArray {
        val x1 = q1[0]; val y1 = q1[1]; val z1 = q1[2]; val w1 = q1[3]
        val x2 = q2[0]; val y2 = q2[1]; val z2 = q2[2]; val w2 = q2[3]

        return floatArrayOf(
            w1 * x2 + x1 * w2 + y1 * z2 - z1 * y2,  // x
            w1 * y2 - x1 * z2 + y1 * w2 + z1 * x2,  // y
            w1 * z2 + x1 * y2 - y1 * x2 + z1 * w2,  // z
            w1 * w2 - x1 * x2 - y1 * y2 - z1 * z2   // w
        )
    }

    /**
     * Normalize a quaternion
     */
    private fun normalizeQuaternion(q: FloatArray): FloatArray {
        val length = kotlin.math.sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
        if (length < 0.0001f) return floatArrayOf(0f, 0f, 0f, 1f)
        return floatArrayOf(q[0] / length, q[1] / length, q[2] / length, q[3] / length)
    }

    /**
     * Node transform data
     */
    private data class NodeTransform(
        val translation: FloatArray,
        val rotation: FloatArray,
        val scale: FloatArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as NodeTransform
            return translation.contentEquals(other.translation) &&
                   rotation.contentEquals(other.rotation) &&
                   scale.contentEquals(other.scale)
        }
        override fun hashCode(): Int {
            var result = translation.contentHashCode()
            result = 31 * result + rotation.contentHashCode()
            result = 31 * result + scale.contentHashCode()
            return result
        }
    }

    /**
     * Get list of all available animation assets
     */
    fun getAvailableAnimations(): List<AnimationAsset> = AnimationAsset.entries
}

/**
 * Represents a loaded VRMA animation.
 *
 * Following amica-master VRMAnimation.ts pattern:
 * - humanoidBoneMapping: Maps VRM bone names to animation node names
 * - restHipsPosition: Rest pose position of hips for translation scaling
 * - boneWorldQuaternions: World quaternions of each bone in animation's rest pose (for retargeting)
 */
data class VrmaAnimation(
    val name: String,
    val durationSeconds: Float,
    val tracks: List<AnimationTrack>,
    val humanoidBoneMapping: Map<String, String>,
    val isLooping: Boolean,
    // Rest pose data for animation retargeting (following amica pattern)
    val restHipsPosition: FloatArray? = null,
    val boneWorldQuaternions: Map<String, FloatArray>? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VrmaAnimation
        if (name != other.name) return false
        if (durationSeconds != other.durationSeconds) return false
        if (tracks != other.tracks) return false
        if (humanoidBoneMapping != other.humanoidBoneMapping) return false
        if (isLooping != other.isLooping) return false
        if (restHipsPosition != null) {
            if (other.restHipsPosition == null) return false
            if (!restHipsPosition.contentEquals(other.restHipsPosition)) return false
        } else if (other.restHipsPosition != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + durationSeconds.hashCode()
        result = 31 * result + tracks.hashCode()
        result = 31 * result + humanoidBoneMapping.hashCode()
        result = 31 * result + isLooping.hashCode()
        result = 31 * result + (restHipsPosition?.contentHashCode() ?: 0)
        return result
    }
}

/**
 * Animation track for a single node/bone
 */
data class AnimationTrack(
    val nodeName: String,
    val nodeIndex: Int,
    val path: AnimationPath,
    val interpolation: InterpolationType,
    val keyframes: List<AnimationKeyframe>
)

/**
 * Single keyframe in an animation track
 */
data class AnimationKeyframe(
    val time: Float,
    val values: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AnimationKeyframe
        return time == other.time && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = time.hashCode()
        result = 31 * result + values.contentHashCode()
        return result
    }
}

/**
 * Animation property path
 */
enum class AnimationPath {
    TRANSLATION,
    ROTATION,
    SCALE,
    WEIGHTS;

    companion object {
        fun fromString(path: String): AnimationPath = when (path.lowercase()) {
            "translation" -> TRANSLATION
            "rotation" -> ROTATION
            "scale" -> SCALE
            "weights" -> WEIGHTS
            else -> TRANSLATION
        }
    }
}

/**
 * Interpolation type for animation
 */
enum class InterpolationType {
    LINEAR,
    STEP,
    CUBICSPLINE;

    companion object {
        fun fromString(type: String): InterpolationType = when (type.uppercase()) {
            "LINEAR" -> LINEAR
            "STEP" -> STEP
            "CUBICSPLINE" -> CUBICSPLINE
            else -> LINEAR
        }
    }
}
