# Piano di Sviluppo: Sistema di Animazione VRM Avatar

## Panoramica del Progetto

Questo documento fornisce un piano step-by-step per implementare un sistema completo di animazione per avatar VRM su Android utilizzando Filament. Il progetto è strutturato in fasi incrementali, ciascuna verificabile dall'utente prima di procedere.

### Stato Attuale del Progetto
- ✅ Avatar 3D visualizzato in uno spazio nero
- ✅ Avatar in T-pose (posizione default VRM)
- ⚠️ Emozioni funzionanti in modo inconsistente
- ❌ Nessuna animazione idle
- ❌ Nessun blinking naturale
- ❌ Nessun lip-sync

### Obiettivi Finali
1. **Idle Loop System** - Respirazione, micro-movimenti, postura rilassata
2. **Animazioni Base** - Wave (saluto), Dance (danza)
3. **Natural Eye Blinking** - Ammiccamento realistico con timing randomico
4. **Advanced Lip-Sync** - Sincronizzazione labiale basata su testo/fonemi

---

## FASE 1: Stabilizzazione e Fix Blend Shapes
**Durata stimata: 2-3 ore**

### 1.1 Diagnosi del Sistema Attuale

**File da analizzare:**
- `FilamentRenderer.kt` - linee 402-480 (blend shape mapping)
- `VrmBlendShapeController.kt` - logica di interpolazione
- `HumanoidViewModel.kt` - gestione stato e mapping emozioni

**Azioni:**
```kotlin
// Aggiungere logging dettagliato in FilamentRenderer.kt
fun updateBlendShapes(blendShapes: Map<String, Float>) {
    Log.d(tag, "=== BLEND SHAPES UPDATE ===")
    Log.d(tag, "Input weights: $blendShapes")
    Log.d(tag, "Available mappings: ${blendShapeMapping.keys}")
    
    blendShapes.forEach { (name, weight) ->
        val normalizedName = name.lowercase()
        val targets = blendShapeMapping[normalizedName]
        Log.d(tag, "Looking for '$normalizedName': found=${targets != null}, targets=${targets?.size ?: 0}")
    }
}
```

### 1.2 Fix VRM Blend Shape Preset Mapping

**Problema:** Il mapping attuale potrebbe non corrispondere ai preset VRM standard.

**Soluzione - Creare file:** `VrmBlendShapePresets.kt`
```kotlin
package com.lifo.humanoid.data.vrm

/**
 * VRM 0.x Standard Blend Shape Presets
 * Riferimento: https://github.com/vrm-c/vrm-specification
 */
object VrmBlendShapePresets {
    
    // Mapping Emotion -> VRM Preset Names (lowercase per compatibilità)
    val EMOTION_TO_VRM = mapOf(
        "happy" to listOf("joy", "fun", "happy", "smile"),
        "sad" to listOf("sorrow", "sad"),
        "angry" to listOf("angry"),
        "surprised" to listOf("surprised"),
        "neutral" to listOf("neutral")
    )
    
    // VRM standard viseme presets
    val VISEME_PRESETS = listOf("a", "i", "u", "e", "o")
    
    // VRM blink presets
    val BLINK_PRESETS = mapOf(
        "both" to "blink",
        "left" to "blink_l",
        "right" to "blink_r"
    )
    
    /**
     * Cerca il primo preset disponibile tra le alternative
     */
    fun findAvailablePreset(
        availablePresets: Set<String>,
        candidates: List<String>
    ): String? {
        return candidates.firstOrNull { it in availablePresets }
    }
}
```

### 1.3 Miglioramento Blend Shape Controller

**Modifiche a** `VrmBlendShapeController.kt`:
```kotlin
// Aggiungere variabile per tracciare i preset disponibili
private var availablePresets: Set<String> = emptySet()

fun setAvailablePresets(presets: Set<String>) {
    availablePresets = presets.map { it.lowercase() }.toSet()
    Log.d("BlendShapeController", "Available presets: $availablePresets")
}

// Migliorare interpolazione con easing
private fun lerp(start: Float, end: Float, alpha: Float): Float {
    // Smooth step per transizioni più naturali
    val t = alpha * alpha * (3f - 2f * alpha) // Smoothstep
    return start + (end - start) * t
}
```

### ✅ Checkpoint 1.1
**Verifica utente:** 
- [ ] Le emozioni funzionano consistentemente?
- [ ] I log mostrano i preset VRM riconosciuti?
- [ ] Le transizioni tra emozioni sono fluide?

---

## FASE 2: Natural Eye Blinking System
**Durata stimata: 2-3 ore**

### 2.1 Teoria del Blinking Naturale

Il blinking umano segue pattern specifici:
- **Frequenza media:** 15-20 blink/minuto
- **Durata:** 100-400ms per blink
- **Pattern:** Clustering (blink tendono a raggrupparsi)
- **Variazione:** Intervalli irregolari tra 2-10 secondi

### 2.2 Creare BlinkController.kt

**Nuovo file:** `com/lifo/humanoid/animation/BlinkController.kt`
```kotlin
package com.lifo.humanoid.animation

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * Controller per il blinking naturale degli occhi.
 * Implementa pattern realistici basati su studi sul blinking umano.
 */
class BlinkController {
    
    private val _blinkWeight = MutableStateFlow(0f)
    val blinkWeight: StateFlow<Float> = _blinkWeight
    
    private var isRunning = false
    private var blinkJob: Job? = null
    
    // Configurazione blinking
    private val config = BlinkConfig()
    
    data class BlinkConfig(
        val minIntervalMs: Long = 2000L,    // Min 2 secondi tra blink
        val maxIntervalMs: Long = 8000L,    // Max 8 secondi tra blink
        val blinkDurationMs: Long = 150L,   // Durata di un blink completo
        val doubleBinkChance: Float = 0.15f, // 15% chance di doppio blink
        val halfBlinkChance: Float = 0.1f    // 10% chance di mezzo blink
    )
    
    /**
     * Avvia il loop di blinking naturale
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true
        
        blinkJob = scope.launch {
            while (isActive && isRunning) {
                // Attendi intervallo random
                val interval = Random.nextLong(config.minIntervalMs, config.maxIntervalMs)
                delay(interval)
                
                // Esegui blink
                performBlink()
                
                // Possibile doppio blink
                if (Random.nextFloat() < config.doubleBinkChance) {
                    delay(100L)
                    performBlink()
                }
            }
        }
    }
    
    /**
     * Ferma il blinking
     */
    fun stop() {
        isRunning = false
        blinkJob?.cancel()
        blinkJob = null
        _blinkWeight.value = 0f
    }
    
    /**
     * Esegue un singolo blink con animazione fluida
     */
    private suspend fun performBlink() {
        val duration = config.blinkDurationMs
        val halfDuration = duration / 2
        
        // Determina se è un mezzo blink
        val maxWeight = if (Random.nextFloat() < config.halfBlinkChance) 0.5f else 1f
        
        // Chiudi occhi (ease-in)
        animateWeight(0f, maxWeight, halfDuration)
        
        // Apri occhi (ease-out, leggermente più veloce)
        animateWeight(maxWeight, 0f, (halfDuration * 0.8).toLong())
    }
    
    /**
     * Animazione fluida del peso con easing
     */
    private suspend fun animateWeight(from: Float, to: Float, durationMs: Long) {
        val steps = (durationMs / 16).toInt() // ~60fps
        val stepDelay = durationMs / steps
        
        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            // Ease in-out cubic
            val eased = if (progress < 0.5f) {
                4 * progress * progress * progress
            } else {
                1 - (-2 * progress + 2).let { it * it * it } / 2
            }
            _blinkWeight.value = from + (to - from) * eased
            delay(stepDelay)
        }
        _blinkWeight.value = to
    }
    
    /**
     * Forza un blink immediato (utile per reazioni)
     */
    suspend fun triggerBlink() {
        performBlink()
    }
}
```

### 2.3 Integrazione nel ViewModel

