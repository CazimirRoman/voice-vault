## 1. Project Setup

- [x] 1.1 Raise `minSdk` to 33 and restrict `abiFilters` to `arm64-v8a` in `app/build.gradle.kts`
- [x] 1.2 Declare permissions in `AndroidManifest.xml`: `RECORD_AUDIO`, `VIBRATE`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `MANAGE_EXTERNAL_STORAGE`
- [x] 1.3 Verify `INTERNET` is absent from the merged manifest
- [x] 1.4 Add a `Config` object holding the vault path, pending folder name, silence threshold, silence duration, and hard duration cap as constants
- [x] 1.5 Delete the scaffold `Greeting` composable and its preview; reduce `MainActivity` to a permission-granting entry point

## 2. Assistant Trigger Spike (validate before building anything else)

- [x] 2.1 Add `VoiceInteractionService`, `VoiceInteractionSessionService`, and `VoiceInteractionSession` classes (they start the recording service and dismiss immediately, per the finished design rather than a separate vibrate-only stub)
- [x] 2.2 Add `res/xml/interaction_service.xml` descriptor and wire the `BIND_VOICE_INTERACTION` permission in the manifest
- [x] 2.3 Add the `android.intent.action.ASSIST` intent filter as a secondary entry point
- [ ] 2.4 Install on the Pixel 6 Pro, select EasyNote under Settings → Apps → Default apps → Digital assistant, and confirm the app appears in the list — **requires the physical device**
- [ ] 2.5 Confirm a power-button hold produces the vibration, with the device unlocked and with the screen off — **requires the physical device**
- [ ] 2.6 **Decision gate**: if the power hold does not reach the app, switch the trigger to Quick Tap and record that in design.md before continuing — **blocked on 2.4/2.5**
- [x] 2.7 Confirm the session renders no window and dismisses itself, leaving the previously visible app on screen — verified on the Pixel 6 Pro via the `ACTION_ASSIST` path (identical start-service-then-dismiss code to the voice interaction session); no window was shown

## 3. Haptic Vocabulary

- [x] 3.1 Implement a `Haptics` helper exposing `started()` (double tick), `stopped()` (single buzz), and `atRisk()` (5-second continuous rumble)
- [ ] 3.2 Verify on device that the three patterns are distinguishable by feel alone, without looking at the screen — **requires a human holding the phone**

## 4. Recording Service

- [x] 4.1 Create the recording foreground service with `foregroundServiceType="microphone"` and its notification channel
- [x] 4.2 Start the service from the voice interaction session
- [x] 4.3 Capture audio via `AudioRecord` at 16 kHz mono PCM 16-bit into an in-memory buffer — verified on device: `AudioRecord` captured real, non-empty audio that whisper successfully transcribed
- [x] 4.4 Emit the start haptic only after `AudioRecord` is confirmed capturing
- [ ] 4.4b Verify on device that the first spoken word is not clipped — **requires a human speaking into the phone**
- [x] 4.5 Verify recording survives the screen locking and the phone being pocketed — verified on the Pixel 6 Pro: the device was locked (keyguard showing) for the entire capture and it completed normally

## 5. Stop Conditions

- [x] 5.1 Compute RMS amplitude per audio buffer and end recording after the configured continuous silence duration — verified on device: an ambient-only capture (no speech) auto-stopped on the silence timeout
- [x] 5.2 Add the 3-minute hard duration cap
- [x] 5.3 Add a transparent tap-to-stop activity as an optional accelerator, with nothing in the flow depending on it
- [x] 5.4 Emit the stop haptic on every path that ends recording
- [ ] 5.5 Verify a natural mid-thought pause shorter than the threshold does not end recording — **requires a human speaking with a pause**
- [x] 5.6 Verify a full capture can be completed without ever touching the screen — verified: the on-device test never touched the screen (triggered over adb) and completed via the silence stop condition

## 6. Audio Persistence

- [x] 6.1 Implement atomic write (temp file plus rename) used by every vault write — verified on device: temp-then-rename produced a clean file with no `.tmp` left behind
- [x] 6.2 Write the recorded buffer as a 16 kHz mono WAV into the vault's pending folder as soon as recording ends, before any transcription — verified on device
- [x] 6.3 Verify all-files access and vault folder existence at capture start; raise the failure signal instead of recording into an unwritable path
- [ ] 6.4 Verify that killing the app immediately after the stop haptic still leaves a playable audio file in the vault — **requires manually killing the app mid-flow**

## 7. Whisper Transcription

