package com.lifo.humanoid.data.ar

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.VertexBuffer
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.lifo.humanoid.domain.ar.ArPlane

/**
 * Renders detected ARCore planes as semi-transparent meshes in the Filament scene.
 *
 * Material pattern follows zirman/arcore-filament-example-app:
 * - shading: UNLIT
 * - blending: TRANSPARENT
 * - transparency: TWO_PASSES_ONE_SIDE (correct translucency rendering)
 * - depthWrite: false (translucent overlay)
 * - depthCulling: true (properly occluded by avatar)
 *
 * Note: In Filament, ALL opaque objects render before ANY translucent objects
 * regardless of priority. Priority only orders within the translucent group.
 */
class ArPlaneRenderer(
    private val engine: Engine,
    private val scene: Scene
) {
    companion object {
        private const val TAG = "ArPlaneRenderer"
        private const val FLOAT_SIZE = 4
        // Grace period: keep planes alive for N frames after they disappear from tracking.
        // Prevents flickering when ARCore briefly loses/redetects planes.
        private const val PLANE_GRACE_FRAMES = 30 // ~500ms at 60fps
        // Position smoothing: lerp factor per frame to prevent planes from sliding/jumping.
        private const val PLANE_POSITION_LERP = 0.15f
    }

    private var material: Material? = null
    private var materialInstance: MaterialInstance? = null
    private val activePlanes = mutableMapOf<Long, PlaneRenderable>()
    private var isInitialized = false
    private var isVisible = true
    private var diagLogCount = 0

    private data class PlaneRenderable(
        val entity: Int,
        val vertexBuffer: VertexBuffer,
        val indexBuffer: IndexBuffer,
        var lastExtentX: Float,
        var lastExtentZ: Float,
        // Smoothed position for anti-slide
        val smoothedPose: FloatArray = FloatArray(16),
        var poseInitialized: Boolean = false,
        // Grace period counter: counts up when plane is absent from tracking list
        var graceMissedFrames: Int = 0
    )

    fun initialize() {
        if (isInitialized) return
        try {
            createMaterial()
            if (material != null && materialInstance != null) {
                isInitialized = true
                Log.d(TAG, "PlaneRenderer initialized OK")
            } else {
                Log.e(TAG, "PlaneRenderer FAILED: material=${material != null}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "PlaneRenderer init exception", e)
        }
    }

    fun updatePlanes(planes: List<ArPlane>) {
        if (!isInitialized || !isVisible) return

        val currentIds = planes.map { it.id }.toSet()

        // Grace period: increment missed-frame counter for absent planes,
        // only remove after PLANE_GRACE_FRAMES consecutive misses.
        // This prevents flickering when ARCore briefly loses/redetects planes.
        val toRemove = mutableListOf<Long>()
        for ((id, renderable) in activePlanes) {
            if (id !in currentIds) {
                renderable.graceMissedFrames++
                if (renderable.graceMissedFrames > PLANE_GRACE_FRAMES) {
                    toRemove.add(id)
                }
                // While in grace period, keep the plane visible at its last smoothed position
            } else {
                renderable.graceMissedFrames = 0
            }
        }
        toRemove.forEach { removePlane(it) }

        // Add or update tracked planes
        for (plane in planes) {
            val existing = activePlanes[plane.id]
            if (existing != null) {
                updatePlaneTransform(existing, plane)
                if (kotlin.math.abs(existing.lastExtentX - plane.extentX) > 0.05f ||
                    kotlin.math.abs(existing.lastExtentZ - plane.extentZ) > 0.05f) {
                    updatePlaneGeometry(existing, plane)
                }
            } else {
                createPlane(plane)
            }
        }

        if (diagLogCount < 10 || (diagLogCount % 180 == 0 && activePlanes.isNotEmpty())) {
            Log.d(TAG, "Planes rendered: ${activePlanes.size} (detected: ${planes.size}, grace=${activePlanes.count { it.value.graceMissedFrames > 0 }})")
        }
        diagLogCount++
    }

    fun setVisible(visible: Boolean) {
        if (isVisible == visible) return
        isVisible = visible
        if (!visible) {
            activePlanes.keys.toList().forEach { removePlane(it) }
        }
        Log.d(TAG, "Visibility=$visible")
    }

    fun cleanup() {
        activePlanes.keys.toList().forEach { removePlane(it) }
        materialInstance?.let { engine.destroyMaterialInstance(it) }
        materialInstance = null
        material?.let { engine.destroyMaterial(it) }
        material = null
        isInitialized = false
        Log.d(TAG, "Cleaned up")
    }

    // --- Material ---

    /**
     * Absolute minimum translucent white material.
     * No UV, no grid, no twoPassesOneSide — just a flat semi-transparent overlay.
     * Maximum device compatibility.
     */
    private fun createMaterial() {
        MaterialBuilder.init()
        try {
            val matBuilder = MaterialBuilder()
                .name("ArPlane")
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.OPENGL)
                .optimization(MaterialBuilder.Optimization.PERFORMANCE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
                // NOTE: No twoPassesOneSide, no UV requirement — absolute minimum
                .depthWrite(false)
                .depthCulling(false)  // Always visible (no depth conflict with camera)
                .doubleSided(true)
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor = vec4(1.0, 1.0, 1.0, 0.15);
                    }
                    """.trimIndent()
                )

            val matPackage: MaterialPackage = matBuilder.build()
            if (!matPackage.isValid) {
                Log.e(TAG, "!!! Plane material FAILED to build — planes will NOT render")
                return
            }

            val matData = matPackage.buffer
            material = Material.Builder()
                .payload(matData, matData.remaining())
                .build(engine)
            materialInstance = material!!.createInstance()
            Log.d(TAG, "Plane material OK (flat white, alpha=0.15)")
        } catch (e: Exception) {
            Log.e(TAG, "Plane material exception: ${e.message}", e)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    // --- Geometry ---

    private fun createPlane(plane: ArPlane) {
        val matInst = materialInstance
        if (matInst == null) {
            Log.e(TAG, "!!! createPlane: materialInstance is NULL — material build failed")
            return
        }

        val halfX = plane.extentX / 2f
        val halfZ = plane.extentZ / 2f

        // Quad in local XZ plane at Y=0. CenterPose transform positions in world.
        // No UV needed — material is just a flat solid color.
        val positions = floatArrayOf(
            -halfX, 0f, -halfZ,
             halfX, 0f, -halfZ,
             halfX, 0f,  halfZ,
            -halfX, 0f,  halfZ
        )
        val indices = shortArrayOf(0, 2, 1, 0, 3, 2)

        val posBuffer = ByteBuffer.allocateDirect(positions.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
        posBuffer.asFloatBuffer().put(positions)
        posBuffer.rewind()

        val indexData = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
        indexData.asShortBuffer().put(indices)
        indexData.rewind()

        val vertexBuffer = VertexBuffer.Builder()
            .vertexCount(4)
            .bufferCount(1)
            .attribute(VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 3 * FLOAT_SIZE)
            .build(engine)
        vertexBuffer.setBufferAt(engine, 0, posBuffer)

        val indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer.setBuffer(engine, indexData)

        val entity = EntityManager.get().create()

        // Transform: place at plane's world pose
        val tm = engine.transformManager
        tm.create(entity)
        val tmInstance = tm.getInstance(entity)
        if (tmInstance != 0) {
            tm.setTransform(tmInstance, plane.centerPose)
        }

        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
            .material(0, matInst)
            .culling(false)
            .receiveShadows(false)
            .castShadows(false)
            .build(engine, entity)

        scene.addEntity(entity)

        activePlanes[plane.id] = PlaneRenderable(
            entity = entity,
            vertexBuffer = vertexBuffer,
            indexBuffer = indexBuffer,
            lastExtentX = plane.extentX,
            lastExtentZ = plane.extentZ,
            smoothedPose = plane.centerPose.copyOf(),
            poseInitialized = true
        )

        Log.d(TAG, "Plane CREATED id=${plane.id}: ${plane.extentX}x${plane.extentZ}m at " +
            "(${plane.centerPose[12]}, ${plane.centerPose[13]}, ${plane.centerPose[14]})")
    }

    private fun updatePlaneTransform(renderable: PlaneRenderable, plane: ArPlane) {
        val tm = engine.transformManager
        val instance = tm.getInstance(renderable.entity)
        if (instance == 0) return

        if (!renderable.poseInitialized) {
            // First frame: snap to position
            plane.centerPose.copyInto(renderable.smoothedPose)
            renderable.poseInitialized = true
        } else {
            // Subsequent frames: lerp position to prevent sliding/jumping.
            // Only smooth translation (indices 12,13,14) and rotation columns.
            val t = PLANE_POSITION_LERP
            for (i in 0..15) {
                renderable.smoothedPose[i] += (plane.centerPose[i] - renderable.smoothedPose[i]) * t
            }
        }

        tm.setTransform(instance, renderable.smoothedPose)
    }

    private fun updatePlaneGeometry(renderable: PlaneRenderable, plane: ArPlane) {
        val halfX = plane.extentX / 2f
        val halfZ = plane.extentZ / 2f

        val positions = floatArrayOf(
            -halfX, 0f, -halfZ,  halfX, 0f, -halfZ,
             halfX, 0f,  halfZ, -halfX, 0f,  halfZ
        )

        val posBuffer = ByteBuffer.allocateDirect(positions.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
        posBuffer.asFloatBuffer().put(positions)
        posBuffer.rewind()
        renderable.vertexBuffer.setBufferAt(engine, 0, posBuffer)

        renderable.lastExtentX = plane.extentX
        renderable.lastExtentZ = plane.extentZ
    }

    private fun removePlane(id: Long) {
        val renderable = activePlanes.remove(id) ?: return
        scene.removeEntity(renderable.entity)
        engine.destroyEntity(renderable.entity)
        EntityManager.get().destroy(renderable.entity)
        engine.destroyVertexBuffer(renderable.vertexBuffer)
        engine.destroyIndexBuffer(renderable.indexBuffer)
    }
}
