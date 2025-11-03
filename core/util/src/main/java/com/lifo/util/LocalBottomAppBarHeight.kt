package com.lifo.util

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * CompositionLocal per condividere l'altezza della NavigationBar
 * tra l'app principale e le schermate.
 *
 * Secondo Material 3 guidelines, la NavigationBar è il componente standard
 * per la navigazione principale persistente (3-5 destinazioni).
 *
 * L'altezza standard è 80.dp secondo le specifiche Material 3.
 *
 * @see androidx.compose.material3.NavigationBar
 */
val LocalBottomAppBarHeight = compositionLocalOf { 0.dp }