**Modifiche a** `HumanoidViewModel.kt`:
```kotlin
// Aggiungere import
import com.lifo.humanoid.animation.BlinkController

// Aggiungere proprietà
private val blinkController = BlinkController()

init {
    // ... codice esistente ...
    
    // Avvia blinking
    viewModelScope.launch {
        blinkController.start(this)
    }
    
    // Osserva blink weight e aggiorna blend shapes
    viewModelScope.launch {
        blinkController.blinkWeight.collect { weight ->
            if (weight > 0.001f) {
                blendShapeController.setWeight("blink", weight)
            }
        }
    }
}

override fun onCleared() {
    super.onCleared()
    blinkController.stop()
}
```

### ✅ Checkpoint 2.1
**Verifica utente:**
- [ ] L'avatar sbatte le palpebre automaticamente?
- [ ] Il timing sembra naturale e non meccanico?
- [ ] Occasionalmente fa doppi blink?
- [ ] Le emozioni non interferiscono con il blinking?

---

## FASE 3: Idle Animation System - Breathing & Micro-movements
**Durata stimata: 4-5 ore**

### 3.1 Approccio Procedurale per Idle

Dato che VRM non include animazioni skeletal di default, implementeremo un sistema procedurale che:
1. Modifica la scala dei bone `chest`/`upperChest` per simulare respirazione
2. Applica micro-rotazioni alla testa
3. Lieve swaying del corpo

**NOTA IMPORTANTE:** Filament supporta l'Animator di glTF, ma i VRM tipicamente non hanno animazioni embedded. Dobbiamo lavorare a livello di TransformManager.

### 3.2 Parsing VRM Humanoid Bones

**Nuovo file:** `VrmHumanoidBoneMapper.kt`
```kotlin
package com.lifo.humanoid.data.vrm

import com.google.android.filament.gltfio.FilamentAsset
import com.google.gson.JsonObject
import android.util.Log

/**
 * Mappa i bone humanoid VRM agli entity di Filament.
 * VRM definisce una struttura standard di bone per avatar umanoidi.
 */
class VrmHumanoidBoneMapper {
    
    private val tag = "VrmHumanoidBoneMapper"
    
    // VRM Humanoid bone names (VRM 0.x spec)
    enum class HumanoidBone {
        // Torso
        HIPS, SPINE, CHEST, UPPER_CHEST, NECK,
        
        // Head
        HEAD, LEFT_EYE, RIGHT_EYE, JAW,
        
        // Arms
        LEFT_SHOULDER, LEFT_UPPER_ARM, LEFT_LOWER_ARM, LEFT_HAND,
        RIGHT_SHOULDER, RIGHT_UPPER_ARM, RIGHT_LOWER_ARM, RIGHT_HAND,
        
        // Legs
        LEFT_UPPER_LEG, LEFT_LOWER_LEG, LEFT_FOOT, LEFT_TOES,
        RIGHT_UPPER_LEG, RIGHT_LOWER_LEG, RIGHT_FOOT, RIGHT_TOES,
        
        // Fingers (opzionali)
        LEFT_THUMB_PROXIMAL, LEFT_INDEX_PROXIMAL, // ... etc
        RIGHT_THUMB_PROXIMAL, RIGHT_INDEX_PROXIMAL // ... etc
    }
    
    // Mapping: HumanoidBone -> Filament entity
    private val boneEntityMap = mutableMapOf<HumanoidBone, Int>()
    
    /**
     * Costruisce il mapping dai dati VRM humanoid extension
     */
    fun buildMapping(
        asset: FilamentAsset,
        vrmHumanoidData: JsonObject
    ): Map<HumanoidBone, Int> {
        boneEntityMap.clear()
        
        val humanBones = vrmHumanoidData.getAsJsonArray("humanBones") ?: return emptyMap()
        
        humanBones.forEach { boneElement ->
            val boneObj = boneElement.asJsonObject
            val boneName = boneObj.get("bone")?.asString ?: return@forEach
            val nodeIndex = boneObj.get("node")?.asInt ?: return@forEach
            
            try {
                val humanoidBone = HumanoidBone.valueOf(
                    boneName.uppercase().replace(" ", "_")
                )
                
                // Trova l'entity corrispondente al node index
                val entity = asset.entities.getOrNull(nodeIndex)
                if (entity != null) {
                    boneEntityMap[humanoidBone] = entity
                    Log.d(tag, "Mapped $boneName -> entity $entity")
                }
            } catch (e: IllegalArgumentException) {
                Log.w(tag, "Unknown humanoid bone: $boneName")
            }
        }
        
        Log.d(tag, "Total bones mapped: ${boneEntityMap.size}")
        return boneEntityMap.toMap()
    }
    
    fun getBoneEntity(bone: HumanoidBone): Int? = boneEntityMap[bone]
}
```

### 3.3 Idle Animation Controller

