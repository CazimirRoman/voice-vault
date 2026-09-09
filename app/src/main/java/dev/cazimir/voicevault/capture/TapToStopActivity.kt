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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import dev.cazimir.voicevault.ui.theme.BrandIndigo
import dev.cazimir.voicevault.ui.theme.BrandPurple
import dev.cazimir.voicevault.ui.theme.BrandViolet
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF14121F).copy(alpha = 0.92f))
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
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            VoiceWave()
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

/**
 * An animated equalizer of rounded bars filled with the brand gradient. The motion is
 * synthetic (each bar oscillates on its own timing), not driven by the live mic level,
 * so it only signals "a capture is running" - it is not a true visualisation of speech.
 */
@Composable
private fun VoiceWave() {
    val transition = rememberInfiniteTransition(label = "voicewave")
    val durations = listOf(520, 610, 470, 680, 540, 640, 500)
    val floors = listOf(0.30f, 0.20f, 0.45f, 0.25f, 0.38f, 0.22f, 0.34f)

    val levels = ArrayList<Float>(durations.size)
    for (i in durations.indices) {
        val level = transition.animateFloat(
            initialValue = floors[i],
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = durations[i], easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$i"
        ).value
        levels.add(level)
    }

    Canvas(modifier = Modifier.size(width = 240.dp, height = 120.dp)) {
        val count = levels.size
        val gap = size.width * 0.045f
        val barWidth = (size.width - gap * (count - 1)) / count
        val brush = Brush.horizontalGradient(
            colors = listOf(BrandIndigo, BrandViolet, BrandPurple),
            startX = 0f,
            endX = size.width
        )
        val minHeight = size.height * 0.16f
        levels.forEachIndexed { i, level ->
            val h = minHeight + (size.height - minHeight) * level
            val x = i * (barWidth + gap)
            val top = (size.height - h) / 2f
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, top),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
