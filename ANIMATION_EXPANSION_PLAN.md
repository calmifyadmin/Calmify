# Animation Expansion Plan

Piano per espandere il sistema di animazioni VRMA con nuove animazioni e rotazione idle automatica.

---

## TASK 1: Aggiornare AnimationAsset enum

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/animation/VrmaAnimationLoader.kt`

**Azione:** Sostituire l'enum `AnimationAsset` (linee 38-48) con il seguente:

```kotlin
enum class AnimationAsset(val fileName: String, val displayName: String) {
    // Idle animations (per rotazione automatica)
    IDLE_LOOP("idle_loop.vrma", "Idle Loop"),
    IDLE_BASIC("idle_basic.vrma", "Idle Basic"),
    IDLE_LOOK_FINGERS("idle_look_fingers.vrma", "Idle Look Fingers"),
    IDLE_LOOKING_AROUND("idle_looking_around.vrma", "Idle Looking Around"),
    IDLE_VARIANT("idle_variant.vrma", "Idle Variant"),

    // Emotion animations
    ANGRY("angry.vrma", "Angry"),
    SAD("sad.vrma", "Sad"),
    DANCING_HAPPY("dancing_happy.vrma", "Dancing Happy"),

    // Gesture animations
    HELLO("hello.vrma", "Hello"),
    GREETING("greeting.vrma", "Greeting"),
    I_AGREE("i_agree.vrma", "I Agree"),
    I_DONT_KNOW("i_dont_know.vrma", "I Don't Know"),
    I_DONT_THINK_SO("i_dont_think_so.vrma", "I Don't Think So"),
    YES_WITH_HEAD("yes_with_head.vrma", "Yes"),
    NO_WITH_HEAD("no_with_head.vrma", "No"),
    POINTING_THING("pointing_thing.vrma", "Pointing"),
    YOU_ARE_CRAZY("you_are_crazy.vrma", "You Are Crazy"),

    // Action animations
    DANCE("dance.vrma", "Dance"),
    PEACE_SIGN("peaceSign.vrma", "Peace Sign"),
    SHOOT("shoot.vrma", "Shoot"),
    SHOW_FULL_BODY("showFullBody.vrma", "Show Full Body")

    // RIMOSSI (buggati): modelPose.vrma, spin.vrma, squat.vrma
}
```

---

## TASK 2: Creare IdleRotationController

**File da creare:** `features/humanoid/src/main/java/com/lifo/humanoid/animation/IdleRotationController.kt`

**Contenuto:**

```kotlin
package com.lifo.humanoid.animation

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.random.Random

/**
 * Controller per la rotazione automatica delle animazioni idle.
 * Cambia animazione ogni 10-40 secondi con probabilità pesata.
 */
