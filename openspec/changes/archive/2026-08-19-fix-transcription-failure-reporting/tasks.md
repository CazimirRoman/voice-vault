## 1. Outcome type and logging foundation

- [x] 1.1 Add `TranscriptionOutcome` sealed interface in `transcribe/` with `Success(notePath)`, `AlreadyHandled`, `NoSpeech(rawText)`, `ModelUnavailable(cause)`, `TranscribeFailed(cause)`, `NoteWriteFailed(cause)`
- [x] 1.2 Add a single logging tag constant for the app and confirm `Log` is usable from every affected package
- [x] 1.3 Add `Config` constants for the diagnostic record extension (`.log`) and the WAV header size used for duration math

## 2. Fix the race in TranscriptionQueue

- [x] 2.1 Change `transcribe()` to return `TranscriptionOutcome` instead of `Boolean`
- [x] 2.2 Re-check `wavFile.exists()` inside `processOne` after the mutex is held; return `AlreadyHandled` when the file is gone, without invoking the recognizer
- [x] 2.3 Add an in-flight set of canonical paths; return `AlreadyHandled` immediately when a path is already enqueued or running, and remove the path in the `finally` alongside the `pendingCount` decrement
- [x] 2.4 Classify `ensureLoaded` failures as `ModelUnavailable`, recognizer throws as `TranscribeFailed`, and `VaultWriter.writeNote` throws as `NoteWriteFailed` — split the single `catch` in `processOne` so the note write is not attributed to transcription
- [x] 2.5 Return `NoSpeech(rawText)` for empty text and for `isNonSpeechPlaceholder` matches, carrying the raw recognizer output
- [x] 2.6 Log every caught throwable in `transcribe`, `processOne`, and `preload` with the capture id
- [x] 2.7 Verify the model is still released exactly once when the queue drains, with the in-flight set in play

## 3. Diagnostic record in _pending/

- [x] 3.1 Add `VaultWriter.writeDiagnostic(captureId, text)` writing `_pending/<captureId>.log` through `AtomicFileWriter`, overwriting any previous attempt
- [x] 3.2 Add `VaultWriter.deleteDiagnostic(captureId)` and call it wherever pending audio is deleted (successful transcription, and the Discard receiver)
- [x] 3.3 Add `VaultWriter.noteExists(captureId)` checking `00-Inbox/<captureId>.md`
- [x] 3.4 Add a WAV duration helper deriving seconds from file size, and confirm `pendingAudioFiles()` still filters `.log` files out

## 4. Rework failure reporting in RecordingService

- [x] 4.1 Make `runCapture` branch exhaustively on `TranscriptionOutcome`; treat `Success` and `AlreadyHandled` as silent
- [x] 4.2 Guard every failure report behind `VaultWriter.noteExists(captureId)` — if the note is there, post nothing and raise no haptic
- [x] 4.3 Write the diagnostic record for each non-success, non-`AlreadyHandled` outcome before posting the notification
- [x] 4.4 Build per-outcome notification messages including the recording duration, and whisper's literal output for `NoSpeech`
- [x] 4.5 Drop the now-redundant `ModelProvisioner.isModelReady` second lookup in favor of the `ModelUnavailable` outcome
- [x] 4.6 Make `retryPending` skip haptics for `NoSpeech` while still posting the notification, and keep haptics on the first-attempt path in `runCapture`
- [x] 4.7 Cancel the failure notification whenever an outcome is `Success` or `AlreadyHandled`, on both the capture and sweep paths

## 5. Notification surface

- [x] 5.1 Extend `postFailure` to take the classified outcome and duration rather than a pre-baked message string
- [x] 5.2 Offer the Discard action only when the pending audio actually exists and the model is available
- [x] 5.3 Add a stale-alert sweep: enumerate `getActiveNotifications()` on the `capture_failures` channel and cancel any whose id is not in the set derived from current `pendingAudioFiles()`
- [x] 5.4 Call the stale-alert sweep from `MainActivity` start and from `retryPending`
- [x] 5.5 Make `PendingAudioActionReceiver` delete the diagnostic record alongside the audio

## 6. Tests

- [x] 6.1 Unit test: a queued file deleted before its turn yields `AlreadyHandled`, and the recognizer is never invoked
- [x] 6.2 Unit test: enqueueing the same path twice yields `AlreadyHandled` for the second
- [x] 6.3 Unit test: outcome classification for empty text, each non-speech placeholder, recognizer throw, and note-write throw
- [x] 6.4 Unit test: WAV duration derivation from file size
- [x] 6.5 Unit test: no failure is reported when a note already exists for the capture id
- [x] 6.6 Run `./gradlew test` and `./gradlew lint`

## 7. Device verification (Pixel 6 Pro, human required)

- [x] 7.1 Reproduce the race on the pre-fix build: two captures inside one minute produce two notes plus a spurious "no note could be produced" notification
- [x] 7.2 On the fixed build, repeat 7.1 and confirm two notes and zero notifications
- [x] 7.3 Confirm the three existing phantom notifications are cleared after installing the fixed build and opening the app
- [x] 7.4 Force a real `NoSpeech` (record silence) and confirm the notification names it as no-speech, shows the duration, quotes whisper's output, and offers Discard
- [x] 7.5 Confirm `_pending/<captureId>.log` appears beside the orphan audio, is readable in Obsidian on another device, and is deleted by Discard
- [x] 7.6 Force a `ModelUnavailable` failure (clear the model) and confirm the message differs from a no-speech failure and offers no Discard
- [x] 7.7 Confirm the at-risk haptic no longer fires on sweep-path `NoSpeech` re-reports