**Nuovo file:** `IdleAnimationController.kt`
```kotlin
package com.lifo.humanoid.animation

import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper.HumanoidBone
import kotlinx.coroutines.*
import kotlin.math.sin
import kotlin.math.cos

/**
 * Controller per animazioni idle procedurali.
 * Simula respirazione, micro-movimenti e sway naturale.
 */
class IdleAnimationController(
    private val engine: Engine,
    private val boneMapper: VrmHumanoidBoneMapper
) {
    
    private var isRunning = false
    private var animationJob: Job? = null
    private var elapsedTime = 0f
    
    // Configurazione respirazione
    private val breathConfig = BreathConfig()
    
    data class BreathConfig(
        val cycleSeconds: Float = 4f,           // Ciclo respiratorio completo
        val chestScaleAmount: Float = 0.02f,    // Quanto si espande il petto
        val shoulderRiseAmount: Float = 0.005f  // Quanto salgono le spalle
    )
    
    // Configurazione micro-movimenti
    private val microMovementConfig = MicroMovementConfig()
    
    data class MicroMovementConfig(
        val headSwayAmplitude: Float = 0.02f,   // Radianti
        val headSwayFrequency: Float = 0.3f,    // Hz
        val bodySwayAmplitude: Float = 0.008f,
        val bodySwayFrequency: Float = 0.15f
    )
    
    /**
     * Avvia le animazioni idle
     */
    fun start(scope: CoroutineScope) {
        if (isRunning) return
        isRunning = true
        elapsedTime = 0f
        
        animationJob = scope.launch {
            val deltaTime = 1f / 60f // Target 60fps
            
            while (isActive && isRunning) {
                update(deltaTime)
                delay((deltaTime * 1000).toLong())
            }
        }
    }
    
    /**
     * Ferma le animazioni idle
     */
    fun stop() {
        isRunning = false
        animationJob?.cancel()
        animationJob = null
        resetToDefaultPose()
    }
    
    /**
     * Aggiorna un frame di animazione
     */
    private fun update(deltaTime: Float) {
        elapsedTime += deltaTime
        
        val tm = engine.transformManager
        
        // 1. Animazione respirazione
        applyBreathing(tm)
        
        // 2. Micro-movimenti testa
        applyHeadSway(tm)
        
        // 3. Sway corpo (opzionale, più sottile)
        applyBodySway(tm)
    }
    
    /**
     * Applica animazione di respirazione tramite scaling del chest
     */
    private fun applyBreathing(tm: TransformManager) {
        val chestEntity = boneMapper.getBoneEntity(HumanoidBone.CHEST) ?: return
        val upperChestEntity = boneMapper.getBoneEntity(HumanoidBone.UPPER_CHEST)
        
        // Calcola fase respirazione (sinusoide)
        val breathPhase = (elapsedTime / breathConfig.cycleSeconds) * 2f * Math.PI.toFloat()
        val breathFactor = (sin(breathPhase) + 1f) / 2f // 0-1
        
        // Scala il chest
        val scale = 1f + breathFactor * breathConfig.chestScaleAmount
        
        applyScaleToEntity(tm, chestEntity, scale)
        upperChestEntity?.let { applyScaleToEntity(tm, it, scale * 0.5f) }
    }
    
    /**
     * Applica micro-movimento oscillatorio alla testa
     */
    private fun applyHeadSway(tm: TransformManager) {
        val headEntity = boneMapper.getBoneEntity(HumanoidBone.HEAD) ?: return
        val neckEntity = boneMapper.getBoneEntity(HumanoidBone.NECK)
        
        // Movimento lento e quasi impercettibile
        val swayX = sin(elapsedTime * microMovementConfig.headSwayFrequency * 2 * Math.PI.toFloat()) *
                    microMovementConfig.headSwayAmplitude
        val swayZ = cos(elapsedTime * microMovementConfig.headSwayFrequency * 1.3f * 2 * Math.PI.toFloat()) *
                    microMovementConfig.headSwayAmplitude * 0.5f
        
        applyRotationToEntity(tm, headEntity, swayX, 0f, swayZ)
        neckEntity?.let { applyRotationToEntity(tm, it, swayX * 0.3f, 0f, swayZ * 0.3f) }
    }
    
    /**
     * Applica leggero sway al corpo (spina dorsale)
     */
    private fun applyBodySway(tm: TransformManager) {
        val spineEntity = boneMapper.getBoneEntity(HumanoidBone.SPINE) ?: return
        
        val swayZ = sin(elapsedTime * microMovementConfig.bodySwayFrequency * 2 * Math.PI.toFloat()) *
                    microMovementConfig.bodySwayAmplitude
        
        applyRotationToEntity(tm, spineEntity, 0f, 0f, swayZ)
    }
    
    /**
     * Applica scala uniforme a un entity
     */
    private fun applyScaleToEntity(tm: TransformManager, entity: Int, scale: Float) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return
        
        val transform = FloatArray(16)
        tm.getTransform(instance, transform)
        
        // Modifica solo la scala (diagonale principale)
        transform[0] = scale  // scaleX
        transform[5] = scale  // scaleY
        transform[10] = scale // scaleZ
        
        tm.setTransform(instance, transform)
    }
    
    /**
     * Applica rotazione a un entity (angoli in radianti)
     */
    private fun applyRotationToEntity(
        tm: TransformManager,
        entity: Int,
        rotX: Float,
        rotY: Float,
        rotZ: Float
    ) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return
        
        val transform = FloatArray(16)
        tm.getTransform(instance, transform)
        
        // Costruisci matrice di rotazione incrementale
        val cosX = cos(rotX)
        val sinX = sin(rotX)
        val cosY = cos(rotY)
        val sinY = sin(rotY)
        val cosZ = cos(rotZ)
        val sinZ = sin(rotZ)
        
        // Rotazione combinata (ordine ZYX)
        val rotMatrix = FloatArray(16).apply {
            this[0] = cosY * cosZ
            this[1] = cosY * sinZ
            this[2] = -sinY
            this[4] = sinX * sinY * cosZ - cosX * sinZ
            this[5] = sinX * sinY * sinZ + cosX * cosZ
            this[6] = sinX * cosY
            this[8] = cosX * sinY * cosZ + sinX * sinZ
            this[9] = cosX * sinY * sinZ - sinX * cosZ
            this[10] = cosX * cosY
            this[15] = 1f
        }
        
        // Applica rotazione mantenendo posizione e scala
        // (implementazione semplificata - in produzione usare quaternioni)
        tm.setTransform(instance, multiplyMatrices(transform, rotMatrix))
    }
    
    private fun multiplyMatrices(a: FloatArray, b: FloatArray): FloatArray {
        // Implementazione moltiplicazione matrici 4x4
        val result = FloatArray(16)
        for (i in 0..3) {
            for (j in 0..3) {
                result[i * 4 + j] = 
                    a[i * 4 + 0] * b[0 * 4 + j] +
                    a[i * 4 + 1] * b[1 * 4 + j] +
                    a[i * 4 + 2] * b[2 * 4 + j] +
                    a[i * 4 + 3] * b[3 * 4 + j]
            }
        }
        return result
    }
    
    /**
     * Resetta alla posa di default
     */
    private fun resetToDefaultPose() {
        // TODO: Salvare e ripristinare le trasformazioni originali
    }
}
```

### 3.4 Fix della T-Pose

**Problema:** I modelli VRM arrivano in T-Pose con braccia distese.

**Soluzione - Creare** `IdlePoseController.kt`:
```kotlin
package com.lifo.humanoid.animation

import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper.HumanoidBone
import kotlin.math.PI

/**
 * Applica una posa idle naturale alla T-Pose iniziale del VRM.
 * Basato sull'approccio di Animaze/VRoid.
 */
class IdlePoseController(
    private val engine: Engine,
    private val boneMapper: VrmHumanoidBoneMapper
) {
    
    companion object {
        // Angoli in gradi, convertiti a radianti
        const val UPPER_ARM_ROTATION_Z = 70f * (PI.toFloat() / 180f)
        const val LOWER_ARM_ROTATION_Z = 10f * (PI.toFloat() / 180f)
        const val HAND_ROTATION_X = -5f * (PI.toFloat() / 180f)
    }
    
    /**
     * Applica la posa idle naturale (braccia abbassate)
     */
    fun applyIdlePose() {
        val tm = engine.transformManager
        
        // Ruota le braccia verso il basso
        // Upper arms: ruota 70° attorno all'asse Z
        boneMapper.getBoneEntity(HumanoidBone.LEFT_UPPER_ARM)?.let { entity ->
            rotateAroundZ(tm, entity, UPPER_ARM_ROTATION_Z)
        }
        boneMapper.getBoneEntity(HumanoidBone.RIGHT_UPPER_ARM)?.let { entity ->
            rotateAroundZ(tm, entity, -UPPER_ARM_ROTATION_Z) // Negativo per lato destro
        }
        
        // Lower arms: ruota 10° addizionali
        boneMapper.getBoneEntity(HumanoidBone.LEFT_LOWER_ARM)?.let { entity ->
            rotateAroundZ(tm, entity, LOWER_ARM_ROTATION_Z)
        }
        boneMapper.getBoneEntity(HumanoidBone.RIGHT_LOWER_ARM)?.let { entity ->
            rotateAroundZ(tm, entity, -LOWER_ARM_ROTATION_Z)
        }
        
        // Mani: leggera rotazione per naturalezza
        boneMapper.getBoneEntity(HumanoidBone.LEFT_HAND)?.let { entity ->
            rotateAroundX(tm, entity, HAND_ROTATION_X)
        }
        boneMapper.getBoneEntity(HumanoidBone.RIGHT_HAND)?.let { entity ->
            rotateAroundX(tm, entity, HAND_ROTATION_X)
        }
    }
    
    private fun rotateAroundZ(tm: TransformManager, entity: Int, angle: Float) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return
        
        val transform = FloatArray(16)
        tm.getTransform(instance, transform)
        
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        
        // Rotazione Z
        val rotZ = floatArrayOf(
            cos, sin, 0f, 0f,
            -sin, cos, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f
        )
        
        // Moltiplica per applicare rotazione
        val result = FloatArray(16)
        multiplyMatrix4(transform, rotZ, result)
        tm.setTransform(instance, result)
    }
    
    private fun rotateAroundX(tm: TransformManager, entity: Int, angle: Float) {
        val instance = tm.getInstance(entity)
        if (instance == 0) return
        
        val transform = FloatArray(16)
        tm.getTransform(instance, transform)
        
        val cos = kotlin.math.cos(angle)
        val sin = kotlin.math.sin(angle)
        
        // Rotazione X
        val rotX = floatArrayOf(
            1f, 0f, 0f, 0f,
            0f, cos, sin, 0f,
            0f, -sin, cos, 0f,
            0f, 0f, 0f, 1f
        )
        
        val result = FloatArray(16)
        multiplyMatrix4(transform, rotX, result)
        tm.setTransform(instance, result)
    }
    
    private fun multiplyMatrix4(a: FloatArray, b: FloatArray, result: FloatArray) {
        for (i in 0..3) {
            for (j in 0..3) {
                result[j * 4 + i] = 
                    a[0 * 4 + i] * b[j * 4 + 0] +
                    a[1 * 4 + i] * b[j * 4 + 1] +
                    a[2 * 4 + i] * b[j * 4 + 2] +
                    a[3 * 4 + i] * b[j * 4 + 3]
            }
        }
    }
}
```