class IdleRotationController(
    private val onPlayAnimation: (VrmaAnimationLoader.AnimationAsset) -> Unit
) {
    companion object {
        private const val TAG = "IdleRotationController"
        private const val MIN_INTERVAL_MS = 10_000L  // 10 secondi
        private const val MAX_INTERVAL_MS = 40_000L  // 40 secondi
    }

    /**
     * Idle animations con peso (percentuale di probabilità).
     * Pesi più alti = più frequente.
     */
    private val idleAnimations = listOf(
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_LOOP, 35),           // 35% - default principale
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_BASIC, 25),          // 25% - variante comune
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_VARIANT, 20),        // 20% - variante
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_LOOKING_AROUND, 12), // 12% - guarda intorno
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_LOOK_FINGERS, 8)     // 8% - guarda le dita (rara)
    )

    private val totalWeight = idleAnimations.sumOf { it.weight }

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _currentIdle = MutableStateFlow<VrmaAnimationLoader.AnimationAsset?>(null)
    val currentIdle: StateFlow<VrmaAnimationLoader.AnimationAsset?> = _currentIdle

    private var rotationJob: Job? = null

    data class WeightedAnimation(
        val animation: VrmaAnimationLoader.AnimationAsset,
        val weight: Int
    )

    /**
     * Avvia la rotazione automatica delle idle.
     */
    fun start(scope: CoroutineScope) {
        if (_isActive.value) {
            Log.d(TAG, "Already active")
            return
        }

        _isActive.value = true
        Log.d(TAG, "Starting idle rotation")

        // Avvia subito con la prima animazione idle
        playRandomIdle()

        rotationJob = scope.launch {
            while (isActive) {
                // Attendi intervallo random tra 10-40 secondi
                val intervalMs = Random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1)
                Log.d(TAG, "Next idle change in ${intervalMs / 1000}s")
                delay(intervalMs)

                if (_isActive.value) {
                    playRandomIdle()
                }
            }
        }
    }

    /**
     * Ferma la rotazione automatica.
     */
    fun stop() {
        Log.d(TAG, "Stopping idle rotation")
        _isActive.value = false
        rotationJob?.cancel()
        rotationJob = null
        _currentIdle.value = null
    }

    /**
     * Pausa temporaneamente (per altre animazioni).
     */
    fun pause() {
        rotationJob?.cancel()
        rotationJob = null
    }

    /**
     * Riprendi dopo pausa.
     */
    fun resume(scope: CoroutineScope) {
        if (_isActive.value && rotationJob == null) {
            start(scope)
        }
    }

    /**
     * Seleziona e riproduce un'animazione idle random pesata.
     */
    private fun playRandomIdle() {
        val selected = selectWeightedRandom()
        _currentIdle.value = selected
        Log.d(TAG, "Playing idle: ${selected.displayName}")
        onPlayAnimation(selected)
    }

    /**
     * Selezione random pesata.
     */
    private fun selectWeightedRandom(): VrmaAnimationLoader.AnimationAsset {
        val randomValue = Random.nextInt(totalWeight)
        var cumulative = 0

        for (weighted in idleAnimations) {
            cumulative += weighted.weight
            if (randomValue < cumulative) {
                return weighted.animation
            }
        }

        // Fallback
        return idleAnimations.first().animation
    }
}
```

---

## TASK 3: Integrare IdleRotationController nel sistema

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/animation/AnimationCoordinator.kt`

**Azioni:**
1. Aggiungere proprietà `idleRotationController: IdleRotationController`
2. Nel metodo di inizializzazione, creare e avviare l'IdleRotationController
3. Quando viene riprodotta un'animazione non-idle, mettere in pausa la rotazione
4. Quando l'animazione non-idle finisce, riprendere la rotazione idle

---

## TASK 4: Aggiornare HumanoidViewModel

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/presentation/HumanoidViewModel.kt`

**Azioni:**
1. Aggiungere `IdleRotationController` come dipendenza
2. Esporre `isIdleRotationActive: StateFlow<Boolean>`
3. Aggiungere metodi:
   - `startIdleRotation()`
   - `stopIdleRotation()`
   - `toggleIdleRotation()`
4. Quando si riproduce un'animazione manuale, pausare idle rotation
5. Quando l'animazione finisce, riprendere idle rotation

---

## TASK 5: Aggiungere debug per animazioni in HumanoidScreen

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/presentation/HumanoidScreen.kt`

**Azioni:**
1. Nel `DebugPanel`, aggiungere sezione "Animations" che mostra:
   - Animazione corrente in riproduzione
   - Stato rotazione idle (attiva/inattiva)
   - Prossimo cambio idle (countdown opzionale)
2. Nel pannello controlli, aggiungere toggle per attivare/disattivare rotazione idle

---

## TASK 6: Helper method per identificare animazioni idle

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/animation/VrmaAnimationLoader.kt`

**Azione:** Aggiungere al companion object o all'enum:

```kotlin
// Nella enum class AnimationAsset
fun isIdle(): Boolean = fileName.startsWith("idle_")

// Nel companion object di VrmaAnimationLoader
fun getIdleAnimations(): List<AnimationAsset> =
    AnimationAsset.entries.filter { it.isIdle() }
