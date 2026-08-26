package com.example.easynote.capture

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.example.easynote.Config

/**
 * Reference-counted holder for one exclusive, transient [AudioFocusRequest], in the shape of
 * [CaptureController]. The first `acquire()` across overlapping captures requests focus; only
 * the `release()` that drops the count back to zero abandons it - an earlier-ending capture
 * must not un-pause playback while another is still recording.
 *
 * Failing to acquire focus is never fatal here: the caller is still counted so the release
 * path stays balanced, and the capture proceeds regardless.
 */
object CaptureAudioFocus {
    private val refCount = FocusRefCount()
    private var audioManager: AudioManager? = null
    private var request: AudioFocusRequest? = null
    private val lossListeners = mutableListOf<() -> Unit>()

    private val listenerThread by lazy {
        HandlerThread("CaptureAudioFocusListener").apply { start() }
    }
    private val listenerHandler by lazy { Handler(listenerThread.looper) }

    fun acquire(context: Context, onLoss: () -> Unit) {
        synchronized(this) {
            lossListeners += onLoss
            if (refCount.acquire()) {
                val manager = context.applicationContext.getSystemService(AudioManager::class.java)
                audioManager = manager
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener(::onFocusChange, listenerHandler)
                    .build()
                request = focusRequest
                val result = manager.requestAudioFocus(focusRequest)
                if (result == AudioManager.AUDIOFOCUS_REQUEST_FAILED) {
                    // A refused request must never block a capture - a noisy note is strictly
                    // better than no note. The count is already incremented, so release() stays
                    // balanced regardless of whether the request succeeded.
                    Log.w(Config.LOG_TAG, "audio focus request was refused")
                }
            }
        }
    }

    fun release() {
        synchronized(this) {
            if (refCount.release()) {
                val manager = audioManager
                val focusRequest = request
                audioManager = null
                request = null
                lossListeners.clear()
                if (manager != null && focusRequest != null) {
                    manager.abandonAudioFocusRequest(focusRequest)
                }
            }
        }
    }

    private fun onFocusChange(change: Int) {
        if (change != AudioManager.AUDIOFOCUS_LOSS && change != AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            // AUDIOFOCUS_GAIN and the duck notifications don't apply - EasyNote never plays
            // anything and a lost-then-regained focus mid-recording still ends as a stop.
            return
        }
        val listeners = synchronized(this) { lossListeners.toList() }
        listeners.forEach { it() }
    }
}

/**
 * Pure reference-count arithmetic, kept free of Android types so it can be unit-tested on the
 * JVM without a device or Robolectric.
 */
internal class FocusRefCount {
    private var count = 0

    /** Returns true exactly when this call takes the count from 0 to 1. */
    fun acquire(): Boolean {
        count++
        return count == 1
    }

    /** Returns true exactly when this call drops the count to 0. A release below 0 is a no-op. */
    fun release(): Boolean {
        if (count == 0) return false
        count--
        return count == 0
    }
}
