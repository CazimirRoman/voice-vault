package com.example.easynote.transcribe

import android.content.Context
import com.example.easynote.Config
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import dev.ffmpegkit.whisper.WhisperModel
import java.io.File

class WhisperTranscriber(private val context: Context) : Transcriber {

    private var model: WhisperModel? = null

    override suspend fun load() {
        val modelFile = ModelProvisioner.ensureModelFile(context)
        model = Whisper.loadModel(context, modelFile.absolutePath)
    }

    override suspend fun transcribe(wavFile: File): String {
        val loadedModel = model ?: error("Transcriber used before load()")
        val result = Whisper.transcribe(
            loadedModel,
            wavFile.absolutePath,
            WhisperConfig(language = Config.TRANSCRIPTION_LANGUAGE)
        )
        return result.text
    }

    override fun release() {
        model?.let { Whisper.releaseModel(it) }
        model = null
    }
}