```

---

## Riepilogo Animazioni

### Animazioni Idle (per rotazione)
| File | Peso | Probabilità |
|------|------|-------------|
| idle_loop.vrma | 35 | 35% |
| idle_basic.vrma | 25 | 25% |
| idle_variant.vrma | 20 | 20% |
| idle_looking_around.vrma | 12 | 12% |
| idle_look_fingers.vrma | 8 | 8% |

### Animazioni Gesture/Emotion
- angry.vrma, sad.vrma, dancing_happy.vrma
- hello.vrma, greeting.vrma
- i_agree.vrma, i_dont_know.vrma, i_dont_think_so.vrma
- yes_with_head.vrma, no_with_head.vrma
- pointing_thing.vrma, you_are_crazy.vrma

### Animazioni Action
- dance.vrma, peaceSign.vrma, shoot.vrma, showFullBody.vrma

### RIMOSSE (buggate)
- ~~modelPose.vrma~~
- ~~spin.vrma~~
- ~~squat.vrma~~

---

## TASK 7: Aggiornare GestureType enum

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/api/HumanoidController.kt`

**Azione:** Sostituire l'enum `GestureType` (linee 135-190) con:

```kotlin
/**
 * Available gesture animations that can be triggered.
 * Corresponds to VRMA animations loaded in the Humanoid module.
 */
enum class GestureType(val animationName: String) {
    // Greetings
    GREETING("greeting"),
    HELLO("hello"),

    // Agreement/Disagreement
    YES("yes_with_head"),
    NO("no_with_head"),
    I_AGREE("i_agree"),
    I_DONT_THINK_SO("i_dont_think_so"),
    I_DONT_KNOW("i_dont_know"),

    // Emotions
    ANGRY("angry"),
    SAD("sad"),
    HAPPY("dancing_happy"),
    YOU_ARE_CRAZY("you_are_crazy"),

    // Actions
    DANCE("dance"),
    PEACE_SIGN("peaceSign"),
    SHOOT("shoot"),
    POINTING("pointing_thing"),
    SHOW_FULL_BODY("showFullBody");

    companion object {
        /**
         * Find GestureType by animation name (case-insensitive).
         */
        fun fromAnimationName(name: String): GestureType? =
            entries.find { it.animationName.equals(name, ignoreCase = true) }

        /**
         * Get all gesture names for function calling documentation.
         */
        fun getAllNames(): List<String> = entries.map { it.animationName }
    }
}
```

---

## TASK 8: Aggiungere Function Calling per Animazioni in Gemini WebSocket

**File:** `features/chat/src/main/java/com/lifo/chat/data/websocket/GeminiLiveWebSocketClient.kt`

**Azione 1:** Aggiungere callback per animazioni (dopo linea 43):

```kotlin
var onPlayAnimation: ((String) -> Unit)? = null
```

**Azione 2:** Nella funzione `sendInitialSetupMessage()`, aggiungere la function declaration per `play_animation` nell'array `tools`:

```kotlin
// Aggiungi dopo search_diary
put(JSONObject().apply {
    put("name", "play_animation")
    put("description", """
        Riproduci un'animazione sull'avatar. Usa per esprimere emozioni o reazioni.
        Animazioni disponibili:
        - greeting/hello: saluto
        - yes/i_agree: annuire, confermare
        - no/i_dont_think_so: negare, dissentire
        - i_dont_know: scrollare spalle, non sapere
        - angry: espressione arrabbiata
        - sad: espressione triste
        - dancing_happy: ballare felice
        - you_are_crazy: gesto "sei pazzo"
        - dance: ballare
        - peace_sign: segno pace
        - pointing_thing: indicare qualcosa
    """.trimIndent())
    put("parameters", JSONObject().apply {
        put("type", "OBJECT")
        put("properties", JSONObject().apply {
            put("animation", JSONObject().apply {
                put("type", "STRING")
                put("description", "Nome dell'animazione da riprodurre")
                put("enum", JSONArray().apply {
                    put("greeting")
                    put("hello")
                    put("yes_with_head")
                    put("no_with_head")
                    put("i_agree")
                    put("i_dont_think_so")
                    put("i_dont_know")
                    put("angry")
                    put("sad")
                    put("dancing_happy")
                    put("you_are_crazy")
                    put("dance")
                    put("peace_sign")
                    put("pointing_thing")
                })
            })
        })
        put("required", JSONArray().put("animation"))
    })
})
```

