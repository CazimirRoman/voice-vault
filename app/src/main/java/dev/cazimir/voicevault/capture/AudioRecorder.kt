package dev.cazimir.voicevault.capture

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import dev.cazimir.voicevault.Config
import kotlinx.coroutines.isActive
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.sqrt

/** RMS mapped to a full-scale (1.0) meter reading; ordinary speech reaches roughly here. */
private const val AMPLITUDE_FULL_SCALE_RMS = 4500.0

class AudioRecorder {

    data class Result(val pcm: ByteArray, val sampleRateHz: Int)

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun record(
        externalStop: ExternalStop,
        onCapturing: () -> Unit,
        onAmplitude: (Float) -> Unit = {},
    ): Result {
        val minBufferSize = AudioRecord.getMinBufferSize(
            Config.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = if (minBufferSize > 0) minBufferSize else 4096

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            Config.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize * 2
        )

        val output = ByteArrayOutputStream()
        val readBuffer = ShortArray(bufferSize / 2)
        val bufferDurationMs = (readBuffer.size * 1000L) / Config.SAMPLE_RATE_HZ
        var silentDurationMs = 0L
        var totalDurationMs = 0L

        try {
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("AudioRecord failed to enter recording state")
            }
            onCapturing()

            while (coroutineContext.isActive) {
                val read = audioRecord.read(readBuffer, 0, readBuffer.size)
                if (read <= 0) break

                appendPcm(output, readBuffer, read)

                val currentRms = rms(readBuffer, read)
                // Perceptual (sqrt) mapping so ordinary speech fills a lively range rather
                // than hugging the bottom of a linear scale.
                onAmplitude(sqrt(currentRms / AMPLITUDE_FULL_SCALE_RMS).toFloat())

                silentDurationMs = if (currentRms < Config.SILENCE_RMS_THRESHOLD) {
                    silentDurationMs + bufferDurationMs
                } else {
                    0L
                }
                totalDurationMs += bufferDurationMs

                if (silentDurationMs >= Config.SILENCE_DURATION_MS) break
                if (totalDurationMs >= Config.MAX_RECORDING_DURATION_MS) break
                if (externalStop.isRequested()) break
            }
        } finally {
            audioRecord.stop()
            audioRecord.release()
        }

        return Result(output.toByteArray(), Config.SAMPLE_RATE_HZ)
    }

    private fun rms(buffer: ShortArray, length: Int): Double {
        var sum = 0.0
        for (i in 0 until length) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        return sqrt(sum / length)
    }

    private fun appendPcm(output: ByteArrayOutputStream, buffer: ShortArray, length: Int) {
        val bytes = ByteArray(length * 2)
        for (i in 0 until length) {
            val sample = buffer[i].toInt()
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        output.write(bytes)
    }
}
