# Amica to Calmify: Complete Implementation Plan
## VRM Humanoid Avatar with Full Animation System

**Objective**: Replicare completamente le funzionalità di Amica (TypeScript/Three.js) in Calmify (Android/Kotlin) con integrazione Gemini Live, includendo lip sync, emotion control, auto-blink, gaze tracking, e animazioni idle.

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Phase 1: Core VRM Enhancement](#phase-1-core-vrm-enhancement)
3. [Phase 2: Emotion & Expression System](#phase-2-emotion--expression-system)
4. [Phase 3: Lip Sync Engine](#phase-3-lip-sync-engine)
5. [Phase 4: Auto-Blink System](#phase-4-auto-blink-system)
6. [Phase 5: Gaze & Look-At System](#phase-5-gaze--look-at-system)
7. [Phase 6: VRMA Animation System](#phase-6-vrma-animation-system)
8. [Phase 7: Gemini Live Integration](#phase-7-gemini-live-integration)
9. [Phase 8: Performance & Polish](#phase-8-performance--polish)
10. [Testing Strategy](#testing-strategy)
11. [File Structure](#file-structure)

---

## 🏗️ Architecture Overview

### Current State Analysis

**Existing Components** (✅ Already Implemented):
- `VrmLoader.kt` - Parsing VRM extensions from glTF
- `VrmModel.kt` - Data structures for VRM
- `VrmBlendShapeController.kt` - Basic blend shape interpolation
- `FilamentRenderer.kt` - Filament engine with basic rendering
- `AvatarState.kt` - Avatar state model
- `Emotion.kt` - Emotion sealed class
- `Viseme.kt` - Phoneme to blend shape mapping
- `HumanoidViewModel.kt` - Basic state management

**Missing Components** (❌ To Implement):
- Expression controller with emotion blending
- Lip sync audio analyzer
- Auto-blink timing system
- Gaze/look-at system with saccade
- VRMA animation loader and player
- Gemini Live STT/TTS integration
- Viseme analyzer from audio
- Idle animation manager
- Blend shape smoother with priorities

### Target Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    HumanoidViewModel                         │
│  - Orchestrates all systems                                  │
│  - Manages Gemini Live integration                           │
└─────────────────────────────────────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        │                 │                 │
┌───────▼────────┐ ┌──────▼──────┐ ┌───────▼────────┐
│ ExpressionCtrl │ │  LipSyncEng │ │ AnimationCtrl  │
│ - Emotions     │ │  - Audio    │ │ - VRMA files   │
│ - Blending     │ │  - Visemes  │ │ - Idle loops   │
└────────┬───────┘ └──────┬──────┘ └───────┬────────┘
         │                │                 │
         └────────┬───────┴─────────────────┘
                  │
┌─────────────────▼─────────────────────────────────────┐
│         BlendShapePriorityController                   │
│  - Manages all blend shape sources                     │
│  - Priority system (blink > emotion > lip sync)        │
│  - Smooth transitions                                  │
└─────────────────┬─────────────────────────────────────┘
                  │
┌─────────────────▼─────────────────────────────────────┐
│              FilamentRenderer                          │
│  - VRM rendering with Filament                         │
│  - Applies final blend shapes                          │
│  - Camera & lighting                                   │
└───────────────────────────────────────────────────────┘
```

### Key Design Decisions

1. **Gemini Live for Voice**: Usare Gemini Live API per STT/TTS invece di sistemi separati
2. **Native STT Fallback**: Implementare Android Speech Recognition come fallback performante
3. **Audio Analysis**: Implementare AudioRecord per analisi real-time del volume
4. **Priority System**: Blink > Emotion > Lip Sync > Idle Animation
5. **Smooth Transitions**: Tutti i blend shapes con interpolazione smooth (lerp/slerp)
6. **Modular Design**: Ogni sistema indipendente e testabile

---

## 🔧 Phase 1: Core VRM Enhancement

### Obiettivo
Potenziare il sistema VRM esistente per supportare tutte le funzionalità avanzate.

### Tasks

#### 1.1 - Enhanced VRM Blend Shape System

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/data/vrm/BlendShapePriorityController.kt`

```kotlin
/**
 * Advanced blend shape controller with priority system.
 * Manages multiple blend shape sources with different priorities.
 *
 * Priority Order (highest to lowest):
 * 1. Blink (CRITICAL) - Always override others
 * 2. Emotion (HIGH) - Current emotional state
 * 3. Lip Sync (MEDIUM) - Speech visemes
 * 4. Idle Animation (LOW) - Subtle movements
 */
class BlendShapePriorityController {

    enum class Priority {
        CRITICAL,  // Blink
        HIGH,      // Emotions
        MEDIUM,    // Lip sync
        LOW        // Idle animations
    }

    data class BlendShapeRequest(
        val name: String,
        val weight: Float,
        val priority: Priority,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val requests = mutableMapOf<String, MutableList<BlendShapeRequest>>()
    private val currentWeights = MutableStateFlow<Map<String, Float>>(emptyMap())
    private val blendSpeed = 0.15f

    /**
     * Submit a blend shape request with priority
     */
    fun submitBlendShape(name: String, weight: Float, priority: Priority)

    /**
     * Clear all requests for a specific priority level
     */
    fun clearPriority(priority: Priority)

    /**
     * Update and resolve conflicts - call every frame
     */
    fun update(deltaTime: Float): Map<String, Float>

    /**
     * Resolve conflicting blend shape requests
     * Higher priority wins, same priority = newest wins
     */
    private fun resolveConflicts(): Map<String, Float>
}
```

**Implementation Details**:
- Ogni sistema (blink, emotion, lip sync) sottomette richieste con priorità
- Il controller risolve i conflitti e produce i pesi finali
- Interpolazione smooth per evitare scatti
- Timestamp per risolvere conflitti a parità di priorità

#### 1.2 - VRM Look-At Bone System

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/data/vrm/VrmLookAtController.kt`

```kotlin
/**
 * Controls VRM look-at system (eye and head rotation).
 * Based on VRM specification for humanoid bone structure.
 */
class VrmLookAtController(
    private val filamentRenderer: FilamentRenderer,
    private val vrmModel: VrmModel
) {

    // VRM humanoid bones we need
    private var headBone: Int? = null
    private var leftEyeBone: Int? = null
    private var rightEyeBone: Int? = null

    // Look-at target in world space
    private var targetPosition = Vector3(0f, 0f, -1f)

    // Smooth parameters
    private var currentYaw = 0f
    private var dampedYaw = 0f
    private var currentPitch = 0f
    private var dampedPitch = 0f

    private val smoothFactor = 4.0f
    private val userLimitAngle = 90.0f

    /**
     * Set look-at target position
     */
    fun setTargetPosition(x: Float, y: Float, z: Float)

    /**
     * Look at camera (user)
     */
    fun lookAtCamera()

    /**
     * Update look-at with smoothing - call every frame
     */
    fun update(deltaTime: Float)

    /**
     * Apply yaw/pitch to eye and head bones
     */
    private fun applyYawPitch(yaw: Float, pitch: Float)
}
```

#### 1.3 - VRMA Animation File Support

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/data/vrm/VrmaLoader.kt`

```kotlin
/**
 * Loader for VRMA (VRM Animation) files.
 * VRMA is glTF with VRM animation extensions.
 *
 * Specification: https://github.com/vrm-c/vrm-specification/tree/master/specification/VRMC_vrm_animation-1.0
 */
class VrmaLoader(private val context: Context) {

    data class VrmaAnimation(
        val duration: Float,
        val humanoidTracks: Map<VrmHumanBoneName, AnimationTrack>,
        val expressionTracks: Map<String, FloatAnimationTrack>,
        val lookAtTrack: RotationAnimationTrack?
    )

    data class AnimationTrack(
        val times: FloatArray,
        val positions: Array<Vector3>?,
        val rotations: Array<Quaternion>?
    )

    data class FloatAnimationTrack(
        val times: FloatArray,
        val values: FloatArray
    )

    data class RotationAnimationTrack(
        val times: FloatArray,
        val rotations: Array<Quaternion>
    )

    /**
     * Load VRMA animation from assets
     */
    suspend fun loadVrmaFromAssets(assetPath: String): VrmaAnimation?

    /**
     * Parse VRMA extensions from glTF
     */
    private fun parseVrmaExtensions(buffer: ByteBuffer): VrmaAnimation
}
```

**Assets to Import**:
```
features/humanoid/src/main/assets/animations/
├── idle_loop.vrma          # Main idle animation
├── greeting.vrma           # Wave hello
├── peaceSign.vrma          # Peace sign gesture
├── modelPose.vrma          # Model pose
├── shoot.vrma              # Finger gun
├── dance.vrma              # Dance animation
├── showFullBody.vrma       # Full body show
├── squat.vrma              # Squat motion
└── spin.vrma               # Spin around
```

#### 1.4 - Animation Player System

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/animation/VrmaPlayer.kt`

```kotlin
/**
 * Plays VRMA animations on VRM model.
 * Supports looping, blending, and transitions.
 */
class VrmaPlayer(
    private val vrmModel: VrmModel,
    private val blendShapeController: BlendShapePriorityController,
    private val lookAtController: VrmLookAtController
) {

    private var currentAnimation: VrmaAnimation? = null
    private var currentTime = 0f
    private var isPlaying = false
    private var isLooping = false

    /**
     * Play animation (with optional loop)
     */
    fun play(animation: VrmaAnimation, loop: Boolean = false)

    /**
     * Stop current animation
     */
    fun stop()

    /**
     * Update animation - call every frame
     */
    fun update(deltaTime: Float)

    /**
     * Apply animation at current time
     */
    private fun applyAnimationFrame(time: Float)

    /**
     * Interpolate between keyframes
     */
    private fun interpolateKeyframes(
        times: FloatArray,
        values: Any,
        currentTime: Float
    ): Any
}
```

---

## 🎭 Phase 2: Emotion & Expression System

### Obiettivo
Sistema completo per gestire le emozioni con transizioni smooth e blending.

### Tasks

#### 2.1 - Expression Controller

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/expression/ExpressionController.kt`

```kotlin
/**
 * High-level controller for facial expressions.
 * Manages emotion transitions and prevents conflicts with blink.
 *
 * Based on Amica's ExpressionController.ts
 */
class ExpressionController(
    private val blendShapeController: BlendShapePriorityController,
    private val autoBlinkController: AutoBlinkController
) {

    private var currentEmotion: Emotion = Emotion.Neutral
    private var targetEmotion: Emotion = Emotion.Neutral
    private var transitionProgress = 0f

    // VRM standard expression presets mapping
    private val emotionToBlendShapes = mapOf(
        Emotion.Happy::class to mapOf("joy" to 1.0f, "happy" to 1.0f, "smile" to 1.0f),
        Emotion.Sad::class to mapOf("sorrow" to 1.0f, "sad" to 1.0f),
        Emotion.Angry::class to mapOf("angry" to 1.0f),
        Emotion.Surprised::class to mapOf("surprised" to 0.5f), // 0.5 per evitare esagerazione
        Emotion.Thinking::class to mapOf("thinking" to 1.0f, "serious" to 0.5f),
        Emotion.Neutral::class to mapOf("neutral" to 1.0f),
        Emotion.Excited::class to mapOf("fun" to 1.0f, "excited" to 1.0f),
        Emotion.Calm::class to mapOf("neutral" to 0.7f),
        Emotion.Confused::class to mapOf("confused" to 1.0f),
        Emotion.Worried::class to mapOf("worried" to 1.0f),
        Emotion.Disappointed::class to mapOf("sorrow" to 0.7f, "disappointed" to 1.0f)
    )

    /**
     * Play emotion with smooth transition.
     * Waits for blink to finish before applying.
     */
    suspend fun playEmotion(emotion: Emotion) {
        if (emotion == currentEmotion) return

        // Disable auto-blink and wait for eyes to open
        val waitTime = autoBlinkController.disableAndGetWaitTime()
        if (waitTime > 0) {
            delay((waitTime * 1000).toLong())
        }

        // Clear previous emotion
        if (currentEmotion != Emotion.Neutral) {
            clearEmotion(currentEmotion)
        }

        // Apply new emotion
        targetEmotion = emotion
        if (emotion == Emotion.Neutral) {
            autoBlinkController.enable()
        }
    }

    /**
     * Update emotion blending - call every frame
     */
    fun update(deltaTime: Float) {
        // Smooth transition between emotions
        if (transitionProgress < 1.0f) {
            transitionProgress += deltaTime * 2.0f // 0.5 seconds transition
            applyEmotionBlendShapes(targetEmotion, transitionProgress.coerceAtMost(1.0f))
        }
    }

    /**
     * Apply emotion blend shapes with intensity
     */
    private fun applyEmotionBlendShapes(emotion: Emotion, intensity: Float) {
        val blendShapes = getBlendShapesForEmotion(emotion)
        blendShapes.forEach { (name, weight) ->
            blendShapeController.submitBlendShape(
                name = name,
                weight = weight * intensity * emotion.intensity,
                priority = BlendShapePriorityController.Priority.HIGH
            )
        }
    }

    /**
     * Clear emotion blend shapes
     */
    private fun clearEmotion(emotion: Emotion) {
        val blendShapes = getBlendShapesForEmotion(emotion)
        blendShapes.forEach { (name, _) ->
            blendShapeController.submitBlendShape(
                name = name,
                weight = 0f,
                priority = BlendShapePriorityController.Priority.HIGH
            )
        }
    }

    /**
     * Get blend shapes for emotion
     */
    private fun getBlendShapesForEmotion(emotion: Emotion): Map<String, Float> {
        return emotionToBlendShapes[emotion::class] ?: mapOf("neutral" to 1.0f)
    }
}
```

#### 2.2 - Custom Expressions Registration

**Enhancement**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/expression/CustomExpressionRegistry.kt`

```kotlin
/**
 * Registry for custom VRM expressions beyond standard presets.
 * Allows avatars to define custom emotions.
 */
class CustomExpressionRegistry {

    private val customExpressions = mutableMapOf<String, Map<String, Float>>()

    /**
     * Register custom expressions from VRM model
     */
    fun registerFromVrm(vrmModel: VrmModel) {
        vrmModel.blendShapes
            .filter { it.preset == VrmBlendShape.BlendShapePreset.UNKNOWN }
            .forEach { blendShape ->
                // Custom blend shape found
                customExpressions[blendShape.name.lowercase()] = mapOf(
                    blendShape.name to 1.0f
                )
            }
    }

    /**
     * Get all available expression names
     */
    fun getAllExpressionNames(): List<String>

    /**
     * Check if expression exists
     */
    fun hasExpression(name: String): Boolean
}
```

---

## 🎤 Phase 3: Lip Sync Engine

### Obiettivo
Sistema di lip sync real-time basato su analisi audio con viseme mapping.

### Tasks

#### 3.1 - Audio Analyzer

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/audio/AudioAnalyzer.kt`

```kotlin
/**
 * Real-time audio analyzer for lip sync.
 * Analyzes audio stream and calculates volume for mouth opening.
 *
 * Based on Amica's LipSync.ts
 */
class AudioAnalyzer {

    companion object {
        private const val TIME_DOMAIN_DATA_LENGTH = 2048
    }

    private var audioTrack: AudioTrack? = null
    private val timeDomainData = FloatArray(TIME_DOMAIN_DATA_LENGTH)

    /**
     * Analyze current audio and return volume (0.0 - 1.0)
     */
    fun analyze(): Float {
        // Get audio samples from AudioTrack
        val samples = getAudioSamples()

        // Calculate RMS volume
        var volume = 0.0f
        for (sample in samples) {
            volume = maxOf(volume, abs(sample))
        }

        // Apply sigmoid function to normalize (Amica's "cook" function)
        // volume = 1 / (1 + exp(-45 * volume + 5))
        volume = 1f / (1f + exp(-45f * volume + 5f))

        // Threshold to eliminate noise
        if (volume < 0.1f) {
            volume = 0f
        }

        return volume
    }

    /**
     * Attach to audio source for real-time analysis
     */
    fun attachToAudioTrack(audioTrack: AudioTrack)

    /**
     * Get audio samples from track
     */
    private fun getAudioSamples(): FloatArray

    /**
     * Cleanup resources
     */
    fun cleanup()
}
```

#### 3.2 - Viseme Analyzer

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/audio/VisemeAnalyzer.kt`

```kotlin
/**
 * Analyzes audio/text and produces viseme sequence.
 *
 * Two modes:
 * 1. Volume-based (simple): Uses volume for generic mouth opening (A/I/U visemes)
 * 2. Phoneme-based (advanced): Uses TTS phoneme callbacks for accurate visemes
 */
class VisemeAnalyzer(
    private val audioAnalyzer: AudioAnalyzer
) {

    /**
     * Analyze volume and produce appropriate viseme
     */
    fun analyzeVolume(): Viseme {
        val volume = audioAnalyzer.analyze()

        return when {
            volume < 0.1f -> Viseme.SILENCE
            volume < 0.3f -> Viseme.I      // Small mouth
            volume < 0.6f -> Viseme.E      // Medium mouth
            else -> Viseme.AA               // Large mouth open
        }
    }

    /**
     * Get viseme from phoneme (for TTS integration)
     */
    fun getVisemeForPhoneme(phoneme: String): Viseme {
        // Map IPA phonemes to visemes
        return when (phoneme.lowercase()) {
            "a", "ɑ", "æ" -> Viseme.AA
            "e", "ɛ", "ə" -> Viseme.E
            "i", "ɪ" -> Viseme.I
            "o", "ɔ" -> Viseme.O
            "u", "ʊ" -> Viseme.U
            "m", "b", "p" -> Viseme.M_B_P
            "f", "v" -> Viseme.F_V
            "θ", "ð" -> Viseme.TH
            "t", "d", "n", "l" -> Viseme.T_D_N_L
            "s", "z" -> Viseme.S_Z
            "ʃ", "ʒ", "tʃ", "dʒ" -> Viseme.SH_ZH_CH_J
            "k", "g", "ŋ" -> Viseme.K_G_NG
            "r", "ɹ" -> Viseme.R
            "w" -> Viseme.W
            else -> Viseme.SILENCE
        }
    }

    /**
     * Analyze text and generate timed viseme sequence
     * (for pre-recorded TTS)
     */
    fun analyzeText(text: String, duration: Float): List<TimedViseme> {
        // Simple implementation: distribute visemes evenly
        val words = text.split(" ")
        val visemes = mutableListOf<TimedViseme>()

        val timePerWord = duration / words.size
        var currentTime = 0f

        words.forEach { word ->
            word.forEach { char ->
                visemes.add(TimedViseme(
                    viseme = Viseme.fromPhoneme(char),
                    startTime = currentTime,
                    duration = timePerWord / word.length
                ))
                currentTime += timePerWord / word.length
            }
        }

        return visemes
    }

    data class TimedViseme(
        val viseme: Viseme,
        val startTime: Float,
        val duration: Float
    )
}
```

#### 3.3 - Lip Sync Controller

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/lipsync/LipSyncController.kt`

```kotlin
/**
 * Controls lip sync animation in real-time.
 * Applies visemes to blend shapes with proper weighting.
 */
class LipSyncController(
    private val blendShapeController: BlendShapePriorityController,
    private val visemeAnalyzer: VisemeAnalyzer
) {

    private var currentViseme: Viseme = Viseme.SILENCE
    private var isSpeaking = false

    /**
     * Update lip sync - call every frame during speech
     */
    fun update(deltaTime: Float) {
        if (!isSpeaking) {
            // Reset to silence
            applyViseme(Viseme.SILENCE, 1.0f)
            return
        }

        // Get current viseme from audio analysis
        val viseme = visemeAnalyzer.analyzeVolume()
        currentViseme = viseme

        // Apply viseme with appropriate weight
        applyViseme(viseme, 1.0f)
    }

    /**
     * Apply viseme blend shapes
     */
    private fun applyViseme(viseme: Viseme, intensity: Float) {
        // Get blend shapes for this viseme
        val blendShapes = viseme.blendShapes

        // Reduce intensity based on emotion (from Amica logic)
        // If neutral: 0.5x, if emotion: 0.25x
        val weight = intensity * 0.5f // Assume neutral for now

        blendShapes.forEach { (name, value) ->
            blendShapeController.submitBlendShape(
                name = name,
                weight = value * weight,
                priority = BlendShapePriorityController.Priority.MEDIUM
            )
        }
    }

    /**
     * Start lip sync
     */
    fun startSpeaking() {
        isSpeaking = true
    }

    /**
     * Stop lip sync
     */
    fun stopSpeaking() {
        isSpeaking = false
        applyViseme(Viseme.SILENCE, 1.0f)
    }

    /**
     * Set speaking state with emotion consideration
     */
    fun setSpeaking(speaking: Boolean, currentEmotion: Emotion) {
        isSpeaking = speaking
    }
}
```

---

## 👁️ Phase 4: Auto-Blink System

### Obiettivo
Sistema di blinking automatico realistico con timing naturale.

### Tasks

#### 4.1 - Auto Blink Controller

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/blink/AutoBlinkController.kt`

```kotlin
/**
 * Automatic blinking system with natural timing.
 *
 * Based on Amica's AutoBlink.ts
 * - Blink duration: ~0.12 seconds
 * - Blink interval: 2-5 seconds (randomized)
 */
class AutoBlinkController(
    private val blendShapeController: BlendShapePriorityController
) {

    companion object {
        private const val BLINK_CLOSE_DURATION = 0.12f  // seconds
        private const val BLINK_OPEN_MIN = 2.0f         // seconds
        private const val BLINK_OPEN_MAX = 5.0f         // seconds
    }

    private var remainingTime = 0f
    private var isOpen = true
    private var isAutoBlinkEnabled = true

    /**
     * Enable/disable auto-blink.
     * Returns wait time if eyes are currently closed.
     */
    fun disableAndGetWaitTime(): Float {
        isAutoBlinkEnabled = false

        return if (!isOpen) {
            // Eyes are closed, return time until they open
            remainingTime
        } else {
            0f
        }
    }

    /**
     * Enable auto-blink
     */
    fun enable() {
        isAutoBlinkEnabled = true
    }

    /**
     * Update blink state - call every frame
     */
    fun update(deltaTime: Float) {
        if (remainingTime > 0) {
            remainingTime -= deltaTime
            return
        }

        if (isOpen && isAutoBlinkEnabled) {
            // Time to close eyes
            close()
        } else {
            // Time to open eyes
            open()
        }
    }

    /**
     * Close eyes (blink)
     */
    private fun close() {
        isOpen = false
        remainingTime = BLINK_CLOSE_DURATION

        // Apply blink blend shape with CRITICAL priority
        blendShapeController.submitBlendShape(
            name = "blink",
            weight = 1.0f,
            priority = BlendShapePriorityController.Priority.CRITICAL
        )

        // Also try individual eye blinks
        blendShapeController.submitBlendShape(
            name = "blink_l",
            weight = 1.0f,
            priority = BlendShapePriorityController.Priority.CRITICAL
        )
        blendShapeController.submitBlendShape(
            name = "blink_r",
            weight = 1.0f,
            priority = BlendShapePriorityController.Priority.CRITICAL
        )
    }

    /**
     * Open eyes
     */
    private fun open() {
        isOpen = true

        // Random interval until next blink (natural variation)
        remainingTime = BLINK_OPEN_MIN + Random.nextFloat() * (BLINK_OPEN_MAX - BLINK_OPEN_MIN)

        // Clear blink blend shapes
        blendShapeController.submitBlendShape(
            name = "blink",
            weight = 0f,
            priority = BlendShapePriorityController.Priority.CRITICAL
        )
        blendShapeController.submitBlendShape(
            name = "blink_l",
            weight = 0f,
            priority = BlendShapePriorityController.Priority.CRITICAL
        )
        blendShapeController.submitBlendShape(
            name = "blink_r",
            weight = 0f,
            priority = BlendShapePriorityController.Priority.CRITICAL
        )
    }
}
```

---

## 👀 Phase 5: Gaze & Look-At System

### Obiettivo
Sistema di sguardo realistico con saccade (micro-movimenti oculari) e head rotation.

### Tasks

#### 5.1 - Saccade Controller

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/lookat/SaccadeController.kt`

```kotlin
/**
 * Simulates natural eye saccade movements.
 *
 * Saccades are rapid eye movements that occur naturally.
 * Based on Amica's VRMLookAtSmoother.ts
 */
class SaccadeController {

    companion object {
        private const val SACCADE_MIN_INTERVAL = 0.5f    // seconds
        private const val SACCADE_PROBABILITY = 0.05f    // 5% chance per frame
        private const val SACCADE_RADIUS = 5.0f          // degrees
    }

    private var saccadeYaw = 0f
    private var saccadePitch = 0f
    private var saccadeTimer = 0f
    private var isEnabled = true

    /**
     * Update saccade - call every frame
     * Returns current saccade offset (yaw, pitch)
     */
    fun update(deltaTime: Float): Pair<Float, Float> {
        if (!isEnabled) {
            return Pair(0f, 0f)
        }

        saccadeTimer += deltaTime

        // Check if it's time for a new saccade
        if (saccadeTimer > SACCADE_MIN_INTERVAL && Random.nextFloat() < SACCADE_PROBABILITY) {
            // Generate random saccade direction
            saccadeYaw = (Random.nextFloat() * 2f - 1f) * SACCADE_RADIUS
            saccadePitch = (Random.nextFloat() * 2f - 1f) * SACCADE_RADIUS
            saccadeTimer = 0f
        }

        return Pair(saccadeYaw, saccadePitch)
    }

    fun enable() {
        isEnabled = true
    }

    fun disable() {
        isEnabled = false
        saccadeYaw = 0f
        saccadePitch = 0f
    }
}
```

#### 5.2 - Advanced Look-At Controller

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/lookat/LookAtController.kt`

```kotlin
/**
 * Advanced look-at system with smooth following and saccade.
 * Controls both eye rotation and head rotation.
 *
 * Based on Amica's VRMLookAtSmoother and AutoLookAt
 */
class LookAtController(
    private val filamentRenderer: FilamentRenderer,
    private val vrmModel: VrmModel,
    private val saccadeController: SaccadeController
) {

    companion object {
        private const val SMOOTH_FACTOR = 4.0f
        private const val USER_LIMIT_ANGLE = 90.0f
        private const val HEAD_ROTATION_FACTOR = 0.4f  // How much head follows gaze
        private const val EYE_ROTATION_FACTOR = 0.6f   // Eye rotation multiplier
    }

    // Target position in world space (e.g., camera position)
    private var userTarget: Vector3? = null

    // Animation target (from VRMA)
    private var animationTarget: Vector3? = null

    // Current and damped yaw/pitch
    private var yaw = 0f
    private var pitch = 0f
    private var yawDamped = 0f
    private var pitchDamped = 0f

    // Bone references
    private var headBone: Int? = null
    private var leftEyeBone: Int? = null
    private var rightEyeBone: Int? = null

    /**
     * Set user target (camera) position to look at
     */
    fun setUserTarget(x: Float, y: Float, z: Float) {
        userTarget = Vector3(x, y, z)
    }

    /**
     * Set animation target (from VRMA)
     */
    fun setAnimationTarget(x: Float, y: Float, z: Float) {
        animationTarget = Vector3(x, y, z)
    }

    /**
     * Enable/disable user look-at
     */
    fun enableUserLookAt(enabled: Boolean) {
        if (!enabled) {
            userTarget = null
        }
    }

    /**
     * Update look-at - call every frame
     */
    fun update(deltaTime: Float) {
        // 1. Calculate yaw/pitch from animation target
        val (yawAnim, pitchAnim) = animationTarget?.let {
            calculateYawPitch(it)
        } ?: Pair(0f, 0f)

        var finalYaw = yawAnim
        var finalPitch = pitchAnim

        // 2. If user target exists, blend with animation
        userTarget?.let { target ->
            val (yawUser, pitchUser) = calculateYawPitch(target)

            // Check angle limits
            if (abs(yawUser) < USER_LIMIT_ANGLE && abs(pitchUser) < USER_LIMIT_ANGLE) {
                // Smooth damping
                val k = 1.0f - exp(-SMOOTH_FACTOR * deltaTime)
                yawDamped += (yawUser - yawDamped) * k
                pitchDamped += (pitchUser - pitchDamped) * k

                // Blend animation with user gaze
                // If animation is looking away (> 30°), prioritize animation
                val animAngle = sqrt(yawAnim * yawAnim + pitchAnim * pitchAnim)
                val userRatio = 1.0f - smoothstep(animAngle, 30.0f, 90.0f)

                finalYaw = lerp(yawAnim, EYE_ROTATION_FACTOR * yawDamped, userRatio)
                finalPitch = lerp(pitchAnim, EYE_ROTATION_FACTOR * pitchDamped, userRatio)

                // Apply head rotation (less than eyes)
                applyHeadRotation(yawDamped, pitchDamped)
            }
        }

        // 3. Add saccade micro-movements
        val (saccadeYaw, saccadePitch) = saccadeController.update(deltaTime)
        finalYaw += saccadeYaw
        finalPitch += saccadePitch

        // 4. Apply final rotation to eyes
        applyEyeRotation(finalYaw, finalPitch)
    }

    /**
     * Calculate yaw and pitch angles to look at target
     */
    private fun calculateYawPitch(target: Vector3): Pair<Float, Float> {
        // Get head position from VRM model
        val headPos = getHeadPosition()

        // Direction vector from head to target
        val direction = target - headPos
        direction.normalize()

        // Calculate yaw (horizontal rotation)
        val yaw = atan2(direction.x, -direction.z) * RAD_TO_DEG

        // Calculate pitch (vertical rotation)
        val pitch = asin(direction.y) * RAD_TO_DEG

        return Pair(yaw, pitch)
    }

    /**
     * Apply rotation to head bone
     */
    private fun applyHeadRotation(yaw: Float, pitch: Float) {
        headBone?.let { bone ->
            val rotation = Quaternion()
            rotation.setFromEuler(
                -pitch * DEG_TO_RAD * HEAD_ROTATION_FACTOR,
                yaw * DEG_TO_RAD * HEAD_ROTATION_FACTOR,
                0f
            )

            // Apply to Filament transform
            filamentRenderer.setBoneRotation(bone, rotation)
        }
    }

    /**
     * Apply rotation to eye bones
     */
    private fun applyEyeRotation(yaw: Float, pitch: Float) {
        val rotation = Quaternion()
        rotation.setFromEuler(
            -pitch * DEG_TO_RAD,
            yaw * DEG_TO_RAD,
            0f
        )

        leftEyeBone?.let { bone ->
            filamentRenderer.setBoneRotation(bone, rotation)
        }

        rightEyeBone?.let { bone ->
            filamentRenderer.setBoneRotation(bone, rotation)
        }
    }

    /**
     * Smoothstep function for smooth blending
     */
    private fun smoothstep(x: Float, edge0: Float, edge1: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Linear interpolation
     */
    private fun lerp(a: Float, b: Float, t: Float): Float {
        return a + (b - a) * t
    }

    private fun getHeadPosition(): Vector3 {
        // TODO: Get head bone world position from Filament
        return Vector3(0f, 1.5f, 0f) // Default head height
    }
}
```

---

## 🎬 Phase 6: VRMA Animation System

### Obiettivo
Sistema completo per caricare e riprodurre animazioni VRMA (idle, gesture, ecc.).

### Tasks

#### 6.1 - Idle Animation Manager

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/animation/IdleAnimationManager.kt`

```kotlin
/**
 * Manages idle animations to keep avatar looking alive.
 * Plays looping idle animations with smooth transitions.
 */
class IdleAnimationManager(
    private val vrmaLoader: VrmaLoader,
    private val vrmaPlayer: VrmaPlayer
) {

    enum class IdleType {
        IDLE_LOOP,      // Main breathing/subtle movement loop
        BREATHING,      // Just breathing
        HEAD_TILT,      // Occasional head movement
        NONE
    }

    private var currentIdleType = IdleType.IDLE_LOOP
    private val idleAnimations = mutableMapOf<IdleType, VrmaAnimation>()

    /**
     * Load idle animations from assets
     */
    suspend fun loadIdleAnimations() {
        idleAnimations[IdleType.IDLE_LOOP] =
            vrmaLoader.loadVrmaFromAssets("animations/idle_loop.vrma")
                ?: throw IllegalStateException("Failed to load idle_loop.vrma")

        // Load other idle variants if needed
    }

    /**
     * Start playing idle animation
     */
    fun startIdle(type: IdleType = IdleType.IDLE_LOOP) {
        currentIdleType = type
        val animation = idleAnimations[type] ?: return

        vrmaPlayer.play(animation, loop = true)
    }

    /**
     * Stop idle animation
     */
    fun stopIdle() {
        vrmaPlayer.stop()
        currentIdleType = IdleType.NONE
    }

    /**
     * Switch to different idle type
     */
    fun switchIdle(type: IdleType) {
        if (type == currentIdleType) return

        stopIdle()
        startIdle(type)
    }
}
```

#### 6.2 - Gesture Animation Manager

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/animation/GestureAnimationManager.kt`

```kotlin
/**
 * Manages gesture animations (greeting, peace sign, etc.)
 * Plays one-shot animations and returns to idle.
 */
class GestureAnimationManager(
    private val vrmaLoader: VrmaLoader,
    private val vrmaPlayer: VrmaPlayer,
    private val idleManager: IdleAnimationManager
) {

    enum class GestureType {
        GREETING,       // Wave hello
        PEACE_SIGN,     // Peace sign
        MODEL_POSE,     // Model pose
        SHOOT,          // Finger gun
        DANCE,          // Dance animation
        SHOW_FULL_BODY, // Show full body
        SQUAT,          // Squat
        SPIN            // Spin around
    }

    private val gestureAnimations = mutableMapOf<GestureType, VrmaAnimation>()
    private var isPlayingGesture = false

    /**
     * Load all gesture animations
     */
    suspend fun loadGestureAnimations() {
        gestureAnimations[GestureType.GREETING] =
            vrmaLoader.loadVrmaFromAssets("animations/greeting.vrma")!!
        gestureAnimations[GestureType.PEACE_SIGN] =
            vrmaLoader.loadVrmaFromAssets("animations/peaceSign.vrma")!!
        gestureAnimations[GestureType.MODEL_POSE] =
            vrmaLoader.loadVrmaFromAssets("animations/modelPose.vrma")!!
        gestureAnimations[GestureType.SHOOT] =
            vrmaLoader.loadVrmaFromAssets("animations/shoot.vrma")!!
        gestureAnimations[GestureType.DANCE] =
            vrmaLoader.loadVrmaFromAssets("animations/dance.vrma")!!
        gestureAnimations[GestureType.SHOW_FULL_BODY] =
            vrmaLoader.loadVrmaFromAssets("animations/showFullBody.vrma")!!
        gestureAnimations[GestureType.SQUAT] =
            vrmaLoader.loadVrmaFromAssets("animations/squat.vrma")!!
        gestureAnimations[GestureType.SPIN] =
            vrmaLoader.loadVrmaFromAssets("animations/spin.vrma")!!
    }

    /**
     * Play gesture animation (one-shot)
     */
    suspend fun playGesture(type: GestureType) {
        if (isPlayingGesture) return

        val animation = gestureAnimations[type] ?: return

        // Stop idle
        idleManager.stopIdle()

        // Play gesture
        isPlayingGesture = true
        vrmaPlayer.play(animation, loop = false)

        // Wait for animation to complete
        delay((animation.duration * 1000).toLong())

        // Resume idle
        isPlayingGesture = false
        idleManager.startIdle()
    }

    /**
     * Check if gesture is playing
     */
    fun isGesturePlaying(): Boolean = isPlayingGesture
}
```

---

## 🤖 Phase 7: Gemini Live Integration

### Obiettivo
Integrare Gemini Live API per conversazione real-time con STT/TTS nativi.

### Tasks

#### 7.1 - Gemini Live Audio Manager

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/gemini/GeminiLiveAudioManager.kt`

```kotlin
/**
 * Manages Gemini Live audio streaming for real-time conversation.
 * Handles bidirectional audio: user speech -> Gemini -> avatar speech
 */
class GeminiLiveAudioManager(
    private val context: Context,
    private val apiKey: String
) {

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var geminiClient: GeminiLiveClient? = null
    private var audioRecorder: AudioRecord? = null
    private var audioPlayer: AudioTrack? = null
    private var isRecording = false
    private var isPlaying = false

    private val _conversationState = MutableStateFlow<ConversationState>(ConversationState.Idle)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _responseAudio = MutableSharedFlow<ByteArray>()
    val responseAudio: SharedFlow<ByteArray> = _responseAudio.asSharedFlow()

    sealed class ConversationState {
        object Idle : ConversationState()
        object Listening : ConversationState()
        object Processing : ConversationState()
        data class Speaking(val text: String) : ConversationState()
        data class Error(val message: String) : ConversationState()
    }

    /**
     * Initialize Gemini Live client
     */
    suspend fun initialize() {
        geminiClient = GeminiLiveClient(apiKey)
        geminiClient?.connect()

        // Listen for responses
        listenForGeminiResponses()
    }

    /**
     * Start listening to user
     */
    fun startListening() {
        if (isRecording) return

        _conversationState.value = ConversationState.Listening

        // Initialize AudioRecord
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )

        audioRecorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        // Start recording and stream to Gemini
        audioRecorder?.startRecording()
        isRecording = true

        CoroutineScope(Dispatchers.IO).launch {
            streamAudioToGemini()
        }
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        isRecording = false
        audioRecorder?.stop()
        audioRecorder?.release()
        audioRecorder = null

        _conversationState.value = ConversationState.Processing
    }

    /**
     * Stream audio to Gemini Live
     */
    private suspend fun streamAudioToGemini() {
        val buffer = ShortArray(1024)

        while (isRecording) {
            val read = audioRecorder?.read(buffer, 0, buffer.size) ?: 0
            if (read > 0) {
                // Convert to byte array and send to Gemini
                val bytes = shortArrayToByteArray(buffer, read)
                geminiClient?.sendAudio(bytes)
            }
        }
    }

    /**
     * Listen for Gemini responses
     */
    private fun listenForGeminiResponses() {
        CoroutineScope(Dispatchers.IO).launch {
            geminiClient?.responses?.collect { response ->
                when (response) {
                    is GeminiResponse.Text -> {
                        // Text response received
                        _conversationState.value = ConversationState.Speaking(response.text)
                    }
                    is GeminiResponse.Audio -> {
                        // Audio response received
                        playResponseAudio(response.audioData)
                        _responseAudio.emit(response.audioData)
                    }
                    is GeminiResponse.Error -> {
                        _conversationState.value = ConversationState.Error(response.message)
                    }
                }
            }
        }
    }

    /**
     * Play Gemini's audio response
     */
    private fun playResponseAudio(audioData: ByteArray) {
        if (isPlaying) return

        isPlaying = true

        // Initialize AudioTrack
        val bufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AUDIO_FORMAT
        )

        audioPlayer = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AUDIO_FORMAT)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        audioPlayer?.play()
        audioPlayer?.write(audioData, 0, audioData.size)

        // Wait for playback to finish
        Thread.sleep((audioData.size * 1000L / SAMPLE_RATE / 2)) // Rough estimate

        audioPlayer?.stop()
        audioPlayer?.release()
        audioPlayer = null
        isPlaying = false

        _conversationState.value = ConversationState.Idle
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopListening()
        audioPlayer?.release()
        geminiClient?.disconnect()
    }

    private fun shortArrayToByteArray(shorts: ShortArray, length: Int): ByteArray {
        val bytes = ByteArray(length * 2)
        for (i in 0 until length) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
```

#### 7.2 - Native STT Fallback

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/stt/NativeSttManager.kt`

```kotlin
/**
 * Native Android Speech Recognition as fallback.
 * Used when Gemini Live is unavailable or for specific use cases.
 */
class NativeSttManager(
    private val context: Context
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private val _recognitionResults = MutableSharedFlow<String>()
    val recognitionResults: SharedFlow<String> = _recognitionResults.asSharedFlow()

    /**
     * Initialize speech recognizer
     */
    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            throw IllegalStateException("Speech recognition not available")
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(recognitionListener)
    }

    /**
     * Start listening
     */
    fun startListening() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        speechRecognizer?.startListening(intent)
    }

    /**
     * Stop listening
     */
    fun stopListening() {
        speechRecognizer?.stopListening()
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { text ->
                CoroutineScope(Dispatchers.Main).launch {
                    _recognitionResults.emit(text)
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            // Handle partial results for real-time feedback
        }

        override fun onError(error: Int) {
            // Handle errors
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Cleanup
     */
    fun cleanup() {
        speechRecognizer?.destroy()
        speechRecognizer = null
    }
}
```

#### 7.3 - Emotion Detection from Text

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/domain/emotion/EmotionDetector.kt`

```kotlin
/**
 * Detects emotion from text using simple keyword matching.
 * Can be enhanced with ML model for better accuracy.
 */
class EmotionDetector {

    private val emotionKeywords = mapOf(
        Emotion.Happy::class to listOf("happy", "joy", "great", "wonderful", "excellent", "love", "haha", "lol", "😊", "😄"),
        Emotion.Sad::class to listOf("sad", "unhappy", "depressed", "down", "cry", "😢", "😞"),
        Emotion.Angry::class to listOf("angry", "mad", "furious", "annoyed", "hate", "😠", "😡"),
        Emotion.Surprised::class to listOf("wow", "amazing", "surprised", "shocked", "omg", "😲", "😮"),
        Emotion.Thinking::class to listOf("hmm", "think", "wonder", "maybe", "perhaps", "🤔"),
        Emotion.Excited::class to listOf("excited", "yay", "awesome", "amazing", "fantastic", "🎉"),
        Emotion.Worried::class to listOf("worried", "concerned", "anxious", "nervous", "😟"),
        Emotion.Confused::class to listOf("confused", "don't understand", "what", "huh", "🤷")
    )

    /**
     * Detect emotion from text
     */
    fun detectEmotion(text: String): Emotion {
        val lowerText = text.lowercase()

        // Check each emotion's keywords
        emotionKeywords.forEach { (emotionClass, keywords) ->
            if (keywords.any { keyword -> lowerText.contains(keyword) }) {
                return when (emotionClass) {
                    Emotion.Happy::class -> Emotion.Happy()
                    Emotion.Sad::class -> Emotion.Sad()
                    Emotion.Angry::class -> Emotion.Angry()
                    Emotion.Surprised::class -> Emotion.Surprised()
                    Emotion.Thinking::class -> Emotion.Thinking()
                    Emotion.Excited::class -> Emotion.Excited()
                    Emotion.Worried::class -> Emotion.Worried()
                    Emotion.Confused::class -> Emotion.Confused()
                    else -> Emotion.Neutral
                }
            }
        }

        return Emotion.Neutral
    }

    /**
     * Detect emotion intensity (0.0 - 1.0)
     */
    fun detectIntensity(text: String): Float {
        // Count exclamation marks, all caps, etc.
        val exclamations = text.count { it == '!' }
        val allCapsWords = text.split(" ").count { it.isNotEmpty() && it.all { c -> c.isUpperCase() } }

        val intensity = (exclamations * 0.2f + allCapsWords * 0.1f).coerceIn(0f, 1f)
        return if (intensity < 0.3f) 0.5f else intensity
    }
}
```

---

## ⚡ Phase 8: Performance & Polish

### Obiettivo
Ottimizzazione performance e rifinitura sistema.

### Tasks

#### 8.1 - Update Loop Manager

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/presentation/HumanoidUpdateManager.kt`

```kotlin
/**
 * Central update loop for all humanoid systems.
 * Ensures proper frame timing and update order.
 */
class HumanoidUpdateManager(
    private val blendShapeController: BlendShapePriorityController,
    private val expressionController: ExpressionController,
    private val lipSyncController: LipSyncController,
    private val autoBlinkController: AutoBlinkController,
    private val lookAtController: LookAtController,
    private val vrmaPlayer: VrmaPlayer,
    private val filamentRenderer: FilamentRenderer
) {

    private var lastFrameTime = System.nanoTime()
    private val targetFrameRate = 60
    private val targetFrameTime = 1_000_000_000L / targetFrameRate

    /**
     * Main update loop - call every frame
     */
    fun update() {
        val currentTime = System.nanoTime()
        val deltaTime = (currentTime - lastFrameTime) / 1_000_000_000f
        lastFrameTime = currentTime

        // Update all systems in proper order
        updateSystems(deltaTime)

        // Apply final blend shapes to renderer
        applyBlendShapes()

        // Render frame
        filamentRenderer.render(currentTime)
    }

    /**
     * Update all systems
     */
    private fun updateSystems(deltaTime: Float) {
        // 1. Update animations (lowest priority)
        vrmaPlayer.update(deltaTime)

        // 2. Update look-at
        lookAtController.update(deltaTime)

        // 3. Update lip sync (medium priority)
        lipSyncController.update(deltaTime)

        // 4. Update expression (high priority)
        expressionController.update(deltaTime)

        // 5. Update blink (highest priority)
        autoBlinkController.update(deltaTime)

        // 6. Resolve all blend shape requests
        blendShapeController.update(deltaTime)
    }

    /**
     * Apply final blend shapes to renderer
     */
    private fun applyBlendShapes() {
        val finalWeights = blendShapeController.currentWeights.value
        filamentRenderer.updateBlendShapes(finalWeights)
    }

    /**
     * Start update loop
     */
    fun start() {
        lastFrameTime = System.nanoTime()
    }

    /**
     * Stop update loop
     */
    fun stop() {
        // Cleanup if needed
    }
}
```

#### 8.2 - Enhanced ViewModel Integration

**Enhancement to**: `features/humanoid/src/main/java/com/lifo/humanoid/presentation/HumanoidViewModel.kt`

```kotlin
@HiltViewModel
class HumanoidViewModel @Inject constructor(
    private val vrmLoader: VrmLoader,
    private val vrmaLoader: VrmaLoader,

    // Controllers
    private val blendShapeController: BlendShapePriorityController,
    private val expressionController: ExpressionController,
    private val lipSyncController: LipSyncController,
    private val autoBlinkController: AutoBlinkController,
    private val lookAtController: LookAtController,

    // Animation
    private val vrmaPlayer: VrmaPlayer,
    private val idleAnimationManager: IdleAnimationManager,
    private val gestureAnimationManager: GestureAnimationManager,

    // Gemini
    private val geminiLiveAudioManager: GeminiLiveAudioManager,
    private val nativeSttManager: NativeSttManager,
    private val emotionDetector: EmotionDetector,

    // Update manager
    private val updateManager: HumanoidUpdateManager
) : ViewModel() {

    // ... existing code ...

    init {
        // Load avatar and animations
        loadDefaultAvatar()
        loadAnimations()

        // Start update loop
        startUpdateLoop()

        // Listen for Gemini responses
        observeGeminiConversation()
    }

    /**
     * Load animations
     */
    private fun loadAnimations() {
        viewModelScope.launch {
            try {
                idleAnimationManager.loadIdleAnimations()
                gestureAnimationManager.loadGestureAnimations()

                // Start idle animation
                idleAnimationManager.startIdle()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load animations", e)
            }
        }
    }

    /**
     * Start main update loop
     */
    private fun startUpdateLoop() {
        viewModelScope.launch {
            updateManager.start()
            while (isActive) {
                updateManager.update()
                delay(16) // ~60 FPS
            }
        }
    }

    /**
     * Observe Gemini conversation
     */
    private fun observeGeminiConversation() {
        viewModelScope.launch {
            geminiLiveAudioManager.conversationState.collect { state ->
                when (state) {
                    is GeminiLiveAudioManager.ConversationState.Listening -> {
                        setListening(true)
                    }
                    is GeminiLiveAudioManager.ConversationState.Speaking -> {
                        // Detect emotion from text
                        val emotion = emotionDetector.detectEmotion(state.text)
                        val intensity = emotionDetector.detectIntensity(state.text)

                        setEmotion(emotion.copy(intensity = intensity))
                        setSpeaking(true)
                    }
                    is GeminiLiveAudioManager.ConversationState.Idle -> {
                        setSpeaking(false)
                        setListening(false)
                    }
                    else -> {}
                }
            }
        }

        // Observe audio for lip sync
        viewModelScope.launch {
            geminiLiveAudioManager.responseAudio.collect { audioData ->
                // Audio analyzer will handle lip sync automatically
            }
        }
    }

    /**
     * Start conversation with Gemini
     */
    fun startConversation() {
        viewModelScope.launch {
            try {
                geminiLiveAudioManager.initialize()
                geminiLiveAudioManager.startListening()

                // Look at camera
                lookAtController.enableUserLookAt(true)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start conversation", e)
            }
        }
    }

    /**
     * Stop conversation
     */
    fun stopConversation() {
        geminiLiveAudioManager.stopListening()
        lookAtController.enableUserLookAt(false)
    }

    /**
     * Play gesture
     */
    fun playGesture(gesture: GestureAnimationManager.GestureType) {
        viewModelScope.launch {
            gestureAnimationManager.playGesture(gesture)
        }
    }

    override fun onCleared() {
        super.onCleared()
        updateManager.stop()
        geminiLiveAudioManager.cleanup()
        nativeSttManager.cleanup()
    }
}
```

---

## 🧪 Testing Strategy

### Unit Tests

```kotlin
// features/humanoid/src/test/java/

// 1. Blend Shape Controller Tests
class BlendShapePriorityControllerTest {
    @Test
    fun `critical priority overrides all others`()

    @Test
    fun `smooth interpolation between weights`()

    @Test
    fun `conflict resolution with same priority`()
}

// 2. Expression Controller Tests
class ExpressionControllerTest {
    @Test
    fun `emotion transition waits for blink`()

    @Test
    fun `neutral emotion enables auto-blink`()

    @Test
    fun `blend shapes mapped correctly`()
}

// 3. Auto Blink Tests
class AutoBlinkControllerTest {
    @Test
    fun `blink timing is natural`()

    @Test
    fun `disable returns correct wait time`()

    @Test
    fun `blink has critical priority`()
}

// 4. Lip Sync Tests
class LipSyncControllerTest {
    @Test
    fun `viseme applied with correct weight`()

    @Test
    fun `silence when not speaking`()

    @Test
    fun `lip sync has medium priority`()
}

// 5. Look-At Tests
class LookAtControllerTest {
    @Test
    fun `smooth following with damping`()

    @Test
    fun `angle limits respected`()

    @Test
    fun `saccade adds natural movement`()
}
```

### Integration Tests

```kotlin
// 1. Full Avatar Animation Test
@Test
fun `avatar shows emotion while speaking with lip sync and blinking`()

// 2. Gemini Integration Test
@Test
fun `conversation triggers correct avatar responses`()

// 3. Animation System Test
@Test
fun `idle animation plays and gestures interrupt correctly`()
```

---

## 📁 File Structure

```
features/humanoid/
├── src/main/
│   ├── assets/
│   │   ├── models/
│   │   │   └── default_avatar.vrm
│   │   └── animations/
│   │       ├── idle_loop.vrma
│   │       ├── greeting.vrma
│   │       ├── peaceSign.vrma
│   │       ├── modelPose.vrma
│   │       ├── shoot.vrma
│   │       ├── dance.vrma
│   │       ├── showFullBody.vrma
│   │       ├── squat.vrma
│   │       └── spin.vrma
│   │
│   └── java/com/lifo/humanoid/
│       ├── data/
│       │   ├── vrm/
│       │   │   ├── VrmBlendShapeController.kt (existing - enhance)
│       │   │   ├── BlendShapePriorityController.kt (new)
│       │   │   ├── VrmLoader.kt (existing)
│       │   │   ├── VrmModel.kt (existing)
│       │   │   ├── VrmLookAtController.kt (new)
│       │   │   └── VrmaLoader.kt (new)
│       │   │
│       │   └── audio/
│       │       └── AudioAnalyzer.kt (new)
│       │
│       ├── domain/
│       │   ├── model/
│       │   │   ├── AvatarState.kt (existing)
│       │   │   ├── Emotion.kt (existing)
│       │   │   └── Viseme.kt (existing)
│       │   │
│       │   ├── expression/
│       │   │   ├── ExpressionController.kt (new)
│       │   │   └── CustomExpressionRegistry.kt (new)
│       │   │
│       │   ├── lipsync/
│       │   │   ├── LipSyncController.kt (new)
│       │   │   └── VisemeAnalyzer.kt (new)
│       │   │
│       │   ├── blink/
│       │   │   └── AutoBlinkController.kt (new)
│       │   │
│       │   ├── lookat/
│       │   │   ├── LookAtController.kt (new)
│       │   │   └── SaccadeController.kt (new)
│       │   │
│       │   ├── animation/
│       │   │   ├── VrmaPlayer.kt (new)
│       │   │   ├── IdleAnimationManager.kt (new)
│       │   │   └── GestureAnimationManager.kt (new)
│       │   │
│       │   ├── gemini/
│       │   │   └── GeminiLiveAudioManager.kt (new)
│       │   │
│       │   ├── stt/
│       │   │   └── NativeSttManager.kt (new)
│       │   │
│       │   └── emotion/
│       │       └── EmotionDetector.kt (new)
│       │
│       ├── presentation/
│       │   ├── HumanoidScreen.kt (existing - enhance)
│       │   ├── HumanoidViewModel.kt (existing - major enhancement)
│       │   ├── HumanoidUpdateManager.kt (new)
│       │   ├── components/
│       │   │   └── FilamentView.kt (existing)
│       │   └── navigation/
│       │       └── HumanoidNavigation.kt (existing)
│       │
│       ├── rendering/
│       │   └── FilamentRenderer.kt (existing - enhance for bones)
│       │
│       └── di/
│           └── HumanoidModule.kt (existing - add all new dependencies)
│
└── build.gradle (add Gemini SDK dependency)
```

---

## 📦 Dependencies to Add

**File**: `gradle/libs.versions.toml`

```toml
[versions]
gemini = "1.0.0"  # Update to latest

[libraries]
# Gemini AI SDK
google-ai-generativeai = { group = "com.google.ai.client.generativeai", name = "generativeai", version.ref = "gemini" }

# Audio processing (if not already present)
androidx-media3-common = { group = "androidx.media3", name = "media3-common", version = "1.2.0" }
```

**File**: `features/humanoid/build.gradle`

```gradle
dependencies {
    // ... existing dependencies ...

    // Gemini Live API
    implementation libs.google.ai.generativeai

    // Audio
    implementation libs.androidx.media3.common

    // Coroutines (if not present)
    implementation libs.kotlinx.coroutines.core
    implementation libs.kotlinx.coroutines.android
}
```

---

## 🎯 Implementation Order (Step-by-Step)

### Week 1: Core Enhancement
1. ✅ Enhance `BlendShapePriorityController` with priority system
2. ✅ Implement `AutoBlinkController`
3. ✅ Test blink timing and priority

### Week 2: Expression System
4. ✅ Implement `ExpressionController`
5. ✅ Implement `CustomExpressionRegistry`
6. ✅ Test emotion transitions

### Week 3: Lip Sync
7. ✅ Implement `AudioAnalyzer`
8. ✅ Implement `VisemeAnalyzer`
9. ✅ Implement `LipSyncController`
10. ✅ Test lip sync with sample audio

### Week 4: Look-At & Saccade
11. ✅ Implement `SaccadeController`
12. ✅ Implement `LookAtController`
13. ✅ Enhance `FilamentRenderer` for bone control
14. ✅ Test gaze tracking

### Week 5: VRMA Animations
15. ✅ Implement `VrmaLoader`
16. ✅ Implement `VrmaPlayer`
17. ✅ Import animation files to assets
18. ✅ Implement `IdleAnimationManager`
19. ✅ Implement `GestureAnimationManager`
20. ✅ Test all animations

### Week 6: Gemini Integration
21. ✅ Implement `GeminiLiveAudioManager`
22. ✅ Implement `NativeSttManager` as fallback
23. ✅ Implement `EmotionDetector`
24. ✅ Test conversation flow

### Week 7: Integration & Polish
25. ✅ Implement `HumanoidUpdateManager`
26. ✅ Enhance `HumanoidViewModel` with all systems
27. ✅ Enhance `HumanoidScreen` UI
28. ✅ Test full integration
29. ✅ Performance optimization
30. ✅ Bug fixes and polish

---

## 🚀 Success Criteria

### Functional Requirements
- [ ] Avatar blinks naturally every 2-5 seconds
- [ ] Emotions transition smoothly without artifacts
- [ ] Lip sync matches audio with appropriate visemes
- [ ] Gaze follows user/camera naturally
- [ ] Saccade adds realistic micro-movements
- [ ] Idle animation loops seamlessly
- [ ] Gestures play and return to idle
- [ ] Gemini conversation works real-time
- [ ] All blend shapes prioritized correctly

### Performance Requirements
- [ ] 60 FPS rendering on mid-range devices
- [ ] < 100ms latency for lip sync
- [ ] < 50ms latency for blink
- [ ] < 200ms latency for emotion change
- [ ] Smooth audio playback without stuttering

### Quality Requirements
- [ ] No visual artifacts during transitions
- [ ] Natural-looking animations
- [ ] Responsive to user interaction
- [ ] Robust error handling
- [ ] Comprehensive logging for debugging

---

## 📝 Notes

### VRM Blend Shape Standard Names
According to VRM spec, standard blend shape presets:
- **Emotions**: neutral, joy, angry, sorrow, fun, surprised
- **Lip Sync**: a, i, u, e, o
- **Blink**: blink, blink_l, blink_r
- **Gaze**: lookUp, lookDown, lookLeft, lookRight

### Gemini Live API Notes
- Supports bidirectional audio streaming
- Built-in VAD (Voice Activity Detection)
- Returns both text and audio responses
- Supports multiple languages
- Rate limits apply

### Filament Bone Access
Per controllare le ossa VRM in Filament:
```kotlin
val transformManager = engine.transformManager
val boneEntity = // get bone entity from VRM
val instance = transformManager.getInstance(boneEntity)
val transform = FloatArray(16) // 4x4 matrix
transformManager.setTransform(instance, transform)
```

---

**Fine del Piano di Implementazione**

Sir, questo piano completo copre TUTTO ciò che serve per replicare Amica nel suo progetto Android. Il sistema sarà modulare, performante, e completamente integrato con Gemini Live. Ho incluso:

✅ Tutti i sistemi di Amica (lip sync, blink, emotions, look-at, saccade)
✅ Integrazione Gemini Live completa
✅ Sistema di animazioni VRMA
✅ Priority system per blend shapes
✅ Update loop ottimizzato
✅ Testing strategy completa
✅ Step-by-step implementation order

Pronto a iniziare quando lo desidera, Sir. 🎩
