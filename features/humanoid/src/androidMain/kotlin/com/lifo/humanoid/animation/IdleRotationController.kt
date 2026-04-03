package com.lifo.humanoid.animation

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
        private const val MIN_INTERVAL_MS = 10_000L  // 10 secondi
        private const val MAX_INTERVAL_MS = 40_000L  // 40 secondi
    }

    /**
     * Idle animations con peso (percentuale di probabilità).
     * Pesi più alti = più frequente.
     */
    private val idleAnimations = listOf(
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_LOOP, 55),           // 55% - default principale
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_BASIC, 35),          // 35% - variante comune
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_VARIANT, 20),        // 14% - variante
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_LOOKING_AROUND, 20), // 14% - guarda intorno
        WeightedAnimation(VrmaAnimationLoader.AnimationAsset.IDLE_LOOK_FINGERS, 10)    //  7% - guarda le dita
    )

    private val totalWeight = idleAnimations.sumOf { it.weight }

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive

    private val _currentIdle = MutableStateFlow<VrmaAnimationLoader.AnimationAsset?>(null)
    val currentIdle: StateFlow<VrmaAnimationLoader.AnimationAsset?> = _currentIdle

    // Traccia l'ultima animazione per evitare ripetizioni consecutive
    private var lastPlayedAnimation: VrmaAnimationLoader.AnimationAsset? = null

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
            println("[IdleRotationController] Already active")
            return
        }

        _isActive.value = true
        println("[IdleRotationController] Starting idle rotation")

        // Avvia subito con la prima animazione idle
        playRandomIdle()

        rotationJob = scope.launch {
            while (_isActive.value) {
                // Attendi intervallo random tra 10-40 secondi
                val intervalMs = Random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1)
                println("[IdleRotationController] Next idle change in ${intervalMs / 1000}s")
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
        println("[IdleRotationController] Stopping idle rotation")
        _isActive.value = false
        rotationJob?.cancel()
        rotationJob = null
        _currentIdle.value = null
        lastPlayedAnimation = null
    }

    /**
     * Pausa temporaneamente (per altre animazioni).
     */
    fun pause() {
        println("[IdleRotationController] Pausing idle rotation")
        rotationJob?.cancel()
        rotationJob = null
    }

    /**
     * Riprendi dopo pausa.
     */
    fun resume(scope: CoroutineScope) {
        if (_isActive.value && rotationJob == null) {
            println("[IdleRotationController] Resuming idle rotation")
            rotationJob = scope.launch {
                while (_isActive.value) {
                    val intervalMs = Random.nextLong(MIN_INTERVAL_MS, MAX_INTERVAL_MS + 1)
                    println("[IdleRotationController] Next idle change in ${intervalMs / 1000}s")
                    delay(intervalMs)

                    if (_isActive.value) {
                        playRandomIdle()
                    }
                }
            }
        }
    }

    /**
     * Seleziona e riproduce un'animazione idle random pesata.
     * Evita di ripetere la stessa animazione consecutivamente.
     */
    private fun playRandomIdle() {
        val selected = selectWeightedRandomExcluding(lastPlayedAnimation)
        lastPlayedAnimation = selected
        _currentIdle.value = selected
        println("[IdleRotationController] Playing idle: ${selected.displayName}")
        onPlayAnimation(selected)
    }

    /**
     * Selezione random pesata, escludendo l'animazione specificata.
     * Questo evita ripetizioni consecutive della stessa animazione.
     */
    private fun selectWeightedRandomExcluding(
        exclude: VrmaAnimationLoader.AnimationAsset?
    ): VrmaAnimationLoader.AnimationAsset {
        // Filtra le animazioni disponibili escludendo quella corrente
        val availableAnimations = if (exclude != null && idleAnimations.size > 1) {
            idleAnimations.filter { it.animation != exclude }
        } else {
            idleAnimations
        }

        val availableTotalWeight = availableAnimations.sumOf { it.weight }
        val randomValue = Random.nextInt(availableTotalWeight)
        var cumulative = 0

        for (weighted in availableAnimations) {
            cumulative += weighted.weight
            if (randomValue < cumulative) {
                return weighted.animation
            }
        }

        // Fallback
        return availableAnimations.first().animation
    }
}
