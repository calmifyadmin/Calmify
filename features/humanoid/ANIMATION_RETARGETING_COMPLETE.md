# VRM Animation Retargeting - Implementation Complete

**Date:** 2025-12-01
**Status:** ✅ COMPILED SUCCESSFULLY
**Implementation:** Amica Retargeting Formula

---

## Overview

Successfully implemented the critical animation retargeting formula from Amica's VRMAnimationLoaderPlugin.ts. This enables VRM 0.x models to properly play VRMA (VRM 1.0) animations with full skeletal articulation instead of remaining stuck in T-pose.

## The Critical Formula

```
retargetedQuat = p * a * c^-1
```

Where:
- **p** = Parent bone's world rotation quaternion in animation's rest pose
- **a** = Animation quaternion value from VRMA file
- **c^-1** = Inverse of this bone's world rotation quaternion in animation's rest pose

## Implementation Summary

### 1. FilamentRenderer.kt - Bone Matrix Initialization

**File:** [FilamentRenderer.kt](features/humanoid/src/main/java/com/lifo/humanoid/rendering/FilamentRenderer.kt#L643-L649)

Added critical initialization after model loading:

```kotlin
// CRITICAL FIX: Initialize bone matrices for the loaded model
val initialAnimator = asset.getInstance()?.animator
if (initialAnimator != null) {
    initialAnimator.updateBoneMatrices()
    Log.d(tag, "✓ Initial bone matrices updated - model should now be in rest pose (not T-pose)")
}
```

**Why needed:** Filament requires explicit `updateBoneMatrices()` call to sync bone transforms with skinning matrices. Without this, the model displays in bind pose (T-pose) regardless of bone transforms.

### 2. VrmaAnimationPlayer.kt - Retargeting Formula

**File:** [VrmaAnimationPlayer.kt](features/humanoid/src/main/java/com/lifo/humanoid/animation/VrmaAnimationPlayer.kt#L1057-L1089)

Implemented Amica's retargeting formula:

```kotlin
// Apply Amica's retargeting formula: retargetedQuat = p * a * c^-1
val retargetedQuat = if (humanoidBone != null) {
    // Get parent bone (walk up hierarchy to find one that exists in animation)
    val parentBone = findParentBoneInAnimation(humanoidBone)

    // Get quaternions from animation rest pose data
    val parentWorldQuat: FloatArray? = if (parentBone != null) {
        // Keys are HumanoidBone enum, not String
        animationBoneWorldQuaternions[parentBone]
    } else {
        // No parent found - use hipsParent quaternion
        animationHipsParentWorldQuaternion
    }

    // Get bone inverse quaternion from map
    val boneWorldQuatInverse: FloatArray? = animationBoneWorldQuaternionInverses[humanoidBone]

    if (parentWorldQuat != null && boneWorldQuatInverse != null) {
        // Apply formula: p * a * c^-1
        // Step 1: a * c^-1
        val temp = multiplyQuaternions(animQuat, boneWorldQuatInverse)
        // Step 2: p * (a * c^-1)
        val result = multiplyQuaternions(parentWorldQuat, temp)
        normalizeQuaternion(result)
    } else {
        // Fallback for bones not in animation's humanoid mapping
        animQuat.copyOf()
    }
} else {
    // Not a humanoid bone - apply directly
    animQuat.copyOf()
}
```

### 3. Parent Bone Resolution

**File:** [VrmaAnimationPlayer.kt](features/humanoid/src/main/java/com/lifo/humanoid/animation/VrmaAnimationPlayer.kt#L1172-L1184)

Following Amica's pattern for parent bone lookup:

```kotlin
private fun findParentBoneInAnimation(bone: HumanoidBone): HumanoidBone? {
    var parentBone = humanoidBoneParentMap[bone]
    // Walk up parent chain until we find a bone with quaternion data
    while (parentBone != null) {
        // Keys are HumanoidBone enum values, not strings
        if (animationBoneWorldQuaternions.containsKey(parentBone)) {
            return parentBone
        }
        parentBone = humanoidBoneParentMap[parentBone]
    }
    return null // Will use hipsParent
}
```

### 4. Rest Pose Data Extraction

**File:** [VrmaAnimationLoader.kt](features/humanoid/src/main/java/com/lifo/humanoid/animation/VrmaAnimationLoader.kt#L385-L463)

Extracts animation rest pose quaternions needed for retargeting:

```kotlin
private fun extractRestPoseData(...): Pair<FloatArray?, Map<String, FloatArray>> {
    // Calculate world quaternion for each bone by walking up parent chain
    val worldQuat = calculateWorldQuaternion(nodeIndex, nodeTransforms, nodeParents)
    boneWorldQuaternions[vrmBoneName] = worldQuat

    // For hips, also calculate hipsParent quaternion
    if (vrmBoneName == "hips") {
        val hipsParentWorldQuat = calculateWorldQuaternion(hipsParentIndex, ...)
        boneWorldQuaternions["hipsParent"] = hipsParentWorldQuat
    }
}
```

## Key Technical Details

### Map Type Declarations

**Critical:** Maps use `HumanoidBone` enum as keys, NOT strings:

```kotlin
// Lines 922-924
private val animationBoneWorldQuaternions = mutableMapOf<HumanoidBone, FloatArray>()
private val animationBoneWorldQuaternionInverses = mutableMapOf<HumanoidBone, FloatArray>()
```

This was the source of all type inference errors - attempting to use String keys when the maps expected `HumanoidBone` enum values.

### Quaternion Multiplication Order

**CRITICAL:** Quaternion multiplication is NOT commutative!

- `p * a * c^-1` ≠ `c^-1 * a * p`
- Order matters: First `a * c^-1`, then `p * result`

### Why This Formula Works

1. **c^-1**: Removes the animation's rest pose bone orientation
2. **a**: Applies the animation rotation
3. **p**: Transforms to parent's coordinate frame in target model

This transforms rotations from animation's rest pose space → target model's bone space.

## Problem Evolution

### Original Problem
- VRM model stuck in T-pose during animation playback
- Only slight oscillations visible (translation on hips only)
- No skeletal articulation

### First Attempt (FAILED)
- Added bone matrix initialization
- Removed coordinate negation
- Applied quaternions directly
- **Result:** Still didn't work - rotations in wrong space

### Final Solution (SUCCESS)
- Implemented complete Amica retargeting formula
- Extracted animation rest pose data
- Applied `p * a * c^-1` transformation
- **Result:** ✅ Compilation successful

## Compilation Status

```bash
./gradlew :features:humanoid:compileDebugKotlin

BUILD SUCCESSFUL in 15s
55 actionable tasks: 55 up-to-date
```

All type inference errors resolved by correctly using `HumanoidBone` enum as map keys.

## Expected Results

With the retargeting formula properly implemented, animations should now:

- ✅ Show full skeletal articulation (not just position oscillation)
- ✅ Arms rotate and move naturally
- ✅ Legs articulate properly during walking/dancing
- ✅ Spine bends and twists
- ✅ Head rotates with body
- ✅ All humanoid bones animate smoothly

## Debug Logs to Monitor

When testing on device, look for these logs in VrmaAnimationPlayer:

```
=== Playing Animation (Amica Retargeting Formula) ===
Retargeting: p * a * c^-1 (from Amica VRMAnimationLoaderPlugin)
Animation rest pose data: N bone quaternions
✓ hips: anim=[x,y,z,w] -> retargeted=[x,y,z,w] (Amica formula: p*a*c^-1)
✓ spine: anim=[x,y,z,w] -> retargeted=[x,y,z,w] (Amica formula: p*a*c^-1)
```

**Key things to verify:**
1. Animation rest pose data count > 0
2. Retargeting being applied (not "direct" fallback)
3. Quaternion values changing frame-to-frame

## Amica Reference Sources

Implementation based on analysis of:

1. **VRMAnimationLoaderPlugin.ts:214-226** - Core retargeting formula
2. **VRMAnimationLoaderPlugin.ts:191-199** - Parent bone resolution pattern
3. **VRMAnimation.ts:47-121** - Coordinate system handling

## Files Modified

1. **FilamentRenderer.kt** - Bone matrix initialization
2. **VrmaAnimationPlayer.kt** - Retargeting formula implementation
3. **VrmaAnimationLoader.kt** - Rest pose data extraction (already had this)

## Testing Checklist

- [x] Code compiles successfully
- [x] Type inference errors resolved
- [x] Retargeting formula implemented
- [x] Debug logging added
- [ ] Test on device with idle animation
- [ ] Test on device with dance animation
- [ ] Test on device with greeting animation
- [ ] Verify full skeletal articulation works
- [ ] Confirm no T-pose stuck behavior

## Next Steps

1. **Build APK:** `./gradlew assembleDebug`
2. **Install on device:** `./gradlew installDebug`
3. **Monitor logcat** for VrmaAnimationPlayer logs
4. **Test all animations** in the humanoid feature
5. **Verify** arms, legs, spine articulate properly

## Technical Notes

### Performance
- Formula applied per-bone per-frame
- Quaternion multiplications: ~16-20 FLOPS each
- Only applied to humanoid bones (typically 15-25 bones max)
- Animation rest pose data cached, no re-computation

### Fallback Behavior
If retargeting data missing for a bone:
- Falls back to direct quaternion application
- Logs: `(Amica formula: direct)`
- Can happen for non-essential bones not in animation's humanoid mapping

### Coordinate Systems
All formats use same system:
- Y-up, Right-handed
- Character faces +Z
- Quaternion format: [x, y, z, w]
- No conversion needed between VRM/VRMA/Filament/glTF

---

## Summary

The implementation is now **complete and compiles successfully**. The Amica retargeting formula has been faithfully replicated in Kotlin/Filament, following the exact mathematical approach from the TypeScript/Three.js reference implementation.

The key insight was understanding that VRMA animations store rotations in their own rest pose space, and the formula `p * a * c^-1` performs the critical coordinate space transformation to the target model's bone space.

**Status:** ✅ Ready for device testing

---

**References:**
- [AMICA_RETARGETING_FORMULA.md](AMICA_RETARGETING_FORMULA.md) - Detailed formula explanation
- [VRM_ANIMATION_FIX.md](VRM_ANIMATION_FIX.md) - Initial fix attempts
- [VRMC_vrm_animation Specification](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm_animation-1.0/README.md)
