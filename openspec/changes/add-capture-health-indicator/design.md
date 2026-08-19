## Context

`TapToStopActivity` is the only screen the user sees during a capture: a full-screen `Theme.Translucent.NoTitleBar.Fullscreen` activity over a 75%-black scrim, with a 96dp red dot pulsing 0.75→1.15 on a 900ms reverse loop, "Listening…" at 24sp, and "Tap anywhere to stop" at 14sp/0.6 alpha. It is launched from `RecordingService.runCapture` at the line immediately *before* `audioRecorder.record(...)`, and it finishes itself when `CaptureEvents.recordingEnded` fires.

Three existing properties constrain everything here:

- **`showWhenLocked="true"`.** The overlay has to render over the keyguard or the power-button hold would not work on a locked phone. Anything drawn here is readable by anyone holding the device.
- **The start haptic must not be late.** It fires only once `AudioRecord` is confirmed capturing, because an earlier tick clips the first spoken word — and a *delayed* `AudioRecord` start clips it just as effectively. The overlay's `onCreate` runs concurrently with that initialisation.
- **The pending-audio-before-note ordering is the data-loss defence.** This change reads `_pending/`; it must never write to it, delete from it, or depend on its contents to proceed.

`fix-transcription-failure-reporting` is in flight against the same pipeline. Its §3 (the `.log` diagnostic record, `noteExists()`, `wavDurationSeconds()`) is complete; its §4–§7 are not. That split matters and is addressed below.

## Goals / Non-Goals

**Goals:**

- Answer "is everything landing in the vault?" at a glance, at a moment the user is already looking at the screen, without opening an app.
- Cost nothing on the capture path — no blocking, no main-thread I/O, no new failure mode for recording.
- Leak nothing on the lock screen.
- Never lie in the reassuring direction. A green dot must mean green.

**Non-Goals:**

- Showing *which* capture is unhealthy, or why. That is the failure notification's job.
- Any recent-notes list, transcript preview, or count.
- Making the overlay load-bearing in any way. Nothing in the capture flow may come to depend on it.
- A home-screen widget. Considered and deferred — see Open Questions.

## Decisions

### Derive health from `_pending/` alone, not from the inbox

The alternative was reading `00-Inbox/` and showing the last five notes. Rejected on four independent grounds:

- **It leaks.** `showWhenLocked="true"` means note text on this surface is readable without unlocking. There is no redaction scheme that keeps the feature useful and the content private, because the content *is* the feature.
- **It is expensive on the wrong thread at the wrong moment.** Five file opens plus frontmatter parsing, in `onCreate`, while `AudioRecord` initialises.
- **It is ambiguous.** `00-Inbox/` mixes voice notes with hand-written ones. Telling them apart needs either a filename regex (a heuristic) or a frontmatter marker (which breaks the deliberate choice that voice notes "sit alongside manually written ones without looking like a different kind of thing").
- **It answers indirectly.** Five notes existing does not prove a sixth did not fail. An empty `_pending/` proves exactly that.

The pending folder is the app's own ledger of unfinished work, and it is exact because of an invariant the app already maintains: the WAV is deleted only after the note is written.

```
_pending/ contents                  state              indicator
─────────────────────────────────────────────────────────────────────
no .wav files                       CLEAR              dim green, static
.wav, no sibling .log, recent       IN FLIGHT          amber, static
.wav with sibling .log              NEEDS ATTENTION    red, pulsing
.wav, no sibling .log, stale        NEEDS ATTENTION    red, pulsing
folder not readable                 UNKNOWN            nothing drawn
```

A capture discarded through the notification's Discard action leaves neither file and reads as clear — correct, since the user chose to discard it.

### Stale pending audio without a diagnostic is a failure, not progress

A WAV can land in `_pending/` and never be attempted: the process is killed between the write and the enqueue, and no `.log` is ever produced. Without a staleness rule that capture shows amber forever, and "in progress" is precisely the state a user reads as *fine, be patient*. Silently-stuck audio masquerading as work-in-progress is the exact failure this feature exists to catch, so a pending WAV whose `lastModified` is older than a configured threshold and which has no diagnostic beside it is classified as needs-attention.

The threshold is a `Config` constant. It must comfortably exceed the worst realistic transcription time for a 3-minute capture on a `base.en` quantised model, while staying short enough that a wedged capture is noticed the same day. This is the one number in the change that is a judgement call rather than a derivation.

### Attention outranks in-flight

The indicator collapses N captures into one dot, so precedence has to be explicit. If any capture needs attention, the dot is red regardless of how many others are healthily in flight. Under-reporting a problem is the only error mode that matters here.

### Never render CLEAR from a failed read

`RecordingService.runCapture` returns early with `haptics.atRisk()` and a failure notification when `!VaultWriter.isVaultReady()`, and it does so *before* launching the overlay — so in practice the overlay only ever runs with storage access granted. The code must not rely on that. A `listFiles()` returning null, a `SecurityException`, or any throw yields an UNKNOWN state that draws nothing at all.

Drawing nothing rather than green matters more than it looks: a green dot that appears when the app cannot see the vault would actively manufacture false confidence, which is worse than the current situation of no signal at all.

### Fix the size, vary opacity and motion

