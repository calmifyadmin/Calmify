package com.lifo.ui.emotion

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.pill
import com.lifo.util.model.Mood

/**
 * M3 Expressive Mood Shape System
 *
 * Each of the 16 Moods maps to a unique RoundedPolygon shape
 * designed to communicate emotion through geometry:
 *
 * Shape Language (M3 Expressive research):
 * - Rounded/soft → warmth, calm, comfort
 * - Angular/sharp → energy, tension, alertness
 * - Organic/irregular → playfulness, creativity
 * - Symmetric/geometric → stability, structure
 * - Morphing transitions → emotional flow
 */
object MoodShapeDefinitions {

    // ══════════════════════════════════════════════════════════
    //  SHAPE FACTORIES — One unique RoundedPolygon per Mood
    // ══════════════════════════════════════════════════════════

    fun getShape(mood: Mood): RoundedPolygon = when (mood) {
        Mood.Happy -> happyShape()
        Mood.Calm -> calmShape()
        Mood.Neutral -> neutralShape()
        Mood.Romantic -> romanticShape()
        Mood.Humorous -> humorousShape()
        Mood.Surprised -> surprisedShape()
        Mood.Mysterious -> mysteriousShape()
        Mood.Angry -> angryShape()
        Mood.Tense -> tenseShape()
        Mood.Bored -> boredShape()
        Mood.Depressed -> depressedShape()
        Mood.Disappointed -> disappointedShape()
        Mood.Lonely -> lonelyShape()
        Mood.Shameful -> shamefulShape()
        Mood.Awful -> awfulShape()
        Mood.Suspicious -> suspiciousShape()
    }

