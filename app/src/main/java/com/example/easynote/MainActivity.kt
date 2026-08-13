package com.example.easynote

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.easynote.capture.RecordingService
import com.example.easynote.transcribe.ModelDownloadState
import com.example.easynote.transcribe.ModelDownloader
import com.example.easynote.ui.theme.EasyNoteTheme

/**
 * Not part of the capture flow itself - this screen only exists to grant the
 * permissions capture depends on, to select EasyNote as the digital assistant, and to
 * fetch the speech model on first launch. Also kicks off a retry scan for any audio
 * left pending from a previous failure.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Starts immediately rather than on a button press: without the model nothing
        // can be transcribed, so there is no reason to make the user ask for it.
        ModelDownloader.ensureDownloaded(this)

        startService(
            Intent(this, RecordingService::class.java).apply {
                action = RecordingService.ACTION_RETRY_PENDING
            }
        )

        setContent {
            EasyNoteTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SetupScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun SetupScreen(modifier: Modifier = Modifier) {
    val requestPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    val context = androidx.compose.ui.platform.LocalContext.current

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("EasyNote setup")
        Text("1. Grant microphone and notification permissions.")
        Button(onClick = {
            requestPermissions.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
            )
        }) {
            Text("Grant permissions")
        }

        Text("2. Allow all-files access so notes can be written into the vault.")
        Button(onClick = {
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            context.startActivity(intent)
        }) {
            Text("Open storage settings")
        }

        Text("3. Set EasyNote as the digital assistant.")
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }) {
            Text("Open assistant settings")
        }

        Text("4. Speech model (~57 MB, downloaded once).")
        ModelStep()
    }
}

@Composable
private fun ModelStep() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by ModelDownloader.state.collectAsState()

    // Anything captured before the model arrived is sitting in _pending/ untranscribed;
    // the download finishing is exactly the moment a retry can finally succeed.
    LaunchedEffect(state) {
        if (state is ModelDownloadState.Ready) {
            context.startService(
                Intent(context, RecordingService::class.java).apply {
                    action = RecordingService.ACTION_RETRY_PENDING
                }
            )
        }
    }

    when (val current = state) {
        is ModelDownloadState.Ready -> Text("Model ready. Capture works offline from here on.")

        is ModelDownloadState.Downloading -> {
            Text("Downloading… ${(current.fraction * 100).toInt()}%")
            LinearProgressIndicator(
                progress = { current.fraction },
                modifier = Modifier.fillMaxWidth()
            )
        }

        is ModelDownloadState.Failed -> {
            Text("Download failed: ${current.message}")
            Button(onClick = { ModelDownloader.ensureDownloaded(context) }) {
                Text("Retry download")
            }
        }

        is ModelDownloadState.Missing -> {
            Text("Model not downloaded yet.")
            Button(onClick = { ModelDownloader.ensureDownloaded(context) }) {
                Text("Download model")
            }
        }
    }
}
