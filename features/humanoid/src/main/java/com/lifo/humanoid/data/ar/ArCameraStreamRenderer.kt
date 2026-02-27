package com.lifo.humanoid.data.ar

import android.util.Log
import com.google.android.filament.Engine
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.RenderableManager
import com.google.android.filament.Scene
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Renders the ARCore camera feed as a full-screen background in Filament.
 *
 * Architecture follows SceneView/sceneview-android's ARCameraStream.kt:
 * - importTexture() for zero-copy OES texture sharing
 * - Oversized triangle in device domain (3 vertices covers entire screen)
 * - Separate position + UV vertex buffers
 * - UV buffer updated per-frame via Frame.transformCoordinates2d()
 * - Fragment shader reads getUV0() directly (no custom variable)
 *
 * Requires Engine.create(sharedEglContext) for GL texture namespace sharing.
 */
class ArCameraStreamRenderer(
    private val engine: Engine,
    private val scene: Scene,
    private val cameraTextureId: Int
) {
    companion object {
        private const val TAG = "ArCameraStreamRenderer"
        private const val VERTEX_COUNT = 3
        private const val POSITION_BUFFER_INDEX = 0
        private const val UV_BUFFER_INDEX = 1
        private const val FLOAT_SIZE = 4 // bytes per float

        // Oversized triangle in device coordinates [-1,1] with z=1.0 (far plane).
        // A single triangle that extends beyond the screen clips to fill it entirely.
        // Same geometry as SceneView's ARCameraStream.kt and Sceneform's CameraStream.java.
        private val VERTICES = floatArrayOf(
            -1.0f,  1.0f, 1.0f,  // top-left
            -1.0f, -3.0f, 1.0f,  // bottom (extended 2x past screen)
             3.0f,  1.0f, 1.0f   // right (extended 2x past screen)
        )

        // Initial UVs in VIEW_NORMALIZED space: (0,0)=top-left, (1,1)=bottom-right.
        // These are overwritten every frame by transformCoordinates2d() output.
        val INPUT_UVS = floatArrayOf(
            0.0f, 0.0f,  // top-left
            0.0f, 2.0f,  // bottom (extended to match triangle)
            2.0f, 0.0f   // right (extended to match triangle)
        )

        private val INDICES = shortArrayOf(0, 1, 2)
    }

    private var filamentTexture: Texture? = null
    private var material: Material? = null
    private var materialInstance: MaterialInstance? = null
    private var vertexBuffer: VertexBuffer? = null
    private var indexBuffer: IndexBuffer? = null
    private var entity: Int = 0
    private var isInitialized = false

    /**
     * Initialize the camera background rendering pipeline.
     */
    fun initialize() {
        if (isInitialized) return

        try {
            // Import camera texture (non-fatal: diagnostic/fallback materials can work without it)
            try {
                importCameraTexture()
            } catch (e: Exception) {
                Log.e(TAG, "importCameraTexture failed (non-fatal): ${e.message}")
                filamentTexture = null
            }

            createMaterial()
            createGeometry()
            isInitialized = true
            Log.d(TAG, "Camera stream renderer initialized! entity=$entity, " +
                "texture=${filamentTexture != null}, material=${material != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera stream renderer", e)
            cleanup()
        }
    }

    /**
     * Update UV coordinates for the current frame.
     *
     * @param uvs 6 floats: 3 pairs of (u,v) — already transformed by
     *            Frame.transformCoordinates2d() and V-flipped for OpenGL.
     *            Null means no update (keep previous UVs).
     */
    fun updateUvs(uvs: FloatArray?) {
        if (!isInitialized || uvs == null || uvs.size < VERTEX_COUNT * 2) return

        val buffer = ByteBuffer.allocateDirect(uvs.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
        buffer.asFloatBuffer().put(uvs)
        buffer.rewind()

        vertexBuffer?.setBufferAt(engine, UV_BUFFER_INDEX, buffer)
    }

    fun cleanup() {
        isInitialized = false

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

        // destroyTexture releases the Filament wrapper but does NOT delete the
        // underlying GL texture (because it was imported, not created by Filament).
        filamentTexture?.let { engine.destroyTexture(it) }
        filamentTexture = null

        Log.d(TAG, "Camera stream renderer cleaned up")
    }

    // --- Private implementation ---

    private fun importCameraTexture() {
        filamentTexture = Texture.Builder()
            .sampler(Texture.Sampler.SAMPLER_EXTERNAL)
            .format(Texture.InternalFormat.RGB8)
            .importTexture(cameraTextureId.toLong())
            .build(engine)

        Log.d(TAG, "Imported OES texture $cameraTextureId into Filament (zero-copy)")
    }

    /**
     * Create the camera background material — matches SceneView's camera_stream_flat.mat
     * and zirman/arcore-filament-example-app's flat.mat exactly.
     *
     * Critical details from working reference implementations:
     * - vertexDomain: DEVICE (clip-space coordinates, no model/view/projection)
     * - Vertex shader: push clip.z to 0.9999 (NOT 1.0, which gets depth-clipped on some drivers)
     * - Vertex shader: set worldPosition from getWorldFromClipMatrix() (required by Filament)
     * - Fragment shader: inverseTonemapSRGB() to counteract Filament's tonemapping
     * - depthWrite=false, depthCulling=false, blending=opaque
     */
    private fun createMaterial() {
        MaterialBuilder.init()

        try {
            val matBuilder = MaterialBuilder()
                .name("ArCameraBackground")
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.OPENGL)
                .optimization(MaterialBuilder.Optimization.PERFORMANCE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .vertexDomain(MaterialBuilder.VertexDomain.DEVICE)
                .depthWrite(false)
                .depthCulling(false)
                .doubleSided(true)
                .require(MaterialBuilder.VertexAttribute.UV0)
                .samplerParameter(
                    MaterialBuilder.SamplerType.SAMPLER_EXTERNAL,
                    MaterialBuilder.SamplerFormat.FLOAT,
                    MaterialBuilder.ParameterPrecision.DEFAULT,
                    "cameraTexture"
                )
                .materialVertex(
                    """
                    void materialVertex(inout MaterialVertexInputs material) {
                        vec4 clip = getPosition();
                        clip.z = 0.9999;
                        material.worldPosition = mulMat4x4Float3(
                            getWorldFromClipMatrix(), clip.xyz);
                    }
                    """.trimIndent()
                )
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        vec4 color = texture(materialParams_cameraTexture, getUV0());
                        material.baseColor.rgb = inverseTonemapSRGB(color.rgb);
                        material.baseColor.a = 1.0;
                    }
                    """.trimIndent()
                )

            Log.d(TAG, "Building camera material (SceneView pattern)...")
            val matPackage: MaterialPackage = matBuilder.build()
            if (!matPackage.isValid) {
                Log.e(TAG, "Camera material FAILED to build — trying fallback")
                buildFallbackMaterial()
                return
            }

            val matData = matPackage.buffer
            material = Material.Builder()
                .payload(matData, matData.remaining())
                .build(engine)

            materialInstance = material!!.createInstance()

            if (filamentTexture != null) {
                materialInstance!!.setParameter(
                    "cameraTexture",
                    filamentTexture!!,
                    TextureSampler(
                        TextureSampler.MinFilter.LINEAR,
                        TextureSampler.MagFilter.LINEAR,
                        TextureSampler.WrapMode.CLAMP_TO_EDGE
                    )
                )
                Log.d(TAG, "Camera material ready (vertex shader + inverseTonemapSRGB + texture bound)")
            } else {
                Log.w(TAG, "Camera material ready but NO texture — will show black until texture is available")
            }
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    /**
     * Fallback material without vertex shader, in case materialVertex() is not available
     * in this Filament version. Still uses inverseTonemapSRGB in fragment.
     */
    private fun buildFallbackMaterial() {
        Log.w(TAG, "Building fallback material (no vertex shader)")

        MaterialBuilder.init()
        try {
            val matBuilder = MaterialBuilder()
                .name("ArCameraBackgroundFallback")
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.OPENGL)
                .optimization(MaterialBuilder.Optimization.PERFORMANCE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .vertexDomain(MaterialBuilder.VertexDomain.DEVICE)
                .depthWrite(false)
                .depthCulling(false)
                .doubleSided(true)
                .require(MaterialBuilder.VertexAttribute.UV0)
                .samplerParameter(
                    MaterialBuilder.SamplerType.SAMPLER_EXTERNAL,
                    MaterialBuilder.SamplerFormat.FLOAT,
                    MaterialBuilder.ParameterPrecision.DEFAULT,
                    "cameraTexture"
                )
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        vec4 color = texture(materialParams_cameraTexture, getUV0());
                        material.baseColor.rgb = inverseTonemapSRGB(color.rgb);
                        material.baseColor.a = 1.0;
                    }
                    """.trimIndent()
                )

            val matPackage: MaterialPackage = matBuilder.build()
            if (!matPackage.isValid) {
                Log.e(TAG, "Fallback material also failed to build!")
                return
            }

            val matData = matPackage.buffer
            material = Material.Builder()
                .payload(matData, matData.remaining())
                .build(engine)

            materialInstance = material!!.createInstance()
            materialInstance!!.setParameter(
                "cameraTexture",
                filamentTexture!!,
                TextureSampler(
                    TextureSampler.MinFilter.LINEAR,
                    TextureSampler.MagFilter.LINEAR,
                    TextureSampler.WrapMode.CLAMP_TO_EDGE
                )
            )

            Log.d(TAG, "Fallback camera material created (no vertex shader, with inverseTonemapSRGB)")
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    /**
     * Create the oversized fullscreen triangle geometry.
     *
     * Two separate vertex buffers:
     * - Buffer 0: POSITION (FLOAT3) — static, never changes
     * - Buffer 1: UV0 (FLOAT2) — updated per-frame by updateUvs()
     */
    private fun createGeometry() {
        // Position buffer (static)
        val posData = ByteBuffer.allocateDirect(VERTICES.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
        posData.asFloatBuffer().put(VERTICES)
        posData.rewind()

        // UV buffer (initial values, overwritten per-frame)
        val uvData = ByteBuffer.allocateDirect(INPUT_UVS.size * FLOAT_SIZE)
            .order(ByteOrder.nativeOrder())
        uvData.asFloatBuffer().put(INPUT_UVS)
        uvData.rewind()

        vertexBuffer = VertexBuffer.Builder()
            .vertexCount(VERTEX_COUNT)
            .bufferCount(2)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION,
                POSITION_BUFFER_INDEX,
                VertexBuffer.AttributeType.FLOAT3,
                0,
                3 * FLOAT_SIZE  // stride
            )
            .attribute(
                VertexBuffer.VertexAttribute.UV0,
                UV_BUFFER_INDEX,
                VertexBuffer.AttributeType.FLOAT2,
                0,
                2 * FLOAT_SIZE  // stride
            )
            .build(engine)

        vertexBuffer!!.setBufferAt(engine, POSITION_BUFFER_INDEX, posData)
        vertexBuffer!!.setBufferAt(engine, UV_BUFFER_INDEX, uvData)

        // Index buffer
        val indexData = ByteBuffer.allocateDirect(INDICES.size * 2)
            .order(ByteOrder.nativeOrder())
        indexData.asShortBuffer().put(INDICES)
        indexData.rewind()

        indexBuffer = IndexBuffer.Builder()
            .indexCount(INDICES.size)
            .bufferType(IndexBuffer.Builder.IndexType.USHORT)
            .build(engine)
        indexBuffer!!.setBuffer(engine, indexData)

        // Renderable entity — priority 0 = rendered FIRST = behind all 3D content.
        // In Filament, lower priority = drawn first = appears behind higher priorities.
        // Previously was priority(7) which drew LAST, overwriting the avatar!
        entity = EntityManager.get().create()

        RenderableManager.Builder(1)
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer!!, indexBuffer!!)
            .material(0, materialInstance!!)
            .culling(false)
            .receiveShadows(false)
            .castShadows(false)
            .priority(0)
            .build(engine, entity)

        scene.addEntity(entity)
        Log.d(TAG, "Fullscreen camera triangle created (3 vertices, device domain)")
    }
}