    /** Happy — Radiant sun with soft rounded rays. Expansive, joyful. */
    private fun happyShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 8,
        radius = 1f,
        innerRadius = 0.78f,
        rounding = CornerRounding(radius = 0.22f, smoothing = 0.8f),
        innerRounding = CornerRounding(radius = 0.22f, smoothing = 0.8f)
    )

    /** Calm — Soft pill/cloud shape. Serene, peaceful. */
    private fun calmShape(): RoundedPolygon = RoundedPolygon(
        numVertices = 8,
        radius = 1f,
        rounding = CornerRounding(radius = 0.9f, smoothing = 1.0f)
    )

    /** Neutral — Perfect balanced circle. Stable, equilibrium. */
    private fun neutralShape(): RoundedPolygon = RoundedPolygon(
        numVertices = 12,
        radius = 1f,
        rounding = CornerRounding(radius = 1.0f, smoothing = 1.0f)
    )

    /** Romantic — Soft 4-leaf clover/flower. Warmth, tenderness. */
    private fun romanticShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 4,
        radius = 1f,
        innerRadius = 0.62f,
        rounding = CornerRounding(radius = 0.4f, smoothing = 1.0f),
        innerRounding = CornerRounding(radius = 0.12f, smoothing = 0.6f)
    )

    /** Humorous — Playful cookie/blob. Fun, lighthearted. */
    private fun humorousShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 6,
        radius = 1f,
        innerRadius = 0.82f,
        rounding = CornerRounding(radius = 0.35f, smoothing = 1.0f),
        innerRounding = CornerRounding(radius = 0.35f, smoothing = 1.0f)
    )

    /** Surprised — Sharp starburst. Sudden, unexpected. */
    private fun surprisedShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 12,
        radius = 1f,
        innerRadius = 0.72f,
        rounding = CornerRounding(radius = 0.08f, smoothing = 0.3f),
        innerRounding = CornerRounding(radius = 0.15f, smoothing = 0.5f)
    )

    /** Mysterious — Hexagram/gem. Enigmatic, deep. */
    private fun mysteriousShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 6,
        radius = 1f,
        innerRadius = 0.55f,
        rounding = CornerRounding(radius = 0.12f, smoothing = 0.4f),
        innerRounding = CornerRounding(radius = 0.12f, smoothing = 0.4f)
    )

    /** Angry — Sharp aggressive star. Intense, explosive. */
    private fun angryShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 5,
        radius = 1f,
        innerRadius = 0.42f,
        rounding = CornerRounding(radius = 0.04f, smoothing = 0.1f),
        innerRounding = CornerRounding(radius = 0.04f, smoothing = 0.1f)
    )

    /** Tense — Tight hexagon with subtle indents. Rigid, contracted. */
    private fun tenseShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 6,
        radius = 1f,
        innerRadius = 0.85f,
        rounding = CornerRounding(radius = 0.06f, smoothing = 0.2f),
        innerRounding = CornerRounding(radius = 0.06f, smoothing = 0.2f)
    )

    /** Bored — Flat oval. Deflated, unenthused. */
    private fun boredShape(): RoundedPolygon = RoundedPolygon.pill(
        width = 2.0f,
        height = 1.2f,
        smoothing = 0.8f
    )

    /** Depressed — Drooping soft triangle. Heavy, weighed down. */
    private fun depressedShape(): RoundedPolygon = RoundedPolygon(
        numVertices = 3,
        radius = 1f,
        rounding = CornerRounding(radius = 0.45f, smoothing = 0.8f)
    )

    /** Disappointed — Downward fan/arch. Let down, deflated. */
    private fun disappointedShape(): RoundedPolygon = RoundedPolygon(
        numVertices = 5,
        radius = 1f,
        rounding = CornerRounding(radius = 0.5f, smoothing = 0.9f)
    )

    /** Lonely — Small isolated diamond. Withdrawn, distant. */
    private fun lonelyShape(): RoundedPolygon = RoundedPolygon(
        numVertices = 4,
        radius = 1f,
        rounding = CornerRounding(radius = 0.25f, smoothing = 0.5f)
    )

    /** Shameful — Tilted/slanted square. Off-balance, uneasy. */
    private fun shamefulShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 4,
        radius = 1f,
        innerRadius = 0.88f,
        rounding = CornerRounding(radius = 0.15f, smoothing = 0.4f),
        innerRounding = CornerRounding(radius = 0.15f, smoothing = 0.4f)
    )

    /** Awful — Spiky distorted shape. Overwhelmed, distressed. */
    private fun awfulShape(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 7,
        radius = 1f,
        innerRadius = 0.52f,
        rounding = CornerRounding(radius = 0.06f, smoothing = 0.15f),
        innerRounding = CornerRounding(radius = 0.1f, smoothing = 0.3f)
    )

    /** Suspicious — Narrow diamond/eye shape. Watchful, wary. */
    private fun suspiciousShape(): RoundedPolygon = RoundedPolygon(
        numVertices = 4,
        radius = 1f,
        rounding = CornerRounding(radius = 0.08f, smoothing = 0.2f)
    )

    // ══════════════════════════════════════════════════════════
    //  MORPHING — Create smooth transitions between moods
    // ══════════════════════════════════════════════════════════

    fun createMorph(from: Mood, to: Mood): Morph {
        return Morph(getShape(from), getShape(to))
    }

    // ══════════════════════════════════════════════════════════
    //  PATH CONVERSION — Shape to Compose Path
    // ══════════════════════════════════════════════════════════

    fun shapeToComposePath(
        shape: RoundedPolygon,
        width: Float,
        height: Float
    ): Path {
        val path = cubicsToPath(shape.cubics)
        val matrix = Matrix()
        matrix.scale(width / 2f, height / 2f)
        matrix.translate(1f, 1f)
        path.transform(matrix)
        return path
    }

    fun morphToComposePath(
        morph: Morph,
        progress: Float,
        width: Float,
        height: Float
    ): Path {
        val cubics = mutableListOf<Cubic>()
        morph.forEachCubic(progress) { cubic -> cubics.add(cubic) }
        val path = cubicsToPath(cubics)
        val matrix = Matrix()
        matrix.scale(width / 2f, height / 2f)
        matrix.translate(1f, 1f)
        path.transform(matrix)
        return path
    }

    private fun cubicsToPath(cubics: List<Cubic>): Path {
        val path = Path()
        if (cubics.isEmpty()) return path
        path.moveTo(cubics[0].anchor0X, cubics[0].anchor0Y)
        for (cubic in cubics) {
            path.cubicTo(
                cubic.control0X, cubic.control0Y,
                cubic.control1X, cubic.control1Y,
                cubic.anchor1X, cubic.anchor1Y
            )
        }
        path.close()
        return path
    }

    // ══════════════════════════════════════════════════════════
    //  COLOR PALETTE — Refined M3-aligned mood colors
    // ══════════════════════════════════════════════════════════

    data class MoodColorPalette(
        val primary: Color,
        val onShape: Color,
        val glow: Color,
        val gradient: Pair<Color, Color>
    )

    fun getColors(mood: Mood): MoodColorPalette = when (mood) {
        Mood.Happy -> MoodColorPalette(
            primary = Color(0xFFFFD54F),
            onShape = Color(0xFF3E2723),
            glow = Color(0x40FFD54F),
            gradient = Color(0xFFFFE082) to Color(0xFFFFC107)
        )
        Mood.Calm -> MoodColorPalette(
            primary = Color(0xFF64B5F6),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x4064B5F6),
            gradient = Color(0xFF90CAF9) to Color(0xFF42A5F5)
        )
        Mood.Neutral -> MoodColorPalette(
            primary = Color(0xFF90A4AE),
            onShape = Color(0xFF263238),
            glow = Color(0x4090A4AE),
            gradient = Color(0xFFB0BEC5) to Color(0xFF78909C)
        )
        Mood.Romantic -> MoodColorPalette(
            primary = Color(0xFFF06292),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x40F06292),
            gradient = Color(0xFFF48FB1) to Color(0xFFEC407A)
        )
        Mood.Humorous -> MoodColorPalette(
            primary = Color(0xFFFFCA28),
            onShape = Color(0xFF3E2723),
            glow = Color(0x40FFCA28),
            gradient = Color(0xFFFFD54F) to Color(0xFFFFA000)
        )
        Mood.Surprised -> MoodColorPalette(
            primary = Color(0xFF4FC3F7),
            onShape = Color(0xFF01579B),
            glow = Color(0x404FC3F7),
            gradient = Color(0xFF81D4FA) to Color(0xFF29B6F6)
        )
        Mood.Mysterious -> MoodColorPalette(
            primary = Color(0xFF4DB6AC),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x404DB6AC),
            gradient = Color(0xFF80CBC4) to Color(0xFF26A69A)
        )
        Mood.Angry -> MoodColorPalette(
            primary = Color(0xFFEF5350),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x40EF5350),
            gradient = Color(0xFFE57373) to Color(0xFFD32F2F)
        )
        Mood.Tense -> MoodColorPalette(
            primary = Color(0xFFFF7043),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x40FF7043),
            gradient = Color(0xFFFF8A65) to Color(0xFFF4511E)
        )
        Mood.Bored -> MoodColorPalette(
            primary = Color(0xFF4DD0E1),
            onShape = Color(0xFF004D40),
            glow = Color(0x404DD0E1),
            gradient = Color(0xFF80DEEA) to Color(0xFF26C6DA)
        )
        Mood.Depressed -> MoodColorPalette(
            primary = Color(0xFFBDBDBD),
            onShape = Color(0xFF424242),
            glow = Color(0x40BDBDBD),
            gradient = Color(0xFFE0E0E0) to Color(0xFF9E9E9E)
        )
        Mood.Disappointed -> MoodColorPalette(
            primary = Color(0xFFCE93D8),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x40CE93D8),
            gradient = Color(0xFFE1BEE7) to Color(0xFFAB47BC)
        )
        Mood.Lonely -> MoodColorPalette(
            primary = Color(0xFFA1887F),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x40A1887F),
            gradient = Color(0xFFBCAAA4) to Color(0xFF8D6E63)
        )
        Mood.Shameful -> MoodColorPalette(
            primary = Color(0xFF9575CD),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x409575CD),
            gradient = Color(0xFFB39DDB) to Color(0xFF7E57C2)
        )
        Mood.Awful -> MoodColorPalette(
            primary = Color(0xFFE57373),
            onShape = Color(0xFFFFFFFF),
            glow = Color(0x40E57373),
            gradient = Color(0xFFEF9A9A) to Color(0xFFE53935)
        )
        Mood.Suspicious -> MoodColorPalette(
            primary = Color(0xFFDCE775),
            onShape = Color(0xFF33691E),
            glow = Color(0x40DCE775),
            gradient = Color(0xFFE6EE9C) to Color(0xFFD4E157)
        )
    }
}
