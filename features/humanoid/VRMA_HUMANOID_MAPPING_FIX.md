# VRMA Animation Fix - Missing Humanoid Bone Mapping

**Date:** 2025-12-01
**Issue:** Animation rest pose data extraction returns 0 bones
**Root Cause:** VRMA animations don't contain humanoid bone mapping

---

## Problem Discovered

From logcat analysis:
```
VrmaAnimationPlayer: Animation rest pose data: 0 bone quaternions
```

This means `extractRestPoseData()` is returning empty because `parseHumanoidMapping()` returns an empty map - the VRMA animations don't have the `humanoid.humanBones` object in their VRMC_vrm_animation extension.

## Root Cause

The VRMA specification allows animations to omit the humanoid bone mapping if it's meant to be inferred from the target VRM model. Our code was trying to extract the mapping from the animation file itself, but it doesn't exist there.

## Solution

Use the **VRM model's humanoid bone mapping** instead of trying to extract it from the animation file. The model knows which nodes correspond to which humanoid bones.

## Implementation Plan

1. Pass the VRM model's humanoid bone information to the animation loader
2. Use the model's bone names to build the mapping for rest pose extraction
3. Match animation node names to model bone names

The animation tracks already reference node names - we just need to map those to the VRM humanoid bones from the loaded model.

---

**Status:** Fix needed - will implement solution to pass model bone mapping to animation loader