### ✅ Checkpoint 3.1
**Verifica utente:**
- [ ] L'avatar respira visibilmente?
- [ ] La testa ha leggeri micro-movimenti?
- [ ] Le braccia sono in posizione naturale (non T-pose)?
- [ ] L'animazione è fluida senza scatti?

---

## FASE 4: Animazioni Gestuali (Wave, Dance)
**Durata stimata: 3-4 ore**

### 4.1 Sistema di Animazione a Keyframe

Poiché i VRM non contengono animazioni embedded, implementeremo un sistema procedurale di keyframe.

**Nuovo file:** `KeyframeAnimator.kt`
```kotlin
package com.lifo.humanoid.animation

import kotlin.math.sin
import kotlin.math.cos

/**
 * Rappresenta un keyframe per una bone animation
 */
data class BoneKeyframe(
    val timeMs: Long,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val rotationZ: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val scaleZ: Float = 1f
)

/**
 * Definisce una animazione come sequenza di keyframes per bone
 */
data class Animation(
    val name: String,
    val durationMs: Long,
    val isLooping: Boolean = false,
    val boneKeyframes: Map<VrmHumanoidBoneMapper.HumanoidBone, List<BoneKeyframe>>
)

/**
 * Libreria di animazioni predefinite
 */
object AnimationLibrary {
    
    // Angoli helper
    private fun deg(degrees: Float) = degrees * (Math.PI.toFloat() / 180f)
    
    /**
     * Animazione Wave (saluto)
     */
    val WAVE = Animation(
        name = "wave",
        durationMs = 2000L,
        isLooping = false,
        boneKeyframes = mapOf(
            VrmHumanoidBoneMapper.HumanoidBone.RIGHT_UPPER_ARM to listOf(
                BoneKeyframe(0, rotationZ = deg(-70f)),           // Posa iniziale
                BoneKeyframe(300, rotationZ = deg(-120f)),        // Alza braccio
                BoneKeyframe(500, rotationZ = deg(-150f)),        // Braccio in alto
                BoneKeyframe(1500, rotationZ = deg(-150f)),       // Mantieni
                BoneKeyframe(2000, rotationZ = deg(-70f))         // Torna giù
            ),
            VrmHumanoidBoneMapper.HumanoidBone.RIGHT_LOWER_ARM to listOf(
                BoneKeyframe(0, rotationZ = deg(-10f)),
                BoneKeyframe(300, rotationZ = deg(-45f)),
                BoneKeyframe(500, rotationZ = deg(-90f)),
                // Oscillazione mano (wave)
                BoneKeyframe(700, rotationZ = deg(-70f)),
                BoneKeyframe(900, rotationZ = deg(-110f)),
                BoneKeyframe(1100, rotationZ = deg(-70f)),
                BoneKeyframe(1300, rotationZ = deg(-110f)),
                BoneKeyframe(1500, rotationZ = deg(-90f)),
                BoneKeyframe(2000, rotationZ = deg(-10f))
            ),
            VrmHumanoidBoneMapper.HumanoidBone.RIGHT_HAND to listOf(
                BoneKeyframe(0),
                BoneKeyframe(500, rotationX = deg(10f)),
                // Wave motion della mano
                BoneKeyframe(700, rotationX = deg(20f)),
                BoneKeyframe(900, rotationX = deg(-10f)),
                BoneKeyframe(1100, rotationX = deg(20f)),
                BoneKeyframe(1300, rotationX = deg(-10f)),
                BoneKeyframe(1500, rotationX = deg(10f)),
                BoneKeyframe(2000)
            )
        )
    )
    
    /**
     * Animazione Dance semplice (side-to-side)
     */
    val DANCE_SIMPLE = Animation(
        name = "dance_simple",
        durationMs = 1600L,
        isLooping = true,
        boneKeyframes = mapOf(
            VrmHumanoidBoneMapper.HumanoidBone.HIPS to listOf(
                BoneKeyframe(0, rotationY = 0f),
                BoneKeyframe(400, rotationY = deg(10f)),
                BoneKeyframe(800, rotationY = 0f),
                BoneKeyframe(1200, rotationY = deg(-10f)),
                BoneKeyframe(1600, rotationY = 0f)
            ),
            VrmHumanoidBoneMapper.HumanoidBone.SPINE to listOf(
                BoneKeyframe(0, rotationZ = 0f),
                BoneKeyframe(400, rotationZ = deg(-5f)),
                BoneKeyframe(800, rotationZ = 0f),
                BoneKeyframe(1200, rotationZ = deg(5f)),
                BoneKeyframe(1600, rotationZ = 0f)
            ),
            VrmHumanoidBoneMapper.HumanoidBone.LEFT_UPPER_ARM to listOf(
                BoneKeyframe(0, rotationZ = deg(70f)),
                BoneKeyframe(400, rotationZ = deg(45f)),
                BoneKeyframe(800, rotationZ = deg(70f)),
                BoneKeyframe(1200, rotationZ = deg(90f)),
                BoneKeyframe(1600, rotationZ = deg(70f))
            ),
            VrmHumanoidBoneMapper.HumanoidBone.RIGHT_UPPER_ARM to listOf(
                BoneKeyframe(0, rotationZ = deg(-70f)),
                BoneKeyframe(400, rotationZ = deg(-90f)),
                BoneKeyframe(800, rotationZ = deg(-70f)),
                BoneKeyframe(1200, rotationZ = deg(-45f)),
                BoneKeyframe(1600, rotationZ = deg(-70f))
            ),
            VrmHumanoidBoneMapper.HumanoidBone.HEAD to listOf(
                BoneKeyframe(0, rotationZ = 0f),
                BoneKeyframe(400, rotationZ = deg(8f)),
                BoneKeyframe(800, rotationZ = 0f),
                BoneKeyframe(1200, rotationZ = deg(-8f)),
                BoneKeyframe(1600, rotationZ = 0f)
            )
        )
    )
}
```

### 4.2 Animation Playback Controller

