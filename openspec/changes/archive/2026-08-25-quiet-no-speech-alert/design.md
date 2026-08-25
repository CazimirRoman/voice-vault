## Context

`RecordingService.handleOutcome` is the single funnel every non-success capture outcome passes through. It writes a diagnostic record, posts a failure notification, and then decides whether to rumble:

```
handleOutcome(outcome, captureId, isFirstAttempt)
  ├─ Success / AlreadyHandled ──────────► cancelFailure, return
  ├─ note already exists ───────────────► cancelFailure, return
  ├─ writeDiagnostic(captureId)
  ├─ postFailure(outcome, captureId, durationSeconds, allowDiscard)
  └─ if (isFirstAttempt || outcome !is NoSpeech) haptics.atRisk()
```

The retry sweep re-runs every pending file on every capture, so a no-speech capture reaches this funnel again and again. `isFirstAttempt` was introduced to stop it rumbling on each of those sweeps — the rumble was already understood to be wrong for repeated no-speech, just not for the first one.

There is a second, less visible alerting path. `postFailure` posts to `FAILURE_CHANNEL_ID`, created at `IMPORTANCE_HIGH`. On the target device that channel supplies its own vibration and a heads-up banner, on top of anything `Haptics` does. `setOnlyAlertOnce(true)` keeps *re-posts* of the same notification id quiet, but the first post for each capture alerts at full strength. Removing `haptics.atRisk()` alone would therefore leave a no-speech capture buzzing the phone and popping the lock screen — quieter, but not quiet.

The capture id is already a timestamp: `VaultWriter.captureIdFor()` formats `yyyy-MM-dd HHmm`, disambiguating collisions with a ` (2)` suffix, and that exact string names the WAV in `_pending/`, the `.log` diagnostic beside it, and the note when one is eventually written. The notification is the only surface in the chain that does not show it.

## Goals / Non-Goals

**Goals:**

- The 5-second rumble means exactly one thing again: recorded speech is at risk.
- A no-speech capture produces a shade entry and nothing else — no rumble, no notification vibration, no sound, no heads-up.
- Every failure notification names its capture by the same timestamp the file system uses, so shade entry, pending audio, and diagnostic record are matchable by eye.
- A no-speech entry is distinguishable from a capture at risk in its collapsed form.

**Non-Goals:**

- Changing when `NoSpeech` is classified. `TranscriptionQueue.isNonSpeechPlaceholder` and the empty-text check are untouched.
- Changing the lifecycle of no-speech audio. The WAV still sits in `_pending/`, still gets a diagnostic record, still offers Discard, and is still re-swept by every later capture. Auto-discarding short no-speech captures was considered and rejected — a recognizer misfire would then delete real speech silently, which is the one failure mode this app is built to prevent.
- Changing `Haptics.atRisk()` itself, or the start/stop haptics.
- Changing the zero-length-capture path (`No audio was captured`, `RecordingService.kt:96`). Nothing is lost there either, but it means `AudioRecord` returned nothing at all — a microphone malfunction, not a quiet room — and it should stay loud.

## Decisions

### A second notification channel rather than a downgraded one

`NotificationChannel` importance is fixed at creation; re-creating a channel with the same id and a lower importance is a no-op. Making no-speech silent therefore requires a new id — `capture_no_speech` at `IMPORTANCE_LOW` — with `FAILURE_CHANNEL_ID` left at `IMPORTANCE_HIGH` for everything else.

Alternatives considered:

- *One channel at `IMPORTANCE_LOW` for all failures.* Rejected: it silences the genuine data-loss alert too, which is the opposite of the point. It would also silently discard any per-channel setting the user has applied to the existing channel.
- *One channel, suppressing the alert per-notification.* There is no reliable per-notification way to opt out of a high-importance channel's vibration and heads-up on modern Android; importance is the channel's property by design.
- *A new id for the loud channel instead, keeping the old id for no-speech.* Rejected: it would inherit the user's existing settings onto the wrong class of alert, and the class that matters most is the one that should keep its established identity.

The cost is a second row under EasyNote in system notification settings. That is acceptable, and arguably correct: the two classes genuinely warrant separate user control.

### Notification identity is chosen from the outcome, not passed in

`postFailure(outcome, …)` already switches on `TranscriptionOutcome` to build its message. Channel and title selection join it there, so there is exactly one place that decides how an outcome is presented, and adding an outcome to the taxonomy forces that decision to be made. The string-only `postFailure(message, …)` overload — used by the four pre-transcription failures in `RecordingService` — keeps the high-importance channel and the "Note needs attention" title, since all four are genuine errors.

### The timestamp goes in the title, not in `when`

Two ways to surface the capture time:

| Approach | Effect | Cost |
| --- | --- | --- |
| Capture id in the title | `No speech · 2026-08-23 1417` | None — the string is already in hand |
| `setWhen()` + `setShowWhen(true)` | System renders its own relative time | `when` defaults to *post* time; on a retry sweep that is hours after the recording, so the capture id must be parsed back into millis to be correct |

The title carries it. It is unambiguous, it survives re-posts by the retry sweep unchanged, and it is character-for-character the filename in `_pending/` — which is the point of showing it at all. `setWhen` remains available later as a refinement, not a replacement.

Titles become:

- No speech → `No speech · <captureId>`
- Everything else → `Note needs attention · <captureId>`, falling back to the bare `Note needs attention` when there is no capture id (the pre-transcription failures, which have nothing written yet).

### `isFirstAttempt` is removed rather than left in place

Once no-speech never rumbles, `isFirstAttempt || outcome !is NoSpeech` collapses to `outcome !is NoSpeech` and the parameter has no remaining reader. Leaving it would leave a parameter that looks like it gates something and gates nothing — a trap for the next change. It is removed from `handleOutcome` and from both call sites (`runCapture`, `retryPending`).

### Notification ids and clearing are untouched

`failureNotificationId(captureId)` stays the keying mechanism, so a no-speech entry still replaces its own previous post instead of stacking, and `clearStaleFailures` still cancels it when the audio leaves `_pending/` by any path. Moving between channels does not change the id, so an entry posted by an older build is still found and cancelled by the sweep.

## Risks / Trade-offs

- **A silent no-speech entry is easier to never notice, and its WAV sits in `_pending/` until Discard is tapped.** → That is the intended trade: nothing is at risk, so nothing urgent is being missed. The audio is not orphaned — the entry stays in the shade, the `.log` stays beside the file, and the planned capture-health indicator reads exactly this state. If pending debris accumulating proves annoying in daily use, that is a separate change about no-speech audio's lifetime, not about its alert.
- **Two channels is one more thing for the user to misconfigure.** → Both are created on `CaptureNotifications` construction, so neither can go missing. A user who silences the loud channel loses failure alerts, but that was already true of the single channel.
- **A no-speech capture that was actually speech the recognizer missed now fails quietly.** → The notification still appears, still quotes what whisper heard, still offers Discard, and the audio is still kept. The change is the volume of the announcement, not whether it is made.
- **Changing the title changes what an existing posted notification looks like after an upgrade.** → Re-posts are keyed by the same id, so an outstanding entry from an older build is updated in place on the next sweep rather than duplicated.
