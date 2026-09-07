## 1. Focus holder

- [x] 1.1 Add `capture/CaptureAudioFocus.kt` — a singleton in the shape of `CaptureController` owning one `AudioFocusRequest` plus an integer reference count, with `acquire(context, onLoss)` and `release()`.
- [x] 1.2 Build the request with `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`, `AudioAttributes` of `USAGE_ASSISTANT` + `CONTENT_TYPE_SPEECH`, an explicit executor/`Handler` for the focus-change listener, and `setAcceptsDelayedFocusGain(false)`.
- [x] 1.3 Make `acquire` request focus only when the count goes 0 → 1, and `release` abandon only when it returns to 0; guard all mutation with a lock so overlapping captures cannot race the count.
- [x] 1.4 Make `acquire` return normally (not throw, not signal failure) when `requestAudioFocus` returns `AUDIOFOCUS_REQUEST_FAILED`, while still counting the holder so the release path stays balanced.
- [x] 1.5 Route `AUDIOFOCUS_LOSS` and `AUDIOFOCUS_LOSS_TRANSIENT` to the per-capture `onLoss` callback; ignore `AUDIOFOCUS_GAIN` and duck notifications.

## 2. Wire into the capture flow

- [x] 2.1 In `RecordingService.runCapture`, acquire focus immediately after `registerScreenOffStop` and before `startTapToStopOverlay`, passing an `onLoss` that calls `stop.request()`.
- [x] 2.2 Wrap the release in the existing idempotent `Registration` helper so a double release cannot decrement the count twice.
- [x] 2.3 Release focus on the `catch` path around `audioRecorder.record`, alongside `screenOffStop.release()`.
- [x] 2.4 Release focus on the normal path, after `screenOffStop.release()` and before `WavEncoder.encode` — verify by reading the method that no encode/persist/transcribe step sits between the recording loop and the release.
- [x] 2.5 Confirm the vault-not-ready early return (before focus is acquired) still cannot leak the count.

## 3. Checks

- [x] 3.1 `./gradlew assembleDebug` builds clean.
- [x] 3.2 `./gradlew lint` reports no new findings.
- [x] 3.3 `./gradlew test` passes.
- [x] 3.4 Add a JVM unit test for the reference-count arithmetic: 0→1 requests, 1→2 does not re-request, 2→1 does not abandon, 1→0 abandons, and a repeated release is a no-op.

## 4. On-device verification (Pixel 6 Pro — requires a human, do not check from code inspection)

- [x] 4.1 Play music, trigger a capture via the power-button hold: music pauses before or as the start tick fires.
- [x] 4.2 Speak, then stop speaking and stay still: recording ends on the silence threshold (~5 s), not at the 3-minute cap. This is the bug being fixed.
- [x] 4.3 Music resumes at the end of the recording, while transcription is still running — not after the note appears.
- [x] 4.4 Read the resulting note: no song lyrics at the head of the transcript. If present, promote design.md Decision 6 (pre-roll discard) to a follow-up change.
- [x] 4.5 Trigger a capture with nothing playing: no perceptible extra delay before the start tick.
- [x] 4.6 Trigger a second capture while the first is still recording: music stays paused across both, and resumes only when the later one ends.
- [x] 4.7 Trigger a capture, then take an incoming call: recording ends, the stop buzz fires, and the partial audio produces a note.
- [x] 4.8 Leak check — after each of a vault-check failure, a microphone-acquisition failure, and a normal capture, confirm music resumes. A stuck count silently kills playback until reboot.
- [x] 4.9 Trigger a capture while already on a phone call: the capture still starts and completes (as `NoSpeech`, no buzz) rather than being refused.
