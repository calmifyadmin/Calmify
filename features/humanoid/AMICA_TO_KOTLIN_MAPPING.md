# Amica TypeScript to Calmify Kotlin - Architectural Mapping

## Executive Summary
This document maps the Amica TypeScript avatar system to the Calmify Kotlin/Filament architecture.
**Purpose**: Convert Amica's Three.js/VRM implementation to Android Filament-based rendering.

---

## 🎯 Current State Analysis

### ✅ **Working Components** (Calmify)
- [x] Filament rendering engine with VRM model loading
- [x] Basic VRM blend shape mapping and application
- [x] Emotion enum system (Happy, Sad, Angry, etc.)
- [x] VrmBlendShapeController with smooth interpolation
- [x] Avatar centering and camera positioning
- [x] Full-body view rendering

### ❌ **Missing Components** (Need Implementation)
- [ ] Auto-Blink System
- [ ] Gaze & Look-At System with smoothing
- [ ] Enhanced Expression Controller
- [ ] Lip Sync Engine
- [ ] VRMA Animation System
- [ ] Gemini LLM, TTS, STT integration

---

## 📂 File Mapping: Amica TypeScript → Calmify Kotlin

### **1. Auto-Blink System**

| Amica TypeScript | → | Calmify Kotlin |
|-----------------|---|----------------|
| `autoBlink.ts` | → | `features/humanoid/domain/animation/AutoBlinkController.kt` |
| `emoteConstants.ts` | → | `features/humanoid/domain/model/AnimationConstants.kt` |

**Key Components:**
```typescript
// Amica: autoBlink.ts
class AutoBlink {
  private _remainingTime: number
  private _isOpen: boolean
  private _isAutoBlink: boolean

  update(delta: number)
  close() // Sets blink weight to 1
  open()  // Sets blink weight to 0
}

const BLINK_CLOSE_MAX = 0.12  // 120ms
const BLINK_OPEN_MAX = 5      // 5 seconds
```

**Kotlin Equivalent:**
```kotlin
// Calmify: AutoBlinkController.kt
class AutoBlinkController(
    private val blendShapeController: VrmBlendShapeController
) {
    private var remainingTime: Float = 0f
    private var isOpen: Boolean = true
    private var isAutoBlinkEnabled: Boolean = true

    fun update(deltaTime: Float)
    private fun close()
    private fun open()
}

object AnimationConstants {
    const val BLINK_CLOSE_DURATION = 0.12f  // seconds
    const val BLINK_OPEN_DURATION = 5.0f     // seconds
}
```

**Implementation Notes:**
- Timer-based state machine: `remainingTime` counts down
- When `remainingTime` reaches 0:
  - If `isOpen` → call `close()` and set timer to `BLINK_CLOSE_MAX`
  - If closed → call `open()` and set timer to `BLINK_OPEN_MAX`
- Sets blend shape weight: `setValue("blink", 1)` for closed, `0` for open

---

### **2. Gaze & Look-At System**

| Amica TypeScript | → | Calmify Kotlin |
|-----------------|---|----------------|
| `autoLookAt.ts` | → | `features/humanoid/domain/animation/GazeController.kt` |
| `VRMLookAtSmoother.ts` | → | `features/humanoid/domain/animation/VrmLookAtSmoother.kt` |

**Key Components:**

#### **Amica: Basic LookAt (autoLookAt.ts)**
```typescript
class AutoLookAt {
  private _lookAtTarget: THREE.Object3D

  constructor(vrm: VRM, camera: THREE.Object3D) {
    this._lookAtTarget = new THREE.Object3D()
    camera.add(this._lookAtTarget)
    vrm.lookAt.target = this._lookAtTarget
  }
}
```

