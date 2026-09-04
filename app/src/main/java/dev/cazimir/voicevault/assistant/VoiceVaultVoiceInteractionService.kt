package dev.cazimir.voicevault.assistant

import android.service.voice.VoiceInteractionService

/**
 * Marker service that lets Voice Vault appear as a selectable digital assistant.
 * The session lifecycle lives in [VoiceVaultVoiceInteractionSessionService]; this
 * class exists only so the system has something to bind.
 */
class VoiceVaultVoiceInteractionService : VoiceInteractionService()
