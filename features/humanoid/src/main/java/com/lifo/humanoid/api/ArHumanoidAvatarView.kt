package com.lifo.humanoid.api

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.filament.EntityManager
import com.google.android.filament.IndexBuffer
import com.google.android.filament.Material
import com.google.android.filament.RenderableManager
import com.google.android.filament.VertexBuffer
import com.google.android.filament.filamat.MaterialBuilder
import com.google.android.filament.filamat.MaterialPackage
import com.google.android.filament.gltfio.AssetLoader
import com.google.android.filament.gltfio.FilamentAsset
import com.google.android.filament.gltfio.ResourceLoader
import com.google.android.filament.gltfio.UbershaderProvider
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Frame
import com.google.ar.core.InstantPlacementPoint
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.lifo.humanoid.animation.LookAtController
import com.lifo.humanoid.data.vrm.VrmBlendShape
import com.lifo.humanoid.data.vrm.VrmExtensions
import com.lifo.humanoid.presentation.HumanoidViewModel
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.arcore.isValid
import io.github.sceneview.ar.getDescription
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.ar.rememberARCameraNode
import io.github.sceneview.node.Node
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import io.github.sceneview.rememberOnGestureListener
import kotlinx.coroutines.delay
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

private const val TAG = "ArHumanoidAvatarView"

/**
 * Public composable for rendering the Humanoid VRM avatar in Augmented Reality.
 *
 * Uses SceneView's [ARScene] for all AR infrastructure:
 * - Camera passthrough background
 * - Plane detection & rendering (built-in)
 * - Hit-testing & anchor placement
 * - ARCore session lifecycle
 *
 * Custom VRM pipeline preserved:
 * - GLB model loading via Filament gltfio
 * - Blend shapes (lip-sync, emotions, blink)
 * - Eye bone tracking (LookAt)
 * - VRMA animations
 */