**Azione 3:** Nel metodo `handleToolCall()`, aggiungere caso per `play_animation`:

```kotlin
"play_animation" -> {
    val animationName = args.getString("animation")
    Log.d(TAG, "🎭 Playing animation: $animationName")
    onPlayAnimation?.invoke(animationName)
    JSONObject().apply {
        put("success", true)
        put("animation", animationName)
        put("message", "Animation '$animationName' started")
    }
}
```

---

## TASK 9: Aggiornare HumanoidControllerImpl per supportare nuove animazioni

**File:** `features/humanoid/src/main/java/com/lifo/humanoid/api/HumanoidControllerImpl.kt`

**Azione:** Aggiornare il mapping da `GestureType` a `AnimationAsset` nel metodo `playGesture()`:

```kotlin
override fun playGesture(gesture: GestureType, loop: Boolean) {
    val animationAsset = when (gesture) {
        // Greetings
        GestureType.GREETING -> AnimationAsset.GREETING
        GestureType.HELLO -> AnimationAsset.HELLO

        // Agreement/Disagreement
        GestureType.YES -> AnimationAsset.YES_WITH_HEAD
        GestureType.NO -> AnimationAsset.NO_WITH_HEAD
        GestureType.I_AGREE -> AnimationAsset.I_AGREE
        GestureType.I_DONT_THINK_SO -> AnimationAsset.I_DONT_THINK_SO
        GestureType.I_DONT_KNOW -> AnimationAsset.I_DONT_KNOW

        // Emotions
        GestureType.ANGRY -> AnimationAsset.ANGRY
        GestureType.SAD -> AnimationAsset.SAD
        GestureType.HAPPY -> AnimationAsset.DANCING_HAPPY
        GestureType.YOU_ARE_CRAZY -> AnimationAsset.YOU_ARE_CRAZY

        // Actions
        GestureType.DANCE -> AnimationAsset.DANCE
        GestureType.PEACE_SIGN -> AnimationAsset.PEACE_SIGN
        GestureType.SHOOT -> AnimationAsset.SHOOT
        GestureType.POINTING -> AnimationAsset.POINTING_THING
        GestureType.SHOW_FULL_BODY -> AnimationAsset.SHOW_FULL_BODY
    }

    // Pausa idle rotation se attiva
    idleRotationController?.pause()

    viewModelScope.launch {
        vrmaAnimationPlayer?.playAnimation(animationAsset, loop)
    }
}
```

---

## TASK 10: Collegare Animation Callback nel LiveChatViewModel

**File:** `features/chat/src/main/java/com/lifo/chat/presentation/viewmodel/LiveChatViewModel.kt`

**Azione 1:** Aggiungere variabile per il controller avatar (dopo linea 100):

```kotlin
private var humanoidController: SpeechAnimationTarget? = null
```

**Azione 2:** Nel metodo `attachHumanoidController()`, salvare il riferimento e configurare il callback:

```kotlin
fun attachHumanoidController(controller: SpeechAnimationTarget) {
    Log.d(TAG, "🤖 Attaching HumanoidController for synchronized lip-sync (Live mode)")
    humanoidController = controller
    synchronizedSpeechController.attachAnimationTarget(controller)

    // Setup animation callback
    geminiWebSocketClient.onPlayAnimation = { animationName ->
        Log.d(TAG, "🎭 AI requested animation: $animationName")
        (controller as? HumanoidController)?.let { humanoid ->
            GestureType.fromAnimationName(animationName)?.let { gesture ->
                humanoid.playGesture(gesture, loop = false)
            }
        }
    }
}
```

**Azione 3:** Nel metodo `detachHumanoidController()`, rimuovere il callback:

```kotlin
fun detachHumanoidController() {
    Log.d(TAG, "🤖 Detaching HumanoidController (Live mode)")
    humanoidController = null
    synchronizedSpeechController.detachAnimationTarget()
    geminiWebSocketClient.onPlayAnimation = null
}
```

---

## TASK 11: Aggiornare System Prompt per istruire Gemini

**File:** `features/chat/src/main/java/com/lifo/chat/data/websocket/GeminiLiveWebSocketClient.kt`

