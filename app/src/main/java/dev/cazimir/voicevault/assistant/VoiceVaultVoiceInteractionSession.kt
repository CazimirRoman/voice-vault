package dev.cazimir.voicevault.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import dev.cazimir.voicevault.capture.RecordingService

/**
 * Triggered by a power-button hold once Voice Vault holds the assistant role.
 * Starts the recording service and dismisses itself immediately - no window is
 * ever shown, so whatever was on screen stays on screen.
 */
class VoiceVaultVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val intent = Intent(context, RecordingService::class.java).apply {
            action = RecordingService.ACTION_START_CAPTURE
        }
        context.startForegroundService(intent)
        hide()
    }
}