#### **Amica: Advanced Look-At Smoother (VRMLookAtSmoother.ts)**
```typescript
class VRMLookAtSmoother extends VRMLookAt {
  public smoothFactor = 4.0
  public userLimitAngle = 90.0  // degrees
  public userTarget?: THREE.Object3D
  public enableSaccade: boolean = true

  // Saccade motion (micro eye movements)
  private _saccadeYaw = 0.0
  private _saccadePitch = 0.0
  private _saccadeTimer = 0.0

  // Smoothed damping
  private _yawDamped = 0.0
  private _pitchDamped = 0.0

  update(delta: number) {
    // 1. Smooth interpolation to target
    const k = 1.0 - Math.exp(-this.smoothFactor * delta)
    this._yawDamped += (this._yaw - this._yawDamped) * k
    this._pitchDamped += (this._pitch - this._pitchDamped) * k

    // 2. Saccade random motion
    if (timer > MIN_INTERVAL && random < PROC) {
      this._saccadeYaw = random(-5, 5) degrees
      this._saccadePitch = random(-5, 5) degrees
    }

    // 3. Apply blend to head bone rotation
    head.quaternion.slerp(targetQuat, 0.4)
  }
}
```

**Kotlin Equivalent:**

```kotlin
// GazeController.kt
class GazeController(
    private val renderer: FilamentRenderer,
    private val cameraPosition: FloatArray
) {
    var gazeTarget: FloatArray = floatArrayOf(0f, 1.5f, 0f)
    var smoothFactor: Float = 4.0f
    var userLimitAngle: Float = 90.0f

    fun setGazeDirection(direction: AvatarState.GazeDirection)
    fun update(deltaTime: Float)
}

// VrmLookAtSmoother.kt
class VrmLookAtSmoother {
    var enableSaccade: Boolean = true

    private var yawDamped: Float = 0f
    private var pitchDamped: Float = 0f
    private var saccadeYaw: Float = 0f
    private var saccadePitch: Float = 0f
    private var saccadeTimer: Float = 0f

    fun update(deltaTime: Float, targetYaw: Float, targetPitch: Float): Pair<Float, Float>
    private fun computeSaccade(deltaTime: Float)
}

object SaccadeConstants {
    const val SACCADE_MIN_INTERVAL = 0.5f   // seconds
    const val SACCADE_PROBABILITY = 0.05f   // 5% per frame
    const val SACCADE_RADIUS = 5.0f         // degrees
}
```

**Implementation Notes:**
- **Exponential smoothing**: `value += (target - value) * (1 - exp(-smoothFactor * deltaTime))`
- **Saccade**: Random micro-movements every 0.5+ seconds
- **Bone rotation**: Modify Filament's TransformManager for head bone
- **Quaternion interpolation**: Use SLERP for smooth rotation

---

### **3. Expression Controller**

| Amica TypeScript | → | Calmify Kotlin |
|-----------------|---|----------------|
| `emoteController.ts` | → | `features/humanoid/domain/controller/EmoteController.kt` |
| `expressionController.ts` | → | `features/humanoid/domain/controller/ExpressionController.kt` |

**Key Components:**

#### **Amica: Expression Controller**
```typescript
class ExpressionController {
  private _currentEmotion: string
  private _currentLipSync: { preset: string, value: number } | null

  playEmotion(preset: string) {
    // 1. Reset previous emotion to 0
    if (_currentEmotion != "neutral") {
      _expressionManager.setValue(_currentEmotion, 0)
    }

    // 2. Disable auto-blink while expressing emotion
    const waitTime = _autoBlink.setEnable(false)

    // 3. Apply new emotion after blink finishes
    setTimeout(() => {
      _expressionManager.setValue(preset, value)
    }, waitTime * 1000)
  }

  lipSync(preset: string, value: number) {
    // Clear previous lip sync
    if (_currentLipSync) {
      _expressionManager.setValue(_currentLipSync.preset, 0)
    }
    _currentLipSync = { preset, value }
  }

  update(delta: number) {
    // Apply lip sync with emotion blending
    const weight = _currentEmotion === "neutral"
      ? value * 0.5   // Full lip movement when neutral
      : value * 0.25  // Reduced when expressing emotion
    _expressionManager.setValue(_currentLipSync.preset, weight)
  }
}
```

**Kotlin Equivalent:**

