package com.lifo.humanoid.rendering

import com.google.android.filament.Engine
import com.google.android.filament.Material
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialBuilder.Shading
import com.google.android.filament.filamat.MaterialBuilder.Variable
import java.nio.ByteBuffer

/**
 * Builder for creating custom point cloud materials for Filament.
 * Designed specifically for rendering glTF POINTS primitives with vertex colors.
 *
 * This material:
 * - Uses UNLIT shading (no lighting calculations, like KHR_materials_unlit)
 * - Supports vertex colors (COLOR_0 attribute)
 * - Configurable point size via gl_PointSize
 * - Brightness multiplier for HDR effect
 */
object PointCloudMaterialBuilder {

    /**
     * Vertex shader code for point rendering.
     * Sets gl_PointSize and passes vertex color to fragment shader.
     */
    private const val VERTEX_SHADER = """
        void materialVertex(inout MaterialVertexInputs material) {
            // Set point size for star rendering
            // Can be controlled via material parameter
            gl_PointSize = materialParams.pointSize;
        }
    """

    /**
     * Fragment shader code for point rendering.
     * Uses vertex color as base color with brightness multiplier.
     */
    private const val FRAGMENT_SHADER = """
        void material(inout MaterialInputs material) {
            prepareMaterial(material);

            // Get vertex color from COLOR_0 attribute
            vec4 vertexColor = getColor();

            // Apply brightness multiplier for HDR-like effect
            vec3 color = vertexColor.rgb * materialParams.brightness;

            // Output as base color for unlit rendering
            material.baseColor.rgb = color;
            material.baseColor.a = vertexColor.a;
        }
    """

    /**
     * Create a point cloud material for rendering glTF POINTS primitives.
     *
     * @param engine Filament engine instance
     * @param pointSize Default point size in pixels (default: 8.0)
     * @param brightness Brightness multiplier for HDR effect (default: 2.0)
     * @return Compiled Filament Material ready to use
     */
    fun createPointCloudMaterial(
        engine: Engine,
        pointSize: Float = 8.0f,
        brightness: Float = 2.0f
    ): Material? {
        return try {
            println("[PointCloudMaterial] Building point cloud material (pointSize=$pointSize, brightness=$brightness)")

            // Create material builder
            val builder = MaterialBuilder()
                .name("point_cloud_unlit")
                .shading(Shading.UNLIT)  // No lighting, like KHR_materials_unlit
                .require(MaterialBuilder.VertexAttribute.COLOR)  // Require COLOR_0 vertex attribute
                .doubleSided(true)  // Render from both sides
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.OPENGL)
                .optimization(MaterialBuilder.Optimization.PERFORMANCE)

            // Add material uniform parameters
            builder.uniformParameter(
                MaterialBuilder.UniformType.FLOAT,
                "pointSize"
            )

            builder.uniformParameter(
                MaterialBuilder.UniformType.FLOAT,
                "brightness"
            )

            // Set vertex shader
            builder.materialVertex(VERTEX_SHADER)

            // Set fragment shader
            builder.material(FRAGMENT_SHADER)

            // Build material package
            println("[PointCloudMaterial] Compiling material...")
            val materialPackage = builder.build()

            if (materialPackage.isValid.not()) {
                println("[PointCloudMaterial] ERROR: Material compilation failed - invalid package")
                return null
            }

            val packageBuffer = materialPackage.buffer
            println("[PointCloudMaterial] Material package compiled successfully (${packageBuffer.capacity()} bytes)")

            // Create material from package
            val material = Material.Builder()
                .payload(packageBuffer, packageBuffer.capacity())
                .build(engine)

            // Set default parameter values
            val defaultInstance = material.defaultInstance
            defaultInstance.setParameter("pointSize", pointSize)
            defaultInstance.setParameter("brightness", brightness)

            println("[PointCloudMaterial] Point cloud material created successfully")
            material

        } catch (e: Exception) {
            println("[PointCloudMaterial] ERROR: Failed to create point cloud material: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Create a high-brightness variant for stellar objects (stars, galaxies).
     * Uses larger point size and higher brightness for dramatic effect.
     */
    fun createStellarMaterial(engine: Engine): Material? {
        return createPointCloudMaterial(
            engine = engine,
            pointSize = 12.0f,   // Larger points for stars
            brightness = 5.0f    // Much brighter for bloom effect
        )
    }
}