@Composable
fun ArHumanoidAvatarView(
    modifier: Modifier = Modifier,
    viewModel: HumanoidViewModel,
    onAvatarPlaced: () -> Unit = {},
    onRendererReady: () -> Unit = {}
) {
    // ===== SceneView infrastructure =====
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val filamentScene = rememberScene(engine)
    val filamentView = rememberView(engine)
    val cameraNode = rememberARCameraNode(engine)
    val childNodes = rememberNodes()
    val collisionSystem = rememberCollisionSystem(filamentView)

    // ===== ViewModel state =====
    val vrmModelData by viewModel.vrmModelData.collectAsStateWithLifecycle()
    val vrmExtensions by viewModel.vrmExtensions.collectAsStateWithLifecycle()
    // blendShapeWeights read directly via viewModel.blendShapeWeights.value in render loop
    // (NOT collected as Compose state — avoids 60fps recomposition!)

    // ===== Local AR state =====
    // Frame stored for hit-test in tap handler (official SceneView pattern)
    var frame by remember { mutableStateOf<Frame?>(null) }
    var trackingFailureReason by remember { mutableStateOf<TrackingFailureReason?>(null) }
    var isPlaced by remember { mutableStateOf(false) }
    var showPlaneRenderer by remember { mutableStateOf(true) }
    var hasDetectedPlanes by remember { mutableStateOf(false) }

    // ===== Focus reticle + anchor monitoring state =====
    var centerHitDetected by remember { mutableStateOf(false) }
    var showRepositionButton by remember { mutableStateOf(false) }
    var pausedFrameCount by remember { mutableIntStateOf(0) }
    var viewWidthPx by remember { mutableFloatStateOf(0f) }
    var viewHeightPx by remember { mutableFloatStateOf(0f) }

    // ===== Instant placement state =====
    var arSessionRef by remember { mutableStateOf<com.google.ar.core.Session?>(null) }
    var instantPlacementPointRef by remember { mutableStateOf<InstantPlacementPoint?>(null) }
    var instantPlacementDistanceAtPlace by remember { mutableFloatStateOf(0f) }
    var isInstantPlacement by remember { mutableStateOf(false) }

    // ===== Ground shadow state =====
    var shadowEntity by remember { mutableIntStateOf(0) }

    // ===== VRM asset state =====
    var vrmAsset by remember { mutableStateOf<FilamentAsset?>(null) }
    var loadedModelDataRef by remember { mutableStateOf<ByteBuffer?>(null) }
    var anchorRef by remember { mutableStateOf<Anchor?>(null) }
    var modelAddedToScene by remember { mutableStateOf(false) }

    // Blend shape mapping: name -> list of (entity, morphTargetIndex)
    val blendShapeMapping = remember { mutableMapOf<String, MutableList<Pair<Int, Int>>>() }

    // Eye bone state for LookAt
    var leftEyeEntity by remember { mutableIntStateOf(0) }
    var rightEyeEntity by remember { mutableIntStateOf(0) }
    var originalLeftEyeTransform by remember { mutableStateOf<FloatArray?>(null) }
    var originalRightEyeTransform by remember { mutableStateOf<FloatArray?>(null) }
    val lookAtController = remember { LookAtController() }

    // Pre-computed scale and Y-offset for placement
    var modelScale by remember { mutableFloatStateOf(1f) }
    var modelYOffset by remember { mutableFloatStateOf(0f) }
    var modelXOffset by remember { mutableFloatStateOf(0f) }
    var modelZOffset by remember { mutableFloatStateOf(0f) }

    // Asset loaders (created once per engine lifecycle)
    val materialProvider = remember(engine) { UbershaderProvider(engine) }
    val assetLoader = remember(engine) {
        AssetLoader(engine, materialProvider, EntityManager.get())
    }
    val resourceLoader = remember(engine) {
        ResourceLoader(engine)
    }

    // ===== VRM Model Loading =====
    LaunchedEffect(vrmModelData, engine) {
        val data = vrmModelData ?: return@LaunchedEffect
        val (buffer, extensions) = data

        // Skip if already loaded this exact buffer
        if (buffer === loadedModelDataRef) return@LaunchedEffect

        Log.d(TAG, "Loading VRM model: buffer capacity=${buffer.capacity()}")

        // Clean up previous asset
        vrmAsset?.let { old ->
            filamentScene.removeEntities(old.entities)
            assetLoader.destroyAsset(old)
            vrmAsset = null
            blendShapeMapping.clear()
            leftEyeEntity = 0
            rightEyeEntity = 0
            originalLeftEyeTransform = null
            originalRightEyeTransform = null
        }

        buffer.position(0)
        val asset = assetLoader.createAsset(buffer)
        if (asset == null) {
            Log.e(TAG, "createAsset returned NULL — GLB data invalid")
            return@LaunchedEffect
        }

        Log.d(TAG, "Asset created: ${asset.entities.size} entities, root=${asset.root}")
        val aabb = asset.boundingBox
        Log.d(TAG, "  boundingBox center=(${aabb.center[0]}, ${aabb.center[1]}, ${aabb.center[2]})")
        Log.d(TAG, "  halfExtent=(${aabb.halfExtent[0]}, ${aabb.halfExtent[1]}, ${aabb.halfExtent[2]})")

        // Load resources (doesn't require entities in scene)
        resourceLoader.loadResources(asset)
        resourceLoader.asyncBeginLoad(asset)
        var pumpIterations = 0
        while (pumpIterations < 50) {
            resourceLoader.asyncUpdateLoad()
            delay(1)
            pumpIterations++
        }
        Log.d(TAG, "Resource loading pumped $pumpIterations iterations")

        // DON'T add to scene yet — model stays invisible until tap-to-place.
        // Entities will be added to scene when the user places the avatar.
        modelAddedToScene = false

        // Pre-compute scale and offsets for placement (DON'T apply to entity yet)
        val aabbCenter = aabb.center
        val aabbHalf = aabb.halfExtent
        val heightExtent = aabbHalf[1] * 2.0f
        val targetHeight = 1.7f
        val computedScale = if (heightExtent > 0f) targetHeight / heightExtent else 1.0f
        // Y offset: move AABB bottom to Y=0 (feet on ground)
        val aabbBottomY = aabbCenter[1] - aabbHalf[1]
        val computedYOffset = -aabbBottomY * computedScale

        modelScale = computedScale
        modelYOffset = computedYOffset
        modelXOffset = -aabbCenter[0] * computedScale
        modelZOffset = -aabbCenter[2] * computedScale

        Log.d(TAG, "Pre-computed: scale=$computedScale yOffset=$computedYOffset " +
            "aabbBottomY=$aabbBottomY xOff=$modelXOffset zOff=$modelZOffset")

        // Find eye bones for LookAt
        val eyeResult = findEyeBones(asset, extensions)
        leftEyeEntity = eyeResult.first
        rightEyeEntity = eyeResult.second
        storeOriginalEyeTransforms(
            engine, leftEyeEntity, rightEyeEntity
        ).let { (left, right) ->
            originalLeftEyeTransform = left
            originalRightEyeTransform = right
        }

        // Build blend shape mapping
        buildBlendShapeMapping(engine, asset, extensions.blendShapes, blendShapeMapping)

        vrmAsset = asset
        loadedModelDataRef = buffer

        // Extract node names and notify ViewModel
        val nodeNames = asset.entities.mapIndexed { index, entity ->
            try { asset.getName(entity) ?: "node_$index" } catch (_: Exception) { "node_$index" }
        }
        viewModel.onSceneViewArModelLoaded(engine, asset, nodeNames)

        Log.d(TAG, "VRM model loaded OK. eyeBones: L=$leftEyeEntity R=$rightEyeEntity")
    }

    // ===== Continuous blend shape updates (lip-sync, blink, emotions) =====
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateBlendShapes(deltaTime = 0.016f)
            delay(16)
        }
    }

    // Blend shapes are applied in onSessionUpdated (render loop), not via LaunchedEffect

    // ===== Cleanup on dispose =====
    DisposableEffect(Unit) {
        onDispose {
            Log.d(TAG, "Disposing ArHumanoidAvatarView")
            viewModel.stopAllControllersBeforeCleanup()

            vrmAsset?.let { asset ->
                try {
                    if (modelAddedToScene) {
                        filamentScene.removeEntities(asset.entities)
                    }
                    assetLoader.destroyAsset(asset)
                } catch (e: Exception) {
                    Log.e(TAG, "Error cleaning up VRM asset", e)
                }
            }

            // Clean up ground shadow entity
            if (shadowEntity != 0) {
                try {
                    engine.destroyEntity(shadowEntity)
                    EntityManager.get().destroy(shadowEntity)
                } catch (_: Exception) {}
            }

            anchorRef?.detach()
        }
    }

    // ===== UI =====
    Box(modifier = modifier) {
        // SceneView ARScene — handles camera, planes, session, hit-test
        ARScene(
            modifier = Modifier.fillMaxSize(),
            childNodes = childNodes,
            engine = engine,
            view = filamentView,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            scene = filamentScene,
            collisionSystem = collisionSystem,
            cameraNode = cameraNode,
            planeRenderer = showPlaneRenderer,
            onViewCreated = {
                // Enable depth-based occlusion ONLY if depth is actually supported
                // Setting this when depth isn't available can break camera stream rendering
                try {
                    val cs = cameraStream
                    if (cs != null) {
                        cs.isDepthOcclusionEnabled = true
                        Log.d(TAG, "Depth occlusion enabled on ARSceneView")
                    } else {
                        Log.w(TAG, "cameraStream is null — depth occlusion skipped")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to enable depth occlusion", e)
                }
            },
            onViewUpdated = {
                // Capture view dimensions for center-screen hit testing
                if (width > 0 && height > 0) {
                    viewWidthPx = width.toFloat()
                    viewHeightPx = height.toFloat()
                }
            },
            sessionConfiguration = { session, config ->
                val depthSupported = session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                config.depthMode = if (depthSupported) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }
                // HORIZONTAL only — avatar stands on floors/tables, not walls.
                // Skipping VERTICAL halves the feature-tracking work → faster first plane.
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                // LOCAL_Y_UP: instant placement for <1s response time.
                // Object placed immediately at approximate distance, refined when plane found.
                config.instantPlacementMode = Config.InstantPlacementMode.LOCAL_Y_UP
                // AMBIENT_INTENSITY is ~5x lighter than ENVIRONMENTAL_HDR.
                // HDR computes full spherical harmonics from the camera feed,
                // adding 300-500ms per frame during initial tracking.
                config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                config.focusMode = Config.FocusMode.AUTO
                Log.d(TAG, "Session config: depth=$depthSupported, " +
                    "planeFinding=HORIZONTAL, instantPlacement=LOCAL_Y_UP, " +
                    "light=AMBIENT_INTENSITY, focusMode=AUTO")
            },
            onTrackingFailureChanged = { reason ->
                trackingFailureReason = reason
            },
            onSessionUpdated = { session, updatedFrame ->
                frame = updatedFrame
                arSessionRef = session

                // Check for detected planes (including ALL trackables, not just updated ones)
                if (!hasDetectedPlanes) {
                    val allPlanes = session.getAllTrackables(Plane::class.java)
                    val trackingPlanes = allPlanes.filter {
                        it.trackingState == TrackingState.TRACKING
                    }
                    if (trackingPlanes.isNotEmpty()) {
                        hasDetectedPlanes = true
                        Log.d(TAG, "Planes detected: ${trackingPlanes.size} tracking " +
                            "(types: ${trackingPlanes.map { it.type }})")
                    }
                    // Log camera tracking state periodically for debugging
                    val cam = updatedFrame.camera
                    if (cam.trackingState != TrackingState.TRACKING && allPlanes.isEmpty()) {
                        Log.d(TAG, "Camera tracking: ${cam.trackingState}, planes: ${allPlanes.size}")
                    }
                }

                // ---- Focus reticle: center-screen hit test ----
                if (!isPlaced && viewWidthPx > 0f && viewHeightPx > 0f) {
                    val cam = updatedFrame.camera
                    if (cam.trackingState == TrackingState.TRACKING) {
                        val cx = viewWidthPx / 2f
                        val cy = viewHeightPx / 2f
                        centerHitDetected = try {
                            updatedFrame.hitTest(cx, cy).any { hit ->
                                val t = hit.trackable
                                t is Plane && t.trackingState == TrackingState.TRACKING
                            }
                        } catch (_: Exception) { false }
                    }
                }

                // ---- Anchor quality monitoring ----
                if (isPlaced && anchorRef != null) {
                    when (anchorRef?.trackingState) {
                        TrackingState.STOPPED -> {
                            showRepositionButton = true
                        }
                        TrackingState.PAUSED -> {
                            pausedFrameCount++
                            if (pausedFrameCount > 180) { // ~3 sec at 60fps
                                showRepositionButton = true
                            }
                        }
                        TrackingState.TRACKING -> {
                            pausedFrameCount = 0
                            showRepositionButton = false
                        }
                        else -> {}
                    }
                }

                // ---- Instant placement: scale compensation on FULL_TRACKING transition ----
                val ipPoint = instantPlacementPointRef
                if (isInstantPlacement && ipPoint != null && isPlaced) {
                    when (ipPoint.trackingMethod) {
                        InstantPlacementPoint.TrackingMethod.SCREENSPACE_WITH_APPROXIMATE_DISTANCE -> {
                            // Still approximate — update distance for later ratio
                            instantPlacementDistanceAtPlace = poseDistance(
                                ipPoint.pose, updatedFrame.camera.pose
                            )
                        }
                        InstantPlacementPoint.TrackingMethod.FULL_TRACKING -> {
                            // Transitioned! Compute scale compensation to avoid size pop.
                            val newDist = poseDistance(ipPoint.pose, updatedFrame.camera.pose)
                            if (instantPlacementDistanceAtPlace > 0.01f) {
                                val scaleRatio = newDist / instantPlacementDistanceAtPlace
                                // Apply compensation to the VRM node scale
                                val compensatedScale = modelScale * scaleRatio
                                val vrmRoot = vrmAsset?.root
                                if (vrmRoot != null && vrmRoot != 0) {
                                    val childNode = childNodes.firstOrNull()
                                        ?.childNodes?.firstOrNull()
                                    childNode?.scale = dev.romainguy.kotlin.math.Float3(
                                        compensatedScale, compensatedScale, compensatedScale
                                    )
                                }
                                Log.d(TAG, "Instant→FULL_TRACKING: dist ${instantPlacementDistanceAtPlace}→$newDist, " +
                                    "scaleRatio=$scaleRatio, compensated=$compensatedScale")
                            }
                            // Done — clear instant placement tracking
                            isInstantPlacement = false
                            instantPlacementPointRef = null
                        }
                        InstantPlacementPoint.TrackingMethod.NOT_TRACKING -> {
                            // Point lost — clear instant placement state
                            Log.w(TAG, "InstantPlacementPoint lost tracking")
                            isInstantPlacement = false
                            instantPlacementPointRef = null
                        }
                    }
                }

                // ---- All GPU updates below: no Compose recomposition ----

                if (modelAddedToScene && vrmAsset != null) {
                    // Apply blend shapes (lip-sync, blink, emotions)
                    if (blendShapeMapping.isNotEmpty()) {
                        val weights = viewModel.blendShapeWeights.value
                        if (weights.isNotEmpty()) {
                            val renderableManager = engine.renderableManager
                            weights.forEach { (name, weight) ->
                                val normalizedName = name.lowercase()
                                val targetWeight = weight.coerceIn(0f, 1f)
                                blendShapeMapping[normalizedName]?.forEach { (entity, morphTargetIndex) ->
                                    try {
                                        val inst = renderableManager.getInstance(entity)
                                        if (inst != 0) {
                                            renderableManager.setMorphWeights(
                                                inst,
                                                floatArrayOf(targetWeight),
                                                morphTargetIndex
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    }

                    // Update eye tracking: eyes follow the device camera
                    if (leftEyeEntity != 0 || rightEyeEntity != 0) {
                        val cameraPose = updatedFrame.camera.pose
                        val cameraPos = floatArrayOf(
                            cameraPose.tx(), cameraPose.ty(), cameraPose.tz()
                        )
                        lookAtController.update(
                            cameraEye = cameraPos,
                            deltaSeconds = 0.016f
                        )
                        rotateEyeBone(engine, leftEyeEntity, lookAtController.lastYawDeg, lookAtController.lastPitchDeg, originalLeftEyeTransform)
                        rotateEyeBone(engine, rightEyeEntity, lookAtController.lastYawDeg, lookAtController.lastPitchDeg, originalRightEyeTransform)
                    }

                    // Update bone matrices for animations
                    try {
                        vrmAsset?.getInstance()?.animator?.updateBoneMatrices()
                    } catch (_: Exception) {}
                }
            },
            onGestureListener = rememberOnGestureListener(
                onSingleTapConfirmed = { motionEvent, node ->
                    if (!isPlaced && vrmAsset != null) {
                        val currentFrame = frame
                        if (currentFrame == null || currentFrame.camera.trackingState != TrackingState.TRACKING) {
                            Log.w(TAG, "Tap ignored — camera not TRACKING (state=${currentFrame?.camera?.trackingState})")
                            return@rememberOnGestureListener
                        }

                        // Strategy: plane-first, instant-fallback.
                        // 1) Try real plane hit (most accurate, stable anchor)
                        val hitResults = currentFrame.hitTest(motionEvent.x, motionEvent.y)
                        val planeHit = hitResults.firstOrNull { hit ->
                            val trackable = hit.trackable
                            trackable is Plane &&
                            trackable.trackingState == TrackingState.TRACKING &&
                            trackable.extentX > 0.15f && trackable.extentZ > 0.15f
                        }

                        val anchor: Anchor?
                        val usedInstantPlacement: Boolean

                        if (planeHit != null) {
                            // Accurate plane hit — best quality
                            anchor = planeHit.createAnchorOrNull()
                            usedInstantPlacement = false
                            Log.d(TAG, "Tap: plane hit at (${anchor?.pose?.tx()}, ${anchor?.pose?.ty()}, ${anchor?.pose?.tz()})")
                        } else {
                            // 2) No plane yet — instant placement for <1s response.
                            // CRITICAL: create anchor via session.createAnchor(pose), NOT
                            // hitResult.createAnchor(). The latter creates a screen-locked
                            // anchor that slides as you move the camera (SCREENSPACE tracking).
                            // session.createAnchor() creates a world-locked anchor that stays put.
                            val instantResults = currentFrame.hitTestInstantPlacement(
                                motionEvent.x, motionEvent.y, 2.0f
                            )
                            val sess = arSessionRef
                            if (instantResults.isNotEmpty() && sess != null) {
                                val instantHit = instantResults[0]
                                val point = instantHit.trackable as InstantPlacementPoint
                                // World-locked anchor at the estimated pose
                                anchor = try {
                                    sess.createAnchor(instantHit.hitPose)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Failed to create world-locked anchor", e)
                                    null
                                }
                                usedInstantPlacement = true
                                instantPlacementPointRef = point
                                val camPose = currentFrame.camera.pose
                                instantPlacementDistanceAtPlace = poseDistance(point.pose, camPose)
                                Log.d(TAG, "Tap: INSTANT world-locked anchor at (${anchor?.pose?.tx()}, ${anchor?.pose?.ty()}, ${anchor?.pose?.tz()}) " +
                                    "method=${point.trackingMethod} dist=${instantPlacementDistanceAtPlace}")
                            } else {
                                anchor = null
                                usedInstantPlacement = false
                                Log.w(TAG, "Tap: no plane AND no instant hit at (${motionEvent.x}, ${motionEvent.y})")
                            }
                        }

                        anchor?.let { anc ->
                            // Add VRM entities to scene NOW (first time visible)
                            if (!modelAddedToScene) {
                                filamentScene.addEntities(vrmAsset!!.entities)
                                modelAddedToScene = true
                                Log.d(TAG, "VRM entities added to scene (${vrmAsset!!.entities.size})")
                            }

                            // Reset root entity transform to identity before wrapping in Node
                            val tm = engine.transformManager
                            val rootInst = tm.getInstance(vrmAsset!!.root)
                            if (rootInst != 0) {
                                tm.setTransform(rootInst, FloatArray(16).apply {
                                    this[0] = 1f; this[5] = 1f; this[10] = 1f; this[15] = 1f
                                })
                            }

                            val anchorNode = AnchorNode(engine = engine, anchor = anc).apply {
                                isEditable = false
                                visibleTrackingStates = setOf(
                                    TrackingState.TRACKING,
                                    TrackingState.PAUSED
                                )
                            }

                            val vrmNode = Node(engine = engine, entity = vrmAsset!!.root)
                            vrmNode.position = dev.romainguy.kotlin.math.Float3(
                                modelXOffset, modelYOffset, modelZOffset
                            )
                            vrmNode.scale = dev.romainguy.kotlin.math.Float3(
                                modelScale, modelScale, modelScale
                            )

                            Log.d(TAG, "VRM Node: pos=(${modelXOffset}, ${modelYOffset}, ${modelZOffset}) scale=$modelScale")
                            anchorNode.addChildNode(vrmNode)

                            // Ground shadow
                            val shadow = createGroundShadowEntity(engine)
                            if (shadow != 0) {
                                shadowEntity = shadow
                                val shadowNode = Node(engine = engine, entity = shadow)
                                shadowNode.position = dev.romainguy.kotlin.math.Float3(0f, 0.003f, 0f)
                                anchorNode.addChildNode(shadowNode)
                                Log.d(TAG, "Ground shadow added")
                            }

                            childNodes += anchorNode

                            anchorRef = anc
                            isPlaced = true
                            isInstantPlacement = usedInstantPlacement
                            showPlaneRenderer = false
                            centerHitDetected = false
                            onAvatarPlaced()

                            Log.d(TAG, "Avatar placed! instant=$usedInstantPlacement")
                        }
                    }
                }
            )
        )

        // ===== Placement overlay =====
        ArPlacementOverlaySceneView(
            isPlaced = isPlaced,
            isInstantPlacement = isInstantPlacement,
            hasDetectedPlanes = hasDetectedPlanes,
            trackingFailureReason = trackingFailureReason,
            centerHitDetected = centerHitDetected,
            showRepositionButton = showRepositionButton,
            onReposition = {
                // Detach old anchor and remove nodes from scene graph
                anchorRef?.detach()
                anchorRef = null
                childNodes.toList().forEach { node ->
                    childNodes -= node
                }
                // Keep VRM entities in Filament scene (modelAddedToScene stays true)
                // so they don't need to be re-added on next placement.
                // Clean up shadow entity
                if (shadowEntity != 0) {
                    try {
                        engine.destroyEntity(shadowEntity)
                        EntityManager.get().destroy(shadowEntity)
                    } catch (_: Exception) {}
                    shadowEntity = 0
                }
                isPlaced = false
                isInstantPlacement = false
                instantPlacementPointRef = null
                showPlaneRenderer = true
                showRepositionButton = false
                pausedFrameCount = 0
                centerHitDetected = false
                Log.d(TAG, "Avatar repositioned — ready for new placement")
            }
        )
    }
}

// ==================== AR Helper Functions ====================

/** Euclidean distance between two ARCore Poses. */
private fun poseDistance(a: com.google.ar.core.Pose, b: com.google.ar.core.Pose): Float =
    sqrt(
        (a.tx() - b.tx()).pow(2) +
        (a.ty() - b.ty()).pow(2) +
        (a.tz() - b.tz()).pow(2)
    )

// ==================== VRM Helper Functions ====================

/**
 * Find eye bone entities for LookAt tracking.
 * Returns (leftEyeEntity, rightEyeEntity).
 */
private fun findEyeBones(asset: FilamentAsset, extensions: VrmExtensions): Pair<Int, Int> {
    var leftEye = 0
    var rightEye = 0
    val vrmLeft = extensions.leftEyeNodeName
    val vrmRight = extensions.rightEyeNodeName

    asset.entities.forEach { entity ->
        val name = try { asset.getName(entity) } catch (_: Exception) { null } ?: return@forEach

        // Check VRM-specified names first
        if (leftEye == 0 && vrmLeft != null && name == vrmLeft) { leftEye = entity; return@forEach }
        if (rightEye == 0 && vrmRight != null && name == vrmRight) { rightEye = entity; return@forEach }

        // Fallback: common naming patterns
        val lower = name.lowercase()
        when {
            leftEye == 0 && (lower == "lefteye" || lower.contains("j_adj_l_faceeye") ||
                    lower.contains("j_bip_l_eye") || lower.contains("left_eye") ||
                    lower.contains("eye_l") || lower == "eye.l") -> leftEye = entity
            rightEye == 0 && (lower == "righteye" || lower.contains("j_adj_r_faceeye") ||
                    lower.contains("j_bip_r_eye") || lower.contains("right_eye") ||
                    lower.contains("eye_r") || lower == "eye.r") -> rightEye = entity
        }
    }

    Log.d(TAG, "findEyeBones: L=$leftEye R=$rightEye")
    return Pair(leftEye, rightEye)
}

/**
 * Store original eye bone transforms for LookAt rotation.
 */
private fun storeOriginalEyeTransforms(
    engine: com.google.android.filament.Engine,
    leftEyeEntity: Int,
    rightEyeEntity: Int
): Pair<FloatArray?, FloatArray?> {
    val tm = engine.transformManager
    var left: FloatArray? = null
    var right: FloatArray? = null

    if (leftEyeEntity != 0) {
        val inst = tm.getInstance(leftEyeEntity)
        if (inst != 0) { left = FloatArray(16); tm.getTransform(inst, left) }
    }
    if (rightEyeEntity != 0) {
        val inst = tm.getInstance(rightEyeEntity)
        if (inst != 0) { right = FloatArray(16); tm.getTransform(inst, right) }
    }
    return Pair(left, right)
}

/**
 * Rotate an eye bone entity by yaw/pitch relative to its original transform.
 */
private fun rotateEyeBone(
    engine: com.google.android.filament.Engine,
    entity: Int,
    yawDeg: Float,
    pitchDeg: Float,
    originalTransform: FloatArray?
) {
    if (entity == 0 || originalTransform == null) return
    val tm = engine.transformManager
    val instance = tm.getInstance(entity)
    if (instance == 0) return

    val m = originalTransform
    val yawRad = Math.toRadians(-yawDeg.toDouble()).toFloat()
    val pitchRad = Math.toRadians(pitchDeg.toDouble()).toFloat()
    val cy = cos(yawRad); val sy = sin(yawRad)
    val cp = cos(pitchRad); val sp = sin(pitchRad)

    val r00 = cy; val r01 = sy * sp; val r02 = sy * cp
    val r10 = 0f; val r11 = cp; val r12 = -sp
    val r20 = -sy; val r21 = cy * sp; val r22 = cy * cp

    val r = FloatArray(16)
    r[0] = m[0]*r00 + m[4]*r10 + m[8]*r20
    r[1] = m[1]*r00 + m[5]*r10 + m[9]*r20
    r[2] = m[2]*r00 + m[6]*r10 + m[10]*r20
    r[3] = 0f
    r[4] = m[0]*r01 + m[4]*r11 + m[8]*r21
    r[5] = m[1]*r01 + m[5]*r11 + m[9]*r21
    r[6] = m[2]*r01 + m[6]*r11 + m[10]*r21
    r[7] = 0f
    r[8] = m[0]*r02 + m[4]*r12 + m[8]*r22
    r[9] = m[1]*r02 + m[5]*r12 + m[9]*r22
    r[10] = m[2]*r02 + m[6]*r12 + m[10]*r22
    r[11] = 0f
    r[12] = m[12]; r[13] = m[13]; r[14] = m[14]; r[15] = m[15]

    tm.setTransform(instance, r)
}

/**
 * Build mapping from VRM blend shape names to Filament morph targets.
 */
private fun buildBlendShapeMapping(
    engine: com.google.android.filament.Engine,
    asset: FilamentAsset,
    vrmBlendShapes: List<VrmBlendShape>,
    mapping: MutableMap<String, MutableList<Pair<Int, Int>>>
) {
    if (vrmBlendShapes.isEmpty()) return
    mapping.clear()

    val renderableManager = engine.renderableManager
    val entitiesWithMorphTargets = mutableListOf<Triple<Int, Int, Int>>()

    asset.entities.forEachIndexed { index, entity ->
        val instance = renderableManager.getInstance(entity)
        if (instance != 0) {
            val count = renderableManager.getMorphTargetCount(instance)
            if (count > 0) entitiesWithMorphTargets.add(Triple(entity, index, count))
        }
    }

    if (entitiesWithMorphTargets.isNotEmpty()) {
        val (targetEntity, _, targetMorphCount) = entitiesWithMorphTargets.first()
        vrmBlendShapes.forEach { vrmBlendShape ->
            val blendShapeName = vrmBlendShape.name.lowercase()
            val presetName = vrmBlendShape.preset?.name?.lowercase()
            vrmBlendShape.bindings.forEach { binding ->
                if (binding.morphTargetIndex < targetMorphCount) {
                    mapping.getOrPut(blendShapeName) { mutableListOf() }
                        .add(Pair(targetEntity, binding.morphTargetIndex))
                    if (presetName != null && presetName != "unknown") {
                        mapping.getOrPut(presetName) { mutableListOf() }
                            .add(Pair(targetEntity, binding.morphTargetIndex))
                    }
                }
            }
        }
    }

    Log.d(TAG, "Blend shape mapping built: ${mapping.size} entries")
}

// ==================== Placement Overlay ====================

@Composable
private fun ArPlacementOverlaySceneView(
    isPlaced: Boolean,
    isInstantPlacement: Boolean = false,
    hasDetectedPlanes: Boolean,
    trackingFailureReason: TrackingFailureReason?,
    centerHitDetected: Boolean = false,
    showRepositionButton: Boolean = false,
    onReposition: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // ---- Focus reticle: pulsing crosshair at screen center ----
        if (!isPlaced) {
            val infiniteTransition = rememberInfiniteTransition(label = "reticle")
            val reticleScale by infiniteTransition.animateFloat(
                initialValue = 0.85f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "reticleScale"
            )
            val reticleColor = if (centerHitDetected) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.4f)
            Canvas(modifier = Modifier.size(48.dp)) {
                val r = size.width / 2f * reticleScale
                drawCircle(
                    color = reticleColor.copy(alpha = 0.6f),
                    radius = r,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
                // Inner dot
                drawCircle(
                    color = reticleColor,
                    radius = 3.dp.toPx()
                )
            }
        }

        when {
            // Avatar placed — show brief confirmation, stabilization hint, or reposition button
            isPlaced -> {
                if (showRepositionButton) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 120.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFFFF5722).copy(alpha = 0.9f),
                            modifier = Modifier.padding(8.dp),
                            onClick = onReposition
                        ) {
                            Text(
                                text = "Riposiziona avatar",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                            )
                        }
                    }
                } else if (isInstantPlacement) {
                    // Instant placement active — avatar visible but position is approximate.
                    // Show a subtle pulsing hint until FULL_TRACKING kicks in.
                    val pulseTransition = rememberInfiniteTransition(label = "stabilize")
                    val pulseAlpha by pulseTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(700, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 120.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFF2196F3).copy(alpha = 0.7f * pulseAlpha)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = "Stabilizzazione...",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                } else {
                    var visible by remember { mutableStateOf(true) }
                    LaunchedEffect(Unit) {
                        delay(1500)
                        visible = false
                    }
                    AnimatedVisibility(visible = visible, exit = fadeOut(tween(500))) {
                        OverlayChip("Avatar posizionato!", Color(0xFF4CAF50))
                    }
                }
            }

            // Tracking failure
            trackingFailureReason != null -> {
                val context = LocalContext.current
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 120.dp)
                ) {
                    OverlayChip(
                        trackingFailureReason.getDescription(context),
                        Color.Black.copy(alpha = 0.6f)
                    )
                }
            }

            // Planes detected — ready to place
            hasDetectedPlanes -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 120.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = if (centerHitDetected) Color(0xFF4CAF50).copy(alpha = 0.8f)
                               else Color.Black.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = if (centerHitDetected) "Tocca per posizionare l'avatar"
                                   else "Punta verso una superficie piana",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            // Scanning for surfaces — with instant placement, user can tap immediately
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 120.dp)
                ) {
                    val scanTransition = rememberInfiniteTransition(label = "scan")
                    val dotAlpha by scanTransition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dotAlpha"
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 12.dp)
                    ) {
                        repeat(3) { i ->
                            Canvas(modifier = Modifier.size(8.dp)) {
                                val alpha = when (i) {
                                    0 -> dotAlpha
                                    1 -> dotAlpha * 0.7f
                                    else -> dotAlpha * 0.4f
                                }
                                drawCircle(
                                    color = Color.White.copy(alpha = alpha),
                                    radius = size.width / 2f
                                )
                            }
                        }
                    }
                    OverlayChip("Tocca dove vuoi posizionare l'avatar", Color.Black.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun OverlayChip(text: String, backgroundColor: Color) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = backgroundColor
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}