```kotlin
// EmoteController.kt
class EmoteController(
    private val expressionController: ExpressionController,
    private val autoBlinkController: AutoBlinkController,
    private val gazeController: GazeController
) {
    fun playEmotion(emotion: Emotion)
    fun setLipSync(viseme: Viseme, intensity: Float)
    fun update(deltaTime: Float)
}

// ExpressionController.kt
class ExpressionController(
    private val blendShapeController: VrmBlendShapeController
) {
    private var currentEmotion: String = "neutral"
    private var currentLipSync: LipSyncState? = null

    fun applyEmotion(emotion: Emotion, autoBlinkController: AutoBlinkController) {
        // Reset previous emotion
        if (currentEmotion != "neutral") {
            blendShapeController.setWeight(currentEmotion, 0f)
        }

        // Wait for eyes to open before applying emotion
        val waitTime = autoBlinkController.disableAndGetWaitTime()

        viewModelScope.launch {
            delay((waitTime * 1000).toLong())
            val weight = when (emotion) {
                is Emotion.Surprised -> 0.5f
                else -> 1.0f
            }
            blendShapeController.setWeight(emotionName, weight)
        }
    }

    fun setLipSync(viseme: Viseme, value: Float) {
        currentLipSync?.let {
            blendShapeController.setWeight(it.preset, 0f)
        }
        currentLipSync = LipSyncState(viseme.name, value)
    }

    fun update(deltaTime: Float) {
        currentLipSync?.let { lipSync ->
            val weight = if (currentEmotion == "neutral") {
                lipSync.value * 0.5f
            } else {
                lipSync.value * 0.25f
            }
            blendShapeController.setWeight(lipSync.preset, weight)
        }
    }
}

data class LipSyncState(val preset: String, val value: Float)
```

**Implementation Notes:**
- **Emotion transition**: Always reset previous emotion to 0 before applying new
- **Blink coordination**: Wait for eyes to open before showing emotion
- **Lip sync blending**: Reduce lip movement intensity when emotion is active
- **Coroutine timing**: Use `delay()` for async emotion application

---

### **4. Lip Sync Engine**

| Amica TypeScript | → | Calmify Kotlin |
|-----------------|---|----------------|
| `lipSync.ts` | → | `features/humanoid/domain/audio/LipSyncEngine.kt` |
| `lipSyncAnalyzeResult.ts` | → | `features/humanoid/domain/audio/LipSyncAnalyzeResult.kt` |

**Key Components:**

#### **Amica: Audio Analysis**
```typescript
class LipSync {
  private audio: AudioContext
  private analyser: AnalyserNode
  private timeDomainData: Float32Array

  update(): LipSyncAnalyzeResult {
    // Get audio samples
    this.analyser.getFloatTimeDomainData(this.timeDomainData)

    // Calculate volume (max amplitude)
    let volume = 0.0
    for (let i = 0; i < 2048; i++) {
      volume = Math.max(volume, Math.abs(timeDomainData[i]))
    }

    // Apply sigmoid curve for better viseme mapping
    volume = 1 / (1 + Math.exp(-45 * volume + 5))
    if (volume < 0.1) volume = 0

    return { volume }
  }

  playFromArrayBuffer(buffer: ArrayBuffer, onEnded?: () => void) {
    const audioBuffer = await audio.decodeAudioData(buffer)
    const bufferSource = audio.createBufferSource()

    bufferSource.connect(audio.destination)
    bufferSource.connect(analyser)  // Connect to analyzer
    bufferSource.start()
  }
}
```

**Kotlin Equivalent:**

```kotlin
// LipSyncEngine.kt
class LipSyncEngine {
    private val audioRecord: AudioRecord
    private val fftAnalyzer: FFTAnalyzer

    fun update(): LipSyncAnalyzeResult {
        // Read audio samples
        val samples = FloatArray(TIME_DOMAIN_DATA_LENGTH)
        audioRecord.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)

        // Calculate volume (max amplitude)
        var volume = 0f
        samples.forEach { sample ->
            volume = max(volume, abs(sample))
        }

        // Apply sigmoid curve
        volume = 1f / (1f + exp(-45f * volume + 5f))
        if (volume < 0.1f) volume = 0f

        return LipSyncAnalyzeResult(volume)
    }

    suspend fun playAudio(audioData: ByteArray, onEnded: () -> Unit) {
        // Use Calmify's existing audio system
        // Connect to AudioTrack with visualizer
        val audioTrack = AudioTrack(...)
        val visualizer = Visualizer(audioTrack.audioSessionId)

        visualizer.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
            override fun onWaveFormDataCapture(waveform: ByteArray) {
                // Feed to update()
            }
        })

        audioTrack.play()
        audioTrack.write(audioData, 0, audioData.size)
    }
}

data class LipSyncAnalyzeResult(val volume: Float)

object LipSyncConstants {
    const val TIME_DOMAIN_DATA_LENGTH = 2048
    const val SAMPLE_RATE = 44100
}
```

