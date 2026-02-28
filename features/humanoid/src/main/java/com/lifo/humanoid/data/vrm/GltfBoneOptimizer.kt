package com.lifo.humanoid.data.vrm

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * glTF Bone Optimizer - Reduces bone count to fit Filament's 256 bone limit.
 *
 * Inspired by three-vrm's VRMUtils.removeUnnecessaryJoints():
 * - Analyzes which bones are actually used by vertices (JOINTS_0/WEIGHTS_0)
 * - Protects VRM 1.0 humanoid bones (essential for animations)
 * - Compacts skin joint arrays to only include used bones
 * - Remaps all binary data (vertex attributes, inverse bind matrices)
 * - Reconstructs glb file with optimized structure
 *
 * This solves the "exceeded 256 bones" error on VRM 1.0 models while
 * maintaining full compatibility with VRMA animations.
 *
 * Reference Implementation:
 * - Amica: https://github.com/semperai/amica/blob/main/src/features/vrmViewer/model.ts
 * - three-vrm: https://github.com/pixiv/three-vrm (removeUnnecessaryJoints)
 *
 * Technical Details:
 * glTF structure:
 *   - Header (12 bytes): magic, version, length
 *   - JSON chunk: scene graph, nodes, skins, meshes, accessors, bufferViews
 *   - BIN chunk: binary data (vertex attributes, matrices, textures)
 *
 * Each skin has:
 *   - joints: array of node indices (bones)
 *   - inverseBindMatrices: accessor pointing to matrix data
 *
 * Each vertex has:
 *   - JOINTS_0: indices into skin.joints array (4 per vertex)
 *   - WEIGHTS_0: weights for those joints (4 per vertex, sum to 1.0)
 *
 * Optimization process:
 * 1. Parse glb -> JSON + BIN chunks
 * 2. For each skin:
 *    a. Find all primitives using this skin
 *    b. Scan JOINTS_0/WEIGHTS_0 to find used joint indices
 *    c. Add VRM humanoid bones (even if unused - needed for animation)
 *    d. Create old->new index mapping
 * 3. Rewrite JSON:
 *    - Compact skin.joints arrays
 *    - Update accessor counts
 * 4. Rewrite BIN:
 *    - Remap JOINTS_0 values to new indices
 *    - Compact inverseBindMatrices
 * 5. Rebuild glb file
 */
class GltfBoneOptimizer(private val context: android.content.Context) {

    private val gson = Gson()

    /**
     * VRM 1.0 humanoid bone names that must ALWAYS be preserved.
     * These are essential for VRMA animations even if they have no vertex weights.
     */
    private val essentialHumanoidBones = setOf(
        // Core bones (required)
        "hips", "spine", "chest", "neck", "head",

        // Upper body (required for gestures)
        "upperChest",
        "leftShoulder", "leftUpperArm", "leftLowerArm", "leftHand",
        "rightShoulder", "rightUpperArm", "rightLowerArm", "rightHand",

        // Lower body (required for locomotion)
        "leftUpperLeg", "leftLowerLeg", "leftFoot",
        "rightUpperLeg", "rightLowerLeg", "rightFoot",

        // Optional but common in VRMA
        "leftToes", "rightToes",
        "leftEye", "rightEye", "jaw"
    )

    /**
     * Result of bone optimization
     */
    data class OptimizationResult(
        val optimizedBuffer: ByteBuffer,
        val originalBoneCount: Int,
        val optimizedBoneCount: Int,
        val skinsOptimized: Int,
        val bonesSaved: Int
    )

