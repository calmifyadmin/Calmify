package com.lifo.humanoid.animation

import android.content.Context
import android.util.Log
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

    companion object {
        private const val TAG = "VrmaAnimationLoader"

        // glTF magic number "glTF" in ASCII
        private const val GLTF_MAGIC = 0x46546C67

        // JSON chunk type "JSON" in ASCII
        private const val JSON_CHUNK_TYPE = 0x4E4F534A

        // Binary chunk type "BIN\0" in ASCII
        private const val BIN_CHUNK_TYPE = 0x004E4942
    }

    private val gson = Gson()

    /**
     * Available animation assets
     */
    enum class AnimationAsset(val fileName: String, val displayName: String) {
        IDLE_LOOP("idle_loop.vrma", "Idle Loop"),
        DANCE("dance.vrma", "Dance"),
        GREETING("greeting.vrma", "Greeting"),
        MODEL_POSE("modelPose.vrma", "Model Pose"),
        PEACE_SIGN("peaceSign.vrma", "Peace Sign"),
        SHOOT("shoot.vrma", "Shoot"),
        SHOW_FULL_BODY("showFullBody.vrma", "Show Full Body"),
        SPIN("spin.vrma", "Spin"),
        SQUAT("squat.vrma", "Squat")
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
            Log.d(TAG, "Loading VRMA animation: $assetPath")

            // Read file from assets
            val buffer = context.assets.open(assetPath).use { inputStream ->
                val bytes = inputStream.readBytes()
                Log.d(TAG, "Read ${bytes.size} bytes from $assetPath")

                ByteBuffer.allocateDirect(bytes.size).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    put(bytes)
                    rewind()
                }
            }

            // Parse glTF structure
            parseVrmaFile(buffer, assetPath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load VRMA animation: $assetPath", e)
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
            Log.e(TAG, "Invalid glTF magic: 0x${magic.toString(16)}")
            return null
        }

        val version = buffer.int
        val length = buffer.int
        Log.d(TAG, "glTF version: $version, total length: $length")

        // Read JSON chunk
        val jsonChunkLength = buffer.int
        val jsonChunkType = buffer.int
        if (jsonChunkType != JSON_CHUNK_TYPE) {
            Log.e(TAG, "Expected JSON chunk, got: 0x${jsonChunkType.toString(16)}")
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
                Log.d(TAG, "Read binary chunk: $binChunkLength bytes")
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
        Log.d(TAG, "VRMC_vrm_animation extension present: $isVrma")

        // Parse animations array
        val animations = gltf.getAsJsonArray("animations")
        if (animations == null || animations.size() == 0) {
            Log.w(TAG, "No animations found in file")
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

        // Use fileName for display name if animation name is generic
        val displayName = if (animationName == "animation" || animationName.isBlank()) {
            fileName.removeSuffix(".vrma")
        } else {
            animationName
        }

        // Determine if animation should loop based on fileName (more reliable than internal name)
        val shouldLoop = fileName.contains("loop", ignoreCase = true) ||
                fileName.contains("idle", ignoreCase = true)

        Log.d(TAG, "Parsed animation '$displayName' from file '$fileName': ${tracks.size} tracks, duration: ${maxDuration}s, looping: $shouldLoop")

        return VrmaAnimation(
            name = displayName,
            durationSeconds = maxDuration,
            tracks = tracks,
            humanoidBoneMapping = humanoidBoneMap,
            isLooping = shouldLoop
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
                    Log.d(TAG, "Humanoid bone mapping: $boneName -> $nodeName (node $nodeIndex)")
                }
            }
        }

        return mapping
    }

    /**
     * Get list of all available animation assets
     */
    fun getAvailableAnimations(): List<AnimationAsset> = AnimationAsset.entries
}

/**
 * Represents a loaded VRMA animation
 */
data class VrmaAnimation(
    val name: String,
    val durationSeconds: Float,
    val tracks: List<AnimationTrack>,
    val humanoidBoneMapping: Map<String, String>,
    val isLooping: Boolean
)

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
