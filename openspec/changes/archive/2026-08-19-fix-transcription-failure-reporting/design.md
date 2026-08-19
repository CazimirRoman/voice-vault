## Context

This change starts from a confirmed on-device diagnosis, not a hypothesis. On the Pixel 6 Pro, three live "Note needs attention" notifications were cross-referenced against the vault:

| Notification names | In `_pending/` | In `00-Inbox` | Note content |
| --- | --- | --- | --- |
| `2026-08-19 0849.wav` | absent | `0849.md` | "buy some boat shoes…" |
| `2026-08-19 0852.wav` | absent | `0852.md` | "I like to have some kind…" |
| `2026-08-19 1145.wav` | absent | `1145.md` | "On my Windows PC, I want…" |

`_pending/` was empty. Every alert was about a capture that had succeeded. Note mtimes pin the mechanism precisely:

```
08:49:00  capture A ends, 0849.wav -> _pending/
          transcribe(0849.wav) takes the mutex ---------+
08:49:1x  capture B starts recording                    |
            RecordingService:59  launch { retryPending }|
              pendingAudioFiles() -> [0849.wav]  <------+-- snapshot, file still there
              transcribe(0849.wav) blocks on mutex --+  |
08:49:40  A writes 0849.md, deletes 0849.wav        |  |
          A releases mutex ------------------------ +--+
          B's sweep entry acquires mutex <----------+
          whisper called on a path that no longer exists -> throws -> false
          "No note could be produced from 2026-08-19 0849.wav"
08:50:07  capture B writes "0849 (2).md"
```

The `(2)` suffix is diagnostic: `captureIdFor()` only disambiguates within the same minute, which is exactly when a sweep overlaps an in-flight transcription. The 08:52 case is the same shape over a longer window — that note took 2m15s to transcribe (08:52 → 08:54:17) and the 08:54 capture's sweep started inside it.

Three defects compound here, and they are separable:

1. **The race.** `pendingAudioFiles()` snapshots the directory, then the mutex makes each entry wait behind the transcription that deletes it. Nothing re-checks existence after the lock is acquired.
2. **The one-bit result channel.** `TranscriptionQueue.transcribe` returns `Boolean`. Six structurally different outcomes collapse into `false`, and every `catch (t: Throwable)` discards the throwable. There is no `Log` call anywhere in the codebase, so a tethered `adb logcat` reveals nothing. The race was undiagnosable from inside the app.
3. **Immortal notifications.** `cancelFailure` fires only when a sweep finds a file and transcribes it successfully. Once the WAV is deleted, no sweep will ever look at it again, and `setAutoCancel(false)` keeps the alert in the shade forever. The symptom reads as "it keeps happening" when it is actually accumulating.

Constraints inherited from the app's design: no settings or list UI, the vault is the only persistent user-visible surface, the device is a single sideloaded Pixel 6 Pro with no CI, and `_pending/` write ordering is the app's core durability guarantee and must not move.

## Goals / Non-Goals

**Goals:**

- No failure alert is ever raised for a capture that produced a note.
- A failure that does occur names its cause, in the notification and in a record that survives reboot.
- The user can decide whether to discard a recording from the notification alone.
- Silent `catch` blocks stop being silent.
- Stale alerts already sitting in the user's shade get cleared by the fixed build.

**Non-Goals:**

- Any settings screen, pending-captures list, or note editing. `MainActivity` stays a permission shim.
- Changing capture behavior, write ordering, haptic patterns, or when audio is deleted.
- Audio playback from the notification. Considered and deferred — see Decisions.
- Re-proposing the archived `offline-transcription` requirements that the model-download work already superseded (bundled-in-assets, no `INTERNET`). Out of scope here.
- Crash reporting, telemetry, or anything that leaves the device.

## Decisions

### Fix the race by re-checking under the lock, not by locking the directory

`TranscriptionQueue.processOne` re-checks `wavFile.exists()` **after** the mutex is acquired and returns an `AlreadyHandled` outcome if it is gone. The caller treats that as "nothing to report".

Alternatives considered:

