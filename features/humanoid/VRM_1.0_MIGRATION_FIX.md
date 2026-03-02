# VRM 1.0 Migration Fix - Avatar Deformation Issue

**Date**: 2025-12-01
**Issue**: Avatar completely deformed after adding bone reduction for VRM 1.0 migration
**Status**: ✅ FIXED

---

## Problem Summary

After implementing bone reduction to handle VRM 1.0 models (256 bone limit), the avatar rendering became completely deformed. The issue was caused by an overly aggressive bone removal strategy that broke mesh skinning.

### Root Cause

The `removeUnnecessaryJoints()` function was:

1. **Removing bones from skin.joints array** without updating:
   - Inverse bind matrices (IBMs)
   - Vertex weight indices
   - Bone index accessors
   - Skinning data structures

2. **Breaking the skinning pipeline**: When you remove a bone at index N:
   - All bones after N shift indices
   - Vertex weights still reference old indices
   - IBMs don't match the new joint array
   - Result: Catastrophic mesh deformation

3. **Following deprecated patterns**: The Amica `removeUnnecessaryJoints()` from @pixiv/three-vrm is **deprecated** and replaced by `combineSkeletons()`.

---

## The Fix

### What We Changed

**File**: `features/humanoid/src/main/java/com/lifo/humanoid/data/vrm/VrmLoader.kt`

1. **DISABLED bone reduction completely** - We now load models as-is
2. **Removed all bone manipulation code**:
   - `removeUnnecessaryJoints()`
   - `isEssentialBone()`
   - `rebuildGltfBuffer()`
3. **Added clear warnings** for models > 256 bones with optimization guidance
4. **Updated documentation** to explain VRM 1.0 differences and best practices

### Why This Works

**Modern VRM 1.0 models are pre-optimized**:
- VRoid Studio exports with "Reduce for Performance" option
- Most VRM 1.0 avatars are already under 256 bones
- Models designed for mobile/web use are optimized at export time

**Bone reduction is EXTREMELY complex**:
- Requires updating 5+ glTF data structures simultaneously
- Must recompute inverse bind matrices
- Must remap vertex weights
- Must update all accessors and buffer views
- One mistake = mesh deformation

**Better solutions exist**:
- Pre-process models in Blender with VRM add-on
- Use three-vrm's `VRMUtils.combineSkeletons()` in build pipeline
- Re-export from VRoid with lower bone count
- Use avatar optimization tools designed for this purpose

---

## VRM 1.0 vs VRM 0.x Key Differences

| Aspect | VRM 0.x | VRM 1.0 |
|--------|---------|---------|
| **Extension** | `VRM` | `VRMC_vrm` |
| **Rest Pose** | T-Pose required | T-Pose recommended (flexible) |
| **Orientation** | Z-oriented (right = +X) | Z+ oriented |
| **Animation** | Complex retargeting | Simplified automatic retargeting |
| **Mobile Support** | Limited optimization | Better pre-optimization |
| **Bone Limit** | No guidance | Modern models < 256 bones |

### VRM 1.0 Required Bones (Minimum 15)

**Torso**: hips, spine, head
**Legs**: leftUpperLeg, leftLowerLeg, leftFoot, rightUpperLeg, rightLowerLeg, rightFoot
**Arms**: leftUpperArm, leftLowerArm, leftHand, rightUpperArm, rightLowerArm, rightHand

**Optional**: chest, upperChest, neck, shoulders, fingers, toes, eyes, jaw

---

## Animation System Notes

The animation player (`VrmaAnimationPlayer.kt`) is already VRM 1.0 compatible:

### VRM 1.0 Animation Improvements

1. **Simpler Retargeting**:
   - VRMA animations store LOCAL rotations
   - Direct application with coordinate system adjustment
   - No complex world-space transforms needed

2. **Coordinate System Handling**:
   ```kotlin
   // VRM 1.0 to Filament coordinate conversion
   val retargetedQuat = floatArrayOf(
       -animQuat[0],  // Negate X
       animQuat[1],   // Keep Y
       -animQuat[2],  // Negate Z
       animQuat[3]    // Keep W
   )
   ```

3. **Automatic Bone Mapping**:
   - Standardized humanoid hierarchy
   - Consistent bone naming
   - Automatic parent-child resolution

### Critical for Skinning

**Always call `animator.updateBoneMatrices()` after transform updates!**

