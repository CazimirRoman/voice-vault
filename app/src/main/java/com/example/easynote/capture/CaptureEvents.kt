package com.example.easynote.capture

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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

    fun requestStop() {
        CaptureController.activeStop?.request()
    }

    fun notifyRecordingEnded() {
        _recordingEnded.tryEmit(Unit)
    }
}
