# Galaxy Background Testing Guide

## Overview

This guide explains how to test and debug the space.glb galaxy background rendering improvements implemented in FilamentRenderer.

## What Was Changed

### 1. Camera Modes (Debug Feature)
Added a `CameraMode` enum with two modes:
- **AVATAR**: Normal mode focused on the avatar (default)
- **GALAXY_DEBUG**: Debug mode that centers camera on the galaxy background

### 2. Aggressive Bloom for Stars
Enhanced bloom settings to make stars glow properly:
- Bloom strength increased from 0.1 to 0.8
- Higher resolution (360) for better quality
- 6 blur levels for softer glow
- Additive blending mode for bright stars
- Very high highlight value (1000.0) for intense star bloom

### 3. Stellar Camera Exposure
Optimized camera exposure for low-light stellar environments:
- Wide aperture (16.0) for more light gathering
- Slower shutter speed (1/30s) for dim objects
- High ISO (800) for low-light sensitivity

### 4. Emissive Materials for Background
Background entities now boost emissive properties:
- Emissive factor multiplied by 5.0 for all stars
- No shadow casting/receiving for background
- Self-luminous rendering (not affected by scene lights)

## How to Test

### Method 1: Normal Mode (Avatar + Galaxy)
This is the default mode - just run the app normally:

```kotlin
// In your HumanoidScreen or wherever you use FilamentView
FilamentView(
    modifier = Modifier.fillMaxSize(),
    vrmModelData = yourVrmData,
    // space.glb will load automatically as background
)
```

**Expected Result**:
- Avatar visible in center
- Galaxy visible in background with much more glow/bloom
- Stars should appear larger and more nebulous, not tiny white dots
- Galaxy center should be brighter and more visible

### Method 2: Galaxy Debug Mode (Camera Focused on Galaxy)
To test if the galaxy model is correct, switch to GALAXY_DEBUG mode:

```kotlin
// After renderer is ready
LaunchedEffect(renderer) {
    renderer?.let { r ->
        // Wait a moment for background to load
        delay(1000)

        // Switch to galaxy debug camera
        r.setCameraMode(FilamentRenderer.CameraMode.GALAXY_DEBUG)

        Log.d("HumanoidScreen", "Switched to GALAXY_DEBUG camera mode")
    }
}
```

**Expected Result**:
- Camera should center on the galaxy bounding box
- Full galaxy should be visible filling most of the screen
- Should look similar to the Model Inspector screenshot
- Bright galactic core with surrounding nebulosity

### Method 3: Toggle Between Modes (Interactive Testing)
Add a button to switch modes dynamically:

```kotlin
var cameraMode by remember { mutableStateOf(FilamentRenderer.CameraMode.AVATAR) }

Column {
    Button(
        onClick = {
            cameraMode = if (cameraMode == FilamentRenderer.CameraMode.AVATAR) {
                FilamentRenderer.CameraMode.GALAXY_DEBUG
            } else {
                FilamentRenderer.CameraMode.AVATAR
            }
            renderer?.setCameraMode(cameraMode)
        }
    ) {
        Text("Toggle Camera: ${cameraMode.name}")
    }

    FilamentView(
        modifier = Modifier.weight(1f),
        vrmModelData = yourVrmData,
        onRendererReady = { r -> renderer = r }
    )
}
```

## Troubleshooting

### Issue: Still seeing tiny white dots
**Possible Causes**:
1. Bloom not enabled - check logs for "View configured with aggressive bloom"
2. Emissive not applied - check logs for "Boosted emissive for background entity"
3. Background not loaded - check logs for "Background asset loaded and configured"

**Solution**: Check logcat for FilamentRenderer tag and verify all initialization steps complete

### Issue: Galaxy not visible at all
**Possible Causes**:
1. space.glb not in assets folder
2. Background loading failed
3. Camera frustum doesn't include the galaxy

**Solution**:
- Verify `features/humanoid/src/main/assets/space.glb` exists
- Check logs for "Failed to load background" errors
- Try GALAXY_DEBUG camera mode to center on galaxy

### Issue: Galaxy too bright/washed out
**Adjustment**: Reduce bloom strength in FilamentRenderer.kt:

```kotlin
view.bloomOptions = view.bloomOptions.apply {
    enabled = true
    strength = 0.4f  // Lower value (was 0.8)
    // ... rest stays same
}
```

### Issue: Galaxy too dim
**Adjustment**: Increase emissive factor in FilamentRenderer.kt:

```kotlin
materialInstance.setParameter(
    "emissiveFactor",
    com.google.android.filament.Colors.RgbType.SRGB,
    10.0f, 10.0f, 10.0f  // Higher value (was 5.0)
)
```

## Comparing to Model Inspector

The Model Inspector screenshot shows:
- Large bright galactic core (orange/yellow)
- Surrounding blue nebulosity
- Stars visible as individual glowing points
- Deep space background

With these changes, the Android rendering should now closely match that appearance when in GALAXY_DEBUG mode, and provide a beautiful stellar backdrop when in AVATAR mode.

## Performance Notes

- Aggressive bloom may impact performance on lower-end devices
- If needed, reduce bloom resolution from 360 to 256 or 180
- Emissive boost happens once during load, no runtime cost
- Galaxy debug camera is for testing only - use AVATAR mode in production

## Implementation Details

All changes are in:
- [FilamentRenderer.kt](features/humanoid/src/main/java/com/lifo/humanoid/rendering/FilamentRenderer.kt)

Key functions:
- `setCameraMode(mode)` - Public API to switch camera modes
- `setupGalaxyDebugCamera()` - Centers camera on galaxy bounding box
- `configureView()` - Enhanced bloom and exposure settings
- `loadBackgroundAsset()` - Applies emissive boost to background materials

## Next Steps

If you want even more control, you could:
1. Make bloom strength configurable via UI slider
2. Add camera distance adjustment for galaxy debug mode
3. Implement multiple background options (different galaxy models)
4. Add rotation speed control for background animation

---

**Note**: After testing, you can easily revert to original settings by changing bloom strength back to 0.1f and removing emissive boost. The camera mode system is completely optional and doesn't affect normal operation.