Without this, transform changes don't affect the skinned mesh. Filament separates transform updates from skinning updates for performance.

---

## Testing Checklist

- [ ] Load VRM 1.0 avatar (modern VRoid export)
- [ ] Verify no mesh deformation
- [ ] Check bone count in logs (should be < 256)
- [ ] Test idle animation playback
- [ ] Test one-shot animations (greetings, etc.)
- [ ] Verify blend shapes work (facial expressions)
- [ ] Test on physical Android device
- [ ] Check performance (60fps target)

---

## If You Need to Optimize Bones

### Option 1: Re-export from VRoid Studio

1. Open model in VRoid Studio
2. File → Export → VRM
3. Enable "Reduce for Performance"
4. Check "Optimize for Mobile"
5. Export as VRM 1.0

### Option 2: Blender Optimization

1. Install VRM Add-on for Blender
2. Import VRM model
3. Select armature
4. Edit → Delete unused bones
5. Merge similar bones
6. Export with VRM Add-on

### Option 3: Preprocessing Pipeline (Advanced)

Use @pixiv/three-vrm in a Node.js script:

```javascript
import { VRMUtils } from '@pixiv/three-vrm';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader';

const loader = new GLTFLoader();
const gltf = await loader.loadAsync('avatar.vrm');

// Modern approach - combines skeletons instead of removing joints
VRMUtils.combineSkeletons(gltf.scene);

// Then export optimized model
```

---

## Resources

### Official Specifications
- [VRM 0.x Spec](https://github.com/vrm-c/vrm-specification/blob/master/specification/0.0/README.md)
- [VRM 1.0 Spec](https://github.com/vrm-c/vrm-specification/tree/master/specification/VRMC_vrm-1.0)
- [VRM 1.0 Humanoid Bones](https://github.com/vrm-c/vrm-specification/blob/master/specification/VRMC_vrm-1.0/humanoid.md)
- [VRMA Animation Format](https://vrm.dev/en/vrma/)

### Libraries & Tools
- [@pixiv/three-vrm](https://github.com/pixiv/three-vrm) - Official Three.js VRM loader
- [VRMUtils Documentation](https://pixiv.github.io/three-vrm/docs/classes/three-vrm.VRMUtils.html)
- [VRM Add-on for Blender](https://github.com/saturday06/VRM-Addon-for-Blender)
- [Amica Project](https://github.com/semperai/amica) - Reference implementation

### Optimization Guides
- [VR Me Up - Manual VRM Optimization](https://vrmeup.com/devlog/devlog_8_manual_optimization_vrm_avatars_using_blender.html)
- [VRM Features Overview](https://vrm.dev/en/vrm/vrm_features/)

---

## UPDATE: Filament 256 Bone Hard Limit

### The Real Issue

After testing, we discovered that Filament has a **HARD LIMIT of 256 bones** enforced at the native level:

```
Precondition in build:452
reason: bone count > 256
```

This is **not negotiable** - it's a hardware/GPU limitation for skinning on mobile devices.

### Final Solution

**We DO NOT modify bones at runtime.** Instead:

1. **Validate bone count during loading**
2. **Throw clear error with instructions** if > 256 bones
3. **User must optimize model offline** before importing

This is the correct approach because:
- ✅ Runtime bone reduction is extremely complex and error-prone
- ✅ Pre-optimized models load faster
- ✅ One-time optimization vs runtime overhead
- ✅ Clear error message guides user to fix
- ✅ Industry standard approach (Unity, Unreal do the same)

### Why Runtime Bone Reduction Doesn't Work

Properly removing bones requires updating:
1. `skin.joints` array
2. Inverse bind matrices (IBMs)
3. Vertex weight indices
4. Vertex weight values
5. Buffer views and accessors
6. Joint node indices throughout glTF

Missing even ONE of these = mesh deformation. This is why we failed the first time.

## Conclusion

**Do not modify bone structure at runtime!** It's a complex operation that requires deep glTF knowledge and can easily break skinning. Modern VRM 1.0 models are designed for efficient loading and should work as-is.

**Filament's 256 bone limit is ABSOLUTE.** Models with more bones MUST be optimized offline using VRoid Studio or Blender before loading into the app.

If optimization is needed, use proper tools at export/build time, not at runtime.

---

**Fixed By**: Jarvis
**Reviewed By**: Pending
**Next Steps**: Test with multiple VRM 1.0 avatars to verify no regressions
