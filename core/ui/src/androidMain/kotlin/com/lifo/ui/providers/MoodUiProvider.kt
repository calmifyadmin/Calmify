package com.lifo.ui.providers

import androidx.compose.ui.graphics.Color
import com.lifo.ui.emotion.MoodShapeDefinitions
import com.lifo.util.model.Mood

/**
 * Mood UI Provider — Shape-based (M3 Expressive)
 *
 * Provides color mappings for Mood shapes.
 * Icon drawables have been replaced by [MoodShapeDefinitions] + MoodShapeIndicator composables.
 */
object MoodUiProvider {

    fun getContentColor(mood: Mood): Color =
        MoodShapeDefinitions.getColors(mood).onShape

    fun getContainerColor(mood: Mood): Color =
        MoodShapeDefinitions.getColors(mood).primary
}
