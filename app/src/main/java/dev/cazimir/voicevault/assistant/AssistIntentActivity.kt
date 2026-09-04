package dev.cazimir.voicevault.assistant

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dev.cazimir.voicevault.capture.RecordingService

/**
 * Secondary entry point for a plain ACTION_ASSIST dispatch, behaving identically
 * to the voice interaction session: start capture, render nothing, finish at once.
 */
class AssistIntentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intent = Intent(this, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_CAPTURE
        }
        startForegroundService(intent)
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
