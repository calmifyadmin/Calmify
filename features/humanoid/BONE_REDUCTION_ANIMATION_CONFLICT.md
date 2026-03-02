# Bone Reduction vs Animation System Conflict

**Date:** 2025-12-01
**Status:** 🔍 INVESTIGATING
**Issue:** VRM model stuck in T-pose despite Amica retargeting formula implementation

---

## Problem Discovery

After implementing the Amica retargeting formula correctly, the VRM model still remains in T-pose during animation playback. Investigation revealed a **critical conflict between the bone reduction system and animation system**.

### Visual Evidence

Model appears in perfect T-pose with arms fully extended horizontally:
- ❌ No skeletal articulation
- ❌ Only slight oscillation visible
- ❌ Arms remain perfectly horizontal
- ❌ No spine bending or leg movement

### Root Cause Analysis

**The bone reduction system and animation system are NOT coordinated:**

1. **VRM Loading (VrmLoader.kt):**
   ```kotlin
   if (boneCount > MAX_BONES_FILAMENT) {
       val reductionResult = reduceBones(rootObject, MAX_BONES_FILAMENT)
       boneIndexMapping = reductionResult.second  // ← Mapping created
       currentBuffer = rebuildGltfBuffer(currentBuffer, modifiedRootObject, boneIndexMapping)
   }
   ```

2. **Animation Loading (VrmaAnimationLoader.kt):**
   - Loads VRMA files independently
   - Uses node names from original glTF structure
   - **NO AWARENESS of bone reduction mapping!**

3. **Animation Playback (VrmaAnimationPlayer.kt):**
   ```kotlin
   // Resolves entities by node name
   val entity = nodeEntityMap[nodeName]  // ← May find wrong bone!
   ```

### The Critical Issue

When bones are reduced:
- Original model: 312 bones with names like "leftUpperArm", "spine", etc.
- After reduction: 200 bones with **remapped indices**
- Some bones removed, others remapped to parents
- **Animation system still uses original node names** but entities may point to wrong bones!

Example scenario:
```
Original skeleton:
  - spine (index 15)
  - chest (index 16)
  - upperChest (index 17)  ← This gets removed
  - neck (index 18)

After reduction:
  - spine (index 15) ← Correct
  - chest (index 16) ← Correct
  - neck (index 17)  ← Now has wrong index! Was 18, now 17

Animation plays:
  - Looks for "neck" → finds entity at old mapping
  - Applies rotation to WRONG bone
  - Result: T-pose with wrong transforms applied
```

## Evidence from Code

### Bone Reduction in VrmLoader.kt

**Lines 139-154:**
```kotlin
if (boneCount > MAX_BONES_FILAMENT) {
    Log.w(tag, "Model has $boneCount bones (>$MAX_BONES_FILAMENT). Applying automatic bone reduction...")

    val reductionResult = reduceBones(rootObject, MAX_BONES_FILAMENT)
    modifiedRootObject = reductionResult.first
    boneIndexMapping = reductionResult.second  // ← Created but never passed to animation system!

    val newBoneCount = countBones(modifiedRootObject)
    Log.d(tag, "Bone reduction completed: $boneCount -> $newBoneCount bones")

    currentBuffer = rebuildGltfBuffer(currentBuffer, modifiedRootObject, boneIndexMapping)
    Log.d(tag, "glTF binary buffer rebuilt with reduced bones")
}
```

### Bone Priority System

**Lines 380-417:** The system removes bones by priority:

**Priority 5 (REMOVED FIRST):**
```kotlin
lowerName.contains("eye") || lowerName.contains("jaw") -> 5
lowerName.contains("brow") || lowerName.contains("mouth") -> 5
```

**Priority 4 (FINGER DETAILS):**
```kotlin
lowerName.contains("distal") || lowerName.contains("intermediate") -> 4
lowerName.contains("proximal") || lowerName.contains("metacarpal") -> 4
```

**Priority 0 (KEPT - Core skeleton):**
```kotlin
lowerName.contains("hips") || lowerName.contains("spine") -> 0
lowerName.contains("neck") || lowerName.contains("head") -> 0
```

### Animation System Unaware

**VrmaAnimationPlayer.kt line 227:**
```kotlin
// Builds node name -> entity mapping AFTER bone reduction
nodeNames.forEachIndexed { index, name ->
    if (index < asset.entities.size) {
        nodeEntityMap[name] = asset.entities[index]  // ← Using post-reduction entities!
    }
}
```

The animation system receives node names from `FilamentRenderer` which provides the post-reduction node names, but the mapping may be incorrect if bones were removed.

## Test Solution: Disable Bone Reduction

To verify this is the root cause, we've **temporarily disabled bone reduction:**

