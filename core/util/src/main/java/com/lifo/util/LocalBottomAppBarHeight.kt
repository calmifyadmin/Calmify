package com.lifo.util

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal per condividere l'altezza della BottomAppBar overlay
 * tra l'app principale e le schermate
 */
val LocalBottomAppBarHeight = compositionLocalOf { 0.dp }
