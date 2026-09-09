package dev.cazimir.voicevault.capture

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    // VibratorManager arrived in API 31 (S). On Android 10-12L we fall back to the deprecated
    // VIBRATOR_SERVICE, which is the only vibrator handle available below S.
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

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
