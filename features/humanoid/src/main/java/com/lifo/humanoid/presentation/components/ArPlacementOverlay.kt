package com.lifo.humanoid.presentation.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifo.humanoid.domain.ar.ArPlacementState

/**
 * Overlay UI displayed on top of the AR camera view to guide the user
 * through the avatar placement flow.
 *
 * @param placementState Current placement state
 * @param planeCount Number of detected planes (0 = scanning)
 */
@Composable
fun ArPlacementOverlay(
    placementState: ArPlacementState,
    planeCount: Int = 0,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (placementState) {
            ArPlacementState.INITIALIZING -> {
                InitializingIndicator()
            }
            ArPlacementState.SCANNING -> {
                ScanningIndicator(planeCount)
            }
            ArPlacementState.READY_TO_PLACE -> {
                ReadyToPlaceIndicator()
            }
            ArPlacementState.PLACED -> {
                PlacedConfirmation()
            }
            ArPlacementState.TRACKING_LOST -> {
                TrackingLostWarning()
            }
        }
    }
}

@Composable
private fun InitializingIndicator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 2.dp,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(16.dp))
        OverlayChip("Inizializzazione AR...")
    }
}

@Composable
private fun ScanningIndicator(planeCount: Int) {
    // No 2D crosshair — the 3D focus reticle in Filament provides spatial feedback.
    // Only show text hints here.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(bottom = 120.dp)
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "scan")
        val dotAlpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "dotAlpha"
        )

        // Small scanning dots indicator
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            repeat(3) { i ->
                Canvas(modifier = Modifier.size(8.dp)) {
                    val alpha = when (i) {
                        0 -> dotAlpha
                        1 -> dotAlpha * 0.7f
                        else -> dotAlpha * 0.4f
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = alpha),
                        radius = size.width / 2f
                    )
                }
            }
        }

        OverlayChip("Punta verso il pavimento o un tavolo")
        if (planeCount > 0) {
            Spacer(Modifier.height(6.dp))
            OverlayChip("$planeCount superfici trovate", fontSize = 12)
        }
    }
}

@Composable
private fun ReadyToPlaceIndicator() {
    // The 3D green ring reticle in Filament shows WHERE to place.
    // This overlay just shows the hint text.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(bottom = 120.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF4CAF50).copy(alpha = 0.8f)
        ) {
            Text(
                text = "Tocca per posizionare l'avatar",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun PlacedConfirmation() {
    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1500)
        visible = false
    }

    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(animationSpec = tween(500))
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF4CAF50).copy(alpha = 0.7f)
        ) {
            Text(
                text = "Avatar posizionato!",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun TrackingLostWarning() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Red reticle
        Canvas(modifier = Modifier.size(80.dp)) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val warn = Color(0xFFFFC107)

            drawCircle(warn, radius = size.width / 2f * 0.8f, center = Offset(cx, cy), style = Stroke(width = 2.dp.toPx()))
            drawLine(warn, Offset(cx, cy - 14.dp.toPx()), Offset(cx, cy + 6.dp.toPx()), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(warn, radius = 2.5f.dp.toPx(), center = Offset(cx, cy + 14.dp.toPx()))
        }

        Spacer(Modifier.height(16.dp))
        OverlayChip("Tracciamento perso")
        Spacer(Modifier.height(4.dp))
        OverlayChip("Muovi il dispositivo lentamente", fontSize = 13)
    }
}

@Composable
private fun OverlayChip(text: String, fontSize: Int = 14) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.5f)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = fontSize.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )
    }
}