// ==================== Ground Shadow ====================

/**
 * Creates a flat translucent disc entity to act as a fake ground shadow.
 * Rendered at the anchor's Y level under the avatar's feet.
 */
private fun createGroundShadowEntity(engine: com.google.android.filament.Engine): Int {
    return try {
        MaterialBuilder.init()
        try {
            val matBuilder = MaterialBuilder()
                .name("GroundShadow")
                .platform(MaterialBuilder.Platform.MOBILE)
                .targetApi(MaterialBuilder.TargetApi.OPENGL)
                .optimization(MaterialBuilder.Optimization.PERFORMANCE)
                .shading(MaterialBuilder.Shading.UNLIT)
                .blending(MaterialBuilder.BlendingMode.TRANSPARENT)
                .depthWrite(false)
                .depthCulling(true)
                .doubleSided(true)
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor = vec4(0.0, 0.0, 0.0, 0.22);
                    }
                    """.trimIndent()
                )

            val matPackage: MaterialPackage = matBuilder.build()
            if (!matPackage.isValid) {
                Log.e(TAG, "Ground shadow material failed to build")
                return 0
            }

            val matData = matPackage.buffer
            val material = Material.Builder()
                .payload(matData, matData.remaining())
                .build(engine)
            val materialInstance = material.createInstance()

            // Disc geometry: center vertex + 32 outer ring vertices
            val segments = 32
            val radius = 0.45f // 45cm radius shadow
            val vertexCount = segments + 1
            val triangleCount = segments

            val positions = FloatArray(vertexCount * 3)
            // Center vertex at origin
            positions[0] = 0f; positions[1] = 0f; positions[2] = 0f
            for (i in 0 until segments) {
                val angle = (2.0 * Math.PI * i / segments).toFloat()
                val idx = (i + 1) * 3
                positions[idx + 0] = cos(angle) * radius
                positions[idx + 1] = 0f
                positions[idx + 2] = sin(angle) * radius
            }

            val indices = ShortArray(triangleCount * 3)
            for (i in 0 until segments) {
                val base = i * 3
                indices[base + 0] = 0 // Center
                indices[base + 1] = (i + 1).toShort()
                indices[base + 2] = ((i + 1) % segments + 1).toShort()
            }

            val posBuffer = ByteBuffer.allocateDirect(positions.size * 4)
                .order(ByteOrder.nativeOrder())
            posBuffer.asFloatBuffer().put(positions)
            posBuffer.rewind()

            val idxBuffer = ByteBuffer.allocateDirect(indices.size * 2)
                .order(ByteOrder.nativeOrder())
            idxBuffer.asShortBuffer().put(indices)
            idxBuffer.rewind()

            val vertexBuffer = VertexBuffer.Builder()
                .vertexCount(vertexCount)
                .bufferCount(1)
                .attribute(
                    VertexBuffer.VertexAttribute.POSITION, 0,
                    VertexBuffer.AttributeType.FLOAT3, 0, 3 * 4
                )
                .build(engine)
            vertexBuffer.setBufferAt(engine, 0, posBuffer)

            val indexBuffer = IndexBuffer.Builder()
                .indexCount(indices.size)
                .bufferType(IndexBuffer.Builder.IndexType.USHORT)
                .build(engine)
            indexBuffer.setBuffer(engine, idxBuffer)

            val entity = EntityManager.get().create()
            RenderableManager.Builder(1)
                .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vertexBuffer, indexBuffer)
                .material(0, materialInstance)
                .culling(false)
                .receiveShadows(false)
                .castShadows(false)
                .priority(6)
                .build(engine, entity)

            Log.d(TAG, "Ground shadow entity created: $entity")
            entity
        } finally {
            MaterialBuilder.shutdown()
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to create ground shadow", e)
        0
    }
}
