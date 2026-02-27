package com.lifo.humanoid.data.ar

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.TransformManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage
import com.lifo.humanoid.domain.ar.ArHitResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin

/**
 * A 3D ring indicator that hovers on detected planes at the screen center.
 * Standard ARCore UX pattern — gives the user direct visual feedback about
 * where the avatar will be placed.
 *
 * Geometry: flat ring (annulus) with 32 segments.
 * Material: unlit, translucent green when tracking, faded when lost.
 * Position is smoothly interpolated (lerp) for natural movement.
 *
 * Priority 5 — between avatar (4) and planes (6).
 */
class ArFocusReticle(
    private val engine: Engine,
    private val scene: Scene
) {
    companion object {
        private const val TAG = "ArFocusReticle"
        private const val FLOAT_SIZE = 4
        private const val SEGMENTS = 32
        private const val OUTER_RADIUS = 0.08f  // 8cm outer radius
        private const val INNER_RADIUS = 0.06f  // 6cm inner radius (2cm ring width)
        // Priority 2: after camera (0) and planes (1), before avatar (default 4)
        private const val RENDER_PRIORITY = 2
        private const val LERP_SPEED = 8.0f      // Position smoothing factor (slower = smoother)
    }

    private var material: Material? = null
    private var materialInstance: MaterialInstance? = null
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var entity: Int = 0

    private var isInitialized = false
    private var isVisible = true
    private var isTracking = false

    // Smoothed position (lerped)
    private val currentPosition = floatArrayOf(0f, 0f, 0f)
    private val currentNormal = floatArrayOf(0f, 1f, 0f)
    private var hasPosition = false

    fun initialize() {
        if (isInitialized) return
        try {
            createMaterial()
            createGeometry()
            isInitialized = true
            Log.d(TAG, "FocusReticle initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize FocusReticle", e)
        }
    }

    /**
     * Update the reticle position based on center-screen hit-test result.
     * Called every frame from ArFilamentRenderer.render().
     *
     * @param hitResult Center-screen hit result, or null if no surface found
     * @param deltaSeconds Time since last frame for smooth interpolation
     */
    fun updatePosition(hitResult: ArHitResult?, deltaSeconds: Float) {
        if (!isInitialized || !isVisible) return

        if (hitResult != null) {
            // Extract position from hit pose (column-major 4x4 matrix)
            val targetX = hitResult.hitPose[12]
            val targetY = hitResult.hitPose[13]
            val targetZ = hitResult.hitPose[14]

            if (!hasPosition) {
                // First hit — snap to position
                currentPosition[0] = targetX
                currentPosition[1] = targetY
                currentPosition[2] = targetZ
                hasPosition = true
            } else {
                // Smooth interpolation
                val t = (LERP_SPEED * deltaSeconds).coerceIn(0f, 1f)
                currentPosition[0] += (targetX - currentPosition[0]) * t
                currentPosition[1] += (targetY - currentPosition[1]) * t
                currentPosition[2] += (targetZ - currentPosition[2]) * t
            }

            // Extract up vector (Y axis of pose) for orientation
            // Column 1 of the 4x4 matrix: indices 4,5,6
            currentNormal[0] = hitResult.hitPose[4]
            currentNormal[1] = hitResult.hitPose[5]
            currentNormal[2] = hitResult.hitPose[6]

            applyTransform()

            if (!isTracking) {
                isTracking = true
                showEntity(true)
            }
        } else {
            if (isTracking) {
                isTracking = false
                showEntity(false)
            }
        }
    }

    fun setVisible(visible: Boolean) {
        isVisible = visible
        if (!visible) {
            showEntity(false)
            hasPosition = false
        }
    }

    fun cleanup() {
        if (entity != 0) {
            scene.removeEntity(entity)
            engine.destroyEntity(entity)
            EntityManager.get().destroy(entity)
            entity = 0
        }

        materialInstance?.let { engine.destroyMaterialInstance(it) }
        materialInstance = null
        material?.let { engine.destroyMaterial(it) }
        material = null
        vertexBuffer?.let { engine.destroyVertexBuffer(it) }
        vertexBuffer = null
        indexBuffer?.let { engine.destroyIndexBuffer(it) }
        indexBuffer = null

        isInitialized = false
        hasPosition = false
        Log.d(TAG, "FocusReticle cleaned up")
    }

    // --- Private ---

    private fun createMaterial() {
        MaterialBuilder.init()
        try {
            val matBuilder = MaterialBuilder()
                .name("ArFocusReticle")
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.OPENGL)
                .optimization(MaterialBuilder.Optimization.PERFORMANCE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
                // NOTE: Do NOT use TWO_PASSES_ONE_SIDE here — broke rendering on device
                .depthWrite(false)
                .depthCulling(false)  // Don't cull against depth — always visible
                .doubleSided(true)
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor = vec4(0.3, 0.9, 0.4, 0.7);
                    }
                    """.trimIndent()
                )

            val matPackage: MaterialPackage = matBuilder.build()
            if (!matPackage.isValid) {
                Log.e(TAG, "Reticle material failed to build")
                return
            }

            val matData = matPackage.buffer
            material = Material.Builder()
                .payload(matData, matData.remaining())
                .build(engine)

            materialInstance = material!!.createInstance()
            Log.d(TAG, "Reticle material created (green translucent)")
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    /**
     * Create a flat ring (annulus) geometry in the XZ plane.
     * Two concentric circles with SEGMENTS points each, connected by triangles.
     */
    private fun createGeometry() {
        val matInst = materialInstance ?: return

        val vertexCount = SEGMENTS * 2 // inner + outer ring
        val triangleCount = SEGMENTS * 2 // 2 triangles per segment

        // Generate vertices
        val positions = FloatArray(vertexCount * 3)
        for (i in 0 until SEGMENTS) {
            val angle = (2.0 * Math.PI * i / SEGMENTS).toFloat()
            val cosA = cos(angle)
            val sinA = sin(angle)

            // Outer vertex
            val outerIdx = i * 2
            positions[outerIdx * 3 + 0] = cosA * OUTER_RADIUS
            positions[outerIdx * 3 + 1] = 0.001f  // Slight Y offset to avoid z-fighting with plane
            positions[outerIdx * 3 + 2] = sinA * OUTER_RADIUS

            // Inner vertex
            val innerIdx = i * 2 + 1
            positions[innerIdx * 3 + 0] = cosA * INNER_RADIUS
            positions[innerIdx * 3 + 1] = 0.001f
            positions[innerIdx * 3 + 2] = sinA * INNER_RADIUS
        }

        // Generate indices (triangle strip around the ring)
        val indices = ShortArray(triangleCount * 3)
        for (i in 0 until SEGMENTS) {
            val outer = (i * 2).toShort()
            val inner = (i * 2 + 1).toShort()
            val nextOuter = (((i + 1) % SEGMENTS) * 2).toShort()
            val nextInner = (((i + 1) % SEGMENTS) * 2 + 1).toShort()

            val baseIdx = i * 6
            // Triangle 1: outer, nextOuter, inner
            indices[baseIdx + 0] = outer
            indices[baseIdx + 1] = nextOuter
            indices[baseIdx + 2] = inner
            // Triangle 2: inner, nextOuter, nextInner
            indices[baseIdx + 3] = inner
            indices[baseIdx + 4] = nextOuter
            indices[baseIdx + 5] = nextInner
        }

        val posData = ByteBuffer.allocateDirect(positions.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
        posData.asFloatBuffer().put(positions)
        posData.rewind()

        val indexData = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
        indexData.asShortBuffer().put(indices)
        indexData.rewind()

        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(vertexCount)
            .bufferCount(1)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION, 0,
                VertexBuffer.AttributeType.FLOAT3, 0, 3 * FLOAT_SIZE
            )
            .build(engine)
        vertexBuffer!!.setBufferAt(engine, 0, posData)

        indexBuffer = IndexBuffer.Builder()
            .indexCount(indices.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer!!.setBuffer(engine, indexData)

        entity = EntityManager.get().create()

        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer!!, indexBuffer!!)
            .material(0, matInst)
            .culling(false)
            .receiveShadows(false)
            .castShadows(false)
            .priority(RENDER_PRIORITY)
            .build(engine, entity)

        // Start hidden until first hit
        // Don't add to scene yet — will be added on first tracking
        Log.d(TAG, "Reticle ring geometry created ($SEGMENTS segments, ${vertexCount} vertices)")
    }

    private fun applyTransform() {
        if (entity == 0) return
        val tm = engine.transformManager
        var instance = tm.getInstance(entity)
        if (instance == 0) {
            tm.create(entity)
            instance = tm.getInstance(entity)
        }
        if (instance == 0) return

        // Build a transform matrix that positions the ring at currentPosition
        // oriented to lie flat on the surface (Y-up = surface normal)
        val transform = FloatArray(16)
        // Identity
        transform[0] = 1f; transform[5] = 1f; transform[10] = 1f; transform[15] = 1f
        // Translation
        transform[12] = currentPosition[0]
        transform[13] = currentPosition[1]
        transform[14] = currentPosition[2]

        tm.setTransform(instance, transform)
    }

    private fun showEntity(show: Boolean) {
        if (entity == 0) return
        if (show) {
            // Add to scene if not already there
            scene.addEntity(entity)
        } else {
            scene.removeEntity(entity)
        }
    }
}
