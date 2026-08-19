## 1. Health derivation

- [ ] 1.1 Add `Config.STALE_PENDING_THRESHOLD_MS` — the age past which pending audio with no diagnostic record counts as stuck rather than in flight
- [ ] 1.2 Add `capture/CaptureHealth.kt` with a `CaptureHealthState` enum (`Clear`, `InFlight`, `NeedsAttention`, `Unknown`) and a pure `derive(pendingDir: File, now: Long): CaptureHealthState`
- [ ] 1.3 Pair each `.wav` with its sibling `.<DIAGNOSTIC_RECORD_EXTENSION>` by base name; classify a pair as `NeedsAttention`, a lone `.wav` as `InFlight`, and a lone diagnostic record as contributing nothing
- [ ] 1.4 Apply the staleness rule: a lone `.wav` whose `lastModified` is older than the threshold classifies as `NeedsAttention`
- [ ] 1.5 Apply precedence — `NeedsAttention` > `InFlight` > `Clear` — across all pending entries
- [ ] 1.6 Return `Unknown` when `listFiles()` returns null or the enumeration throws; return `Clear` when the folder simply does not exist
- [ ] 1.7 Keep the file free of Android imports so it stays JVM-testable and reusable by a future widget
- [ ] 1.8 Log any throwable caught during derivation under `Config.LOG_TAG`

## 2. Overlay indicator

- [ ] 2.1 In `TapToStopActivity`, hold the health state in Compose state initialised to `Unknown`
- [ ] 2.2 Derive it off the main thread in `lifecycleScope` on an IO dispatcher; never call `derive` from composition
- [ ] 2.3 Re-derive on a ~2s poll for as long as the overlay is alive, cancelling with the lifecycle scope
- [ ] 2.4 Add the indicator to the existing root `Box` at `Alignment.TopEnd`, 20dp diameter, 24dp inset from top and end
- [ ] 2.5 Render `Clear` as `#4CAF50` at 0.55 alpha with no animation, `InFlight` as `#FFA726` at 0.75 alpha with no animation
- [ ] 2.6 Render `NeedsAttention` at full alpha with a slow pulse, using an animation distinct from the record dot's 900ms 0.75→1.15 loop
- [ ] 2.7 Draw nothing at all for `Unknown`
- [ ] 2.8 Confirm the record dot, "Listening…", and "Tap anywhere to stop" keep their exact current size, position, and animation, and that the indicator does not intercept the tap-to-stop pointer input

## 3. Tests

- [ ] 3.1 Unit test: empty pending dir → `Clear`; non-existent pending dir → `Clear`
- [ ] 3.2 Unit test: lone `.wav` within the threshold → `InFlight`
- [ ] 3.3 Unit test: `.wav` + sibling `.log` → `NeedsAttention`
- [ ] 3.4 Unit test: lone `.wav` older than the threshold → `NeedsAttention`
- [ ] 3.5 Unit test: lone `.log` with no `.wav` → `Clear`
- [ ] 3.6 Unit test: one failed plus one healthy in-flight capture → `NeedsAttention` (precedence)
- [ ] 3.7 Unit test: unreadable directory → `Unknown`, and never `Clear`
- [ ] 3.8 Run `./gradlew test` and `./gradlew lint`

## 4. Device verification (Pixel 6 Pro, human required)

- [ ] 4.1 Trigger a capture with an empty `_pending/` and confirm the green dot is visible at arm's length yet does not pull attention from the record dot
- [ ] 4.2 Confirm the green dot is legible in a dark room and in direct daylight; adjust alpha if not
- [ ] 4.3 Place a `.wav` in `_pending/` by hand, trigger a capture, and confirm amber
- [ ] 4.4 Place a `.wav` + `.log` pair by hand, trigger a capture, and confirm red — and decide `#E53935` vs `#FF5722` against the pulsing red record dot
- [ ] 4.5 Backdate a lone `.wav` past the staleness threshold and confirm it reads red rather than amber
- [ ] 4.6 Lock the phone, trigger via the power button, and confirm the indicator appears over the keyguard and discloses nothing but its colour
- [ ] 4.7 Revoke all-files access and confirm no dot is drawn — specifically that no green dot appears
- [ ] 4.8 Confirm the start haptic timing is unchanged and the first spoken word is still captured intact
- [ ] 4.9 Confirm tapping anywhere — including on the indicator — still stops the recording immediately
- [ ] 4.10 Record for the full 3-minute cap and confirm no jank in either animation from the poll
- [ ] 4.11 With one capture queued, trigger a second and confirm the dot goes amber → green mid-recording as the first completes
- [ ] 4.12 Measure worst-case transcription time for a 3-minute capture and fix `STALE_PENDING_THRESHOLD_MS` against it

## 5. Follow-up gated on `fix-transcription-failure-reporting`

- [ ] 5.1 After §4.3 of that change lands, force a real failure and confirm the indicator turns red from a genuinely written diagnostic record rather than a hand-placed one
- [ ] 5.2 Confirm discarding the capture from its notification returns the indicator to green
