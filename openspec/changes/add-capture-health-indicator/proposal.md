## Why

The app tells the user nothing when it works. Success is signalled by silence — no haptic beyond the stop buzz, no notification, no visual. That is the correct design for the capture moment, but it leaves the user with no way to answer a question they keep asking anyway: *are my notes actually landing in the vault?* Today the only way to find out is to open Obsidian and look, which is exactly the app-opening the power-button gesture exists to avoid.

The obvious fix — showing recent transcripts on the listening overlay — was explored and rejected. It puts note content on a surface with `showWhenLocked="true"`, so anyone holding the power button on a locked phone reads the user's last five notes. It requires opening and parsing Markdown files on the capture path while `AudioRecord` is initialising. And it answers the question indirectly, by asking the user to eyeball evidence.

The direct answer is already computable, and it is one bit. The pipeline deletes a capture's WAV from `_pending/` only after its note is on disk, which makes the pending folder an exact ledger of unfinished work:

> `_pending/` holds no audio ⟺ every capture ever made became a note.

Deriving that needs one directory listing — no note bodies, no transcripts, nothing that could leak. `fix-transcription-failure-reporting` sharpens it further: the per-capture `.log` diagnostic record written beside orphan audio splits "still working on it" from "this one failed".

## What Changes

- **Define a capture health state** derived solely from enumerating `_pending/`: *clear* (no pending audio), *in flight* (pending audio with no diagnostic record beside it), *needs attention* (pending audio with a diagnostic record, or pending audio that has sat unresolved past a staleness threshold). Attention outranks in-flight when both are present.
- **Show it as one indicator on the listening overlay.** A 20dp dot in the top-end corner. No text, no count, no capture ids, no transcript. The existing 96dp pulsing record dot, "Listening…", and "Tap anywhere to stop" are unchanged and unmoved.
- **Make healthy quiet and broken loud.** Size stays fixed; opacity and motion carry the urgency. Clear is a dim, motionless green; needs-attention is full-opacity and is the only other moving thing on a screen whose motion is otherwise owned by the record dot.
- **Never show a false all-clear.** If the pending folder cannot be enumerated, the indicator shows nothing rather than clear.
- **Never let the health read touch recording.** The read runs off the main thread, the overlay renders before it completes, and no capture path waits on it or fails because of it.
- **Treat silently-stuck audio as unhealthy.** Pending audio older than a configured threshold with no diagnostic record beside it reads as needs-attention, so a capture wedged forever cannot masquerade as one that is merely in progress.

Not in scope: any list of recent captures or transcripts, anywhere; any detail UI in `MainActivity`; any change to capture, transcription, vault-write ordering, or the haptic vocabulary; any new permission; any settings UI. When the indicator is red, the detail surface is the failure notification that `fix-transcription-failure-reporting` already builds — it names the capture, the classified outcome, the duration, and what whisper heard. Duplicating that on a screen shown while the user is speaking would be worse, not better.

## Capabilities

### New Capabilities

- `capture-health`: How the health of the capture pipeline is derived from the pending folder, what the three states mean, how the indicator renders them, and the guarantees it must hold — no note content on the surface, no false all-clear, no interference with recording.

### Modified Capabilities

- `voice-capture`: Adds the requirement that the listening overlay may carry status information but SHALL remain non-load-bearing — nothing it displays or fails to display may delay, block, or alter a recording.

## Impact

- **New** `capture/CaptureHealth.kt` — pure derivation of the health state from a directory listing, plus the state enum. JVM-testable against a temp directory.
- `capture/TapToStopActivity.kt` — reads the health state asynchronously, renders the corner indicator, refreshes on a low-frequency poll while the overlay is up.
- `Config.kt` — staleness threshold for pending audio with no diagnostic record.
- `vault/VaultWriter.kt` — a listing helper if the existing `pendingAudioFiles()` does not already suffice for pairing audio with its diagnostic record.
- Visual constants (20dp, 24dp inset, the three colours and alphas) stay local to the overlay composable rather than in `Config`, since they are not behavioural tunables.
- No new permissions, no new dependencies, no network access, no change to any file written into the vault.
- Tests: `app/src/test` gains coverage for state derivation, precedence, staleness, and the unreadable-folder case. The visual read at arm's length and the lock-screen check need the physical Pixel 6 Pro.
- **Sequencing:** the `.log` helpers this depends on already exist (`fix-transcription-failure-reporting` §3 is complete), but nothing writes them yet (§4.3 is open). This change is safe to build now; its needs-attention state stays unreachable until §4.3 lands. See `design.md`.
