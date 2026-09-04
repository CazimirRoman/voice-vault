package dev.cazimir.voicevault.capture

import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator = context.getSystemService(VibratorManager::class.java).defaultVibrator

    /** Microphone is live — start speaking. */
    fun started() {
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 60, 90, 60), -1))
    }

    /** Recording ended — safe to pocket the phone. */
    fun stopped() {
        vibrator.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    /** A capture needs attention — distinct 5s rumble. */
    fun atRisk() {
        vibrator.vibrate(VibrationEffect.createOneShot(5_000, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
