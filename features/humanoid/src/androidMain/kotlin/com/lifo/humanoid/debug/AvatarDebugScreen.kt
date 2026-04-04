package com.lifo.humanoid.debug

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.humanoid.animation.VrmaAnimationLoader
import com.lifo.humanoid.api.GestureType
import com.lifo.humanoid.api.HumanoidAvatarView
import com.lifo.humanoid.api.HumanoidController
import com.lifo.humanoid.api.asHumanoidController
import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.lipsync.LipSyncController
import com.lifo.humanoid.presentation.HumanoidViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AvatarDebugScreen(
    onClose: () -> Unit
) {
    val humanoidViewModel: HumanoidViewModel = koinViewModel()
    val lipSyncController: LipSyncController = koinInject()
    val blendShapeController: VrmBlendShapeController = koinInject()
    val humanoidController = remember {
        humanoidViewModel.asHumanoidController(lipSyncController)
    }
    val scope = rememberCoroutineScope()

    // Debug state
    var activeSection by remember { mutableStateOf("animations") }
    var lastTriggered by remember { mutableStateOf("---") }
    var isVisemeLooping by remember { mutableStateOf(false) }

    // Viseme test loop — directly sets blend shapes for immediate feedback
    LaunchedEffect(isVisemeLooping) {
        if (!isVisemeLooping) return@LaunchedEffect
        val visemes = listOf(
            mapOf("a" to 0.8f),
            mapOf("e" to 0.7f),
            mapOf("i" to 0.7f),
            mapOf("o" to 0.8f),
            mapOf("u" to 0.7f),
            emptyMap<String, Float>()
        )
        var idx = 0
        while (isVisemeLooping) {
            blendShapeController.setCategoryWeights(
                VrmBlendShapeController.CATEGORY_LIPSYNC,
                visemes[idx]
            )
            idx = (idx + 1) % visemes.size
            delay(300)
        }
        blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Avatar Debug", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Avatar — same proportions as LiveScreen
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            ) {
                HumanoidAvatarView(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = humanoidViewModel
                )

                // Status badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                    tonalElevation = 2.dp
                ) {
                    Text(
                        text = lastTriggered,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            // Controls panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Section tabs
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        listOf("animations" to "Animations", "visemes" to "Visemes", "idle" to "Idle", "lighting" to "Lighting").forEach { (key, label) ->
                            FilterChip(
                                selected = activeSection == key,
                                onClick = { activeSection = key },
                                label = { Text(label, fontSize = 13.sp) }
                            )
                        }
                    }

                    // Content based on section
                    when (activeSection) {
                        "animations" -> AnimationsPanel(
                            humanoidViewModel = humanoidViewModel,
                            humanoidController = humanoidController,
                            onTriggered = { lastTriggered = it }
                        )
                        "visemes" -> VisemesPanel(
                            blendShapeController = blendShapeController,
                            isLooping = isVisemeLooping,
                            onToggleLoop = { isVisemeLooping = !isVisemeLooping },
                            onTriggered = { lastTriggered = it }
                        )
                        "idle" -> IdlePanel(
                            humanoidViewModel = humanoidViewModel,
                            humanoidController = humanoidController,
                            onTriggered = { lastTriggered = it }
                        )
                        "lighting" -> LightingPanel(
                            humanoidViewModel = humanoidViewModel,
                            onTriggered = { lastTriggered = it }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimationsPanel(
    humanoidViewModel: HumanoidViewModel,
    humanoidController: HumanoidController,
    onTriggered: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("All Animations", style = MaterialTheme.typography.labelLarge)

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .heightIn(max = 220.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Idle animations
            Text("Idle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                VrmaAnimationLoader.AnimationAsset.entries
                    .filter { it.isIdle() }
                    .forEach { asset ->
                        FilledTonalButton(
                            onClick = {
                                humanoidViewModel.playAnimation(asset)
                                onTriggered(asset.displayName)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                        ) {
                            Text(asset.displayName, fontSize = 11.sp)
                        }
                    }
            }

            // Gesture animations grouped
            val groups = mapOf(
                "Greetings" to listOf(GestureType.GREETING, GestureType.HELLO),
                "Reactions" to listOf(GestureType.YES, GestureType.NO, GestureType.I_AGREE, GestureType.I_DONT_THINK_SO, GestureType.I_DONT_KNOW),
                "Emotions" to listOf(GestureType.ANGRY, GestureType.SAD, GestureType.HAPPY, GestureType.YOU_ARE_CRAZY),
                "Actions" to listOf(GestureType.DANCE, GestureType.PEACE_SIGN, GestureType.SHOOT, GestureType.POINTING, GestureType.SHOW_FULL_BODY)
            )

            groups.forEach { (category, gestures) ->
                Text(category, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    gestures.forEach { gesture ->
                        FilledTonalButton(
                            onClick = {
                                humanoidController.playGesture(gesture, loop = false)
                                onTriggered(gesture.animationName)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(gesture.animationName.replace("_", " "), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VisemesPanel(
    blendShapeController: VrmBlendShapeController,
    isLooping: Boolean,
    onToggleLoop: () -> Unit,
    onTriggered: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Viseme Controls", style = MaterialTheme.typography.labelLarge)

        // Individual visemes
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("a", "e", "i", "o", "u").forEach { v ->
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            blendShapeController.setCategoryWeights(
                                VrmBlendShapeController.CATEGORY_LIPSYNC,
                                mapOf(v to 0.8f)
                            )
                            onTriggered("viseme: $v")
                            delay(600)
                            blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text(v.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Viseme loop toggle
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalButton(
                onClick = onToggleLoop,
                colors = if (isLooping) ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) else ButtonDefaults.filledTonalButtonColors()
            ) {
                Text(if (isLooping) "Stop Loop" else "A-E-I-O-U Loop")
            }

            // Silence
            OutlinedButton(
                onClick = {
                    blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
                    onTriggered("silence")
                }
            ) {
                Text("Reset")
            }
        }

        // Intensity slider
        var testIntensity by remember { mutableFloatStateOf(0.7f) }
        Text("Intensity: ${"%.2f".format(testIntensity)}", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = testIntensity,
            onValueChange = {
                testIntensity = it
                // Live update — mouth moves with slider
                blendShapeController.setCategoryWeights(
                    VrmBlendShapeController.CATEGORY_LIPSYNC,
                    mapOf("a" to it)
                )
            },
            onValueChangeFinished = {
                blendShapeController.clearCategory(VrmBlendShapeController.CATEGORY_LIPSYNC)
            },
            valueRange = 0f..1f,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun IdlePanel(
    humanoidViewModel: HumanoidViewModel,
    humanoidController: HumanoidController,
    onTriggered: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Idle & System", style = MaterialTheme.typography.labelLarge)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(onClick = {
                humanoidController.stopGesture()
                onTriggered("stop -> idle")
            }) { Text("Stop / Idle") }

            FilledTonalButton(onClick = {
                humanoidController.resetToNeutral()
                onTriggered("reset neutral")
            }) { Text("Reset Neutral") }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val scope = rememberCoroutineScope()
            FilledTonalButton(onClick = {
                scope.launch {
                    humanoidController.triggerBlink()
                    onTriggered("blink")
                }
            }) { Text("Blink") }
        }
    }
}

@Composable
private fun LightingPanel(
    humanoidViewModel: HumanoidViewModel,
    onTriggered: (String) -> Unit
) {
    val renderer = humanoidViewModel.getFilamentRenderer()

    // Current values (defaults from setupLighting)
    var sunIntensity by remember { mutableFloatStateOf(109_210f) }
    var sunDirX by remember { mutableFloatStateOf(0.1f) }
    var sunDirY by remember { mutableFloatStateOf(-0.85f) }
    var sunDirZ by remember { mutableFloatStateOf(0.25f) }
    var fillIntensity by remember { mutableFloatStateOf(35_001f) }
    var rimIntensity by remember { mutableFloatStateOf(46_214f) }
    var indirectIntensity by remember { mutableFloatStateOf(15_869f) }
    var shBase by remember { mutableFloatStateOf(0.64f) }
    var exposure by remember { mutableFloatStateOf(0.45f) }
    var contrast by remember { mutableFloatStateOf(1.08f) }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .heightIn(max = 280.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Lighting Controls", style = MaterialTheme.typography.labelLarge)

        if (renderer == null) {
            Text("Renderer not ready", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            return@Column
        }

        LightingSlider("SUN", sunIntensity, 0f..150_000f, { "%.0f".format(it) }) {
            sunIntensity = it; renderer.updateSunIntensity(it)
        }
        LightingSlider("Sun X", sunDirX, -1f..1f, { "%.2f".format(it) }) {
            sunDirX = it; renderer.updateSunDirection(sunDirX, sunDirY, sunDirZ)
        }
        LightingSlider("Sun Y", sunDirY, -1f..0f, { "%.2f".format(it) }) {
            sunDirY = it; renderer.updateSunDirection(sunDirX, sunDirY, sunDirZ)
        }
        LightingSlider("Sun Z", sunDirZ, -1f..1f, { "%.2f".format(it) }) {
            sunDirZ = it; renderer.updateSunDirection(sunDirX, sunDirY, sunDirZ)
        }
        LightingSlider("Fill", fillIntensity, 0f..60_000f, { "%.0f".format(it) }) {
            fillIntensity = it; renderer.updateFillIntensity(it)
        }
        LightingSlider("Rim", rimIntensity, 0f..80_000f, { "%.0f".format(it) }) {
            rimIntensity = it; renderer.updateRimIntensity(it)
        }
        LightingSlider("Indirect", indirectIntensity, 0f..60_000f, { "%.0f".format(it) }) {
            indirectIntensity = it; renderer.updateIndirectIntensity(it)
        }
        LightingSlider("SH Base", shBase, 0f..1f, { "%.2f".format(it) }) {
            shBase = it; renderer.updateIndirectSHBase(it)
        }
        LightingSlider("Exposure", exposure, 0f..2f, { "%.2f".format(it) }) {
            exposure = it; renderer.updateColorGrading(it, contrast)
        }
        LightingSlider("Contrast", contrast, 0.5f..2f, { "%.2f".format(it) }) {
            contrast = it; renderer.updateColorGrading(exposure, it)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Face Material Override", style = MaterialTheme.typography.labelLarge)

        var faceRoughness by remember { mutableFloatStateOf(0.9f) }
        var faceMetallic by remember { mutableFloatStateOf(0.0f) }

        LightingSlider("Face Rough", faceRoughness, 0f..1f, { "%.2f".format(it) }) {
            faceRoughness = it; renderer.overrideFaceMaterial(it, faceMetallic)
        }
        LightingSlider("Face Metal", faceMetallic, 0f..1f, { "%.2f".format(it) }) {
            faceMetallic = it; renderer.overrideFaceMaterial(faceRoughness, it)
        }

        FilledTonalButton(
            onClick = {
                renderer.dumpFaceMaterialInfo()
                onTriggered("Material info dumped to logcat")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Dump Face Material") }

        FilledTonalButton(
            onClick = {
                val values = "SUN=%.0f Dir=(%.2f,%.2f,%.2f) Fill=%.0f Rim=%.0f Ind=%.0f SH=%.2f Exp=%.2f Con=%.2f".format(
                    sunIntensity, sunDirX, sunDirY, sunDirZ, fillIntensity, rimIntensity, indirectIntensity, shBase, exposure, contrast
                )
                println("[LightingDebug] $values")
                onTriggered(values)
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Log Values") }
    }
}

@Composable
private fun LightingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    format: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "$label: ${format(value)}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(120.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            modifier = Modifier.weight(1f)
        )
    }
}
