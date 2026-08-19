## Why

The app has been posting "No note could be produced from `<capture>`.wav" alerts for captures that actually **succeeded**. On-device forensics found three live failure notifications, an empty `_pending/` folder, and a correctly transcribed note in `00-Inbox` for every one of them — a 3/3 false-positive rate, and zero real data loss.

The cause is a race: the retry sweep snapshots `_pending/` at sweep start, then blocks on the transcription mutex behind the very transcription that deletes the file it is holding. When it finally gets the lock, it hands whisper a path that no longer exists. The resulting failure is indistinguishable from a real one because `TranscriptionQueue.transcribe` returns a bare `Boolean` — six structurally different outcomes collapse into one `false`, and every `catch (t: Throwable)` in the app discards the throwable without logging it. There is no `Log` call anywhere in the codebase, so even a tethered `adb logcat` reveals nothing.

These two defects compound: the race manufactures phantom failures, and the one-bit result channel makes them unfalsifiable. A third defect makes them permanent — `cancelFailure` only fires when a sweep *finds* a file and transcribes it, so once the WAV is gone nothing can ever clear its notification.

## What Changes

- **Fix the race.** The retry sweep re-checks that a pending file still exists after acquiring the transcription lock, and treats a vanished file as "already handled" rather than a failure.
- **Suppress phantom alerts at the source.** No failure is reported for a capture that already has a note in the vault.
- **Replace the `Boolean` result with a typed outcome.** `TranscriptionQueue.transcribe` returns a sealed result distinguishing: success, no speech detected (with the raw whisper output), model unavailable, audio file vanished, transcription error (with cause), and note-write error (with cause).
- **Make failure notifications self-describing.** Each failure states which of those outcomes occurred, the recording's duration, and — when whisper produced text that was filtered as a non-speech placeholder — what it actually heard.
- **Let the user know what they are discarding.** The Discard action is only offered for audio that is genuinely still pending, and the notification carries enough detail (duration, cause, what whisper heard) to make the decision without a file manager.
- **Add diagnostic logging.** Every currently silent `catch` logs its throwable. A durable per-capture failure record is written beside the orphan audio in `_pending/`, so a failure that happened hours ago in a pocket is still diagnosable without a tethered laptop.
- **Clear stale alerts.** A failure notification whose audio is no longer pending is cancelled on the next sweep.

Not in scope: any settings or list UI, changing the capture or write ordering, changing the haptic vocabulary, or altering when audio is deleted.

## Capabilities

### New Capabilities

- `failure-diagnostics`: How a failed capture is classified, recorded durably, and explained to the user — the typed outcome taxonomy, the per-capture diagnostic record in `_pending/`, and the logging contract for otherwise-silent failure paths.

### Modified Capabilities

- `offline-transcription`: Transcription reports a typed outcome rather than a bare success/failure flag, and distinguishes "no speech in the audio" from "transcription could not be attempted". Adds the requirement that a pending file which disappears before its turn in the queue is not a failure.
- `vault-writing`: The retry sweep must not report a failure for audio that has already been transcribed or removed; a stale failure notification must be cleared; and the alert must identify the capture well enough to act on it. Also narrows "a note at risk raises a distinct alert" so that phantom failures raise no alert at all.

## Impact

- `transcribe/TranscriptionQueue.kt` — sealed result type, existence re-check under the lock, per-outcome classification, logging.
- `transcribe/Transcriber.kt` / `WhisperTranscriber.kt` — no interface change expected; failures continue to surface as thrown exceptions, now captured rather than discarded.
- `capture/RecordingService.kt` — sweep re-check, note-exists guard before posting a failure, stale-notification cleanup, per-outcome message and haptic decisions.
- `notify/CaptureNotifications.kt` — richer failure content, conditional Discard action.
- `vault/VaultWriter.kt` — helpers to test for an existing note and to write/read the diagnostic record.
- `Config.kt` — diagnostic record filename/extension constant.
- Tests: `app/src/test` gains coverage for outcome classification and the vanished-file path; the race itself needs a device-level check on the Pixel 6 Pro.
- No new permissions, no new dependencies, no network access.