**File:** [VrmLoader.kt:46-49](features/humanoid/src/main/java/com/lifo/humanoid/data/vrm/VrmLoader.kt#L46-L49)

```kotlin
companion object {
    // TEMPORARY: Increased to 512 to disable bone reduction for animation testing
    // Original Filament limit is 256, but we're testing if bone reduction breaks animations
    // TODO: Fix bone reduction to properly update animation bone mappings
    const val MAX_BONES_FILAMENT = 512 // Filament's hardware limit (TEMPORARILY INCREASED)
}
```

### Expected Results After Disabling Bone Reduction

If bone reduction is the culprit:
- ✅ Model should animate properly (no T-pose)
- ✅ Full skeletal articulation (arms, legs, spine)
- ✅ Smooth animation playback
- ✅ Amica retargeting formula working correctly

If model STILL in T-pose:
- ❌ Bone reduction is NOT the problem
- ❌ Need to investigate other causes (transform application, coordinate systems, etc.)

## Permanent Solutions (After Confirming Root Cause)

### Option 1: Pass Bone Mapping to Animation System

Modify the loading pipeline to pass `boneIndexMapping` through the entire chain:

```kotlin
// VrmLoader.kt
data class VrmModel(
    val buffer: ByteBuffer,
    val extensions: VrmExtensions,
    val boneIndexMapping: Map<Int, Int>? = null  // ← NEW
)

// FilamentRenderer.kt
fun loadModel(vrmModel: VrmModel) {
    // Pass mapping to animation player
    animationPlayer.setBoneMapping(vrmModel.boneIndexMapping)
}

// VrmaAnimationPlayer.kt
private var boneIndexMapping: Map<Int, Int>? = null

fun setBoneMapping(mapping: Map<Int, Int>?) {
    this.boneIndexMapping = mapping
    // Rebuild entity mappings with correct indices
    rebuildEntityMappingsWithReduction()
}
```

### Option 2: Apply Bone Reduction to Animation Files

Modify VRMA loader to be aware of bone reduction:

```kotlin
// VrmaAnimationLoader.kt
fun loadVrmaFromAssets(
    filename: String,
    boneIndexMapping: Map<Int, Int>?  // ← NEW parameter
): VrmaAnimation {
    // When building humanoid mapping, apply bone index remapping
    val remappedTracks = applyBoneRemappingToTracks(tracks, boneIndexMapping)
    // ...
}
```

### Option 3: Disable Bone Reduction (Simple but Limited)

Keep the current temporary solution:
- Models with >512 bones will fail
- Works for most VRM models from VRoid Studio
- No complex refactoring needed
- Slightly higher GPU memory usage

### Option 4: Smarter Bone Reduction

Improve reduction to maintain name-based lookups:
- Keep all bones that appear in VRMA animation files
- Only remove bones that are truly unused
- Build a "bone alias" map for removed bones

```kotlin
// When animation looks for removed bone, redirect to parent
val resolvedEntity = nodeEntityMap[nodeName]
    ?: boneAliasMap[nodeName]?.let { parentName -> nodeEntityMap[parentName] }
```

## Testing Checklist

- [x] Disabled bone reduction (increased MAX_BONES to 512)
- [x] Code compiles successfully
- [ ] Build and install on device
- [ ] Test animation playback
- [ ] Verify if T-pose issue is resolved
- [ ] Check logcat for bone count logs
- [ ] Monitor for any Filament bone limit errors

## Next Steps

1. **Test on device** with bone reduction disabled
2. **Observe results:**
   - If animations work → bone reduction was the problem
   - If still T-pose → investigate other causes
3. **Implement permanent solution** based on test results
4. **Document final approach** in architecture docs

## Related Files

- [VrmLoader.kt](features/humanoid/src/main/java/com/lifo/humanoid/data/vrm/VrmLoader.kt) - Bone reduction implementation
- [VrmaAnimationPlayer.kt](features/humanoid/src/main/java/com/lifo/humanoid/animation/VrmaAnimationPlayer.kt) - Animation playback
- [FilamentRenderer.kt](features/humanoid/src/main/java/com/lifo/humanoid/rendering/FilamentRenderer.kt) - Model loading
- [BONE_REDUCTION_SYSTEM.md](BONE_REDUCTION_SYSTEM.md) - Bone reduction documentation
- [AMICA_RETARGETING_FORMULA.md](AMICA_RETARGETING_FORMULA.md) - Animation retargeting

## Technical Notes

### Why Bone Reduction Exists

Filament has a **hard limit of 256 bones per skinned mesh**. VRM models from VRoid Studio often have:
- Core humanoid skeleton: ~50 bones
- Hair physics: 100-150 bones
- Clothing dynamics: 50-100 bones
- Accessories: 20-50 bones
- **Total:** 300-400 bones

The reduction system removes physics/cosmetic bones to stay under 256.

### Why This Affects Animations

VRMA animations reference bones by:
1. **Node name** in glTF structure (e.g., "J_Bip_C_UpperChest")
2. **Humanoid bone mapping** (e.g., "upperChest" → "J_Bip_C_UpperChest")

When bones are removed/remapped:
- Node names may point to wrong entities
- Humanoid bone mapping becomes stale
- Animation applies transforms to incorrect bones

---

**Status:** Awaiting device test with bone reduction disabled to confirm root cause.
