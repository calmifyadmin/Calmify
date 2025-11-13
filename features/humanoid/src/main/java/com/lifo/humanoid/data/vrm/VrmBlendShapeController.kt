package com.lifo.humanoid.data.vrm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls VRM blend shape weights with smooth interpolation.
 * Manages transitions between different facial expressions.
 */
class VrmBlendShapeController {

    private val _currentWeights = MutableStateFlow<Map<String, Float>>(emptyMap())
    val currentWeights: StateFlow<Map<String, Float>> = _currentWeights.asStateFlow()

    private val targetWeights = mutableMapOf<String, Float>()
    private val blendSpeed = 0.15f // Interpolation speed (0-1)

    /**
     * Set target blend shape weights.
     * Weights not included will fade to 0 automatically.
     *
     * @param weights Map of blend shape names to target weights (0.0-1.0)
     */
    fun setTargetWeights(weights: Map<String, Float>) {
        // DON'T clear targetWeights - we need old keys to fade to 0
        // Instead, set all new weights and let update() fade out missing ones

        // Set all current weights to 0 (they will fade out)
        targetWeights.keys.toList().forEach { key ->
            if (!weights.containsKey(key)) {
                targetWeights[key] = 0f
            }
        }

        // Set new target weights
        targetWeights.putAll(weights.mapValues { it.value.coerceIn(0f, 1f) })
    }

    fun clearAndSetTargets(weights: Map<String, Float>) {
        // Immediately clear all current weights
        targetWeights.clear()
        _currentWeights.value = emptyMap()

        // Set new targets
        targetWeights.putAll(weights.mapValues { it.value.coerceIn(0f, 1f) })
    }

    /**
     * Set a single blend shape weight
     */
    fun setWeight(name: String, weight: Float) {
        targetWeights[name] = weight.coerceIn(0f, 1f)
    }

    /**
     * Update blend shape interpolation.
     * Call this every frame to smoothly transition between expressions.
     *
     * @param deltaTime Time since last update in seconds
     */
    fun update(deltaTime: Float) {
        val updatedWeights = _currentWeights.value.toMutableMap()

        // Interpolate towards target weights
        targetWeights.forEach { (name, targetWeight) ->
            val currentWeight = updatedWeights[name] ?: 0f
            val newWeight = lerp(currentWeight, targetWeight, blendSpeed)

            if (newWeight > 0.001f) {
                updatedWeights[name] = newWeight
            } else {
                updatedWeights.remove(name) // Remove negligible weights
            }
        }

        // Fade out weights not in target
        val keysToRemove = mutableListOf<String>()
        updatedWeights.forEach { (name, currentWeight) ->
            if (!targetWeights.containsKey(name)) {
                val newWeight = lerp(currentWeight, 0f, blendSpeed)
                if (newWeight > 0.001f) {
                    updatedWeights[name] = newWeight
                } else {
                    keysToRemove.add(name)
                }
            }
        }

        keysToRemove.forEach { updatedWeights.remove(it) }

        _currentWeights.value = updatedWeights
    }

    /**
     * Immediately set weights without interpolation
     */
    fun setImmediateWeights(weights: Map<String, Float>) {
        targetWeights.clear()
        targetWeights.putAll(weights)
        _currentWeights.value = weights.toMap()
    }

    /**
     * Reset all blend shapes to neutral
     */
    fun reset() {
        targetWeights.clear()
        _currentWeights.value = emptyMap()
    }

    /**
     * Linear interpolation
     */
    private fun lerp(start: Float, end: Float, alpha: Float): Float {
        return start + (end - start) * alpha
    }
}
