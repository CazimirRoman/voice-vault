## 1. Project Setup

- [ ] 1.1 Raise `minSdk` to 33 and restrict `abiFilters` to `arm64-v8a` in `app/build.gradle.kts`
- [ ] 1.2 Declare permissions in `AndroidManifest.xml`: `RECORD_AUDIO`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `MANAGE_EXTERNAL_STORAGE`
- [ ] 1.3 Verify `INTERNET` is absent from the merged manifest
- [ ] 1.4 Add a `Config` object holding the vault path, pending folder name, silence threshold, silence duration, and hard duration cap as constants
- [ ] 1.5 Delete the scaffold `Greeting` composable and its preview; reduce `MainActivity` to a permission-granting entry point

## 2. Assistant Trigger Spike (validate before building anything else)

- [ ] 2.1 Add `VoiceInteractionService`, `VoiceInteractionSessionService`, and `VoiceInteractionSession` classes that do nothing but vibrate once
- [ ] 2.2 Add `res/xml/interaction_service.xml` descriptor and wire the `BIND_VOICE_INTERACTION` permission in the manifest
- [ ] 2.3 Add the `android.intent.action.ASSIST` intent filter as a secondary entry point
- [ ] 2.4 Install on the Pixel 6 Pro, select EasyNote under Settings → Apps → Default apps → Digital assistant, and confirm the app appears in the list
- [ ] 2.5 Confirm a power-button hold produces the vibration, with the device unlocked and with the screen off
- [ ] 2.6 **Decision gate**: if the power hold does not reach the app, switch the trigger to Quick Tap (Settings → System → Gestures → Quick Tap → Open app) and record that in design.md before continuing
- [ ] 2.7 Confirm the session renders no window and dismisses itself, leaving the previously visible app on screen

## 3. Haptic Vocabulary

- [ ] 3.1 Implement a `Haptics` helper exposing `started()` (double tick), `stopped()` (single buzz), and `atRisk()` (5-second continuous rumble)
- [ ] 3.2 Verify on device that the three patterns are distinguishable by feel alone, without looking at the screen

## 4. Recording Service

- [ ] 4.1 Create the recording foreground service with `foregroundServiceType="microphone"` and its notification channel
- [ ] 4.2 Start the service from the voice interaction session, replacing the spike's vibrate-only behavior
- [ ] 4.3 Capture audio via `AudioRecord` at 16 kHz mono PCM 16-bit into an in-memory buffer
- [ ] 4.4 Emit the start haptic only after `AudioRecord` is confirmed capturing; verify the first spoken word is not clipped
- [ ] 4.5 Verify recording survives the screen locking and the phone being pocketed

## 5. Stop Conditions

- [ ] 5.1 Compute RMS amplitude per audio buffer and end recording after the configured continuous silence duration
- [ ] 5.2 Add the 3-minute hard duration cap
- [ ] 5.3 Add a transparent tap-to-stop activity as an optional accelerator, with nothing in the flow depending on it
- [ ] 5.4 Emit the stop haptic on every path that ends recording
- [ ] 5.5 Verify a natural mid-thought pause shorter than the threshold does not end recording
- [ ] 5.6 Verify a full capture can be completed without ever touching the screen

## 6. Audio Persistence

- [ ] 6.1 Implement atomic write (temp file plus rename) used by every vault write
- [ ] 6.2 Write the recorded buffer as a 16 kHz mono WAV into the vault's pending folder as soon as recording ends, before any transcription
- [ ] 6.3 Verify all-files access and vault folder existence at capture start; raise the failure signal instead of recording into an unwritable path
- [ ] 6.4 Verify that killing the app immediately after the stop haptic still leaves a playable audio file in the vault

## 7. Whisper Transcription

- [ ] 7.1 Add the `dev.ffmpegkit-maintained:whisper-android` dependency
- [ ] 7.2 Bundle the quantized `base.en` GGML model in assets and set `noCompress` for its extension
- [ ] 7.3 Extract the model to `filesDir` on first use behind an existence check, treating a partial extraction as invalid and reattempting
- [ ] 7.4 Define a single `Transcriber` interface wrapping the AAR, so vendored whisper.cpp can replace it without touching callers
- [ ] 7.5 Kick off model loading concurrently with recording so load time overlaps speech
- [ ] 7.6 Release the loaded model once the transcription queue is empty
- [ ] 7.7 Measure actual transcription latency on device for a 30-second capture and record the result in design.md

## 8. Note Writing

- [ ] 8.1 Write the transcript as a timestamp-named `.md` file into the configured vault folder using the atomic write from 6.1
- [ ] 8.2 Delete the pending audio file only after the note is successfully written
- [ ] 8.3 Treat empty or whitespace-only transcripts as a failure: write no note, keep the audio, raise the failure signal
- [ ] 8.4 Confirm the note appears in Obsidian on the device

## 9. Queueing and Retry

- [ ] 9.1 Hold pending transcriptions in a queue drained one at a time, so recording and transcription are not singletons
- [ ] 9.2 Start a new recording normally when triggered during an in-progress transcription, with its usual haptics
- [ ] 9.3 Scan the pending folder on capture start and app launch, queueing any orphaned audio for retry
- [ ] 9.4 Verify two captures triggered in quick succession both produce their own notes

## 10. Failure Alerting

- [ ] 10.1 Raise the 5-second rumble plus a persistent notification on every failure path
- [ ] 10.2 Verify the notification persists in the shade after the vibration is missed
- [ ] 10.3 Verify a successful capture produces no vibration beyond the stop signal and no notification

## 11. End-to-End Verification

- [ ] 11.1 Airplane mode: confirm a full capture transcribes and writes a note with no network of any kind
- [ ] 11.2 Eyes-free run: trigger, speak, pocket the phone, and confirm the note lands without ever looking at the screen
- [ ] 11.3 Revoke all-files access and confirm the failure signal fires rather than silent loss
- [ ] 11.4 Disable app hibernation so permissions are not revoked after disuse
- [ ] 11.5 Resolve the open questions in design.md against real usage: timestamp vs first-words filenames, whether the duration cap needs its own haptic, and whether a success signal is wanted