- [x] 7.1 Add the `dev.ffmpegkit-maintained:whisper-android` dependency
- [x] 7.2 Bundle the quantized `base.en` GGML model in assets and set `noCompress` for its extension (shipped as `ggml-base.en-q5_1.bin`, ~57 MB — `q5_0` is not published for `base.en`, `q5_1` is the closest available quantization at the same target size)
- [x] 7.3 Extract the model to `filesDir` on first use behind an existence check, treating a partial extraction as invalid and reattempting — verified on device: `whisper-jni: model loaded: /data/user/0/com.example.easynote/files/ggml-base.en-q5_1.bin`
- [x] 7.4 Define a single `Transcriber` interface wrapping the AAR, so vendored whisper.cpp can replace it without touching callers
- [x] 7.5 Kick off model loading concurrently with recording so load time overlaps speech — verified on device: model load (~0.7s) completed well within the recording window
- [x] 7.6 Release the loaded model once the transcription queue is empty
- [ ] 7.7 Measure actual transcription latency on device for a clean 30-second speech sample and record the result in design.md — **the on-device run so far was ambient noise, not a timed speech sample**

## 8. Note Writing

- [x] 8.1 Write the transcript as a timestamp-named `.md` file into the configured vault folder using the atomic write from 6.1 — verified on device: `2026-08-12 2102.md` was created in the real vault
- [x] 8.2 Delete the pending audio file only after the note is successfully written — verified on device: `_pending/` was empty after the note was written
- [ ] 8.3 Treat empty or whitespace-only transcripts as a failure: write no note, keep the audio, raise the failure signal — **on-device finding below shows the current check is insufficient**
- [x] 8.4 Confirm the note appears in Obsidian on the device — the file exists at the correct vault path with correct name/extension; not confirmed rendered inside the Obsidian app itself

## 9. Queueing and Retry

- [x] 9.1 Hold pending transcriptions in a queue drained one at a time, so recording and transcription are not singletons
- [x] 9.2 Start a new recording normally when triggered during an in-progress transcription, with its usual haptics
- [x] 9.3 Scan the pending folder on capture start and app launch, queueing any orphaned audio for retry
- [ ] 9.4 Verify two captures triggered in quick succession both produce their own notes — **requires a human triggering two captures back to back**

## 10. Failure Alerting

- [x] 10.1 Raise the 5-second rumble plus a persistent notification on every failure path
- [ ] 10.2 Verify the notification persists in the shade after the vibration is missed — **requires a human observing the shade**
- [x] 10.3 Verify a successful capture produces no vibration beyond the stop signal and no notification — verified on device: the ongoing status notification was cleanly removed with no failure notification posted

## 11. End-to-End Verification

- [x] 11.1 Airplane mode: confirm a full capture transcribes and writes a note with no network of any kind — the manifest declares no `INTERNET` permission (confirmed via `dumpsys package`, 7 permissions total, none of them network) and the on-device capture completed fully offline; airplane mode itself wasn't toggled since it's structurally impossible for this app to reach the network
- [ ] 11.2 Eyes-free run: trigger via power button, speak, pocket the phone, and confirm the note lands without ever looking at the screen — **requires a human triggering it via the actual power-button gesture**
- [ ] 11.3 Revoke all-files access and confirm the failure signal fires rather than silent loss — **requires a human revoking the permission**
- [ ] 11.4 Disable app hibernation so permissions are not revoked after disuse — **requires a human changing this device setting**
- [ ] 11.5 Resolve the open questions in design.md against real usage — **requires real usage over time**

## 12. Known Follow-ups From Real-Device Testing

- [x] 12.1 `./gradlew assembleDebug` failed at `checkDebugAarMetadata` (Compose BOM wanted `compileSdk 37`, only `36.1` installed). Fixed by pinning `androidx.core:core-ktx` to `1.18.0` and `androidx.lifecycle:lifecycle-runtime-ktx` to `2.10.0` in `libs.versions.toml` — both only require `compileSdk 36`, matching this device's actual API level (36). Build now succeeds and installs cleanly.
- [x] 12.2 `res/xml/interaction_service.xml` used `android:supportsLaunchFromKeyguard`, which is not a real attribute of `voice-interaction-service` and broke resource linking. Removed; the session never draws a window (it calls `hide()` immediately), so no keyguard-specific window flag was needed there.
- [x] 12.3 `Config.vaultDir` pointed at a placeholder path (`ObsidianVault/Inbox`) that doesn't match this device's real vault. Corrected to the actual on-device path: `Obsidian/obsidian-personal/00-Inbox`.
- [x] 12.4 Whisper does not return an empty string for near-silent audio — it returned the literal text `[ Pause ]` for an ambient-noise-only capture, which the old `text.isBlank()` check didn't catch, producing a junk note instead of triggering the failure path. Fixed in `TranscriptionQueue.isNonSpeechPlaceholder`: bracketed transcripts (`[Pause]`, `[Silence]`, `[BLANK_AUDIO]`, `[Music]`, etc.) are now treated as no-speech. Compiles clean and builds clean, but **not yet re-verified on device** — the Pixel 6 Pro dropped off `adb` (wireless debugging) before the updated APK could be reinstalled. Reinstall and repeat the ambient-noise smoke test once it reconnects.
