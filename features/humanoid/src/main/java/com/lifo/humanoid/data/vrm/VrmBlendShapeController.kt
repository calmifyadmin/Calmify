package com.lifo.humanoid.data.vrm

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Controls VRM blend shape weights with smooth interpolation.
 * Manages transitions between different facial expressions.
 *
 * Features:
 * - Smoothstep easing for natural transitions
 * - Priority-based weight management (lip-sync > emotion > idle)
 * - Available preset tracking for compatibility
 */
class VrmBlendShapeController {

    companion object {
        private const val TAG = "BlendShapeController"

        // Weight categories for priority management
        const val CATEGORY_LIPSYNC = "lipsync"
        const val CATEGORY_EMOTION = "emotion"
        const val CATEGORY_BLINK = "blink"
        const val CATEGORY_IDLE = "idle"
    }

    private val _currentWeights = MutableStateFlow<Map<String, Float>>(emptyMap())
    val currentWeights: StateFlow<Map<String, Float>> = _currentWeights.asStateFlow()

    private val targetWeights = mutableMapOf<String, Float>()
    private val blendSpeed = 0.15f // Interpolation speed (0-1)

    // Track which blend shape presets are available in the current VRM model
    private var _availablePresets: Set<String> = emptySet()
    val availablePresets: Set<String> get() = _availablePresets

    // Category-based weights for priority management
    private val categoryWeights = mutableMapOf<String, MutableMap<String, Float>>()

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
        categoryWeights.clear()
        _currentWeights.value = emptyMap()
    }

    /**
     * Set the available presets from the loaded VRM model
     *
     * @param presets Set of available blend shape preset names
     */
    fun setAvailablePresets(presets: Set<String>) {
        _availablePresets = presets.map { it.lowercase() }.toSet()
        Log.d(TAG, "Available presets: $_availablePresets")
    }

    /**
     * Set weights for a specific category (enables priority management)
     *
     * @param category The category (CATEGORY_LIPSYNC, CATEGORY_EMOTION, etc.)
     * @param weights Map of blend shape names to weights
     */
    fun setCategoryWeights(category: String, weights: Map<String, Float>) {
        categoryWeights.getOrPut(category) { mutableMapOf() }.apply {
            clear()
            putAll(weights.mapValues { it.value.coerceIn(0f, 1f) })
        }
        recalculateTargetWeights()
    }

    /**
     * Clear weights for a specific category
     */
    fun clearCategory(category: String) {
        categoryWeights[category]?.clear()
        recalculateTargetWeights()
    }

    /**
     * Recalculate target weights based on all category weights with priority
     * Priority: lip-sync > blink > emotion > idle
     */
    private fun recalculateTargetWeights() {
        val merged = mutableMapOf<String, Float>()

        // Apply in priority order (lowest to highest, so highest overwrites)
        val priorityOrder = listOf(CATEGORY_IDLE, CATEGORY_EMOTION, CATEGORY_BLINK, CATEGORY_LIPSYNC)

        for (category in priorityOrder) {
            categoryWeights[category]?.forEach { (name, weight) ->
                if (weight > 0.001f) {
                    // For blink, always add (it overlays on everything)
                    if (category == CATEGORY_BLINK) {
                        merged[name] = weight
                    } else {
                        // For others, take the max weight
                        val existing = merged[name] ?: 0f
                        merged[name] = maxOf(existing, weight)
                    }
                }
            }
        }

        setTargetWeights(merged)
    }

    /**
     * Check if a preset is available in the current VRM model
     */
    fun hasPreset(name: String): Boolean {
        return name.lowercase() in _availablePresets
    }

    /**
     * Find the first available preset from candidates
     */
    fun findAvailablePreset(candidates: List<String>): String? {
        return VrmBlendShapePresets.findAvailablePreset(_availablePresets, candidates)
    }

    /**
     * Smoothstep interpolation for more natural transitions
     */
    private fun lerp(start: Float, end: Float, alpha: Float): Float {
        // Smoothstep: smoother acceleration/deceleration
        val t = alpha * alpha * (3f - 2f * alpha)
        return start + (end - start) * t
    }

    /**
     * Linear interpolation (without smoothstep)
     */
    private fun lerpLinear(start: Float, end: Float, alpha: Float): Float {
        return start + (end - start) * alpha
    }
}