**Nuovo file:** `AnimationPlaybackController.kt`
```kotlin
package com.lifo.humanoid.animation

import com.google.android.filament.Engine
import com.google.android.filament.TransformManager
import com.lifo.humanoid.data.vrm.VrmHumanoidBoneMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gestisce la riproduzione delle animazioni keyframe
 */
class AnimationPlaybackController(
    private val engine: Engine,
    private val boneMapper: VrmHumanoidBoneMapper
) {
    
    private val _currentAnimation = MutableStateFlow<Animation?>(null)
    val currentAnimation: StateFlow<Animation?> = _currentAnimation
    
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying
    
    private var playbackJob: Job? = null
    private var elapsedTimeMs: Long = 0
    
    // Memorizza le trasformazioni originali per il reset
    private val originalTransforms = mutableMapOf<Int, FloatArray>()
    
    /**
     * Riproduce un'animazione
     */
    fun play(animation: Animation, scope: CoroutineScope) {
        stop()
        
        _currentAnimation.value = animation
        _isPlaying.value = true
        elapsedTimeMs = 0
        
        // Salva trasformazioni originali
        saveOriginalTransforms(animation)
        
        playbackJob = scope.launch {
            val frameTimeMs = 16L // ~60fps
            
            while (isActive && _isPlaying.value) {
                updateAnimation(animation, elapsedTimeMs)
                
                elapsedTimeMs += frameTimeMs
                
                // Gestione loop o fine
                if (elapsedTimeMs >= animation.durationMs) {
                    if (animation.isLooping) {
                        elapsedTimeMs = 0
                    } else {
                        stop()
                        break
                    }
                }
                
                delay(frameTimeMs)
            }
        }
    }
    
    /**
     * Ferma l'animazione corrente
     */
    fun stop() {
        playbackJob?.cancel()
        playbackJob = null
        _isPlaying.value = false
        _currentAnimation.value = null
        
        restoreOriginalTransforms()
    }
    
    /**
     * Aggiorna il frame corrente dell'animazione
     */
    private fun updateAnimation(animation: Animation, currentTimeMs: Long) {
        val tm = engine.transformManager
        
        animation.boneKeyframes.forEach { (bone, keyframes) ->
            val entity = boneMapper.getBoneEntity(bone) ?: return@forEach
            val instance = tm.getInstance(entity)
            if (instance == 0) return@forEach
            
            // Trova i keyframe precedente e successivo
            val (prevFrame, nextFrame, t) = findKeyframeInterval(keyframes, currentTimeMs)
            
            // Interpola tra i keyframe
            val interpolatedRotX = lerp(prevFrame.rotationX, nextFrame.rotationX, t)
            val interpolatedRotY = lerp(prevFrame.rotationY, nextFrame.rotationY, t)
            val interpolatedRotZ = lerp(prevFrame.rotationZ, nextFrame.rotationZ, t)
            
            // Applica rotazione
            applyRotation(tm, instance, interpolatedRotX, interpolatedRotY, interpolatedRotZ)
        }
    }
    
    /**
     * Trova l'intervallo di keyframe per il tempo corrente
     */
    private fun findKeyframeInterval(
        keyframes: List<BoneKeyframe>,
        currentTimeMs: Long
    ): Triple<BoneKeyframe, BoneKeyframe, Float> {
        // Trova il keyframe precedente e successivo
        var prevIndex = 0
        for (i in keyframes.indices) {
            if (keyframes[i].timeMs <= currentTimeMs) {
                prevIndex = i
            } else {
                break
            }
        }
        
        val nextIndex = minOf(prevIndex + 1, keyframes.lastIndex)
        val prevFrame = keyframes[prevIndex]
        val nextFrame = keyframes[nextIndex]
        
        // Calcola t (0-1) nell'intervallo
        val intervalDuration = nextFrame.timeMs - prevFrame.timeMs
        val t = if (intervalDuration > 0) {
            ((currentTimeMs - prevFrame.timeMs).toFloat() / intervalDuration).coerceIn(0f, 1f)
        } else {
            0f
        }
        
        return Triple(prevFrame, nextFrame, t)
    }
    
    /**
     * Linear interpolation con smoothstep
     */
    private fun lerp(a: Float, b: Float, t: Float): Float {
        // Smoothstep per transizioni più naturali
        val smoothT = t * t * (3f - 2f * t)
        return a + (b - a) * smoothT
    }
    
    private fun applyRotation(tm: TransformManager, instance: Int, rotX: Float, rotY: Float, rotZ: Float) {
        val original = originalTransforms[instance] ?: return
        
        // Costruisci matrice di rotazione da Euler angles (ordine ZYX)
        val cosX = kotlin.math.cos(rotX)
        val sinX = kotlin.math.sin(rotX)
        val cosY = kotlin.math.cos(rotY)
        val sinY = kotlin.math.sin(rotY)
        val cosZ = kotlin.math.cos(rotZ)
        val sinZ = kotlin.math.sin(rotZ)
        
        val rotation = FloatArray(16)
        rotation[0] = cosY * cosZ
        rotation[1] = cosY * sinZ
        rotation[2] = -sinY
        rotation[4] = sinX * sinY * cosZ - cosX * sinZ
        rotation[5] = sinX * sinY * sinZ + cosX * cosZ
        rotation[6] = sinX * cosY
        rotation[8] = cosX * sinY * cosZ + sinX * sinZ
        rotation[9] = cosX * sinY * sinZ - sinX * cosZ
        rotation[10] = cosX * cosY
        rotation[15] = 1f
        
        // Combina con trasformazione originale
        val result = FloatArray(16)
        multiplyMatrix4(original, rotation, result)
        tm.setTransform(instance, result)
    }
    
    private fun saveOriginalTransforms(animation: Animation) {
        val tm = engine.transformManager
        
        animation.boneKeyframes.keys.forEach { bone ->
            val entity = boneMapper.getBoneEntity(bone) ?: return@forEach
            val instance = tm.getInstance(entity)
            if (instance != 0) {
                val transform = FloatArray(16)
                tm.getTransform(instance, transform)
                originalTransforms[instance] = transform.copyOf()
            }
        }
    }
    
    private fun restoreOriginalTransforms() {
        val tm = engine.transformManager
        
        originalTransforms.forEach { (instance, transform) ->
            tm.setTransform(instance, transform)
        }
        originalTransforms.clear()
    }
    
    private fun multiplyMatrix4(a: FloatArray, b: FloatArray, result: FloatArray) {
        for (i in 0..3) {
            for (j in 0..3) {
                result[j * 4 + i] = 
                    a[0 * 4 + i] * b[j * 4 + 0] +
                    a[1 * 4 + i] * b[j * 4 + 1] +
                    a[2 * 4 + i] * b[j * 4 + 2] +
                    a[3 * 4 + i] * b[j * 4 + 3]
            }
        }
    }
}
```

### 4.3 Integrazione UI

**Modifiche a** `HumanoidScreen.kt` - Aggiungere bottoni per Wave e Dance:
```kotlin
// Nella sezione control panel, aggiungere:
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    FilledTonalButton(
        onClick = { viewModel.playAnimation(AnimationLibrary.WAVE) },
        modifier = Modifier.weight(1f)
    ) {
        Text("👋 Wave")
    }
    
    FilledTonalButton(
        onClick = { viewModel.toggleDance() },
        modifier = Modifier.weight(1f)
    ) {
        Text(if (avatarState.isDancing) "⏹️ Stop" else "💃 Dance")
    }
}
```

### ✅ Checkpoint 4.1
**Verifica utente:**
- [ ] L'animazione Wave funziona?
- [ ] La mano si muove fluidamente durante il saluto?
- [ ] L'animazione Dance loop correttamente?
- [ ] Fermare la dance resetta alla posa idle?

---

## FASE 5: Advanced Lip-Sync System
**Durata stimata: 5-6 ore**

### 5.1 Architettura Lip-Sync

Il sistema si compone di:
1. **Text-to-Phoneme Converter** - Converte testo in sequenza di fonemi
2. **Phoneme-to-Viseme Mapper** - Mappa fonemi ai visemi VRM
3. **Viseme Animation Controller** - Applica blend shapes con timing corretto

### 5.2 Phoneme Dictionary (CMU-based)

