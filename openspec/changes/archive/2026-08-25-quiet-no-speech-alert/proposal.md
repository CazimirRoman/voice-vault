## Why

The 5-second at-risk rumble is the app's loudest signal and it means one thing: *a recording you made is in danger of being lost*. A capture where nothing was said is not that. It is the expected outcome of a pocket-triggered power hold or a false start, and it fires the same alarming rumble as a genuine transcription error — so the signal that is supposed to make the user stop what they are doing and check the phone has been trained into noise.

The notification the rumble accompanies is also harder to act on than it needs to be. It names the failure and the recording's duration, but not *which* recording — so a user reading "No speech detected in a 12s recording" hours later cannot tell it apart from any other, even though the capture's timestamp is already its filename in `_pending/`.

## What Changes

- **The no-speech outcome stops rumbling entirely.** `Haptics.atRisk()` is no longer emitted for `TranscriptionOutcome.NoSpeech` — not on the first attempt, and not on any retry sweep. Every other failure class keeps it, on the first attempt and on every later sweep, exactly as today.
- **The no-speech notification also stops buzzing.** Today's failure channel is `IMPORTANCE_HIGH`, which makes Android emit its own vibration and a heads-up banner independently of `Haptics` — so removing the rumble alone would still leave a buzz and a lock-screen pop. No-speech moves to a new `IMPORTANCE_LOW` channel that posts silently into the shade. Channel importance is immutable after creation, so this is a new channel id rather than a downgrade of the existing one; the failure channel stays `IMPORTANCE_HIGH` for everything else.
- **Every failure notification names its capture.** The capture id — already a `yyyy-MM-dd HHmm` timestamp, already the WAV's filename and the diagnostic record's filename — is carried on the notification, so the shade entry, the pending audio, and the `.log` beside it all identify the recording by the same string.
- **The no-speech notification is titled as what it is.** "No speech" rather than the generic "Note needs attention", so the shade distinguishes a discardable non-event from a capture at risk without the user opening it.
- Not in scope: any change to when `NoSpeech` is classified, to the diagnostic record, to the Discard action, to how long no-speech audio sits in `_pending/`, or to the start/stop haptics. The zero-length-capture path (`No audio was captured`) keeps the rumble — nothing was lost there either, but it means the microphone genuinely malfunctioned rather than that the room was quiet.

## Capabilities

### New Capabilities

<!-- None. This changes the alerting behavior of capabilities that already exist. -->

### Modified Capabilities

- `vault-writing`: The "A note at risk raises a distinct alert" requirement currently mandates a 5-second vibration for *any* capture that cannot be completed into a note. It gains a carve-out: an outcome that leaves no recorded speech at risk SHALL be reported without the vibration and without an alerting notification.
- `failure-diagnostics`: The "A failure alert identifies the capture well enough to act on" requirement gains the capture's timestamp alongside the reason and duration it already mandates, and gains the requirement that a no-speech alert is visually distinguishable from a capture at risk.

## Impact

- `capture/RecordingService.kt` — `handleOutcome` stops calling `haptics.atRisk()` for `NoSpeech`. The `isFirstAttempt` parameter exists solely to suppress the rumble on no-speech retry sweeps; with no-speech never rumbling it is dead and is removed from the signature and both call sites.
- `notify/CaptureNotifications.kt` — a second failure channel (`capture_no_speech`, `IMPORTANCE_LOW`), outcome-based channel and title selection, and the capture id on the content.
- No change to `Haptics.kt`: `atRisk()` itself is unchanged, only who calls it.
- No new permissions, no new dependencies, no change to anything written into the vault, no change to capture or transcription ordering.
- Users upgrading will see a second EasyNote notification channel in system settings. The existing failure channel keeps its id, so any per-channel setting the user has already applied to it is preserved.
- Tests: `app/src/test` can cover channel/title/text selection per outcome. Confirming the absence of a vibration, and that the shade entry is silent with the screen off, needs the physical Pixel 6 Pro.
