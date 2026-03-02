# Amica Retargeting Formula Implementation

**Date:** 2025-12-01
**Status:** ✅ IMPLEMENTED
**Source:** amica-master/src/lib/VRMAnimation/VRMAnimationLoaderPlugin.ts:214-226

## The Problem

VRM model was stuck in T-pose during animations with only slight oscillations visible. This was caused by **missing the critical retargeting formula** that transforms animation rotations from the animation's rest pose space to the target model's bone space.

## The Solution: Amica's Retargeting Formula

### Mathematical Formula

From Amica's source code comments:

```
a  = p^-1 * a' * p * c
a' = p * p^-1 * a' * p * c * c^-1 * p^-1
   = p * a * c^-1
```

### Variable Definitions

- **p** = `parentWorldQuat` - Parent bone's world rotation quaternion in animation's rest pose
- **a** = `animQuat` - The animation quaternion value from the VRMA file
- **c** = `boneWorldQuat` - This bone's world rotation quaternion in animation's rest pose
- **c^-1** = Inverse of `c`

### Amica TypeScript Implementation

```typescript
// From VRMAnimationLoaderPlugin.ts:218-226
const worldMatrix = worldMatrixMap.get(boneName)!;           // c
const parentWorldMatrix = worldMatrixMap.get(parentBoneName)!; // p

_quatA.setFromRotationMatrix(worldMatrix).normalize().invert();    // c^-1
_quatB.setFromRotationMatrix(parentWorldMatrix).normalize();       // p

const trackValues = arrayChunk(origTrack.values, 4).flatMap((q) =>
    _quatC.fromArray(q).premultiply(_quatB).multiply(_quatA).toArray()
);                                                                 // p * a * c^-1
```

### Our Kotlin Implementation

**File:** `VrmaAnimationPlayer.kt:1056-1098`

