package com.example.easynote.assistant

import android.service.voice.VoiceInteractionService

/**
 * Marker service that lets EasyNote appear as a selectable digital assistant.
 * The session lifecycle lives in [EasyNoteVoiceInteractionSessionService]; this
 * class exists only so the system has something to bind.
 */
class EasyNoteVoiceInteractionService : VoiceInteractionService()
