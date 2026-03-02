# VRM Avatar Bone Optimization Guide

**Problem**: Your VRM avatar has too many bones (>256) and cannot be loaded in Calmify.

**Why**: Filament (the 3D rendering engine) has a hard limit of 256 bones for GPU skinning on mobile devices. This is a hardware limitation, not a software bug.

---

## Quick Check: Does Your Model Need Optimization?

Common bone counts:
- ✅ **Simple avatar**: 50-150 bones (loads fine)
- ✅ **Standard VRoid**: 150-250 bones (loads fine)
- ⚠️ **Complex VRoid with full fingers**: 250-300 bones (may exceed limit)
- ❌ **VRoid with detailed hair**: 300-500+ bones (definitely exceeds limit)

---

## Solution 1: VRoid Studio (Easiest) ⭐ RECOMMENDED

If your model was created in VRoid Studio, this is the easiest way:

### Steps:

1. **Open VRoid Studio**
   - Download from: https://vroid.com/en/studio

2. **Load Your Model**
   - File → Open → Select your .vroid file
   - If you only have a .vrm file, you'll need to use Option 2 (Blender)

3. **Export with Optimization**
   - File → Export → VRM
   - **Enable "Reduce for Performance"** ✓
   - **Check "Optimize for Mobile"** ✓
   - Choose VRM 1.0 format
   - Click Export

4. **Verify**
   - Try loading the new file in Calmify
   - Should now have < 256 bones

### What Gets Reduced:

- Detailed finger bones → simplified hand bones
- Hair strand bones → fewer hair bones
- Accessory bones → merged or removed
- Facial detail bones → kept (needed for expressions)

---

## Solution 2: Blender (More Control)

Use Blender if you want manual control or don't have the original .vroid file.

### Requirements:

- Blender 3.6+ (Download: https://www.blender.org/)
- VRM Add-on for Blender (https://github.com/saturday06/VRM-Addon-for-Blender)

### Steps:

#### 1. Install VRM Add-on

```
1. Download VRM Add-on .zip from GitHub releases
2. Blender → Edit → Preferences → Add-ons
3. Click "Install..." → Select the .zip
4. Enable "VRM_Addon_for_Blender"
```

#### 2. Import Your VRM

```
1. File → Import → VRM (.vrm)
2. Select your .vrm file
3. Wait for import (may take a minute)
```

#### 3. Check Bone Count

```
1. Select the armature (bone structure) in the scene
2. Switch to Object Data Properties (bone icon on right panel)
3. Look at the bone list - count how many you have
```

#### 4. Identify Bones to Remove

Common candidates for removal:

**Hair Bones** (usually the most):
- Look for bones named `Hair_R_001` through `Hair_R_100+`
- VRoid creates MANY hair bones - you can safely remove 50-80% of them
- Keep every 2nd or 3rd hair bone for decent hair movement

**Finger Bones** (saves ~28 bones):
- Each hand has 14 finger bones (thumb + 4 fingers × 3 joints each)
- Remove all finger bones if your avatar doesn't need detailed hand gestures
- Keep `Hand_L` and `Hand_R` bones

**Accessory Bones**:
- Remove bones for accessories you don't need
- Look for `Accessory_*` or custom bone names

**DO NOT Remove**:
- `Hips`, `Spine`, `Chest`, `Neck`, `Head`
- `LeftUpperArm`, `RightUpperArm`, `LeftLowerArm`, `RightLowerArm`
- `LeftHand`, `RightHand`
- `LeftUpperLeg`, `RightUpperLeg`, `LeftLowerLeg`, `RightLowerLeg`
- `LeftFoot`, `RightFoot`
- Eye bones (needed for gaze)

#### 5. Remove Bones

```
1. Switch to Edit Mode (Tab key)
2. Select bones to remove (Shift+Click for multiple)
3. Press X → Delete → Bones
4. Tab back to Object Mode
```

#### 6. Test and Export

```
1. Switch to Pose Mode
2. Move bones around to test if model deforms correctly
3. If OK: File → Export → VRM
4. Choose VRM 1.0 format
5. Export
```

---

## Solution 3: Use Pre-Optimized Avatars

Download avatars that are already optimized for mobile/VR:

### Where to Find:

- **VRoid Hub**: https://hub.vroid.com/
  - Filter by "Mobile" or "VR" tags
  - Look for "Low Poly" or "Optimized" in description

- **Booth**: https://booth.pm/
  - Search for "VRM 最適化" (optimized)
  - Many creators provide mobile-optimized versions

- **The Seed Online**: Official VRoid models
  - These are pre-optimized by VRoid team

### What to Look For:

- Bone count mentioned in description (< 256)
- Tags: "Mobile", "VR Ready", "Optimized"
- Recent models (2023+) are usually VRM 1.0 and optimized

---

## Solution 4: Commission a Professional

If you have a complex custom avatar and can't optimize it yourself:

1. **Hire a VRoid/Blender artist**
   - Look on Fiverr, VRoid Hub, or VTuber communities
   - Ask them to optimize for "mobile VRM" or "256 bone limit"
   - Cost: Usually $20-100 depending on complexity

2. **Provide These Requirements**:
   - Maximum 256 bones
   - VRM 1.0 format
   - Keep facial expressions (blend shapes)
   - Maintain overall appearance

---

## Understanding Bone Count

### What are "bones"?

In 3D models, bones (also called "joints") are invisible points that control how the mesh deforms. More bones = more detailed movement, but also more performance cost.

### Why 256?

- **GPU Uniform Limit**: Most mobile GPUs can pass ~256 matrices to the shader
- **WebGL Limit**: 256 vec4 uniforms = 64 mat4 transforms per draw call
- **Industry Standard**: Unity, Unreal, and most engines use similar limits

### Bone Budget Breakdown:

```
Body Skeleton:      ~30 bones (hips to head, arms, legs)
Fingers:            ~28 bones (14 per hand) - OPTIONAL
Eyes/Face:          ~5 bones
Hair:               ~50-150 bones (biggest variable)
Accessories:        ~10-30 bones
Spring Bones:       ~20-50 bones (physics)
```

**Total**: 143-293 bones (depending on detail level)

To stay under 256, reduce **hair** and **fingers** first.

---

## Verification

After optimization, verify your model:

### Check Bone Count:

**In Blender**:
1. Select armature
2. Object Data Properties → Bones list
3. Count visible bones

**In VRoid Studio**:
- No direct way to check
- Use "Reduce for Performance" and trust the export

**In Logcat (when loading in Calmify)**:
```
VrmLoader: glTF model has 234 joints ✓ (under 256)
```

### Test Animations:

Load the avatar and verify:
- Idle animation plays correctly
- Hair moves naturally (if reduced)
- Hands pose correctly (if finger bones removed)
- Facial expressions work (blend shapes)

---

## FAQ

**Q: Will removing bones affect facial expressions?**
A: No. Facial expressions use "blend shapes" (morph targets), not bones.

**Q: Can I remove bones for accessories I don't use?**
A: Yes, but be careful. Make sure no mesh vertices are weighted to those bones.

**Q: My model looks broken after removing bones!**
A: You likely removed bones that the mesh depends on. In Blender, check Weight Paint mode to see which vertices are affected by each bone.

**Q: Is there a way to increase the 256 limit?**
A: No. This is a hardware limitation of mobile GPUs. Even high-end phones are limited.

**Q: Do VRM 1.0 models automatically have fewer bones?**
A: Not necessarily. VRM 1.0 is a format version, not an optimization guarantee. You still need to manually optimize.

**Q: Will this affect performance?**
A: Fewer bones = better performance! Optimized models render faster and use less memory.

---

## Need Help?

If you're stuck:

1. **Check logcat** for exact bone count:
   ```bash
   adb logcat | grep "VrmLoader"
   ```

2. **Share the error** on GitHub Issues:
   - Include bone count from log
   - Mention where you got the avatar
   - Describe what you've tried

3. **VRoid Community**:
   - VRoid Discord: https://discord.gg/vroid
   - VRoid Hub forums
   - Reddit: r/VRoid

---

**Remember**: This is not a bug in Calmify - it's a fundamental hardware limitation of mobile 3D rendering. All mobile VR/avatar apps have the same limit.

The good news: Once optimized, your avatar will load faster and perform better! 🚀