**Nuovo file:** `PhonemeConverter.kt`
```kotlin
package com.lifo.humanoid.lipsync

import android.content.Context
import android.util.Log

/**
 * Converte testo inglese in sequenza di fonemi.
 * Basato sul CMU Pronouncing Dictionary per parole conosciute,
 * con fallback rule-based per parole sconosciute.
 */
class PhonemeConverter(context: Context) {
    
    private val tag = "PhonemeConverter"
    
    // Dictionary: word -> list of phonemes (ARPAbet)
    private val dictionary = mutableMapOf<String, List<String>>()
    
    init {
        // Carica un subset essenziale del CMU dictionary
        loadEssentialDictionary()
    }
    
    /**
     * Converte una stringa di testo in sequenza di fonemi con timing
     */
    fun textToPhonemes(text: String, totalDurationMs: Long): List<PhonemeTiming> {
        val words = text.lowercase().split(Regex("[\\s,.!?;:]+")).filter { it.isNotBlank() }
        val allPhonemes = mutableListOf<String>()
        
        words.forEach { word ->
            val phonemes = wordToPhonemes(word)
            allPhonemes.addAll(phonemes)
            allPhonemes.add("SIL") // Pausa tra parole
        }
        
        // Rimuovi silenzio finale
        if (allPhonemes.lastOrNull() == "SIL") {
            allPhonemes.removeAt(allPhonemes.lastIndex)
        }
        
        // Calcola timing
        return calculateTiming(allPhonemes, totalDurationMs)
    }
    
    /**
     * Converte una singola parola in fonemi
     */
    fun wordToPhonemes(word: String): List<String> {
        val normalizedWord = word.lowercase().trim()
        
        // Prima cerca nel dictionary
        dictionary[normalizedWord]?.let { return it }
        
        // Fallback: regole fonetiche semplificate
        return applyPhoneticRules(normalizedWord)
    }
    
    /**
     * Calcola il timing per ogni fonema
     */
    private fun calculateTiming(phonemes: List<String>, totalDurationMs: Long): List<PhonemeTiming> {
        if (phonemes.isEmpty()) return emptyList()
        
        // Durata base per fonema (ms)
        val baseDuration = totalDurationMs / phonemes.size
        
        return phonemes.mapIndexed { index, phoneme ->
            PhonemeTiming(
                phoneme = phoneme,
                startTimeMs = index * baseDuration,
                durationMs = adjustDurationForPhoneme(phoneme, baseDuration)
            )
        }
    }
    
    /**
     * Aggiusta la durata in base al tipo di fonema
     */
    private fun adjustDurationForPhoneme(phoneme: String, baseDuration: Long): Long {
        return when {
            phoneme == "SIL" -> (baseDuration * 0.5).toLong()
            phoneme.endsWith("1") || phoneme.endsWith("2") -> // Vocali accentate
                (baseDuration * 1.2).toLong()
            phoneme in listOf("M", "N", "NG", "L", "R") -> // Consonanti sonore
                (baseDuration * 0.9).toLong()
            phoneme in listOf("P", "B", "T", "D", "K", "G") -> // Plosive
                (baseDuration * 0.7).toLong()
            else -> baseDuration
        }
    }
    
    /**
     * Applica regole fonetiche per parole non nel dictionary
     */
    private fun applyPhoneticRules(word: String): List<String> {
        val phonemes = mutableListOf<String>()
        var i = 0
        
        while (i < word.length) {
            val remaining = word.substring(i)
            val (phoneme, consumed) = matchPhonemePattern(remaining)
            phonemes.add(phoneme)
            i += consumed
        }
        
        return phonemes
    }
    
    /**
     * Pattern matching per grafemi -> fonemi
     */
    private fun matchPhonemePattern(s: String): Pair<String, Int> {
        // Digraphs prima
        val digraphs = mapOf(
            "th" to "TH", "sh" to "SH", "ch" to "CH", "wh" to "W",
            "ph" to "F", "ng" to "NG", "ck" to "K", "ee" to "IY",
            "oo" to "UW", "ea" to "IY", "ou" to "AW", "ow" to "OW",
            "ai" to "EY", "ay" to "EY", "oi" to "OY", "oy" to "OY"
        )
        
        for ((pattern, phoneme) in digraphs) {
            if (s.startsWith(pattern)) {
                return phoneme to pattern.length
            }
        }
        
        // Single characters
        val charMap = mapOf(
            'a' to "AE", 'e' to "EH", 'i' to "IH", 'o' to "AA", 'u' to "AH",
            'b' to "B", 'c' to "K", 'd' to "D", 'f' to "F", 'g' to "G",
            'h' to "HH", 'j' to "JH", 'k' to "K", 'l' to "L", 'm' to "M",
            'n' to "N", 'p' to "P", 'q' to "K", 'r' to "R", 's' to "S",
            't' to "T", 'v' to "V", 'w' to "W", 'x' to "K S", 'y' to "Y",
            'z' to "Z"
        )
        
        val char = s.firstOrNull() ?: return "SIL" to 1
        val phoneme = charMap[char] ?: "SIL"
        return phoneme to 1
    }
    
    /**
     * Carica un dizionario essenziale per parole comuni
     */
    private fun loadEssentialDictionary() {
        // Parole comuni con pronuncia
        val essentialWords = mapOf(
            "hello" to listOf("HH", "AH", "L", "OW"),
            "hi" to listOf("HH", "AY"),
            "how" to listOf("HH", "AW"),
            "are" to listOf("AA", "R"),
            "you" to listOf("Y", "UW"),
            "i" to listOf("AY"),
            "am" to listOf("AE", "M"),
            "is" to listOf("IH", "Z"),
            "the" to listOf("DH", "AH"),
            "a" to listOf("AH"),
            "and" to listOf("AE", "N", "D"),
            "or" to listOf("AO", "R"),
            "but" to listOf("B", "AH", "T"),
            "yes" to listOf("Y", "EH", "S"),
            "no" to listOf("N", "OW"),
            "thank" to listOf("TH", "AE", "NG", "K"),
            "thanks" to listOf("TH", "AE", "NG", "K", "S"),
            "please" to listOf("P", "L", "IY", "Z"),
            "help" to listOf("HH", "EH", "L", "P"),
            "good" to listOf("G", "UH", "D"),
            "great" to listOf("G", "R", "EY", "T"),
            "nice" to listOf("N", "AY", "S"),
            "okay" to listOf("OW", "K", "EY"),
            "sorry" to listOf("S", "AA", "R", "IY"),
            "what" to listOf("W", "AH", "T"),
            "where" to listOf("W", "EH", "R"),
            "when" to listOf("W", "EH", "N"),
            "why" to listOf("W", "AY"),
            "who" to listOf("HH", "UW"),
            "my" to listOf("M", "AY"),
            "your" to listOf("Y", "AO", "R"),
            "name" to listOf("N", "EY", "M"),
            "think" to listOf("TH", "IH", "NG", "K"),
            "know" to listOf("N", "OW"),
            "feel" to listOf("F", "IY", "L"),
            "like" to listOf("L", "AY", "K"),
            "love" to listOf("L", "AH", "V"),
            "happy" to listOf("HH", "AE", "P", "IY"),
            "sad" to listOf("S", "AE", "D")
        )
        
        dictionary.putAll(essentialWords)
        Log.d(tag, "Loaded ${dictionary.size} words in phoneme dictionary")
    }
}

/**
 * Rappresenta un fonema con il suo timing
 */
data class PhonemeTiming(
    val phoneme: String,
    val startTimeMs: Long,
    val durationMs: Long
)
```

### 5.3 Phoneme to Viseme Mapper

**Nuovo file:** `VisemeMapper.kt`
```kotlin
package com.lifo.humanoid.lipsync

import com.lifo.humanoid.domain.model.Viseme

/**
 * Mappa fonemi ARPAbet ai Visemi VRM.
 * Basato sul sistema 15-viseme (Microsoft SAPI) adattato per VRM.
 */
object VisemeMapper {
    
    /**
     * Mapping ARPAbet phoneme -> Viseme
     * Riferimento: https://docs.microsoft.com/en-us/azure/cognitive-services/speech-service/how-to-speech-synthesis-viseme
     */
    private val PHONEME_TO_VISEME = mapOf(
        // Silenzio
        "SIL" to Viseme.SILENCE,
        
        // Vocali
        "AA" to Viseme.AA,   // father
        "AE" to Viseme.AA,   // bat
        "AH" to Viseme.AA,   // but
        "AO" to Viseme.O,    // bought
        "AW" to Viseme.O,    // cow (diphthong)
        "AY" to Viseme.AA,   // bite (diphthong)
        "EH" to Viseme.E,    // bet
        "ER" to Viseme.E,    // bird
        "EY" to Viseme.E,    // bait
        "IH" to Viseme.I,    // bit
        "IY" to Viseme.I,    // beat
        "OW" to Viseme.O,    // boat
        "OY" to Viseme.O,    // boy (diphthong)
        "UH" to Viseme.U,    // book
        "UW" to Viseme.U,    // boot
        
        // Consonanti bilabiali (labbra chiuse)
        "B" to Viseme.M_B_P,
        "M" to Viseme.M_B_P,
        "P" to Viseme.M_B_P,
        
        // Consonanti labiodentali
        "F" to Viseme.F_V,
        "V" to Viseme.F_V,
        
        // Consonanti dentali
        "DH" to Viseme.TH,  // the
        "TH" to Viseme.TH,  // think
        
        // Consonanti alveolari
        "D" to Viseme.T_D_N_L,
        "L" to Viseme.T_D_N_L,
        "N" to Viseme.T_D_N_L,
        "T" to Viseme.T_D_N_L,
        
        // Sibilanti
        "S" to Viseme.S_Z,
        "Z" to Viseme.S_Z,
        
        // Palatali/Post-alveolari
        "CH" to Viseme.SH_ZH_CH_J,
        "JH" to Viseme.SH_ZH_CH_J,
        "SH" to Viseme.SH_ZH_CH_J,
        "ZH" to Viseme.SH_ZH_CH_J,
        
        // Velari
        "G" to Viseme.K_G_NG,
        "K" to Viseme.K_G_NG,
        "NG" to Viseme.K_G_NG,
        
        // R
        "R" to Viseme.R,
        
        // W
        "W" to Viseme.W,
        
        // Glottali e altri
        "HH" to Viseme.SILENCE,  // H è quasi invisibile
        "Y" to Viseme.I          // Y come in "yes"
    )
    
    /**
     * Converte un fonema nel corrispondente viseme
     */
    fun phonemeToViseme(phoneme: String): Viseme {
        // Rimuovi numeri di stress (es. "AH1" -> "AH")
        val cleanPhoneme = phoneme.replace(Regex("[0-2]"), "")
        return PHONEME_TO_VISEME[cleanPhoneme] ?: Viseme.SILENCE
    }
    
    /**
     * Converte una lista di fonemi con timing in visemi con timing
     */
    fun phonemesToVisemes(phonemes: List<PhonemeTiming>): List<VisemeTiming> {
        return phonemes.map { pt ->
            VisemeTiming(
                viseme = phonemeToViseme(pt.phoneme),
                startTimeMs = pt.startTimeMs,
                durationMs = pt.durationMs
            )
        }
    }
}

/**
 * Rappresenta un viseme con il suo timing
 */
data class VisemeTiming(
    val viseme: Viseme,
    val startTimeMs: Long,
    val durationMs: Long
)
```