**Implementation Notes:**
- **Audio Analysis**: Use Android's `AudioRecord` or `Visualizer` API
- **Volume extraction**: Max amplitude from time-domain samples
- **Sigmoid mapping**: Smooth 0-1 curve for viseme intensity
- **Integration**: Connect with Calmify's existing `OnDeviceNaturalVoiceSystem`
- **Viseme mapping**: Map volume → mouth open amount (A, O, etc.)

---

### **5. VRMA Animation System**

| Amica TypeScript | → | Calmify Kotlin |
|-----------------|---|----------------|
| `VRMAnimation.ts` | → | `features/humanoid/data/vrma/VrmaAnimation.kt` |
| `loadVRMAnimation.ts` | → | `features/humanoid/data/vrma/VrmaLoader.kt` |
| `VRMAnimationLoaderPlugin.ts` | → | `features/humanoid/data/vrma/VrmaLoaderPlugin.kt` |

**Key Components:**

#### **Amica: VRMAnimation Class**
```typescript
class VRMAnimation {
  duration: number
  restHipsPosition: THREE.Vector3

  humanoidTracks: {
    translation: Map<VRMHumanBoneName, THREE.VectorKeyframeTrack>
    rotation: Map<VRMHumanBoneName, THREE.VectorKeyframeTrack>
  }
  expressionTracks: Map<string, THREE.NumberKeyframeTrack>
  lookAtTrack: THREE.QuaternionKeyframeTrack | null

  createAnimationClip(vrm: VRM): THREE.AnimationClip {
    // Combine all tracks into an animation clip
    tracks.push(...createHumanoidTracks(vrm))
    tracks.push(...createExpressionTracks(vrm.expressionManager))
    tracks.push(createLookAtTrack())

    return new THREE.AnimationClip("Clip", duration, tracks)
  }

  createHumanoidTracks(vrm: VRM): THREE.KeyframeTrack[] {
    // Map bone rotations/translations to THREE.js tracks
    // Handle VRM 0.0 vs 1.0 coordinate system differences
  }
}

async function loadVRMAnimation(url: string): Promise<VRMAnimation> {
  const gltf = await GLTFLoader.loadAsync(url)
  return gltf.userData.vrmAnimations[0]
}
```

**Kotlin Equivalent:**

```kotlin
// VrmaAnimation.kt
class VrmaAnimation(
    val duration: Float,
    val restHipsPosition: FloatArray,
    val humanoidTracks: HumanoidTracks,
    val expressionTracks: Map<String, KeyframeTrack>,
    val lookAtTrack: QuaternionKeyframeTrack?
) {
    data class HumanoidTracks(
        val translation: Map<VrmHumanBoneName, VectorKeyframeTrack>,
        val rotation: Map<VrmHumanBoneName, VectorKeyframeTrack>
    )

    fun createFilamentAnimation(
        renderer: FilamentRenderer,
        vrmModel: VrmModel
    ): FilamentAnimation {
        // Convert Three.js keyframe tracks to Filament's animation system
        val animator = renderer.engine.createAnimator()

        // Apply bone transforms
        humanoidTracks.rotation.forEach { (boneName, track) ->
            val entity = vrmModel.getBoneEntity(boneName)
            // Apply rotation keyframes
        }

        return FilamentAnimation(animator, duration)
    }
}

// VrmaLoader.kt
class VrmaLoader(private val context: Context) {
    suspend fun loadVrmaFromAssets(assetPath: String): VrmaAnimation? {
        return withContext(Dispatchers.IO) {
            val buffer = context.assets.open(assetPath).use { stream ->
                // Read VRMA file (glTF with VRMC_vrm_animation extension)
            }

            parseVrmaAnimation(buffer)
        }
    }

    private fun parseVrmaAnimation(buffer: ByteBuffer): VrmaAnimation {
        // Parse glTF JSON for VRMC_vrm_animation extension
        // Extract keyframe tracks for bones, expressions, lookAt
    }
}

// KeyframeTrack data classes
data class VectorKeyframeTrack(
    val times: FloatArray,
    val values: FloatArray  // 3 components per keyframe
)

data class QuaternionKeyframeTrack(
    val times: FloatArray,
    val values: FloatArray  // 4 components per keyframe
)

data class KeyframeTrack(
    val times: FloatArray,
    val values: FloatArray  // 1 component per keyframe
)
```

