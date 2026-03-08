# AMICA VRM Avatar System - Architecture Guide

## Overview

AMICA is a TypeScript/Next.js web application with advanced 3D avatar animation using VRM format.
Built with Three.js 0.154.0 and @pixiv/three-vrm 2.0.7.

## Core Modules

### 1. Viewer (viewer.ts)
Main 3D rendering engine.

Features:
- WebGL renderer with alpha blending
- Perspective camera at (0, 1.3, 1.5)
- DirectionalLight (0.6) + AmbientLight (0.4)
- OrbitControls for interaction
- requestAnimationFrame loop

Methods:
- setup(canvas): Initialize renderer
- loadVrm(url): Load VRM model
- update(): Main render loop
- resetCamera(): Center on head

### 2. Model (model.ts)
Manages VRM, animations, lip-sync, emotions.

Components:
- VRM instance
- AnimationMixer
- EmoteController
- LipSync

Methods:
- loadVRM(url): Load VRM file
- loadAnimation(clip): Set idle animation
- playAnimation(clip, name): Play one-shot with fade
- speak(buffer, screenplay): Audio + lip-sync
- update(delta): Update all systems

### 3. EmoteController System

Structure:
- EmoteController (facade)
  - ExpressionController (orchestrator)
    - AutoBlink (timing system)
    - AutoLookAt (gaze target)
    - VRMExpressionManager (blend shapes)

ExpressionController:
- Manages active emotion
- Prevents overlapping
- Coordinates lip-sync
- Waits for blink before emotion

Emotion Flow:
1. Check if different from current
2. Reset previous emotion
3. Wait for eyes to open
4. Apply at proper intensity
5. Blend with lip-sync

AutoBlink:
- Blink close: 120ms
- Eyes open: 5 seconds
- blink = 0: open, blink = 1: closed

### 4. VRMLookAtSmoother (Advanced Gaze)

Saccade Parameters:
- Min interval: 0.5 seconds
- Trigger chance: 5% per frame
- Movement range: 5 degrees

Features:
- Animation-based look-at
- User look-at blending
- Head rotation (40% of eyes)
- Natural eye jitter

### 5. Lip Sync System

Audio Processing:
1. Analyze 2048 audio samples
2. Find peak volume
3. Apply sigmoid smoothing
4. Apply deadzone

Connection:
- BufferSource -> speakers (audio destination)
- BufferSource -> analyser (volume analysis)

Weight Adjustment:
- Neutral: volume * 0.5
- Emotional: volume * 0.25

### 6. VRM Animation

VRMAnimation Structure:
- duration
- restHipsPosition
- humanoidTracks (rotation + translation)
- expressionTracks (blendshapes)
- lookAtTrack

Load Formats:
- VRMA: GLTFLoader + VRMAnimationLoaderPlugin
- Mixamo FBX: FBXLoader + bone mapping

### 7. Emotion System

14 Emotions:
neutral, happy, angry, sad, relaxed, surprised, shy, jealous, bored, serious, suspicious, victory, sleep, love

Intensity Levels:
- Standard emotions: 1.0
- Surprised: 0.5
- Other custom: 0.5

Parsing:
Text: "[happy] Great! [surprised] Really?"
Extract emotion tags, map to system format, remove from text

## Key Files

Viewer/Model:
- viewer.ts (229 lines)
- model.ts (207 lines)

Expression/Emotion:
- expressionController.ts (98 lines)
- emoteController.ts (21 lines)
- autoBlink.ts (52 lines)
- autoLookAt.ts (18 lines)

Audio/LipSync:
- lipSync.ts (42 lines)

Animation:
- VRMAnimation.ts (129 lines)
- VRMLookAtSmoother.ts (174 lines)
- loadVRMAnimation.ts (10 lines)
- loadMixamoAnimation.ts (76 lines)

## Performance

1. Frustum culling: Disabled
2. Pixel ratio: device.devicePixelRatio
3. Fade duration: 0.5 seconds
4. Saccade throttle: 0.5s minimum
5. Audio FFT: 2048 samples
6. Blink delay: max 120ms before emotion

## Android Equivalents

3D Rendering: Filament or OpenGL
Audio: ExoPlayer + TarsosDSP
Async: Kotlin Coroutines
State Machines: Custom implementations
Blendshapes: Shader-based or custom
