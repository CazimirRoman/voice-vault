## 1. Split the failure channels

- [x] 1.1 In `CaptureNotifications`, add `NO_SPEECH_CHANNEL_ID = "capture_no_speech"` alongside the existing channel ids, and create it in `init` at `NotificationManager.IMPORTANCE_LOW` with a user-facing name such as "Captures with no speech". Leave `FAILURE_CHANNEL_ID` at `IMPORTANCE_HIGH` and leave its id unchanged, so a user's existing per-channel settings survive.
- [x] 1.2 Add a private helper that maps a `TranscriptionOutcome` to its channel id: `NoSpeech` → the new channel, everything else → `FAILURE_CHANNEL_ID`. `Success` and `AlreadyHandled` are unreachable here and should keep failing loudly the way `messageFor` already does.
- [x] 1.3 Verify on the device that the low-importance channel posts with no vibration, no sound, and no heads-up banner. If Android still alerts, set the channel's vibration and sound off explicitly rather than lowering importance further.

## 2. Put the capture on the notification

- [x] 2.1 Add a private helper mapping a `TranscriptionOutcome` and an optional capture id to a title: `NoSpeech` → `"No speech · <captureId>"`, every other outcome → `"Note needs attention · <captureId>"`, and a null capture id → the bare `"Note needs attention"`.
- [x] 2.2 Route the outcome-typed `postFailure` through both helpers so it selects its own channel and title instead of inheriting the string overload's.
- [x] 2.3 Leave the string-only `postFailure(message, captureId, allowDiscard)` on `FAILURE_CHANNEL_ID` with the "Note needs attention" title — its four callers in `RecordingService` are all genuine errors, and none of them has a capture id yet.
- [x] 2.4 Confirm `failureNotificationId(captureId)` is still the only source of notification ids, so an entry posted before this change is replaced in place rather than duplicated, and `clearStaleFailures` still finds and cancels it. Note that `clearStaleFailures` filters on `channelId != FAILURE_CHANNEL_ID` — extend it to also sweep the new channel, or it will leave stale no-speech entries behind forever.

## 3. Stop the rumble for no-speech

- [x] 3.1 In `RecordingService.handleOutcome`, change the rumble condition to fire only when the outcome is not `NoSpeech`, dropping the `isFirstAttempt ||` disjunct.
- [x] 3.2 Remove the now-unread `isFirstAttempt` parameter from `handleOutcome` and from both call sites in `runCapture` and `retryPending`. Update the comment above the condition so it explains the rule that now holds (the rumble means recorded speech is at risk) rather than the retry-sweep special case it replaced.
- [x] 3.3 Leave the four pre-transcription `haptics.atRisk()` calls in `runCapture` untouched — vault unavailable, recording failed to start, no audio captured, and save failed all keep the rumble.

## 4. Tests

- [x] 4.1 Add JVM tests in `app/src/test` over the outcome → channel and outcome → title mapping, covering `NoSpeech`, `TranscribeFailed`, `NoteWriteFailed`, and `ModelUnavailable`. Extract the mapping helpers so they are callable without an Android `Context` if they are not already.
- [x] 4.2 Assert the no-speech title contains the capture id verbatim, including a disambiguated id of the ` (2)` form produced by `VaultWriter.captureIdFor`.
- [x] 4.3 Run `./gradlew test` and `./gradlew lint`. `./gradlew test` passes. `./gradlew lint` fails on a pre-existing `MissingPermission` finding in `RecordingService.kt:79` unrelated to this change — confirmed identical on the pre-change tree via `git stash`.

## 5. Verify on the device

These cannot be confirmed from code inspection — the whole change is about what the phone does in the user's pocket. Do not mark them done without running them on the Pixel 6 Pro.

- [x] 5.1 Trigger a capture and say nothing until it stops. Confirm the stop buzz fires, then **nothing else vibrates at all** and no banner appears over the lock screen.
- [x] 5.2 Pull down the shade. Confirm a single "No speech · <timestamp>" entry is present, that the timestamp matches the `.wav` name in `Inbox/_pending/`, and that Discard still removes both the entry and the file.
- [x] 5.3 Repeat 5.1 with the screen already off and the phone pocketed. Confirm the capture is genuinely silent — this is the flow the change exists for.
- [x] 5.4 Trigger several more captures so the retry sweep re-runs the no-speech file. Confirm it re-posts silently, does not stack a second entry, and still never rumbles.
- [x] 5.5 Force a genuine failure (e.g. remove the vault folder, or trigger a capture before the model download completes) and confirm the 5-second rumble and the heads-up notification both still fire, on the first attempt and on a later sweep.
- [x] 5.6 Check EasyNote's notification settings and confirm two channels are listed, that the pre-existing failure channel kept any setting previously applied to it, and that silencing one does not silence the other.