- *Have the sweep skip files that are in flight.* Requires the queue to expose a set of in-flight paths — worth doing as well (see next decision) but insufficient on its own, because the file can also vanish via the Discard action or an external file manager. The existence re-check is the correct backstop and is one line.
- *Give the sweep a lock over `_pending/`.* Heavier, and cross-process file locking on `/sdcard` under a live sync client is not something to rely on.
- *Have the sweep claim files by renaming them.* Would break the invariant that audio sitting in `_pending/` is exactly what the user can recover; a crash mid-sweep would leave audio under a name nothing looks for.

The existence check is the primitive that makes every other ordering safe, so it goes in regardless.

### Also dedupe in-flight entries, because the race is cheap to avoid entirely

`TranscriptionQueue` keeps a set of canonical paths currently enqueued or running. `transcribe()` returns `AlreadyHandled` immediately for a path already in the set. This turns the common case (sweep overlaps the capture that owns the file) into a no-op instead of a wasted queue slot that waits for the mutex only to discover the file is gone. The existence re-check still stands behind it for the cases the set cannot see.

### Replace `Boolean` with a sealed `TranscriptionOutcome`

```
TranscriptionOutcome
├─ Success(notePath)
├─ AlreadyHandled                      // file gone before its turn; not a failure
├─ NoSpeech(rawText: String)           // "" or "[ Pause ]" — whisper worked, there was nothing to hear
├─ ModelUnavailable(cause: Throwable?)
├─ TranscribeFailed(cause: Throwable)
└─ NoteWriteFailed(cause: Throwable)
```

This is the load-bearing change. `AlreadyHandled` would have made the race impossible to ship silently, and the split between `NoSpeech` and the two `*Failed` cases is what lets the notification stop lying. `ModelUnavailable` is kept distinct from `TranscribeFailed` because `RecordingService` already branches on it via `ModelProvisioner.isModelReady`, and that second lookup becomes redundant once the outcome carries the answer.

Rejected: an `enum` plus a nullable `Throwable` field. Sealed classes let `NoSpeech` carry a `String` and `Success` carry a path without a bag of nullable fields, and force exhaustive `when` at each call site — which is how the new outcomes get wired into the notification text rather than silently defaulting.

### Guard on "does a note already exist" before posting any failure

Independent of the race fix: before `RecordingService` posts a failure for `captureId`, it checks `00-Inbox/<captureId>.md`. If the note is there, the capture succeeded by some path and no alert is warranted.

This is deliberately redundant with the race fix. The race fix addresses the mechanism we found; this guard addresses the *class* of bug — any future path that loses track of a successful capture gets caught here. Given that the failure mode is "wake the user with a high-importance alert about data loss that did not occur", belt-and-braces is the right call.

### Write the diagnostic record into `_pending/`, not logcat alone

Both, but they serve different windows. `Log.w(TAG, msg, t)` at every previously silent catch makes a tethered debug session useful. It does not help the actual reported problem, where the failure happened hours earlier in a pocket and the ring buffer has rotated.

The durable record is a sidecar next to the orphan audio: `_pending/<captureId>.log`. Rationale:

- It lives exactly where the recoverable audio lives, so the two are found and cleaned up together.
- `_pending/` is already a staging area, so a non-note file there does not pollute the Obsidian graph the way it would in `00-Inbox`.
- It syncs with the vault, so the record is readable on another device without adb.

`pendingAudioFiles()` already filters on `extension == "wav"`, so `.log` files are invisible to the sweep for free. The record is **overwritten** per attempt, not appended, so a file that fails on every sweep cannot grow without bound. Deleting the audio (success or Discard) deletes the record.

Rejected: a single `_pending/failures.md`. Concurrent sweeps would contend on one file, and the per-capture lifecycle (delete with the audio) is exactly what a per-capture file gives for free.

### Derive duration from the WAV, and put it in the notification

`(fileSize - 44) / 2 / SAMPLE_RATE_HZ` seconds, given the fixed PCM16 mono header `WavEncoder` writes. No new state and no need to read the audio. A 6-second orphan and a 2-minute orphan demand completely different decisions from the user, and duration is the single cheapest fact that separates them.