### 5.4 Lip Sync Controller

**Nuovo file:** `LipSyncController.kt`
```kotlin
package com.lifo.humanoid.lipsync

import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import com.lifo.humanoid.domain.model.Viseme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import android.util.Log

/**
 * Controller principale per il lip-sync.
 * Coordina la conversione testo->fonemi->visemi e l'animazione.
 */
class LipSyncController(
    private val phonemeConverter: PhonemeConverter,
    private val blendShapeController: VrmBlendShapeController
) {
    
    private val tag = "LipSyncController"
    
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking
    
    private var lipSyncJob: Job? = null
    
    // Configurazione transizioni
    private val transitionConfig = TransitionConfig()
    
    data class TransitionConfig(
        val fadeInMs: Long = 50L,    // Tempo per raggiungere il viseme
        val fadeOutMs: Long = 80L,   // Tempo per tornare a neutral
        val minVisemeDurationMs: Long = 40L,  // Durata minima
        val coarticulationStrength: Float = 0.3f // Quanto il viseme precedente influenza il successivo
    )
    
    /**
     * Avvia lip-sync per un testo con durata totale specificata
     * 
     * @param text Il testo da sincronizzare
     * @param durationMs Durata totale dell'audio/speech in millisecondi
     * @param scope CoroutineScope per l'esecuzione
     */
    fun speak(text: String, durationMs: Long, scope: CoroutineScope) {
        stop()
        
        if (text.isBlank() || durationMs <= 0) {
            Log.w(tag, "Invalid input: text='$text', duration=$durationMs")
            return
        }
        
        _isSpeaking.value = true
        
        // Converti testo in fonemi
        val phonemeTimings = phonemeConverter.textToPhonemes(text, durationMs)
        Log.d(tag, "Generated ${phonemeTimings.size} phonemes for text: '$text'")
        
        // Converti fonemi in visemi
        val visemeTimings = VisemeMapper.phonemesToVisemes(phonemeTimings)
        
        lipSyncJob = scope.launch {
            playVisemeSequence(visemeTimings)
            _isSpeaking.value = false
        }
    }
    
    /**
     * Ferma il lip-sync corrente
     */
    fun stop() {
        lipSyncJob?.cancel()
        lipSyncJob = null
        _isSpeaking.value = false
        
        // Resetta a neutral
        resetMouth()
    }
    
    /**
     * Riproduce la sequenza di visemi con transizioni fluide
     */
    private suspend fun playVisemeSequence(visemes: List<VisemeTiming>) {
        if (visemes.isEmpty()) return
        
        var previousViseme: Viseme? = null
        val startTime = System.currentTimeMillis()
        
        for (visemeTiming in visemes) {
            // Aspetta fino al momento giusto
            val targetTime = startTime + visemeTiming.startTimeMs
            val waitTime = targetTime - System.currentTimeMillis()
            if (waitTime > 0) {
                delay(waitTime)
            }
            
            // Applica viseme con coarticolazione
            applyVisemeWithCoarticulation(
                visemeTiming.viseme,
                previousViseme,
                visemeTiming.durationMs
            )
            
            previousViseme = visemeTiming.viseme
        }
        
        // Fade out finale
        fadeToNeutral()
    }
    
    /**
     * Applica un viseme considerando l'effetto del viseme precedente
     */
    private suspend fun applyVisemeWithCoarticulation(
        viseme: Viseme,
        previousViseme: Viseme?,
        durationMs: Long
    ) {
        val blendShapes = viseme.blendShapes.toMutableMap()
        
        // Coarticolazione: mantieni un po' del viseme precedente
        previousViseme?.blendShapes?.forEach { (name, prevWeight) ->
            val currentWeight = blendShapes[name] ?: 0f
            blendShapes[name] = currentWeight + prevWeight * transitionConfig.coarticulationStrength
        }
        
        // Normalizza i pesi
        val maxWeight = blendShapes.values.maxOrNull() ?: 1f
        if (maxWeight > 1f) {
            blendShapes.forEach { (name, weight) ->
                blendShapes[name] = weight / maxWeight
            }
        }
        
        // Applica con fade-in
        fadeToViseme(blendShapes, transitionConfig.fadeInMs)
        
        // Mantieni per la durata rimanente
        val holdTime = durationMs - transitionConfig.fadeInMs
        if (holdTime > 0) {
            delay(holdTime)
        }
    }
    
    /**
     * Transizione fluida verso un set di blend shapes
     */
    private suspend fun fadeToViseme(targetShapes: Map<String, Float>, durationMs: Long) {
        val steps = (durationMs / 10).toInt().coerceAtLeast(1) // 10ms per step
        val stepDelay = durationMs / steps
        
        for (step in 1..steps) {
            val progress = step.toFloat() / steps
            val easedProgress = easeOutQuad(progress)
            
            val interpolatedShapes = targetShapes.mapValues { (_, targetWeight) ->
                targetWeight * easedProgress
            }
            
            blendShapeController.setTargetWeights(interpolatedShapes)
            delay(stepDelay)
        }
    }
    
    /**
     * Transizione a bocca chiusa/neutral
     */
    private suspend fun fadeToNeutral() {
        fadeToViseme(mapOf("mouthClosed" to 0.2f), transitionConfig.fadeOutMs)
        delay(50)
        blendShapeController.setTargetWeights(emptyMap())
    }
    
    /**
     * Reset immediato della bocca
     */
    private fun resetMouth() {
        blendShapeController.clearAndSetTargets(emptyMap())
    }
    
    /**
     * Easing function per transizioni più naturali
     */
    private fun easeOutQuad(t: Float): Float = 1 - (1 - t) * (1 - t)
}
```

### 5.5 Integrazione nel ViewModel

**Aggiornare** `HumanoidModule.kt`:
```kotlin
@Provides
@Singleton
fun providePhonemeConverter(
    @ApplicationContext context: Context
): PhonemeConverter {
    return PhonemeConverter(context)
}

@Provides
@Singleton
fun provideLipSyncController(
    phonemeConverter: PhonemeConverter,
    blendShapeController: VrmBlendShapeController
): LipSyncController {
    return LipSyncController(phonemeConverter, blendShapeController)
}
```

**Aggiornare** `HumanoidViewModel.kt`:
```kotlin
@HiltViewModel
class HumanoidViewModel @Inject constructor(
    private val vrmLoader: VrmLoader,
    private val blendShapeController: VrmBlendShapeController,
    private val lipSyncController: LipSyncController
) : ViewModel() {
    
    // ... codice esistente ...
    
    /**
     * Avvia lip-sync per un testo
     */
    fun speakText(text: String, durationMs: Long) {
        lipSyncController.speak(text, durationMs, viewModelScope)
    }
    
    /**
     * Ferma il lip-sync
     */
    fun stopSpeaking() {
        lipSyncController.stop()
    }
}
```

