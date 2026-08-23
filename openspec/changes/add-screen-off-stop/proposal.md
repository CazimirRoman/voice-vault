## Why

The intended flow is *trigger → speak → pocket the phone → a note appears*. In practice the recording never ends: the user presses the power button, pockets the phone, feels no stop buzz, and finds the capture still listening ten seconds later.

The cause is that pocketing the phone is simultaneously the gesture that means "I am done" and the thing that makes "I am done" undetectable. `SILENCE_RMS_THRESHOLD` is 700 out of 32767 — roughly 2% of full scale — and `silentDurationMs` resets to zero on any single 40 ms buffer above it. Cloth moving against the microphone port clears that bar continuously, so the five contiguous quiet seconds the stop condition requires never arrive.

`design.md:134` in `add-voice-note-capture` anticipated this as "RMS silence detection will misfire in loud environments … caught by the 3-minute cap", and accepted it because `design.md:65` scoped silence detection to "only guards the case where the user has already walked away". That scoping is what turned out to be wrong. Pocketing is not a rare loud environment; it is the happy path, every time. Bounding it with the 3-minute cap means the normal outcome is three minutes of fabric noise handed to whisper, a slow transcription pass, and a probable hallucinated note.

The fix is not better silence detection. The user already performs a deliberate, unambiguous, zero-inference "I am done" action: pressing the power button. The app simply does not listen for it.

## What Changes

- **Add screen-off as a fourth stop condition.** While a capture is in progress, `ACTION_SCREEN_OFF` SHALL end the recording. It is a *stop*, not a cancel: audio captured up to that point is persisted and transcribed through the existing path.
- **Route it through the existing `ExternalStop` flag** that tap-to-stop already uses, so `AudioRecorder` does not change at all. The new stop is not a new branch in the recording loop; it sets the same volatile flag from a different source.
- **Keep the screen on while the overlay is up.** `TapToStopActivity` gains `FLAG_KEEP_SCREEN_ON` so the display cannot time out by itself during a long thinking pause. Any screen-off that arrives is therefore a deliberate button press, not an idle timeout.
- **Register the receiver in `RecordingService`, not the overlay.** `ACTION_SCREEN_OFF` cannot be declared in the manifest and must be registered at runtime. The service owns the `ExternalStop`, so the stop works even if the overlay never launched — the overlay stays non-load-bearing.
- **Change nothing else about stopping.** Silence detection, tap-to-stop, and the 3-minute cap are untouched, and `SILENCE_RMS_THRESHOLD` keeps its current value. Each of the four conditions covers a case the others do not: screen-off for the pocketed happy path, silence for walk-away, tap for when the user is looking at the screen, the cap for when everything else has failed.

Not in scope: retuning the silence threshold, adaptive or spectral voice activity detection (Silero VAD remains the deferred remedy named in `design.md:65`), proximity-sensor pocket detection, any change to the haptic vocabulary, and any change to transcription, vault-write ordering, or the notification surface.

## Capabilities

### Modified Capabilities

- `voice-capture`: adds screen-off as a stop condition, and the requirement that the display does not sleep on its own while a capture is in progress so screen-off can be read as intentional. Narrows the existing "screen locks during recording" scenario, which asserted that recording continues when the screen turns off.

## Impact

- `capture/RecordingService.kt` — register a `BroadcastReceiver` for `ACTION_SCREEN_OFF` for the duration of a capture; unregister once recording ends, on every exit path including the failure path.
- `capture/TapToStopActivity.kt` — set `FLAG_KEEP_SCREEN_ON` on the window.
- `AudioRecorder.kt`, `CaptureEvents.kt`, `Config.kt` — unchanged.
- No new permissions, no new dependencies, no network access, no change to any file written into the vault.
- Verification requires the physical Pixel 6 Pro: the failure this fixes cannot be reproduced on the JVM or in an emulator, since it depends on real microphone input from a real pocket.

## Trade-offs

**This appears to reverse `design.md:41`**, which chose a foreground service specifically so that recording survives the screen locking. It does not. That decision was about surviving an *automatic* lock while the phone sits in a pocket, and the service architecture that guarantees it is unchanged — recording still survives the screen turning off, the app losing visibility, and the device locking. What changes is the interpretation of a *user-initiated* power press, which `FLAG_KEEP_SCREEN_ON` makes distinguishable from an idle timeout for the first time.

**If the overlay fails to launch, the display can still time out and stop the capture early.** The overlay is where `FLAG_KEEP_SCREEN_ON` lives, so its absence removes the guarantee. This is accepted rather than guarded, because gating the stop on the overlay being visible would give the overlay a behavioural role that `voice-capture` explicitly forbids it. The degradation is bounded in exactly the way `design.md:63` already argues for: the user *feels* the stop buzz, so a premature cutoff is immediately known, and the partial audio is already durable in `_pending/`.

**Triggering with the screen already off falls back to the old behaviour.** No `ACTION_SCREEN_OFF` broadcast will arrive, so such a capture is bounded by silence detection and the 3-minute cap as before. Adding `turnScreenOn="true"` to the overlay would normalise this, and is deliberately excluded: it is not the observed flow, and it would make a power-button hold light up the screen of a phone already in a pocket.