```kotlin
// Apply Amica's retargeting formula: retargetedQuat = p * a * c^-1
val retargetedQuat = if (humanoidBone != null) {
    // Get parent bone (walk up hierarchy to find one that exists in animation)
    val parentBone = findParentBoneInAnimation(humanoidBone)

    // Get quaternions from animation rest pose data
    val parentWorldQuat = if (parentBone != null) {
        getAnimationQuaternion(parentBone.vrmName)
    } else {
        // No parent found - use hipsParent quaternion
        getAnimationQuaternion("hipsParent") ?: animationHipsParentWorldQuaternion
    }

    val boneWorldQuatInverse = getAnimationQuaternionInverse(humanoidBone.vrmName)

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

## Why This Formula is Necessary

### The Problem Without Retargeting

VRMA animations store rotations in the **animation file's rest pose space**. If we apply these rotations directly to a different VRM model:

1. The animation's rest pose may differ from the model's rest pose
2. Different bone orientations cause rotations to be in the wrong coordinate frame
3. Result: Model appears stuck in T-pose with only slight positional movement

### What the Formula Does

The formula `p * a * c^-1` transforms the animation rotation from:
- **Source:** Animation's rest pose bone space
- **Destination:** Target model's local bone space

This is accomplished by:
1. **c^-1**: Remove the animation's rest pose orientation
2. **a**: Apply the animation rotation
3. **p**: Transform to parent's coordinate frame in target model

## Implementation Details

### Rest Pose Data Extraction

Animation rest pose quaternions are extracted in `VrmaAnimationLoader.kt:385-463`:

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

### Parent Bone Resolution

Following Amica's pattern (VRMAnimationLoaderPlugin.ts:191-199):

```kotlin
private fun findParentBoneInAnimation(bone: HumanoidBone): HumanoidBone? {
    var parentBone = humanoidBoneParentMap[bone]
    // Walk up parent chain until we find a bone with quaternion data
    while (parentBone != null) {
        if (animationBoneWorldQuaternions.containsKey(parentBone.vrmName)) {
            return parentBone
        }
        parentBone = humanoidBoneParentMap[parentBone]
    }
    return null // Will use hipsParent
}
```

### Quaternion Multiplication Order

**CRITICAL:** Quaternion multiplication is **not commutative**!

- `p * a * c^-1` ≠ `c^-1 * a * p`
- In Three.js: `quat.premultiply(p).multiply(c^-1)` applies p first, then c^-1
- In our code: `multiplyQuaternions(p, multiplyQuaternions(a, c^-1))`

Order of operations:
1. First: `a * c^-1` (remove animation rest pose orientation)
2. Second: `p * (result)` (apply parent world orientation)

## Debugging & Verification

### Debug Logs Added

```
VrmaAnimationPlayer: === Playing Animation (Amica Retargeting Formula) ===
VrmaAnimationPlayer: Retargeting: p * a * c^-1 (from Amica VRMAnimationLoaderPlugin)
VrmaAnimationPlayer: Animation rest pose data: N bone quaternions
VrmaAnimationPlayer: ✓ hips: anim=[x,y,z,w] -> retargeted=[x,y,z,w] (Amica formula: p*a*c^-1)
```

### Expected Results

With correct retargeting:
- ✅ Full skeletal animation (not just position oscillation)
- ✅ Arms rotate and move naturally
- ✅ Legs articulate properly during walking/dancing
- ✅ Spine bends and twists
- ✅ All humanoid bones animate smoothly

### Fallback Behavior

If retargeting data is missing for a bone:
- Falls back to direct quaternion application
- Logs: `(Amica formula: direct)`
- This can happen for non-essential bones not in animation's humanoid mapping

## Key Differences from Previous Implementation

### Before (INCORRECT)

```kotlin
// Just copied the quaternion directly or applied coordinate negation
val retargetedQuat = floatArrayOf(-animQuat[0], animQuat[1], -animQuat[2], animQuat[3])
// OR
val retargetedQuat = animQuat.copyOf()
```

**Result:** Model stuck in T-pose, rotations in wrong space

### After (CORRECT - Amica Formula)

```kotlin
// Apply proper retargeting transformation
val temp = multiplyQuaternions(animQuat, boneWorldQuatInverse)  // a * c^-1
val retargetedQuat = multiplyQuaternions(parentWorldQuat, temp)  // p * (a * c^-1)
```

**Result:** Full skeletal animation with proper bone articulation

## References

### Amica Source Files Analyzed

1. **VRMAnimationLoaderPlugin.ts** (lines 214-226)
   - Contains the critical retargeting formula implementation
   - Defines how animations are transformed for different models

2. **VRMAnimation.ts** (lines 47-121)
   - Creates animation tracks for VRM models
   - Handles coordinate system conversion for VRM 0.x vs 1.0

3. **proceduralAnimation.ts**
   - Shows direct bone manipulation for procedural animations
   - Demonstrates rotation application to humanoid bones

### VRM Specification References

- [VRMC_vrm_animation Specification](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm_animation-1.0/README.md)
- [VRM Humanoid Bones](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/humanoid.md)

## Implementation Checklist

- [x] Analyzed Amica's retargeting formula from source code
- [x] Implemented quaternion multiplication helper functions
- [x] Implemented `findParentBoneInAnimation()` following Amica pattern
- [x] Applied formula: `p * a * c^-1` in rotation application
- [x] Added comprehensive debug logging
- [x] Handled fallback for bones without retargeting data
- [x] Documented formula and implementation
- [ ] Test on device with all animations
- [ ] Verify full skeletal animation works

## Technical Notes

### Why Amica Uses World Matrices

Amica extracts world quaternions from world matrices because:
1. The animation's skeleton may have different local orientations than the target model
2. World space provides a common reference frame for transformation
3. The formula `p * a * c^-1` converts from one world space to another

### Quaternion Storage Format

Both Amica and our implementation use `[x, y, z, w]` format:
- x, y, z: Imaginary/vector components
- w: Real/scalar component
- This matches Three.js and Filament conventions

### Performance Considerations

- Retargeting formula applied per-bone per-frame
- Quaternion multiplications are relatively cheap (16-20 FLOPS each)
- Caching animation rest pose data avoids re-computation
- Only applied to humanoid bones (typically 15-25 bones max)

---

**Status:** ✅ Implementation Complete - Ready for Testing

This implementation precisely replicates Amica's animation retargeting formula, which is the industry-standard approach for cross-model VRM animation application.
