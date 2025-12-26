# AAA Audio Engine - Professional Grade Audio Playback

## Overview

Il AAA Audio Engine è un sistema di riproduzione audio di livello professionale, ispirato alle architetture utilizzate da:

- **Discord** - 4+ miliardi di minuti vocali al giorno
- **Google Oboe/AAudio** - Latenza <10ms su Android
- **WebRTC NetEQ** - Jitter buffer adattivo
- **Signal** - Adaptive audio processing

## Architettura

```
┌──────────────────────────────────────────────────────────────┐
│                   AAA AUDIO ENGINE                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────┐    ┌──────────────────┐    ┌─────────────┐ │
│  │  WebSocket  │───▶│  ADAPTIVE JITTER │───▶│  LOCK-FREE  │ │
│  │  Receiver   │    │     BUFFER       │    │ RING BUFFER │ │
│  └─────────────┘    │  (50-300ms)      │    │  (SPSC)     │ │
│                     │  - PLC Engine    │    └──────┬──────┘ │
│                     │  - Gap Detection │           │        │
│                     └──────────────────┘           ▼        │
│                                                             │
│  ┌─────────────┐    ┌──────────────────┐    ┌─────────────┐ │
│  │  AudioTrack │◀───│   PLAYBACK       │◀───│  HIGH       │ │
│  │  (24kHz     │    │   THREAD         │    │  PRIORITY   │ │
│  │   16-bit)   │    │  URGENT_AUDIO    │    │  THREAD     │ │
│  └─────────────┘    └──────────────────┘    └─────────────┘ │
│                                                              │
└──────────────────────────────────────────────────────────────┘
```

## Componenti

### 1. LockFreeAudioRingBuffer
Buffer circolare lock-free Single Producer Single Consumer (SPSC).

**Features:**
- Zero allocazioni durante read/write
- Thread-safe senza lock (atomic operations)
- Memory barriers impliciti
- Statistiche real-time

**Usage:**
```kotlin
val buffer = LockFreeAudioRingBuffer(96000) // 2 sec @ 24kHz
buffer.write(audioData)
buffer.read(outputBuffer)
```

### 2. AdaptiveJitterBuffer
Jitter buffer adattivo stile WebRTC NetEQ.

**Features:**
- Dimensione dinamica 50-300ms
- Compensazione clock skew
- Riordinamento pacchetti
- Statistiche jitter real-time

**Usage:**
```kotlin
val jitterBuffer = AdaptiveJitterBuffer(sampleRate = 24000)
jitterBuffer.pushPacket(audioData, sequenceNumber)
val packet = jitterBuffer.popPacket()
```

### 3. PacketLossConcealmentEngine
Engine PLC per mascherare perdite di pacchetti.

**Tecniche:**
- Zero Fill (fallback)
- Waveform Repetition
- Waveform Extrapolation (pitch-based)
- Interpolation
- Comfort Noise Generation

**Usage:**
```kotlin
val plc = PacketLossConcealmentEngine(sampleRate = 24000)
plc.processGoodFrame(goodFrame)
val concealed = plc.concealLostFrame()
```

### 4. HighPriorityAudioThread
Thread dedicato per playback con priorità URGENT_AUDIO.

**Features:**
- THREAD_PRIORITY_URGENT_AUDIO (-19)
- Zero allocazioni nel loop
- Underrun detection e recovery
- Callbacks per eventi

### 5. AAAudioEngine
Orchestratore principale che integra tutti i componenti.

**Usage:**
```kotlin
val engine = AAAudioEngine(context)
engine.initialize(config)
engine.queueAudio(base64Audio)
engine.startPlayback()
```

## Performance Targets

| Metrica | Target AAA |
|---------|------------|
| Latenza E2E | < 150ms |
| Jitter Tolerance | < 20ms |
| Packet Loss Recovery | > 99% |
| Underrun Rate | < 0.01% |
| Buffer Warmup | < 100ms |

## Configurazione

```kotlin
val config = AAAudioEngine.EngineConfig(
    sampleRate = 24000,
    preBufferTargetMs = 100,
    minJitterBufferMs = 50,
    maxJitterBufferMs = 300,
    enablePLC = true,
    enableAdaptiveJitter = true
)
```

## Metriche

```kotlin
val report = engine.getMetricsReport()
// Output:
// ═══════════════════════════════════════
//        AAA AUDIO ENGINE METRICS
// ═══════════════════════════════════════
// 📊 HEALTH: EXCELLENT (95%)
// ⏱️ LATENZA
//    End-to-End: 120ms
//    Jitter: 15ms
// 📦 BUFFER
//    Ring Buffer: 60% (80ms)
//    Jitter Buffer: 100ms / 100ms
// ...
```

## Toggle AAA Engine

Per disabilitare l'AAA Engine e usare il sistema legacy:

```kotlin
// In GeminiLiveAudioManager.kt
private const val USE_AAA_ENGINE = false
```

## Author

Jarvis AI Assistant - AAA Audio Engine