    /**
     * Optimize glTF bone structure to fit within bone limits.
     * This is the main entry point.
     *
     * @param glbBuffer Original glb file data
     * @param maxBonesPerSkin Maximum bones per skin (default: 256 for Filament)
     * @return OptimizationResult with new buffer and statistics
     */
    fun optimize(glbBuffer: ByteBuffer, maxBonesPerSkin: Int = 256): OptimizationResult {
        println("[GltfBoneOptimizer] ╔═══════════════════════════════════════════════════════════╗")
        println("[GltfBoneOptimizer] ║         glTF Bone Optimizer (Filament 256 Limit)         ║")
        println("[GltfBoneOptimizer] ╚═══════════════════════════════════════════════════════════╝")

        glbBuffer.position(0)

        // Parse glb structure
        val glbData = parseGlbStructure(glbBuffer)

        // Parse JSON
        val rootJson = gson.fromJson(glbData.jsonString, JsonObject::class.java)

        // Analyze skins
        val skins = rootJson.getAsJsonArray("skins") ?: run {
            println("[GltfBoneOptimizer] WARNING: No skins found in glTF - returning original buffer")
            glbBuffer.position(0)
            return OptimizationResult(glbBuffer, 0, 0, 0, 0)
        }

        var originalBoneCount = 0
        var optimizedBoneCount = 0
        var skinsOptimized = 0

        println("[GltfBoneOptimizer] Found ${skins.size()} skins to analyze")

        // Check if optimization is needed
        var needsOptimization = false
        skins.forEach { skinElement ->
            val skin = skinElement.asJsonObject
            val joints = skin.getAsJsonArray("joints")
            val jointCount = joints?.size() ?: 0
            originalBoneCount += jointCount

            if (jointCount > maxBonesPerSkin) {
                needsOptimization = true
                println("[GltfBoneOptimizer] WARNING: Skin exceeds limit: $jointCount bones (max: $maxBonesPerSkin)")
            }
        }

        if (!needsOptimization) {
            println("[GltfBoneOptimizer] All skins within limit - no optimization needed")
            glbBuffer.position(0)
            return OptimizationResult(glbBuffer, originalBoneCount, originalBoneCount, 0, 0)
        }

        // Perform optimization
        println("[GltfBoneOptimizer] Starting bone optimization...")

        // For each skin, analyze bone usage
        val skinOptimizations = analyzeSkinBoneUsage(rootJson, glbData)

        skinOptimizations.forEach { (skinIndex, optimization) ->
            println("[GltfBoneOptimizer] Skin $skinIndex: ${optimization.originalJointCount} -> ${optimization.compactedJointCount} bones (saved ${optimization.bonesSaved})")
            optimizedBoneCount += optimization.compactedJointCount
            if (optimization.bonesSaved > 0) {
                skinsOptimized++
            }
        }

        // Apply optimization to JSON and BIN
        val optimizedGlbData = applyOptimization(rootJson, glbData, skinOptimizations)

        val bonesSaved = originalBoneCount - optimizedBoneCount

        println("[GltfBoneOptimizer] ╔═══════════════════════════════════════════════════════════╗")
        println("[GltfBoneOptimizer] ║                 Optimization Complete                     ║")
        println("[GltfBoneOptimizer] ╠═══════════════════════════════════════════════════════════╣")
        println("[GltfBoneOptimizer] ║ Original bones:    $originalBoneCount                                  ║")
        println("[GltfBoneOptimizer] ║ Optimized bones:   $optimizedBoneCount                                  ║")
        println("[GltfBoneOptimizer] ║ Bones saved:       $bonesSaved                                  ║")
        println("[GltfBoneOptimizer] ║ Skins optimized:   $skinsOptimized                                     ║")
        println("[GltfBoneOptimizer] ╚═══════════════════════════════════════════════════════════╝")

        return OptimizationResult(
            optimizedBuffer = optimizedGlbData,
            originalBoneCount = originalBoneCount,
            optimizedBoneCount = optimizedBoneCount,
            skinsOptimized = skinsOptimized,
            bonesSaved = bonesSaved
        )
    }

    /**
     * Parse glb file structure into JSON and BIN chunks
     */
    private fun parseGlbStructure(buffer: ByteBuffer): GlbData {
        buffer.position(0)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // Read header
        val magic = buffer.int
        if (magic != 0x46546C67) { // "glTF"
            throw IllegalArgumentException("Not a valid glTF file - magic: 0x${magic.toString(16)}")
        }

        val version = buffer.int
        val totalLength = buffer.int

        println("[GltfBoneOptimizer] glTF version: $version, total length: $totalLength")

        // Read JSON chunk
        val jsonLength = buffer.int
        val jsonType = buffer.int
        if (jsonType != 0x4E4F534A) { // "JSON"
            throw IllegalArgumentException("Expected JSON chunk, got: 0x${jsonType.toString(16)}")
        }

        val jsonBytes = ByteArray(jsonLength)
        buffer.get(jsonBytes)
        val jsonString = String(jsonBytes, Charsets.UTF_8)

        // Read BIN chunk (if exists)
        val binData = if (buffer.hasRemaining()) {
            val binLength = buffer.int
            val binType = buffer.int
            if (binType != 0x004E4942) { // "BIN\0"
                throw IllegalArgumentException("Expected BIN chunk, got: 0x${binType.toString(16)}")
            }

            val binBytes = ByteArray(binLength)
            buffer.get(binBytes)
            ByteBuffer.wrap(binBytes).order(ByteOrder.LITTLE_ENDIAN)
        } else {
            ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN)
        }