### Defer audio playback from the notification

A "Play" action would answer "what am I discarding?" definitively. It needs a `FileProvider`, an `ACTION_VIEW` chooser, and a working external player for a bare `_pending/*.wav` — meaningful surface area for an app that currently has no UI at all. Duration plus the classified reason plus, for `NoSpeech`, whisper's literal output should resolve most cases. Revisit if it does not.

### Do not vibrate for `NoSpeech` on the retry path

The at-risk rumble is a data-loss signal. A recording with no speech in it is not lost data, and the sweep re-runs it on every capture. The notification still posts (silently, via the existing `setOnlyAlertOnce`), because the audio genuinely does need a Discard decision eventually. The first-attempt path in `runCapture` keeps its haptic for all failure classes — that one is contemporaneous and the user is holding the phone.

### Clear the three phantom alerts already on the device

The fixed build must clean up after the broken one. On app start and on each sweep, cancel any failure notification whose capture has no pending audio. Implementation: enumerate active notifications via `NotificationManager.getActiveNotifications()`, filtered to the `capture_failures` channel, and cancel those whose id does not correspond to a currently pending file.

This needs the capture id recoverable from the notification. `failureNotificationId()` is a one-way hash of the capture id, so the reverse lookup goes the other direction: build the set of expected ids from `pendingAudioFiles()` and cancel any active failure notification not in it. That works without storing anything new.

## Risks / Trade-offs

- **The existence re-check narrows the race but cannot close it absolutely** — a file can be deleted between the check and whisper opening it. → Acceptable: the residual window is microseconds versus the current multi-minute one, and `TranscribeFailed` on a vanished file now writes a diagnostic record naming the path, so a recurrence is visible rather than mysterious. The in-flight set removes the common case entirely.

- **The "note already exists" guard could mask a real failure** if a capture id collides with an unrelated existing note. → `captureIdFor()` already disambiguates against `00-Inbox/*.md` at capture time, so a collision would require a note to appear at that exact name between capture and failure. Vanishingly unlikely, and the failure mode is a missing alert for audio that is still safely in `_pending/` and still swept.

- **Writing `.log` files into a synced vault adds sync traffic and visible clutter.** → Small (a few hundred bytes), scoped to `_pending/`, and deleted with the audio. The user already accepts `_pending/` as app-managed space.

- **Notification cleanup relies on `getActiveNotifications()`**, which only returns notifications posted by this app and only since the last process start in some OEM builds. → Cleanup is opportunistic, not a guarantee. The three known phantoms can also be cleared by tapping Discard, which is already a harmless no-op now that the files are gone.

- **Exhaustive `when` over the sealed outcome touches every call site** and this app has no CI. → That is the point: the compiler forces each new case to be handled rather than defaulting. The call sites are few (`RecordingService` twice, tests).

- **The diagnostic record is only written on failure**, so a successful-but-wrong transcription leaves no trace. → Out of scope; the reported problem is failure reporting, not transcription quality.

## Migration Plan

No data migration. The change is additive to `_pending/` and backward compatible with existing pending audio: a `.wav` with no `.log` beside it is a pre-change orphan and still sweeps normally.

Rollback is uninstall-free — reverting the build restores the old behavior, and any `.log` files left behind are inert (the sweep filters on `.wav`).

Verification is device-only, as everywhere in this repo. The race reproduces by triggering two captures inside one minute, or one capture followed by a second inside the first's transcription window; a fixed build must produce two notes and zero notifications.

## Open Questions

- Should `NoSpeech` post a notification at all on the *first* attempt, or only once the sweep has re-tried it and confirmed it is not a transient model problem? Posting immediately is the current behavior and is kept; worth revisiting if pocket-triggered captures turn out to be common.
- Is `_pending/<captureId>.log` the right extension, or would `.md` make it readable in Obsidian at the cost of appearing in the graph? Leaning `.log` to stay out of the vault index.
- Whether to also record *successful* transcription durations in the diagnostic record, which would answer "how long does whisper actually take on this device" — directly relevant, since a 2m15s transcription is what widened the race window in the 08:52 case.