"Visible but subtle" is not achievable with size alone — a dot small enough to ignore is small enough to miss, and a dot large enough to notice competes with the record dot. Splitting the job across three channels resolves it:

```
                     ┌────────────────────────┐
    24dp inset ─────▶│                    ●   │  20dp — 1/5 the diameter
    top & end        │                        │         ~1/23 the area
                     │                        │         of the record dot
                     │           ⬤            │  96dp, pulsing (unchanged)
                     │                        │
                     │      Listening…        │
                     │  Tap anywhere to stop  │
                     └────────────────────────┘

  state        colour     alpha   motion
  ───────────────────────────────────────────
  clear        #4CAF50    0.55    none
  in flight    #FFA726    0.75    none
  attention    #E53935    1.00    slow pulse
  unknown      —          —       not drawn
```

Motion is the strongest attention magnet on a screen, and the record dot already owns all of it. Keeping the status dot **perfectly still** in the two non-urgent states is what makes it recede into ambient reassurance instead of becoming a second thing to look at. The needs-attention state inverts both channels at once — full opacity plus the only other movement on screen.

Red-on-red is a known collision: a pulsing red corner dot alongside a pulsing red record dot may read poorly at arm's length despite the 5× size difference and the corner placement. An orange-red (`#FF5722`) fallback is specified as the alternative, and the choice is deferred to a device check rather than settled on a monitor.

Visual constants stay local to the overlay composable. `Config` holds behavioural tunables — sample rates, thresholds, paths — and adding colours and dp values to it would dilute that; the staleness threshold is the only genuinely behavioural constant this change introduces.

### Read asynchronously, then poll slowly

The read happens off the main thread and the overlay composes before it completes, so the record dot and stop affordance are on screen at the earliest possible moment regardless of filesystem latency. The indicator simply is not drawn until the first read returns.

A single read at overlay start would go stale: an earlier capture finishing its transcription two seconds into the current recording would leave the dot amber for the rest of a three-minute session. The state therefore refreshes on a low-frequency poll (~2s) for as long as the overlay is up. This costs one `listFiles()` on a folder that is almost always empty — roughly 90 listings across a worst-case capture, which is negligible against continuous 16 kHz audio capture and a pulsing animation.

Observing `TranscriptionQueue` directly was rejected: it would couple the overlay to queue internals, and it cannot see the case that matters most — audio left pending by a process that died before enqueueing anything.

### The current capture never appears in its own indicator

The WAV for the in-progress capture is written after recording ends, and the overlay finishes on `recordingEnded`. So the indicator always describes captures *prior* to this one. This is the desired reading — "did my previous notes land" — but it needs stating, because a user who expects the dot to track the capture they are currently speaking will misread it.

## Risks / Trade-offs

- **One dot for N captures.** Three states cannot say *how many* or *which*. Accepted deliberately: the dot's job is to route attention, and the failure notification is where the detail already lives.
- **The user has to learn what the colours mean.** Single-user, self-built app; the cost is one moment of learning. Green-amber-red is the least surprising possible encoding.
- **Amber is not actionable.** In-flight is a normal transient state with nothing for the user to do. It earns its place only by making a *stuck* capture distinguishable via the staleness rule — without that rule amber should be folded into green.
- **A poll on the capture screen.** Low-frequency directory listings during recording. Cheap, but it is new work on a screen that previously did none; if it shows up in device testing as jank in the pulse animation, drop to a single read at start and accept staleness.
- **The needs-attention state is untestable end-to-end until `fix-transcription-failure-reporting` §4.3 lands.** Until something writes `.log` records on failure, red is only reachable via the staleness path or by placing a file by hand.

## Migration Plan

No migration. No persisted state, no vault format change, no permission change. The feature is additive to one activity and reversible by deleting the indicator composable.

## Sequencing against in-flight changes

- **`fix-transcription-failure-reporting`** — §3 is complete, so `VaultWriter`'s diagnostic helpers and `pendingAudioFiles()` already exist and can be built against today. §4.3 (writing the record on each non-success outcome) is what makes the needs-attention state actually reachable in the field. Build this change now; verify red on device only after §4.3 lands. There is no file overlap: that change works in `RecordingService`, `TranscriptionQueue`, and `CaptureNotifications`; this one works in `TapToStopActivity` and a new `CaptureHealth`.
- **`add-voice-timer-commands`** — no overlap. It touches `Haptics`, `RecordingService`, `TranscriptionQueue`, and a new `commands/` package, and does not go near the overlay.

## Open Questions

- **Red or orange-red for needs-attention?** Settled on device, at arm's length, in a dark room and in daylight.
- **Is the staleness threshold right?** Needs one real measurement of worst-case transcription time for a 3-minute capture on the Pixel 6 Pro before the constant is fixed.
- **Should the same indicator become a home-screen widget?** It is the only variant that reports health with no gesture at all — ambient rather than trigger-bound — and it reuses the identical derivation. Deliberately out of scope here so the derivation can prove itself first, but the state logic is being written as a standalone, Android-free unit specifically so a widget could consume it unchanged.
- **Should the dot be tappable?** Tapping the overlay currently stops recording, and that must not change. A separate tap target on a screen whose entire surface means "stop" is a conflict with no good resolution, so the indicator is display-only.