**Azione:** Aggiungere al system prompt in `buildSystemInstruction()`:

```kotlin
// Aggiungi alla fine del system prompt, prima del return

"""

ANIMAZIONI AVATAR:
Io ho un corpo. Posso muovermi. Quando voglio esprimere qualcosa, posso usare 'play_animation'.

Uso le animazioni per:
- Salutare quando incontro qualcuno: 'hello' o 'greeting'
- Annuire quando sono d'accordo: 'yes_with_head' o 'i_agree'
- Scuotere la testa quando non sono d'accordo: 'no_with_head' o 'i_dont_think_so'
- Scrollare le spalle quando non so: 'i_dont_know'
- Mostrare rabbia: 'angry'
- Mostrare tristezza: 'sad'
- Ballare quando sono felice: 'dancing_happy' o 'dance'
- Fare il segno della pace: 'peace_sign'
- Indicare qualcosa: 'pointing_thing'
- Gesto "sei pazzo": 'you_are_crazy'

Non esagero con le animazioni. Le uso quando servono per esprimermi meglio.
Non annuncio "sto per fare un'animazione" - la faccio e basta.
"""
```

---

## Riepilogo Animazioni

### Animazioni Idle (per rotazione)
| File | Peso | Probabilità |
|------|------|-------------|
| idle_loop.vrma | 35 | 35% |
| idle_basic.vrma | 25 | 25% |
| idle_variant.vrma | 20 | 20% |
| idle_looking_around.vrma | 12 | 12% |
| idle_look_fingers.vrma | 8 | 8% |

### Animazioni Gesture/Emotion (usabili da Gemini AI)
| Animazione | GestureType | Uso |
|------------|-------------|-----|
| hello.vrma | HELLO | Saluto |
| greeting.vrma | GREETING | Saluto formale |
| yes_with_head.vrma | YES | Annuire |
| no_with_head.vrma | NO | Negare |
| i_agree.vrma | I_AGREE | Confermare |
| i_dont_think_so.vrma | I_DONT_THINK_SO | Dissentire |
| i_dont_know.vrma | I_DONT_KNOW | Non sapere |
| angry.vrma | ANGRY | Rabbia |
| sad.vrma | SAD | Tristezza |
| dancing_happy.vrma | HAPPY | Felicità |
| you_are_crazy.vrma | YOU_ARE_CRAZY | Incredulità |
| dance.vrma | DANCE | Ballare |
| peace_sign.vrma | PEACE_SIGN | Pace |
| pointing_thing.vrma | POINTING | Indicare |
| shoot.vrma | SHOOT | Sparare |
| showFullBody.vrma | SHOW_FULL_BODY | Mostra corpo |

### RIMOSSE (buggate)
- ~~modelPose.vrma~~
- ~~spin.vrma~~
- ~~squat.vrma~~

---

## Ordine di Esecuzione

### Fase 1: Fondamenta (Humanoid Module)
1. TASK 1 - Aggiornare AnimationAsset enum
2. TASK 6 - Helper methods isIdle()
3. TASK 7 - Aggiornare GestureType enum

### Fase 2: Idle Rotation System
4. TASK 2 - Creare IdleRotationController
5. TASK 3 - Integrare in AnimationCoordinator
6. TASK 4 - Aggiornare HumanoidViewModel

### Fase 3: Gemini AI Integration
7. TASK 8 - Function Calling nel WebSocket
8. TASK 9 - Aggiornare HumanoidControllerImpl mapping
9. TASK 10 - Collegare callback nel LiveChatViewModel
10. TASK 11 - Aggiornare System Prompt

### Fase 4: Debug/UI
11. TASK 5 - Aggiornare UI debug

---

## Diagramma di Flusso AI -> Animazione

```
Gemini AI
    │
    ▼ (tool call: play_animation)
GeminiLiveWebSocketClient
    │
    ▼ (onPlayAnimation callback)
LiveChatViewModel
    │
    ▼ (GestureType.fromAnimationName)
HumanoidController.playGesture()
    │
    ▼ (mapping to AnimationAsset)
VrmaAnimationPlayer
    │
    ▼
Avatar Animation
```
