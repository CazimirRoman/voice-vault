package dev.cazimir.voicevault.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class ExternalStop {
    @Volatile
    private var requested = false

    fun request() {
        requested = true
    }

    fun isRequested(): Boolean = requested
}

/** The recording currently in progress, if any. Lets the tap-to-stop overlay reach it. */
object CaptureController {
    @Volatile
    var activeStop: ExternalStop? = null
}

/** In-process signalling between the recording service and the tap-to-stop overlay activity. */
object CaptureEvents {
    private val _recordingEnded = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val recordingEnded = _recordingEnded.asSharedFlow()

    /** Normalised mic level (0..1) of the current capture, driving the listening overlay. */
    private val _amplitude = MutableStateFlow(0f)
    val amplitude = _amplitude.asStateFlow()

    fun requestStop() {
        CaptureController.activeStop?.request()
    }

    fun publishAmplitude(level: Float) {
        _amplitude.value = level.coerceIn(0f, 1f)
    }

    fun notifyRecordingEnded() {
        _amplitude.value = 0f
        _recordingEnded.tryEmit(Unit)
    }
}
