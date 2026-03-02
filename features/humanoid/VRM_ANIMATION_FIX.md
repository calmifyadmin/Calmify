# VRM Animation T-Pose Fix - Implementation Summary

**Date:** 2025-12-01
**Issue:** VRM model remains in T-pose during animations, only slight oscillation visible
**Root Cause:** Incorrect quaternion coordinate transformation for VRMA animations

## Problem Analysis

### Symptoms
- VRM model loads correctly and appears in scene
- Animations trigger but model stays in T-pose
- Only slight positional oscillation visible (translation on hips)
- No actual bone rotations being applied to skeleton

### Root Causes Identified

1. **Missing Initial Bone Matrix Update** (CRITICAL)
   - Filament separates transform updates from skinning matrix updates
   - After loading model, `updateBoneMatrices()` was never called
   - Skinned mesh remained in bind pose (T-pose) even though bone transforms existed
   - **Location:** `FilamentRenderer.kt:loadModel()`

2. **Incorrect Coordinate System Conversion** (CRITICAL)
   - VRMA animations (VRM 1.0 format) were having X and Z components negated
   - This transformation is WRONG for VRMA → VRM models
   - VRMA format already handles cross-model retargeting internally
   - **Location:** `VrmaAnimationPlayer.kt:applyRotationWithWorldTransform()`

## Implementation Details

### Fix 1: Initialize Bone Matrices After Model Load

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/rendering/FilamentRenderer.kt`

**Change:** Added bone matrix initialization after model loading and scaling

```kotlin
// CRITICAL FIX: Initialize bone matrices for the loaded model
val initialAnimator = asset.getInstance()?.animator
if (initialAnimator != null) {
    initialAnimator.updateBoneMatrices()
    Log.d(tag, "✓ Initial bone matrices updated - model should now be in rest pose (not T-pose)")
} else {
    Log.w(tag, "⚠ No animator found - model may remain in T-pose if skinned")
}
```

**Rationale:**
- Filament requires explicit `updateBoneMatrices()` call to sync transforms with skinning
- Without this, skinned mesh displays bind pose (T-pose) regardless of bone transforms
- Must be called after any transform modifications

### Fix 2: Remove Incorrect Quaternion Coordinate Negation

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/animation/VrmaAnimationPlayer.kt`

**Change:** Removed X and Z component negation, apply quaternions directly

**Before (INCORRECT):**
```kotlin
// Apply coordinate system adjustment for Filament
val retargetedQuat = floatArrayOf(
    -animQuat[0],  // Negate X - WRONG!
    animQuat[1],   // Keep Y
    -animQuat[2],  // Negate Z - WRONG!
    animQuat[3]    // Keep W
)
```

**After (CORRECT):**
```kotlin
// Apply animation quaternion DIRECTLY (no coordinate conversion needed)
// VRMA format already handles cross-model retargeting internally
val retargetedQuat = animQuat.copyOf()
```

**Rationale:**
- VRMA animations use standardized humanoid bone space
- VRM 1.0, VRM 0.x, and Filament/glTF all use same coordinate system:
  - Y-up, Right-handed
  - Character faces +Z
  - Quaternions in [x, y, z, w] format
- Coordinate negation was corrupting the rotation data
- VRMA extension handles retargeting through its humanoid bone mapping

## Technical Background

### VRM Coordinate Systems

**VRM 1.0 & VRM 0.x:**
- Y-up, Right-handed coordinate system
- Character faces +Z direction
- Quaternion format: [x, y, z, w]

**Filament/glTF:**
- Y-up, Right-handed coordinate system
- Column-major matrices
- Quaternion format: [x, y, z, w]

**Conclusion:** NO coordinate conversion needed!

### VRMA Animation Format

According to [official VRM specification](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm_animation-1.0/README.md):

- VRMC_vrm_animation extension stores rotations in standardized humanoid bone space
- Format designed for cross-model compatibility
- Retargeting handled internally through humanoid bone mapping
- Only hips bone can have translation, other bones rotation only

### Filament Skinning Architecture

Filament uses a two-step process for skeletal animation:

1. **Transform Updates:** Modify bone entity transforms via `TransformManager`
2. **Skinning Update:** Call `Animator.updateBoneMatrices()` to update GPU skinning matrices

**Critical:** Step 2 is NOT automatic! Must be called explicitly after transform changes.

## Testing

### Expected Results After Fix

1. ✅ Model loads in correct rest pose (not T-pose)
2. ✅ Animations apply full skeletal rotations
3. ✅ Arms, legs, spine all animate correctly
4. ✅ No more "stuck in T-pose" behavior
5. ✅ Smooth animation playback with proper bone deformation

### Test Cases

1. **Idle Animation:** Should show natural breathing/idle motion
2. **Dance Animation:** Should show full body movement with arm/leg rotation
3. **Greeting Animation:** Should wave hand with proper arm rotation
4. **Full Body Animations:** Should demonstrate complete skeletal articulation

### Debugging Logs

New logs added for verification:

```
FilamentRenderer: ✓ Initial bone matrices updated - model should now be in rest pose (not T-pose)
VrmaAnimationPlayer: === Playing Animation (VRM 1.0 VRMA Direct Retargeting) ===
VrmaAnimationPlayer: Using DIRECT quaternion application (no coordinate negation)
VrmaAnimationPlayer: ✓ hips: quat=[x, y, z, w] (direct VRMA application)
VrmaAnimationPlayer: ✓ spine: quat=[x, y, z, w] (direct VRMA application)
```

## References

### Official Specifications
- [VRMC_vrm_animation Specification](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm_animation-1.0/README.md)
- [VRM Animation Overview](https://vrm.dev/en/vrma/)
- [VRM 1.0 Humanoid Specification](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/humanoid.md)

### Related Issues & Discussions
- [Three-VRM Retargeting Issues](https://github.com/pixiv/three-vrm/issues/1387)
- [VRM T-Pose Animation Discussions](https://discourse.threejs.org/t/fixing-skeletonutils-retarget-and-retargetclip-functions/65149)

## Files Modified

1. **FilamentRenderer.kt**
   - Added `updateBoneMatrices()` call after model loading
   - Lines: ~643-649

2. **VrmaAnimationPlayer.kt**
   - Removed incorrect quaternion coordinate negation
   - Updated documentation with VRMA retargeting explanation
   - Added improved debug logging
   - Lines: ~1026-1073, ~377-382

## Implementation Status

- [x] Root cause analysis completed
- [x] Web research on VRM 1.0 specification
- [x] Fix 1: Initial bone matrix update implemented
- [x] Fix 2: Quaternion direct application implemented
- [x] Debug logging enhanced
- [x] Documentation created
- [ ] Build and test on device
- [ ] Verify all animations work correctly

## Notes for Future Development

1. **Always call `updateBoneMatrices()`** after modifying bone transforms in Filament
2. **Trust VRMA format** - it handles retargeting, don't apply manual conversions
3. **VRM 1.0 and 0.x use same coordinate system** - no conversion needed
4. Monitor for any scaling issues if mixing models with different proportions
5. Consider implementing VRM rest pose data for even better retargeting accuracy

---

**Status:** ✅ Implementation Complete - Ready for Testing
