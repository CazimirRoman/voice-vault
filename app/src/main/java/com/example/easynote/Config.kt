package com.example.easynote

import android.os.Environment
import java.io.File

object Config {
    private const val VAULT_ROOT = "Obsidian/obsidian-personal"
    private const val INBOX_FOLDER = "00-Inbox"
    private const val PENDING_FOLDER = "_pending"

    val vaultDir: File
        get() = File(Environment.getExternalStorageDirectory(), VAULT_ROOT)

    val inboxDir: File
        get() = File(vaultDir, INBOX_FOLDER)

    val pendingDir: File
        get() = File(inboxDir, PENDING_FOLDER)

    const val SAMPLE_RATE_HZ = 16_000
    const val SILENCE_RMS_THRESHOLD = 700.0
    const val SILENCE_DURATION_MS = 5_000L
    const val MAX_RECORDING_DURATION_MS = 180_000L

    /** Tag for every `Log` call in the app, so a tethered logcat session can filter on one string. */
    const val LOG_TAG = "EasyNote"

    /** Extension for the per-capture diagnostic record written beside orphan audio in `_pending/`. */
    const val DIAGNOSTIC_RECORD_EXTENSION = "log"

    /** Byte size of the fixed PCM16 mono header `WavEncoder` writes, used to derive duration from file size. */
    const val WAV_HEADER_SIZE_BYTES = 44

    const val MODEL_FILE_NAME = "ggml-base.en-q5_1.bin"

    /**
     * The model is fetched once on first launch instead of shipping in assets: at ~57 MB it
     * dominates both the APK and the git repository. This is the only network access the app
     * ever makes - no audio, text, or telemetry is ever sent anywhere.
     *
     * Size and digest are pinned so a truncated or substituted download is rejected rather
     * than handed to whisper, which would otherwise fail much later and far less clearly.
     * Both must be updated together with [MODEL_FILE_NAME] when swapping models.
     */
    const val MODEL_DOWNLOAD_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en-q5_1.bin"
    const val MODEL_SIZE_BYTES = 59_721_011L
    const val MODEL_SHA256 = "4baf70dd0d7c4247ba2b81fafd9c01005ac77c2f9ef064e00dcf195d0e2fdd2f"

    /**
     * ISO 639-1 code forced on every transcription. Must stay in sync with the model:
     * the `.en` models above cannot decode anything but English, so any non-"en" value
     * here also requires swapping in a multilingual model. "auto" is deliberately not
     * used - detection on a few seconds of speech is unreliable, and this is a
     * single-language device.
     */
    const val TRANSCRIPTION_LANGUAGE = "en"
}
