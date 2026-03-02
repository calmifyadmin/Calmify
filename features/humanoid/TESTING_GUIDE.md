# Humanoid Avatar - Testing Guide

**Status**: ✅ **Code Compiles Successfully**
**Date**: 2025-01-24

---

## ✅ Pre-Testing Checklist

- [x] All Kotlin files compile without errors
- [x] Dependency injection configured (HumanoidModule)
- [x] ViewModel integration complete
- [x] View layer updated with callbacks
- [ ] VRM model with blend shapes added to assets
- [ ] Build and install APK on device

---

## 📱 Quick Start Testing

### **Step 1: Add VRM Model**

Place a VRM model file in:
```
features/humanoid/src/main/assets/models/default_avatar.vrm
```

**Requirements**:
- Valid VRM 0.0 or 1.0 file
- Must include blend shapes:
  - `blink` (for auto-blinking)
  - `joy`, `sorrow`, `angry`, `surprised` (for emotions)
  - `a`, `o`, `u` (optional, for lip sync)

**Free VRM Models**:
- [VRoid Hub](https://hub.vroid.com/)
- [Booth.pm](https://booth.pm/en/search/VRM)
- Test with: [Alicia Solid](https://github.com/vrm-c/UniVRM/releases)

---

### **Step 2: Build and Install**

```bash
cd c:/Users/lifoe/AndroidStudioProjects/CalmifyAppAndroid

# Build debug APK
./gradlew :app:assembleDebug

# Install on connected device
./gradlew :app:installDebug

# Or build and install in one command
./gradlew :app:installDebug
```

---

### **Step 3: Navigate to Humanoid Screen**

In the app:
1. Launch Calmify app
2. Navigate to **Humanoid Avatar** screen
3. Wait for avatar to load

---

## 🧪 Test Cases

### **1. Auto-Blink System** ✅

**What to test**:
- Avatar should blink automatically every 3-5 seconds
- Blink duration should be quick (~120ms)
- Eyes should not blink during emotion transitions

**Steps**:
1. Launch Humanoid screen
2. Observe avatar for 30 seconds
3. Count blinks (should be 6-10 blinks in 30 seconds)

**Expected behavior**:
```
t=0s:  Eyes open
t=5s:  Blink! (120ms)
t=10s: Blink!
t=15s: Blink!
...
```

**Debug**:
```kotlin
// In HumanoidViewModel
val debugState = viewModel.getDebugState()
println("Blink state: ${debugState.emoteState.blinkState}")

// Output:
// BlinkState(isOpen=true, isAutoBlinkEnabled=true, remainingTime=4.2)
```

---

### **2. Emotion Expressions** ✅

**What to test**:
- Emotions should apply smoothly
- No blinking during emotion change
- Emotions should map to correct blend shapes

**Steps**:
1. Click **😊 Happy** button
2. Observe avatar face changes to joy expression
3. Wait 2 seconds
4. Click **😢 Sad** button
5. Observe smooth transition to sorrow expression

**Expected behavior**:
- **Happy**: `joy` blend shape = 1.0
- **Sad**: `sorrow` blend shape = 1.0
- **Angry**: `angry` blend shape = 1.0
- **Surprised**: `surprised` blend shape = 0.5 (special case)
- **Thinking**: `neutral` blend shape = 1.0 (fallback)

**Debug**:
```kotlin
// Check current emotion
println("Current emotion: ${viewModel.avatarState.value.emotion.getName()}")
println("Blend shapes: ${viewModel.blendShapeWeights.value}")

// Output:
// Current emotion: Happy
// Blend shapes: {joy=1.0}
```

---

### **3. Gaze & Look-At System** ⏸️

**Note**: Gaze system requires Filament bone API which is not yet implemented.

**What to test** (when bone API is ready):
- Eyes should track gaze direction
- Smooth interpolation (no jerky movement)
- Subtle saccadic micro-movements every 0.5-1s

**Steps**:
1. Call `viewModel.setGazeDirection(AvatarState.GazeDirection.Left)`
2. Observe eyes smoothly move left
3. Wait 2 seconds
4. Call `viewModel.setGazeDirection(AvatarState.GazeDirection.Right)`
5. Observe eyes smoothly move right

**Expected behavior**:
- Smooth exponential interpolation (not linear)
- Random micro-movements (saccades) within ±5°
- Head rotation blends with eye movement (0.4 factor)

**Debug**:
```kotlin
val gazeState = viewModel.getDebugState().gazeState
println("Gaze: yaw=${gazeState?.lookAtState?.yawDamped}, pitch=${gazeState?.lookAtState?.pitchDamped}")
println("Saccade: yaw=${gazeState?.lookAtState?.saccadeYaw}, pitch=${gazeState?.lookAtState?.saccadePitch}")
```

---

### **4. Lip Sync Engine** ⏸️

**Note**: Requires Gemini TTS integration (audio session ID)

**What to test** (when TTS integrated):
- Mouth opens proportionally to audio volume
- Mouth closes during silence
- Lip sync weight reduced when emotion is active

**Steps**:
1. Play Gemini TTS audio
2. Initialize `LipSyncEngine` with audio session ID
3. Observe mouth opening/closing with speech

**Expected behavior**:
- **High volume** (loud speech) → Mouth fully open (weight = 0.5 when neutral)
- **Low volume** (quiet speech) → Mouth slightly open (weight = 0.1-0.3)
- **Silence** → Mouth closed (weight = 0.0)
- **With emotion** → Lip sync weight reduced to 0.25x

**Debug**:
```kotlin
val lipSyncEngine = LipSyncEngine(audioSessionId)
lipSyncEngine.initialize()

// In update loop
val result = lipSyncEngine.update()
println("Volume: ${result.volume}, Mouth open: ${result.getMouthOpenAmount()}")

// Output:
// Volume: 0.65, Mouth open: 0.65
```

---

### **5. Performance Test** ✅

**What to test**:
- App maintains 60 FPS during all operations
- No frame drops during emotion transitions
- Memory usage remains stable

**Steps**:
1. Enable Android Profiler in Android Studio
2. Launch Humanoid screen
3. Trigger multiple emotion changes
4. Monitor FPS and memory usage

**Expected behavior**:
- **FPS**: 55-60 FPS consistently
- **Frame time**: < 16ms per frame
- **Memory**: No leaks, GC collections are minor

**Debug**:
```kotlin
// In update loop, measure frame time
val startTime = System.nanoTime()
viewModel.update(frameTimeNanos)
val frameTime = (System.nanoTime() - startTime) / 1_000_000f
println("Frame time: ${frameTime}ms")

// Expected: 1-3ms for animation updates
```

---

### **6. State Persistence Test** ✅

**What to test**:
- Avatar state survives configuration changes (rotation)
- Blend shapes persist correctly

**Steps**:
1. Set emotion to **Happy**
2. Rotate device
3. Observe avatar still has happy expression

**Expected behavior**:
- ViewModel survives rotation (Hilt-scoped)
- BlendShapeController state persists
- No reset to neutral on rotation

---

## 🐛 Common Issues & Solutions

### **Issue 1: Avatar not blinking**

**Possible causes**:
- VRM model missing `blink` blend shape
- Auto-blink disabled
- Update loop not running

**Debug**:
```kotlin
val blinkState = viewModel.getDebugState().emoteState.blinkState
println("Auto-blink enabled: ${blinkState.isAutoBlinkEnabled}")
println("Eyes open: ${blinkState.isOpen}")
println("Remaining time: ${blinkState.remainingTime}")
```

**Solution**:
1. Check VRM model has `blink` blend shape
2. Call `viewModel.setAutoBlinkEnabled(true)`
3. Verify `viewModel.update()` is called every frame

---

### **Issue 2: Emotions not changing**

**Possible causes**:
- VRM model missing emotion blend shapes
- Blend shape names don't match VRM standard
- EmoteController not updating

**Debug**:
```kotlin
val blendShapes = viewModel.blendShapeWeights.value
println("Current blend shapes: $blendShapes")

// Expected output when Happy:
// {joy=1.0, blink=0.0}
```

**Solution**:
1. Check VRM model has `joy`, `sorrow`, `angry`, `surprised` blend shapes
2. Check blend shape mapping in `FilamentRenderer.buildBlendShapeMapping()`
3. Verify `EmoteController.update()` is called

---

### **Issue 3: App crashes on launch**

**Possible causes**:
- Hilt dependency injection error
- FilamentRenderer initialization failure
- VRM model loading error

**Debug**:
```bash
# Check logcat for errors
adb logcat | grep -E "HumanoidViewModel|FilamentRenderer|VrmLoader"
```

**Solution**:
1. Verify all Hilt modules are installed
2. Check VRM model file exists in assets
3. Check Filament native libraries loaded

---

### **Issue 4: Gaze not working**

**Status**: Expected - bone API not yet implemented

**Workaround**: Implement `FilamentRenderer.getBoneEntity()` and `setBoneRotation()` methods

---

### **Issue 5: Lip sync not working**

**Status**: Expected - requires Gemini TTS integration

**Next step**: Connect `LipSyncEngine` to `GeminiNativeVoiceSystem` audio output

---

## 📊 Debug Dashboard (Optional)

Add this to `HumanoidScreen` for real-time debug info:

```kotlin
@Composable
fun DebugOverlay(viewModel: HumanoidViewModel) {
    val debugState = viewModel.getDebugState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(8.dp)
    ) {
        Text("Blink: ${debugState.emoteState.blinkState.isOpen} (${debugState.emoteState.blinkState.remainingTime}s)",
             color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text("Emotion: ${debugState.avatarState.emotion.getName()}",
             color = Color.White, style = MaterialTheme.typography.bodySmall)
        Text("Blend shapes: ${viewModel.blendShapeWeights.value.keys.joinToString()}",
             color = Color.White, style = MaterialTheme.typography.bodySmall)
    }
}
```

---

## ✅ Success Criteria

The implementation is successful when:

- [x] **Code compiles** without errors
- [ ] **Avatar loads** and displays correctly
- [ ] **Auto-blink works** (blinks every 3-5s)
- [ ] **Emotions change** smoothly without artifacts
- [ ] **Performance** maintains 60 FPS
- [ ] **No crashes** during normal usage

---

## 🚀 Next Steps

### **Phase 1: Basic Testing** (Now)
1. Add VRM model to assets
2. Build and install APK
3. Test auto-blink and emotions
4. Verify no crashes

### **Phase 2: Filament Bone API** (Required for gaze)
1. Implement `FilamentRenderer.getBoneEntity(boneName: String)`
2. Implement `FilamentRenderer.setBoneRotation(entity: Int, quaternion: FloatArray)`
3. Test gaze controller

### **Phase 3: Gemini Integration** (Required for lip sync)
1. Connect `LipSyncEngine` to `GeminiNativeVoiceSystem`
2. Extract audio session ID from TTS output
3. Test lip sync with real speech

### **Phase 4: VRMA Animations** (Optional)
1. Implement VRMA loader
2. Add idle animations (breathing, head tilt)
3. Add gesture animations (wave, nod)

---

## 📚 Additional Resources

### **VRM Documentation**
- [VRM Specification](https://github.com/vrm-c/vrm-specification)
- [UniVRM (Unity implementation)](https://github.com/vrm-c/UniVRM)
- [Three-VRM (Three.js implementation)](https://github.com/pixiv/three-vrm)

### **Filament Documentation**
- [Filament Home](https://google.github.io/filament/)
- [glTF IO](https://google.github.io/filament/gltfio.html)
- [Animation System](https://google.github.io/filament/Filament.html#animation)

### **Amica Reference**
- Original implementation: `c:\Users\lifoe\amica\`
- Mapping document: [AMICA_TO_KOTLIN_MAPPING.md](./AMICA_TO_KOTLIN_MAPPING.md)

---

**Sir, la guida di testing è completa. Il codice compila perfettamente. Possiamo procedere con il testing appena aggiungi un modello VRM.**

Preferisce che:
1. **Test immediato**: Aggiunga un modello VRM e testiamo auto-blink/emotions?
2. **Gemini integration**: Collego il sistema lip sync al TTS esistente?
3. **Bone API**: Implemento l'API per rotazione head bone (per gaze)?

Come preferisce procedere, Sir?
