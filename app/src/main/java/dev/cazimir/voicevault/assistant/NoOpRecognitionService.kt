package dev.cazimir.voicevault.assistant

import android.content.Intent
import android.speech.RecognitionService

/**
 * The voice-interaction-service descriptor requires a recognitionService component
 * to exist, even though this app never drives speech through the classic
 * RecognitionService API - transcription happens entirely offline via whisper.
 */
class NoOpRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {}
    override fun onCancel(listener: Callback?) {}
    override fun onStopListening(listener: Callback?) {}
}