### 5.6 UI Demo per Lip-Sync

**Aggiungere a** `HumanoidScreen.kt`:
```kotlin
// Stato per demo lip-sync
var demoText by remember { mutableStateOf("Hello, I am your AI assistant!") }
var speechDuration by remember { mutableStateOf(3000L) }

// In control panel
OutlinedTextField(
    value = demoText,
    onValueChange = { demoText = it },
    label = { Text("Demo Text") },
    modifier = Modifier.fillMaxWidth()
)

Slider(
    value = speechDuration.toFloat(),
    onValueChange = { speechDuration = it.toLong() },
    valueRange = 1000f..10000f,
    modifier = Modifier.fillMaxWidth()
)

Text("Duration: ${speechDuration}ms")

Button(
    onClick = { viewModel.speakText(demoText, speechDuration) },
    modifier = Modifier.fillMaxWidth()
) {
    Text("🗣️ Test Lip-Sync")
}
```

### ✅ Checkpoint 5.1
**Verifica utente:**
- [ ] La bocca si muove quando attivato il lip-sync?
- [ ] Le forme della bocca corrispondono ai suoni?
- [ ] Le transizioni sono fluide?
- [ ] Funziona con diverse frasi?

---

## FASE 6: Integrazione Finale e Ottimizzazioni
**Durata stimata: 3-4 ore**

### 6.1 Animation Priority System

Gestione delle priorità tra diversi sistemi di animazione:
1. Lip-Sync (massima priorità durante speech)
2. Emozioni
3. Blink (si sovrappone sempre)
4. Idle (minima priorità)

**Nuovo file:** `AnimationCoordinator.kt`
```kotlin
package com.lifo.humanoid.animation

import com.lifo.humanoid.data.vrm.VrmBlendShapeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Coordina tutti i sistemi di animazione e gestisce le priorità
 */
class AnimationCoordinator(
    private val blendShapeController: VrmBlendShapeController,
    private val blinkController: BlinkController,
    private val lipSyncController: LipSyncController,
    private val idleAnimationController: IdleAnimationController,
    private val animationPlaybackController: AnimationPlaybackController
) {
    
    /**
     * Inizializza e coordina tutte le animazioni
     */
    fun start(scope: CoroutineScope) {
        // Avvia idle e blink di default
        idleAnimationController.start(scope)
        blinkController.start(scope)
        
        // Osserva lo stato di parlato per coordinare
        scope.launch {
            lipSyncController.isSpeaking.collect { isSpeaking ->
                // Durante il lip-sync, riduci l'intensità idle
                // ma mantieni il blinking
            }
        }
        
        // Combina tutti i pesi blend shape
        scope.launch {
            combine(
                blendShapeController.currentWeights,
                blinkController.blinkWeight
            ) { emotionWeights, blinkWeight ->
                // Merge weights con priorità
                val mergedWeights = emotionWeights.toMutableMap()
                
                // Blink si sovrappone sempre
                if (blinkWeight > 0.01f) {
                    mergedWeights["blink"] = blinkWeight
                }
                
                mergedWeights
            }.collect { finalWeights ->
                // I pesi finali sono già gestiti dal controller
            }
        }
    }
    
    /**
     * Ferma tutte le animazioni
     */
    fun stop() {
        idleAnimationController.stop()
        blinkController.stop()
        lipSyncController.stop()
        animationPlaybackController.stop()
    }
}
```

### 6.2 Performance Optimizations

**Ottimizzazioni per** `FilamentRenderer.kt`:
```kotlin
// Batch delle operazioni blend shape
fun updateBlendShapesBatched(blendShapes: Map<String, Float>) {
    if (blendShapes.isEmpty() && lastAppliedWeights.isEmpty()) return
    
    // Skip se nessun cambiamento significativo
    val hasSignificantChange = blendShapes.any { (name, weight) ->
        val lastWeight = lastAppliedWeights[name] ?: 0f
        abs(weight - lastWeight) > 0.001f
    }
    
    if (!hasSignificantChange && blendShapes.size == lastAppliedWeights.size) {
        return
    }
    
    // Applica in batch
    val renderableManager = engine.renderableManager
    // ... resto del codice ...
    
    lastAppliedWeights = blendShapes.toMap()
}

private var lastAppliedWeights: Map<String, Float> = emptyMap()
```

### 6.3 Debug UI Panel

**Componente per debug:**
```kotlin
@Composable
fun DebugPanel(
    vrmExtensions: VrmExtensions?,
    blendShapeWeights: Map<String, Float>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    
    Column(modifier = modifier) {
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide Debug" else "Show Debug")
        }
        
        if (expanded) {
            Card(modifier = Modifier.padding(8.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Available Blend Shapes:", style = MaterialTheme.typography.titleSmall)
                    vrmExtensions?.blendShapes?.take(10)?.forEach { shape ->
                        Text("• ${shape.name} (${shape.preset})")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Active Weights:", style = MaterialTheme.typography.titleSmall)
                    blendShapeWeights.filter { it.value > 0.01f }.forEach { (name, weight) ->
                        Row {
                            Text(name, modifier = Modifier.weight(1f))
                            Text("%.2f".format(weight))
                            LinearProgressIndicator(
                                progress = weight,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
```

### ✅ Checkpoint 6.1 (Finale)
**Verifica utente:**
- [ ] Tutte le animazioni funzionano insieme senza conflitti?
- [ ] Il blinking continua durante le emozioni e il lip-sync?
- [ ] Le performance sono accettabili (60fps)?
- [ ] L'avatar appare vivo e naturale?

---

## Struttura File Finale

```
com.lifo.humanoid/
├── animation/
│   ├── BlinkController.kt
│   ├── IdleAnimationController.kt
│   ├── IdlePoseController.kt
│   ├── KeyframeAnimator.kt
│   ├── AnimationPlaybackController.kt
│   ├── AnimationLibrary.kt
│   └── AnimationCoordinator.kt
├── data/vrm/
│   ├── VrmModel.kt
│   ├── VrmLoader.kt
│   ├── VrmBlendShapeController.kt
│   ├── VrmBlendShapePresets.kt
│   └── VrmHumanoidBoneMapper.kt
├── lipsync/
│   ├── PhonemeConverter.kt
│   ├── VisemeMapper.kt
│   └── LipSyncController.kt
├── domain/model/
│   ├── AvatarState.kt
│   ├── Emotion.kt
│   └── Viseme.kt
├── presentation/
│   ├── HumanoidScreen.kt
│   ├── HumanoidViewModel.kt
│   ├── components/
│   │   ├── FilamentView.kt
│   │   └── DebugPanel.kt
│   └── navigation/
│       └── HumanoidNavigation.kt
├── rendering/
│   └── FilamentRenderer.kt
└── di/
    └── HumanoidModule.kt
```

---

## Note Tecniche Importanti

### VRM e Filament
- Filament non ha supporto nativo VRM, ma legge glTF su cui VRM si basa
- Le animazioni VRM sono tipicamente vuote, richiedono approccio procedurale
- I blend shapes VRM usano preset standardizzati

### Risorse Esterne Consultate
1. **Filament Animator** - `FilamentAsset.getAnimator()` per animazioni glTF embedded
2. **VRM Spec** - Preset blend shapes standard (joy, sorrow, angry, etc.)
3. **CMU Dictionary** - Per conversione testo->fonemi
4. **SAPI Viseme System** - Mapping 15-viseme per lip-sync
5. **Animaze** - Approccio per idle pose e breathing procedurale

### Limitazioni Attuali
- Il lip-sync è basato su regole, non su audio analysis
- Le animazioni skeletal sono procedurali, non da file
- Non c'è supporto per spring bones/physics hair

---

## Conclusione

Questo piano fornisce una roadmap completa per trasformare un avatar VRM statico in un personaggio vivente con:
- Respirazione naturale
- Blinking realistico  
- Animazioni gestuali
- Lip-sync testuale

Ogni fase è verificabile prima di procedere alla successiva, garantendo sviluppo incrementale e debug facilitato.

**Tempo totale stimato: 20-25 ore di sviluppo**