**Implementation Notes:**
- **VRMA Format**: glTF 2.0 with `VRMC_vrm_animation` extension
- **Keyframe tracks**: Time-value pairs for bone transforms and blend shapes
- **Filament integration**: Use Filament's `Animator` class for bone animation
- **Coordinate conversion**: Handle VRM 0.0 vs 1.0 coordinate system differences
- **Assets**: Store animation files in `features/humanoid/src/main/assets/animations/`

---

## 🏗️ Project Structure

```
features/humanoid/src/main/java/com/lifo/humanoid/
│
├── domain/
│   ├── model/
│   │   ├── Emotion.kt               ✅ EXISTS
│   │   ├── Viseme.kt                ✅ EXISTS
│   │   ├── AvatarState.kt           ✅ EXISTS
│   │   └── AnimationConstants.kt    ❌ NEW
│   │
│   ├── animation/
│   │   ├── AutoBlinkController.kt   ❌ NEW
│   │   ├── GazeController.kt        ❌ NEW
│   │   └── VrmLookAtSmoother.kt     ❌ NEW
│   │
│   ├── controller/
│   │   ├── EmoteController.kt       ❌ NEW
│   │   └── ExpressionController.kt  ❌ NEW
│   │
│   └── audio/
│       ├── LipSyncEngine.kt         ❌ NEW
│       └── LipSyncAnalyzeResult.kt  ❌ NEW
│
├── data/
│   ├── vrm/
│   │   ├── VrmLoader.kt             ✅ EXISTS
│   │   ├── VrmBlendShapeController.kt ✅ EXISTS
│   │   └── VrmModel.kt              ✅ EXISTS
│   │
│   └── vrma/
│       ├── VrmaAnimation.kt         ❌ NEW
│       ├── VrmaLoader.kt            ❌ NEW
│       └── VrmaLoaderPlugin.kt      ❌ NEW
│
├── presentation/
│   ├── HumanoidViewModel.kt         ✅ EXISTS (needs updates)
│   ├── HumanoidScreen.kt            ✅ EXISTS
│   └── components/
│       └── FilamentView.kt          ✅ EXISTS
│
└── rendering/
    └── FilamentRenderer.kt          ✅ EXISTS (needs bone API)
```

---

## 🔄 Integration Points

### **1. Update HumanoidViewModel**
```kotlin
@HiltViewModel
class HumanoidViewModel @Inject constructor(
    private val vrmLoader: VrmLoader,
    private val blendShapeController: VrmBlendShapeController,
    // NEW DEPENDENCIES
    private val autoBlinkController: AutoBlinkController,
    private val gazeController: GazeController,
    private val expressionController: ExpressionController,
    private val lipSyncEngine: LipSyncEngine
) : ViewModel() {

    fun update(deltaTime: Float) {
        // Update all animation systems
        autoBlinkController.update(deltaTime)
        gazeController.update(deltaTime)
        expressionController.update(deltaTime)

        // Update lip sync from audio
        val lipSyncResult = lipSyncEngine.update()
        expressionController.setLipSync(
            Viseme.AA,  // Map from audio analysis
            lipSyncResult.volume
        )

        // Apply to blend shapes
        blendShapeController.update(deltaTime)
    }
}
```

### **2. Filament Renderer Bone API** (New)
```kotlin
class FilamentRenderer {
    // NEW: Bone manipulation API
    fun getBoneEntity(boneName: VrmHumanBoneName): Int? {
        // Return Filament entity ID for given bone
    }

    fun setBoneRotation(entity: Int, quaternion: FloatArray) {
        val tm = engine.transformManager
        val instance = tm.getInstance(entity)
        // Set quaternion rotation
    }

    fun getBoneRotation(entity: Int): FloatArray {
        // Get current quaternion rotation
    }
}
```

---

## 🎮 Gemini Integration

### **Connection to Existing Chat System**

Calmify already has Gemini integration in `features/chat/`:
- `GeminiNativeVoiceSystem.kt`
- `ChatViewModel.kt`
- `ApiConfigManager.kt`

