package dev.cazimir.voicevault.capture

import android.os.Bundle
import android.view.MotionEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Full-screen overlay started alongside a recording. It is the only visual
 * confirmation that a power-button hold actually started a capture; haptics remain
 * the primary channel, since the phone is expected to be pocketed. A tap anywhere
 * ends the capture early; nothing else in the capture flow depends on it, and it
 * closes itself the moment recording ends by any other stop condition. It keeps the
 * display awake while it is up, so that a screen-off is always a deliberate power press
 * and never an idle timeout - the recording service reads it as a stop.
 */
class TapToStopActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The service treats screen-off as "the user is done speaking", so the display must
        // never sleep on its own - otherwise a long thinking pause would read as a stop.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        CaptureEvents.recordingEnded
            .onEach { finish() }
            .launchIn(lifecycleScope)

        setContent {
            ListeningOverlay(onTap = ::stopCapture)
        }
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (event?.action == MotionEvent.ACTION_DOWN) {
            stopCapture()
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun stopCapture() {
        CaptureEvents.requestStop()
        finish()
    }
}

@Composable
private fun ListeningOverlay(onTap: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "listening")
    val scale by pulse.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            // Stop on touch-down rather than on click, to match the pre-Compose
            // behaviour where a tap ends the capture as early as possible.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    awaitPointerEvent()
                    onTap()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .scale(scale)
                    .background(Color(0xFFE53935), CircleShape)
            )
            Text(
                text = "Listening…",
                color = Color.White,
                fontSize = 24.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Tap anywhere to stop",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
