## Context

`AudioRecorder.record()` calls `AudioRecord.startRecording()` directly. Nothing in the app has ever touched `AudioManager` — a grep for `AudioManager|AudioFocus` across `app/src/main` returns nothing. The consequence is that a capture triggered during playback records the speaker output through the microphone.

The secondary effect is the more serious one. `AudioRecorder` computes an RMS per buffer and ends the recording once `SILENCE_DURATION_MS` (5 s) of buffers fall below `SILENCE_RMS_THRESHOLD` (700.0). Speaker output keeps every buffer above that threshold, so `silentDurationMs` resets on every iteration and the silence stop can never fire. With music playing, the only surviving stop conditions are screen-off, tap, and the 3-minute hard cap — the walk-away backstop described in the `voice-capture` spec is gone.

Constraints that shape the design:

- `minSdk = 33`, so `AudioFocusRequest` (API 26) is available with no compat shim and no version branch. No new Gradle dependency.
- `RecordingService` supports overlapping captures. `runCapture` is launched once per `onStartCommand`, and CLAUDE.md names "don't reintroduce a singleton assumption" as an invariant.
- The start haptic must fire only after `AudioRecord` is confirmed capturing — an earlier tick clips the first spoken word.
- Audio must reach `_pending/` before transcription is attempted. Nothing in this change may reorder that.
- `runCapture` has two teardown points today: the `catch` around `audioRecorder.record` and the normal path just after it. Both already release the screen-off receiver via the idempotent `Registration` helper.

## Goals / Non-Goals

**Goals:**

- Pause other apps' playback for exactly the duration of a recording, and ask them to resume the moment it ends.
- Restore the silence stop condition as a working backstop when audio is playing.
- Keep audio out of the transcript: no song lyrics rendered into a note.
- Survive overlapping captures without the earlier one un-pausing playback for the later one.
- Turn focus loss (incoming call) into a clean stop instead of five seconds of dead air followed by the silence stop.

**Non-Goals:**

- Guaranteeing that playback resumes. Resume is the other app's decision on `AUDIOFOCUS_GAIN`; the app can only make the request correctly.
- Switching `MediaRecorder.AudioSource.MIC` to `VOICE_RECOGNITION`. Related symptom, separate change, separate on-device verification.
- Suppressing the other app's fade-out tail from the head of the recording (see Decision 6).
- Any user-visible surface. No setting, no toggle, no notification text change.

## Decisions

### 1. `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE`, not `GAIN` or `TRANSIENT_MAY_DUCK`

- **`GAIN`** (permanent) tells the other app it will not get focus back, so well-behaved players stop rather than pause and do not resume. That fails the second half of the request.
- **`TRANSIENT_MAY_DUCK`** invites the system to lower the other app's volume instead of pausing it. Ducked playback is typically ~20% volume and still lands in the microphone well above `SILENCE_RMS_THRESHOLD`, so it fixes the annoyance and leaves the actual bug in place.
- **`TRANSIENT_EXCLUSIVE`** is what Android documents for recording and speech recognition: the other app receives `AUDIOFOCUS_LOSS_TRANSIENT` and pauses, the system does not auto-duck, and the app is asked to resume on `AUDIOFOCUS_GAIN`.

### 2. `USAGE_ASSISTANT` + `CONTENT_TYPE_SPEECH` attributes

EasyNote is the registered system assistant (`EasyNoteVoiceInteractionService`), so this is not an approximation — it is the literal usage. The attributes only affect focus arbitration here, since the app plays nothing.

### 3. Focus brackets the recording loop, not the service lifetime

```
runCapture()
  startForeground / vault check
  preload + retryPending          (async)
  registerScreenOffStop(stop)
  ▶ acquire focus                          ─┐
  startTapToStopOverlay()                   │  focus held
  audioRecorder.record(stop) { tick }       │  (seconds)
  screenOffStop.release()                   │
  ▶ release focus                          ─┘
  haptics.stopped()
  WavEncoder.encode
  VaultWriter.writePendingAudio    ← durability checkpoint, unchanged
  TranscriptionQueue.transcribe    ← seconds to minutes; music is already back
```

Holding focus across transcription would silence the user's music long after they had pocketed the phone, and would couple playback to a queue whose depth depends on unrelated captures. Focus is released at the same point the screen-off receiver is, which keeps one teardown story rather than two.