        return GlbData(jsonString, binData, version)
    }

    /**
     * Analyze bone usage for each skin
     */
    private fun analyzeSkinBoneUsage(
        rootJson: JsonObject,
        glbData: GlbData
    ): Map<Int, SkinOptimization> {
        val skins = rootJson.getAsJsonArray("skins")
        val nodes = rootJson.getAsJsonArray("nodes")
        val meshes = rootJson.getAsJsonArray("meshes")
        val accessors = rootJson.getAsJsonArray("accessors")
        val bufferViews = rootJson.getAsJsonArray("bufferViews")

        // Extract VRM humanoid bone mappings
        val humanoidBoneNodeIndices = extractVrmHumanoidBones(rootJson)
        println("[GltfBoneOptimizer] Found ${humanoidBoneNodeIndices.size} VRM humanoid bones to protect")

        val skinOptimizations = mutableMapOf<Int, SkinOptimization>()

        skins.forEachIndexed { skinIndex, skinElement ->
            val skin = skinElement.asJsonObject
            val joints = skin.getAsJsonArray("joints")
            val jointCount = joints.size()

            println("[GltfBoneOptimizer] ═══ Analyzing Skin $skinIndex (${jointCount} joints) ═══")

            // Find primitives using this skin
            val primitivesUsingSkin = findPrimitivesUsingSkin(skinIndex, nodes, meshes)
            println("[GltfBoneOptimizer] Found ${primitivesUsingSkin.size} primitives using this skin")

            // Scan vertex data to find used joints
            val usedJointIndices = mutableSetOf<Int>()

            primitivesUsingSkin.forEach { primitive ->
                val jointsAccessorIndex = primitive.getAsJsonObject("attributes")
                    ?.get("JOINTS_0")?.asInt
                val weightsAccessorIndex = primitive.getAsJsonObject("attributes")
                    ?.get("WEIGHTS_0")?.asInt

                if (jointsAccessorIndex != null && weightsAccessorIndex != null) {
                    val usedInPrimitive = scanPrimitiveForUsedJoints(
                        jointsAccessorIndex,
                        weightsAccessorIndex,
                        accessors,
                        bufferViews,
                        glbData.binData
                    )
                    usedJointIndices.addAll(usedInPrimitive)
                }
            }

            println("[GltfBoneOptimizer] Vertex data uses ${usedJointIndices.size} joints")

            // Add VRM humanoid bones (even if unused by vertices)
            joints.forEachIndexed { paletteIndex, jointElement ->
                val nodeIndex = jointElement.asInt
                if (humanoidBoneNodeIndices.contains(nodeIndex)) {
                    usedJointIndices.add(paletteIndex)
                    println("[GltfBoneOptimizer] Protected humanoid bone at palette index $paletteIndex (node $nodeIndex)")
                }
            }

            println("[GltfBoneOptimizer] Total bones to keep (including humanoid): ${usedJointIndices.size}")

            // Create compaction mapping
            val sortedUsedIndices = usedJointIndices.sorted()
            val oldToNewIndexMap = sortedUsedIndices.withIndex().associate { (newIndex, oldIndex) ->
                oldIndex to newIndex
            }

            skinOptimizations[skinIndex] = SkinOptimization(
                skinIndex = skinIndex,
                originalJointCount = jointCount,
                compactedJointCount = sortedUsedIndices.size,
                usedJointIndices = sortedUsedIndices,
                oldToNewIndexMap = oldToNewIndexMap,
                primitivesToRemap = primitivesUsingSkin
            )
        }

        return skinOptimizations
    }

    /**
     * Extract VRM 1.0 humanoid bone node indices
     */
    private fun extractVrmHumanoidBones(rootJson: JsonObject): Set<Int> {
        val nodeIndices = mutableSetOf<Int>()

        try {
            // VRM 1.0 format
            val vrmcVrm = rootJson.getAsJsonObject("extensions")
                ?.getAsJsonObject("VRMC_vrm")

            if (vrmcVrm != null) {
                val humanoid = vrmcVrm.getAsJsonObject("humanoid")
                val humanBones = humanoid?.getAsJsonObject("humanBones")

                humanBones?.entrySet()?.forEach { (boneName, boneData) ->
                    val boneObj = boneData.asJsonObject
                    val nodeIndex = boneObj.get("node")?.asInt

                    if (nodeIndex != null && essentialHumanoidBones.contains(boneName.lowercase())) {
                        nodeIndices.add(nodeIndex)
                        println("[GltfBoneOptimizer] VRM 1.0 humanoid bone: $boneName -> node $nodeIndex")
                    }
                }
            } else {
                // Try VRM 0.x format
                val vrm = rootJson.getAsJsonObject("extensions")?.getAsJsonObject("VRM")
                val humanoid = vrm?.getAsJsonObject("humanoid")
                val humanBones = humanoid?.getAsJsonArray("humanBones")

                humanBones?.forEach { boneElement ->
                    val boneObj = boneElement.asJsonObject
                    val boneName = boneObj.get("bone")?.asString?.lowercase()
                    val nodeIndex = boneObj.get("node")?.asInt

                    if (nodeIndex != null && boneName != null && essentialHumanoidBones.contains(boneName)) {
                        nodeIndices.add(nodeIndex)
                        println("[GltfBoneOptimizer] VRM 0.x humanoid bone: $boneName -> node $nodeIndex")
                    }
                }
            }
        } catch (e: Exception) {
            println("[GltfBoneOptimizer] WARNING: Failed to extract VRM humanoid bones: ${e.message}")
        }

        return nodeIndices
    }

    /**
     * Find all primitives that use a specific skin
     */
    private fun findPrimitivesUsingSkin(
        skinIndex: Int,
        nodes: JsonArray,
        meshes: JsonArray
    ): List<JsonObject> {
        val primitives = mutableListOf<JsonObject>()

        nodes.forEach { nodeElement ->
            val node = nodeElement.asJsonObject
            val nodeSkinIndex = node.get("skin")?.asInt
            val meshIndex = node.get("mesh")?.asInt

            if (nodeSkinIndex == skinIndex && meshIndex != null) {
                val mesh = meshes.get(meshIndex).asJsonObject
                val meshPrimitives = mesh.getAsJsonArray("primitives")

                meshPrimitives?.forEach { primitiveElement ->
                    primitives.add(primitiveElement.asJsonObject)
                }
            }
        }

        return primitives
    }

    /**
     * Scan primitive vertex data to find which joints are actually used
     */
    private fun scanPrimitiveForUsedJoints(
        jointsAccessorIndex: Int,
        weightsAccessorIndex: Int,
        accessors: JsonArray,
        bufferViews: JsonArray,
        binData: ByteBuffer
    ): Set<Int> {
        val usedJoints = mutableSetOf<Int>()

        val jointsAccessor = accessors.get(jointsAccessorIndex).asJsonObject
        val weightsAccessor = accessors.get(weightsAccessorIndex).asJsonObject

        val vertexCount = jointsAccessor.get("count").asInt

        // Read joints data
        val jointsData = readAccessorData(jointsAccessor, bufferViews, binData)
        val weightsData = readAccessorData(weightsAccessor, bufferViews, binData)

        // Scan each vertex
        for (vertexIndex in 0 until vertexCount) {
            for (slot in 0 until 4) { // 4 joints per vertex
                val offset = (vertexIndex * 4 + slot)
                val weight = weightsData.getFloat(offset * 4)

                if (weight > 0.001f) { // Threshold for meaningful weight
                    val jointIndex = when (jointsAccessor.get("componentType").asInt) {
                        5121 -> jointsData.get(offset).toInt() and 0xFF // UNSIGNED_BYTE
                        5123 -> jointsData.getShort(offset * 2).toInt() and 0xFFFF // UNSIGNED_SHORT
                        else -> jointsData.getShort(offset * 2).toInt() and 0xFFFF
                    }
                    usedJoints.add(jointIndex)
                }
            }
        }

        return usedJoints
    }

    /**
     * Read accessor data from binary buffer
     */
    private fun readAccessorData(
        accessor: JsonObject,
        bufferViews: JsonArray,
        binData: ByteBuffer
    ): ByteBuffer {
        val bufferViewIndex = accessor.get("bufferView").asInt
        val bufferView = bufferViews.get(bufferViewIndex).asJsonObject

        val byteOffset = (accessor.get("byteOffset")?.asInt ?: 0) +
                         (bufferView.get("byteOffset")?.asInt ?: 0)
        val byteLength = bufferView.get("byteLength").asInt

        val data = ByteArray(byteLength)
        binData.position(byteOffset)
        binData.get(data)

        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    }

    /**
     * Apply optimization to glTF JSON and BIN data
     */
    private fun applyOptimization(
        rootJson: JsonObject,
        glbData: GlbData,
        skinOptimizations: Map<Int, SkinOptimization>
    ): ByteBuffer {
        println("[GltfBoneOptimizer] Applying optimization to JSON and BIN data...")

        val accessors = rootJson.getAsJsonArray("accessors")
        val bufferViews = rootJson.getAsJsonArray("bufferViews")
        val skins = rootJson.getAsJsonArray("skins")

        // Create mutable copy of BIN data
        val newBinData = ByteBuffer.allocate(glbData.binData.capacity() + 100000) // Extra space for safety
        newBinData.order(ByteOrder.LITTLE_ENDIAN)
        var binWriteOffset = 0

        // Track accessor modifications
        val accessorUpdates = mutableMapOf<Int, AccessorUpdate>()

        // Step 1: Update skins and compact inverseBindMatrices
        skinOptimizations.forEach { (skinIndex, optimization) ->
            val skin = skins.get(skinIndex).asJsonObject

            // Update joints array
            val oldJoints = skin.getAsJsonArray("joints")
            val newJoints = JsonArray()
            optimization.usedJointIndices.forEach { oldIndex ->
                newJoints.add(oldJoints.get(oldIndex))
            }
            skin.add("joints", newJoints)

            println("[GltfBoneOptimizer] Skin $skinIndex: Updated joints array (${oldJoints.size()} -> ${newJoints.size()})")

            // Compact inverseBindMatrices
            val inverseBindMatricesAccessorIndex = skin.get("inverseBindMatrices")?.asInt
            if (inverseBindMatricesAccessorIndex != null) {
                val compactedMatrices = compactInverseBindMatrices(
                    inverseBindMatricesAccessorIndex,
                    optimization.usedJointIndices,
                    accessors,
                    bufferViews,
                    glbData.binData,
                    newBinData,
                    binWriteOffset
                )

                accessorUpdates[inverseBindMatricesAccessorIndex] = compactedMatrices
                binWriteOffset = compactedMatrices.newByteOffset + compactedMatrices.newByteLength
            }

            // Remap JOINTS_0 in primitives
            optimization.primitivesToRemap.forEach { primitive ->
                val jointsAccessorIndex = primitive.getAsJsonObject("attributes")
                    ?.get("JOINTS_0")?.asInt

                if (jointsAccessorIndex != null && !accessorUpdates.containsKey(jointsAccessorIndex)) {
                    val remappedJoints = remapJointsData(
                        jointsAccessorIndex,
                        optimization.oldToNewIndexMap,
                        accessors,
                        bufferViews,
                        glbData.binData,
                        newBinData,
                        binWriteOffset
                    )

                    accessorUpdates[jointsAccessorIndex] = remappedJoints
                    binWriteOffset = remappedJoints.newByteOffset + remappedJoints.newByteLength
                }
            }
        }

        // Copy ALL remaining buffer views that weren't modified
        // We need to copy position data, normal data, texture coordinates, etc.
        val modifiedAccessors = accessorUpdates.keys

        bufferViews.forEachIndexed { bufferViewIndex, bufferViewElement ->
            val bufferView = bufferViewElement.asJsonObject

            // Check if this bufferView is used by any accessor we modified
            val isModified = accessors.any { accessorElement ->
                val accessor = accessorElement.asJsonObject
                val accessorBVIndex = accessor.get("bufferView")?.asInt
                val accessorIndex = accessors.indexOf(accessorElement)

                accessorBVIndex == bufferViewIndex && modifiedAccessors.contains(accessorIndex)
            }

            if (!isModified) {
                // Copy this bufferView's data unchanged
                val oldByteOffset = bufferView.get("byteOffset")?.asInt ?: 0
                val byteLength = bufferView.get("byteLength").asInt

                glbData.binData.position(oldByteOffset)
                val data = ByteArray(byteLength)
                glbData.binData.get(data)

                newBinData.position(binWriteOffset)
                newBinData.put(data)

                // Update bufferView offset
                bufferView.addProperty("byteOffset", binWriteOffset)

                binWriteOffset += byteLength
            }
        }

        // Step 2: Update accessors and bufferViews
        accessorUpdates.forEach { (accessorIndex, update) ->
            val accessor = accessors.get(accessorIndex).asJsonObject
            val bufferViewIndex = accessor.get("bufferView").asInt
            val bufferView = bufferViews.get(bufferViewIndex).asJsonObject

            // Update bufferView
            bufferView.addProperty("byteOffset", update.newByteOffset)
            bufferView.addProperty("byteLength", update.newByteLength)

            // Update accessor count if changed
            if (update.newCount != null) {
                accessor.addProperty("count", update.newCount)
            }

            // Clear accessor byteOffset (it's now relative to bufferView)
            if (accessor.has("byteOffset")) {
                accessor.addProperty("byteOffset", 0)
            }
        }

        // Step 3: Update buffer length
        val buffers = rootJson.getAsJsonArray("buffers")
        if (buffers.size() > 0) {
            val buffer = buffers.get(0).asJsonObject
            buffer.addProperty("byteLength", binWriteOffset)
        }

        // Step 4: Rebuild glb file
        val optimizedBuffer = rebuildGlbFile(rootJson, newBinData, binWriteOffset, glbData.version)

        println("[GltfBoneOptimizer] Optimization applied successfully")
        return optimizedBuffer
    }

    /**
     * Compact inverse bind matrices to only include used joints
     */
    private fun compactInverseBindMatrices(
        accessorIndex: Int,
        usedJointIndices: List<Int>,
        accessors: JsonArray,
        bufferViews: JsonArray,
        sourceBin: ByteBuffer,
        destBin: ByteBuffer,
        writeOffset: Int
    ): AccessorUpdate {
        val accessor = accessors.get(accessorIndex).asJsonObject
        val bufferViewIndex = accessor.get("bufferView").asInt
        val bufferView = bufferViews.get(bufferViewIndex).asJsonObject

        val byteOffset = (accessor.get("byteOffset")?.asInt ?: 0) +
                         (bufferView.get("byteOffset")?.asInt ?: 0)

        val matrixSize = 16 * 4 // 16 floats × 4 bytes = 64 bytes per matrix

        // Write compacted matrices
        destBin.position(writeOffset)
        usedJointIndices.forEach { oldIndex ->
            val matrixOffset = byteOffset + (oldIndex * matrixSize)
            sourceBin.position(matrixOffset)

            val matrix = ByteArray(matrixSize)
            sourceBin.get(matrix)
            destBin.put(matrix)
        }

        val newByteLength = usedJointIndices.size * matrixSize

        return AccessorUpdate(
            accessorIndex = accessorIndex,
            newByteOffset = writeOffset,
            newByteLength = newByteLength,
            newCount = usedJointIndices.size
        )
    }

    /**
     * Remap JOINTS_0 vertex data to new joint indices
     */
    private fun remapJointsData(
        accessorIndex: Int,
        oldToNewMap: Map<Int, Int>,
        accessors: JsonArray,
        bufferViews: JsonArray,
        sourceBin: ByteBuffer,
        destBin: ByteBuffer,
        writeOffset: Int
    ): AccessorUpdate {
        val accessor = accessors.get(accessorIndex).asJsonObject
        val bufferViewIndex = accessor.get("bufferView").asInt
        val bufferView = bufferViews.get(bufferViewIndex).asJsonObject

        val byteOffset = (accessor.get("byteOffset")?.asInt ?: 0) +
                         (bufferView.get("byteOffset")?.asInt ?: 0)
        val count = accessor.get("count").asInt
        val componentType = accessor.get("componentType").asInt

        sourceBin.position(byteOffset)
        destBin.position(writeOffset)

        when (componentType) {
            5121 -> { // UNSIGNED_BYTE
                for (i in 0 until (count * 4)) {
                    val oldIndex = sourceBin.get().toInt() and 0xFF
                    val newIndex = oldToNewMap[oldIndex] ?: 0
                    destBin.put(newIndex.toByte())
                }
            }
            5123 -> { // UNSIGNED_SHORT
                for (i in 0 until (count * 4)) {
                    val oldIndex = sourceBin.short.toInt() and 0xFFFF
                    val newIndex = oldToNewMap[oldIndex] ?: 0
                    destBin.putShort(newIndex.toShort())
                }
            }
        }

        val bytesPerComponent = if (componentType == 5121) 1 else 2
        val newByteLength = count * 4 * bytesPerComponent

        return AccessorUpdate(
            accessorIndex = accessorIndex,
            newByteOffset = writeOffset,
            newByteLength = newByteLength,
            newCount = null // Count doesn't change
        )
    }

    /**
     * Rebuild glb file with optimized JSON and BIN chunks
     */
    private fun rebuildGlbFile(
        rootJson: JsonObject,
        binData: ByteBuffer,
        binLength: Int,
        version: Int
    ): ByteBuffer {
        // Serialize JSON
        val jsonString = gson.toJson(rootJson)
        val jsonBytes = jsonString.toByteArray(Charsets.UTF_8)

        // Pad JSON to 4-byte boundary
        val jsonPadding = (4 - (jsonBytes.size % 4)) % 4
        val jsonLength = jsonBytes.size + jsonPadding

        // Pad BIN to 4-byte boundary
        val binPadding = (4 - (binLength % 4)) % 4
        val totalBinLength = binLength + binPadding

        // Calculate total length
        val totalLength = 12 + // Header
                          8 + jsonLength + // JSON chunk header + data
                          8 + totalBinLength // BIN chunk header + data

        // Build glb
        val glbBuffer = ByteBuffer.allocate(totalLength)
        glbBuffer.order(ByteOrder.LITTLE_ENDIAN)

        // Header
        glbBuffer.putInt(0x46546C67) // "glTF"
        glbBuffer.putInt(version)
        glbBuffer.putInt(totalLength)

        // JSON chunk
        glbBuffer.putInt(jsonLength)
        glbBuffer.putInt(0x4E4F534A) // "JSON"
        glbBuffer.put(jsonBytes)
        repeat(jsonPadding) { glbBuffer.put(0x20.toByte()) } // Space padding

        // BIN chunk
        glbBuffer.putInt(totalBinLength)
        glbBuffer.putInt(0x004E4942) // "BIN\0"
        binData.position(0)
        binData.limit(binLength)
        glbBuffer.put(binData)
        repeat(binPadding) { glbBuffer.put(0x00.toByte()) } // Zero padding

        glbBuffer.position(0)
        return glbBuffer
    }

    /**
     * Accessor update information
     */
    private data class AccessorUpdate(
        val accessorIndex: Int,
        val newByteOffset: Int,
        val newByteLength: Int,
        val newCount: Int? // null if count doesn't change
    )

    /**
     * Data classes
     */
    private data class GlbData(
        val jsonString: String,
        val binData: ByteBuffer,
        val version: Int
    )

    private data class SkinOptimization(
        val skinIndex: Int,
        val originalJointCount: Int,
        val compactedJointCount: Int,
        val usedJointIndices: List<Int>,
        val oldToNewIndexMap: Map<Int, Int>,
        val primitivesToRemap: List<JsonObject>
    ) {
        val bonesSaved: Int get() = originalJointCount - compactedJointCount
    }
}