**Integration Strategy:**
1. **LLM**: Use existing `GeminiChatRepository` for text generation
2. **TTS**: Use existing `GeminiNativeVoiceSystem` for speech synthesis
3. **STT**: Use existing `VoiceRecognitionManager` for speech-to-text
4. **Emotion detection**: Parse Gemini response for emotion keywords → set avatar emotion
5. **Lip sync**: Connect `LipSyncEngine` to audio output from `GeminiNativeVoiceSystem`

**Code Example:**
```kotlin
// In HumanoidViewModel or new HumanoidChatController
fun handleGeminiResponse(response: String, audioData: ByteArray) {
    // 1. Detect emotion from text
    val emotion = detectEmotionFromText(response)
    expressionController.applyEmotion(emotion, autoBlinkController)

    // 2. Play audio with lip sync
    viewModelScope.launch {
        lipSyncEngine.playAudio(audioData) {
            // On audio end, return to neutral
            expressionController.applyEmotion(Emotion.Neutral, autoBlinkController)
        }
    }
}

private fun detectEmotionFromText(text: String): Emotion {
    return when {
        text.contains("happy", ignoreCase = true) -> Emotion.Happy()
        text.contains("sad", ignoreCase = true) -> Emotion.Sad()
        text.contains("excited", ignoreCase = true) -> Emotion.Excited()
        // ... more patterns
        else -> Emotion.Neutral
    }
}
```

---

## 📊 Implementation Priority

### **Phase 1: Core Animation Systems** (Week 1)
1. ✅ AnimationConstants.kt
2. ✅ AutoBlinkController.kt
3. ✅ ExpressionController.kt
4. ✅ EmoteController.kt

### **Phase 2: Gaze & Look-At** (Week 2)
5. ✅ GazeController.kt
6. ✅ VrmLookAtSmoother.kt
7. ✅ Update FilamentRenderer with bone API

### **Phase 3: Lip Sync** (Week 3)
8. ✅ LipSyncEngine.kt
9. ✅ LipSyncAnalyzeResult.kt
10. ✅ Integrate with existing audio system

### **Phase 4: VRMA Animations** (Week 4)
11. ✅ VrmaAnimation.kt
12. ✅ VrmaLoader.kt
13. ✅ VrmaLoaderPlugin.kt
14. ✅ Load animation assets (dance.vrma, idle_loop.vrma, etc.)

### **Phase 5: Gemini Integration** (Week 5)
15. ✅ Connect to existing Gemini chat system
16. ✅ Emotion detection from text
17. ✅ Audio-driven lip sync
18. ✅ STT for user input

---

## 🔬 Key Algorithms

### **Exponential Smoothing**
```kotlin
fun smoothValue(current: Float, target: Float, smoothFactor: Float, deltaTime: Float): Float {
    val k = 1f - exp(-smoothFactor * deltaTime)
    return current + (target - current) * k
}
```

### **Sigmoid Volume Mapping**
```kotlin
fun mapVolumeToLipSync(rawVolume: Float): Float {
    var volume = 1f / (1f + exp(-45f * rawVolume + 5f))
    if (volume < 0.1f) volume = 0f
    return volume
}
```

### **Quaternion SLERP**
```kotlin
fun slerp(q1: Quaternion, q2: Quaternion, t: Float): Quaternion {
    // Spherical linear interpolation for smooth rotation
    val dot = q1.dot(q2)
    // ... implementation
}
```

---

## 📝 Notes

- **Filament vs Three.js**: Filament uses a different coordinate system; adjust bone transforms accordingly
- **VRM versions**: Handle both VRM 0.0 and VRM 1.0 specifications
- **Performance**: All animation updates should run at 60 FPS (16ms per frame)
- **Threading**: Audio analysis should run on background thread, UI updates on main thread
- **Assets**: Place VRMA animation files in `features/humanoid/src/main/assets/animations/`

---

## 🎯 Success Criteria

- [ ] Avatar blinks naturally every 3-5 seconds
- [ ] Eyes smoothly track gaze direction with saccadic motion
- [ ] Emotions transition smoothly without artifacts
- [ ] Lip sync matches audio volume accurately
- [ ] VRMA animations play correctly (dance, idle, etc.)
- [ ] Gemini responses trigger appropriate avatar emotions
- [ ] System maintains 60 FPS during all operations

---

**End of Mapping Document**