Focus is acquired *before* `startRecording()` rather than after, so the other app begins fading out as early as possible. The start haptic still fires from `AudioRecorder`'s `onCapturing` callback after `RECORDSTATE_RECORDING` is confirmed, so the "no clipped first word" invariant is untouched.

### 4. Reference-counted holder in `capture/`, mirroring `CaptureController`

A per-capture `AudioFocusRequest` would let the earlier of two overlapping recordings hand playback back while the later one is still running. A small object in `capture/` owns a single `AudioFocusRequest` and an integer count: the first acquire builds and requests it, subsequent acquires increment, and only the release that drops the count to zero abandons it.

This deliberately follows the shape of the existing `CaptureController`/`CaptureEvents` singletons rather than inventing a new coordination mechanism, and each capture's release goes through the same idempotent `Registration` wrapper already used for the screen-off receiver — so a double release from the error path cannot decrement the count twice.

Alternative considered: giving each capture its own listener instance and relying on the platform's per-client focus stack. Rejected as depending on undocumented arbitration ordering to produce the right pause/resume behavior.

### 5. `AUDIOFOCUS_LOSS` → `stop.request()`, reusing the existing stop channel

The focus change listener sets the same `ExternalStop` flag that tap-to-stop and screen-off already set. Everything downstream of the recording loop is therefore unchanged: the stop haptic fires, the WAV lands in `_pending/`, transcription proceeds. This is a stop, not a cancel, exactly as the screen-off stop is.

`AUDIOFOCUS_LOSS_TRANSIENT` is treated the same as `AUDIOFOCUS_LOSS`. There is no meaningful "wait and resume" for a recording — a paused-then-resumed capture would have a hole in it and no way to signal that to a user who is not looking at the screen. Ending the capture and keeping what was said is the honest outcome.

The listener needs its own `Handler`/executor so a loss delivered while the service's default dispatcher is busy still arrives promptly.

### 6. Deferred: pre-roll discard for the other app's fade-out

Media apps fade out over roughly 100–500 ms after `LOSS_TRANSIENT`. Since recording starts immediately, the head of the WAV can contain the tail of the song, and whisper may render a lyric fragment at the top of the note.

The fix, if it turns out to be needed:

```
if (audioManager.isMusicActive) {
    acquire focus
    startRecording()
    read and DISCARD ~300 ms of PCM     ← swallow the fade-out
}
tick → user speaks
```

`isMusicActive` gates it, so the silent-room path keeps its current zero added latency, and the discard happens before `onCapturing` fires, so the tick still follows a confirmed-live microphone and no speech is clipped. It is deferred rather than included because the size of the artifact is unknown until this is on the device — the fade may well be quiet enough that whisper emits nothing for it, in which case 300 ms of added latency on every music-playing capture would be paid for nothing. Verify first, then decide.

## Risks / Trade-offs

- **The other app does not resume.** Resume is not ours to control. → Mainstream players (Spotify, YouTube Music, Poweramp) resume after a transient loss, and `TRANSIENT_EXCLUSIVE` is the documented signal for it. Verify with the player actually used on the device; if a specific app misbehaves, that is a note in the change, not a code fix.
- **Fade-out tail at the head of the recording.** → Decision 6 has the mitigation ready to apply if on-device testing shows lyrics reaching the note.
- **Reference count leaks and playback never resumes.** The failure mode is silent and lasts until reboot — much worse than the bug being fixed. → Every capture releases through the idempotent `Registration` wrapper, releases live in `finally`-equivalent positions on both exit paths, and the count reaching zero is the single abandon site. This is the highest-value thing to check on device: trigger a capture that fails at the vault check, one that fails to acquire the microphone, and one that succeeds, and confirm music resumes in all three.
- **A capture during a phone call.** Focus will be refused and the microphone is owned by telephony, so the recording is likely silent. → Unchanged from today's behavior: the capture proceeds, the silence stop ends it, and `NoSpeech` is reported without a buzz. The change explicitly does not make a refused request fatal.
- **The stop haptic and the resuming music arrive together.** → Harmless; one is haptic, the other audio. Release ordering relative to `haptics.stopped()` is not load-bearing.

## Open Questions

- Does the fade-out tail actually reach the transcript on the Pixel 6 Pro with the user's usual player, or is it below whisper's floor? Determines whether Decision 6 gets promoted out of deferral.
- Should a capture triggered *during a phone call* be refused outright with the at-risk rumble, rather than recording silence and reporting `NoSpeech`? Out of scope here, but this change is what makes the condition detectable.
