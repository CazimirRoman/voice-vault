## Context

The app currently has exactly one capture entry point: a power-button hold that starts `assistant/` → `capture/RecordingService` → `vault/` (pending WAV) → `transcribe/` → `vault/` (note). That pipeline exists because voice capture has real durability concerns (audio must survive a crash before transcription is attempted) and is eyes-free by design (the phone gets pocketed mid-capture).

Typed text has neither of those properties: it's fully "captured" the instant it's on screen, and the user is looking at the phone the whole time. This change adds a second, independent entry point that skips straight to `vault/VaultWriter.writeNote`, the same function `transcribe/TranscriptionQueue` calls once a voice note's transcript is ready. No new abstraction is needed in `vault/`; this is purely a new caller.

Precedent for a lightweight always-on-top capture surface already exists in `capture/TapToStopActivity`: `excludeFromRecents`, `noHistory`, a translucent theme. That activity is eyes-free (full-bleed black, `FLAG_KEEP_SCREEN_ON`, tap-anywhere-to-stop); this one is eyes-on (user is actively typing), so it borrows the lifecycle/manifest shape but not the visual treatment or screen-keep-awake behavior.

## Goals / Non-Goals

**Goals:**
- One tap from a home-screen widget to a focused, ready-to-type text field.
- Zero confirmation steps: dismissing the activity by any normal means (screen off, home, back, app switch) saves non-blank text as a new inbox note.
- Reuse `VaultWriter.writeNote`/`captureIdFor` exactly as-is, so quick notes are indistinguishable in the vault from voice notes (same frontmatter schema) except for content.
- Reuse the existing "capture ended successfully" haptic (`Haptics.stopped()`) rather than inventing new feedback.

**Non-Goals:**
- No launcher long-press static shortcut, no Quick Settings tile, no Pixel "Quick Tap" gesture integration — considered during exploration, deliberately deferred to keep this change to one trigger surface.
- No editing of past notes, no search, no full note browser, no Markdown toolbar, no title field on the input itself — plain text becomes the entire note body, same as today's transcripts. The recent-notes list added below is read-only recall, not a browse/edit UI.
- No new durability checkpoint (no `_pending/`-style staging). Unlike audio, there is nothing to durably checkpoint before "processing" — the write itself, via `AtomicFileWriter`, is the only guarantee needed, and it already exists.
- No protection against the OS hard-killing the activity's process before `onStop()` runs. This is the same class of risk any Android app accepts for unsaved input; forcing every keystroke to disk would be complexity this app has no existing pattern for and the proposal doesn't ask for.

## Decisions

### `QuickNoteActivity` saves in `onStop()`, not on an explicit action

`onStop()` fires for every path the proposal lists as a valid dismissal: screen off (power button), home press, recent-apps switch, and back press (finish() also triggers onStop before onDestroy). This makes "no confirmation" fall out of a single lifecycle hook rather than needing separate handlers per dismissal path.

*Alternative considered:* `onPause()`. Rejected — `onPause()` also fires for transient, non-dismissing interruptions (e.g. a system dialog or notification shade momentarily overlapping the activity) where the user hasn't actually left; saving there is more eager than necessary and offers no benefit over `onStop()` given this activity has no reason to keep running once truly backgrounded.

### Blank-text guard lives in the same `onStop()` write path

If `editText.text.isBlank()`, skip `VaultWriter.writeNote`/`captureIdFor` entirely — no note, no haptic. This mirrors `TranscriptionQueue.isNonSpeechPlaceholder`'s spirit (don't write a note for "nothing was actually said") without needing that exact check, since there's no transcription placeholder here — a plain blank/whitespace check is sufficient because the text is user-typed, not model output.

### New activity, not a repurposed one

`QuickNoteActivity` is new rather than adding a mode flag to `MainActivity` or `TapToStopActivity`. `MainActivity` already carries first-run setup (permissions, model download) — overloading it with a fast-path text-entry mode via intent extras would couple two unrelated lifecycles. `TapToStopActivity` is eyes-free by design (`FLAG_KEEP_SCREEN_ON`, full-bleed overlay, tap-to-stop) and semantically tied to an in-progress recording via `CaptureEvents`; forcing quick-note through it would mean stripping most of what it does. A small new activity is less code than the conditionals either repurposing would require.

### Manifest shape: opaque blank backdrop with a centered card, own task, `exported="false"`

The widget's `PendingIntent` is created and owned by the app's own `AppWidgetProvider` (`PendingIntent.getActivity` from within the app's process); the launcher only ever fires that already-constructed `PendingIntent`, it never needs to resolve `QuickNoteActivity` by component name the way it resolves a launcher-shortcut target. So `QuickNoteActivity` follows `TapToStopActivity`'s manifest shape — `exported="false"`, `excludeFromRecents`, `noHistory` — not `MainActivity`/`AssistIntentActivity`'s `exported="true"`. No `FLAG_KEEP_SCREEN_ON` (this is eyes-on; the user turning the screen off is the deliberate "I'm done" signal, exactly as it already is for voice capture). *Reversed during implementation — see "The surface keeps the display awake" below.*

*Revised during implementation:* the theme is **opaque**, not translucent, and the activity has its own `taskAffinity=""`. A genuinely translucent window (the first cut used `android:Theme.Translucent.NoTitleBar`) composites against whatever else is in `QuickNoteActivity`'s *task* — and with no `taskAffinity` set, that task could be `MainActivity`'s (the first-run permissions/storage/assistant-setup screen), which then became visible through the translucent parts of the window instead of "whatever the user was doing before." An opaque window with its own task sidesteps this: the backdrop is a plain solid-color screen we fully control, the input card is centered on it (not top-aligned), and nothing about it depends on back-stack/task state at tap time.

*Alternative considered:* reuse `Theme.Translucent.NoTitleBar.Fullscreen` as-is. Rejected both for the reason above (task-affinity leakage) and because it has no window chrome/backdrop suited to a small centered input dialog.

### `onStop()` clears the buffer and finishes the activity

`onStop()` saving the note is necessary but not sufficient: `android:noHistory` does *not* finish the activity when the screen turns off (the platform defers no-history finishing while the device is going to sleep), so a power-button dismissal left the activity merely stopped, with its Compose state intact. Waking and unlocking the phone resumed the same instance, still showing the text that had already been written to the vault, and the next screen-off ran `onStop()` again — minting a second note (`captureIdFor()` returns a fresh id each time, so nothing collided) and firing a second haptic, once per power-cycle.

Two changes, deliberately both:

1. **Clear the text after a successful write.** The invariant is *a given piece of typed text is written to the vault at most once*, not *`onStop()` happens to fire once*. Clearing makes the existing blank-guard short-circuit any repeat `onStop()`, so no duplicate is possible even on a lifecycle path that skips the finish.
2. **`finish()` on `onStop()`, saved or not.** The surface is transient by definition and `onStop()` is already this change's declared commit point for every dismissal path; letting it survive one means waking the phone hours later still sitting on an (empty) EasyNote screen on top of whatever was there before. Finishing unconditionally also keeps the recent-notes list's "read once at start" reasoning true — a saved note can never need to appear in its own still-visible list, because the list goes away with the save.

This required hoisting the text state: the composable owned it via `remember { mutableStateOf("") }` and the activity held only a write-only mirror (`currentText`), which gave the activity no way to push a clear back down. The activity now owns the single `mutableStateOf`, so there is no shadow copy to desync.

*Alternative considered:* clear the text but keep the activity alive, treating the surface as a persistent scratchpad. Rejected — it makes the recent-notes list stale the moment it matters (the note you just wrote is missing from the recall list you are still looking at), and a surface that never goes away is a note-browser by another name, which the Non-Goals rule out.

### The surface keeps the display awake

`FLAG_KEEP_SCREEN_ON`, matching `TapToStopActivity`. The original decision above declined the flag on the grounds that screen-off is the user's deliberate "I'm done" signal — but that assumes every screen-off is the user's doing. An idle display timeout produces an identical `onStop()`, and this activity has no way to tell them apart. Typing resets the timeout, so the window is narrow (pausing to think, or being interrupted mid-note), but the consequence got sharper once `onStop()` also finishes the activity: before, a timeout saved a partial note and left the screen up, so the user unlocked and kept typing; now the partial note is committed and the surface is gone, and finishing the thought means writing a second, separate note.

This is the same pairing CLAUDE.md already records as an invariant for the capture overlay — "removing the flag makes an idle display timeout indistinguishable from a deliberate power press, and silently cuts recordings short mid-thought" — and it holds here for the same reason.

*Alternative considered:* leave the flag off and accept the rare mis-commit. Rejected — an abandoned quick-note screen holding the display awake is a visible, self-correcting failure (the phone is face-up with a lit screen), whereas a silently truncated note is exactly the failure this app exists to prevent. Noted as a real cost, though: unlike a recording, a quick note has no self-terminating condition (no silence backstop, no duration cap), so an untouched surface stays lit until the user comes back to it.

### Widget is a plain `AppWidgetProvider` with a single click target, no configuration activity

A minimal 1x1 widget: one `ImageView`/icon-only `RemoteViews` layout, `onUpdate` wires a `PendingIntent` (via `PendingIntent.getActivity`) covering the whole widget to launch `QuickNoteActivity`. No widget configuration screen, no resizing behavior beyond the OS default, since there is nothing to configure — it does exactly one thing.

### Recent notes shown above the input are read via a new `VaultWriter.recentNotes`, not a new abstraction

Added `VaultWriter.recentNotes(limit = 5)`, returning the newest 5 `.md` files directly under the inbox folder (by `lastModified()`), each with its frontmatter block stripped for display. This reuses `Config.inboxDir` and the same file-listing style already used by `pendingAudioFiles()` — no new storage abstraction. The list is read once, off the main thread, when `QuickNoteActivity` starts (`LaunchedEffect` + `Dispatchers.IO`); it does not live-update while the screen is open, since a quick note being saved only happens at `onStop()` (i.e., as the screen is already going away) so there is never a moment where the freshly-saved note should have appeared in its own still-visible list.

*Alternative considered:* sort by parsing the `Created:` frontmatter field instead of filesystem `lastModified()`. Rejected — `captureIdFor()` already guarantees the filename itself is a sortable timestamp with disambiguating suffixes, and `lastModified()` needs no parsing; both orderings agree in every normal case (notes are never touched after creation).

Each note is shown frontmatter-stripped, capture-id label plus up to 3 lines of body text, in a plain read-only card — tapping a card does nothing (no navigation, no edit), it only blocks the tap from bubbling to the screen's tap-outside-to-dismiss handler.

*Revised during implementation:* the app targets SDK 36, where edge-to-edge is OS-enforced regardless of `windowSoftInputMode` — the window draws fully behind both the status bar and the keyboard with nothing padding around either, which first surfaced as the "Recent notes" label rendering under the status bar and, more seriously, the input card being laid out underneath the keyboard (still present, just invisible/unreachable) rather than shrinking above it. Fixed by calling `enableEdgeToEdge()` in `onCreate` and applying `Modifier.safeDrawingPadding()` on the root layout, which accounts for both the status bar and the IME inset in one call rather than relying on manifest-level `adjustResize` behavior that no longer resizes the window on this target.

## Risks / Trade-offs

- **Process death before `onStop()` runs loses unsaved text.** → Accepted (see Non-Goals); this is standard Android behavior for any unsaved input field and out of proportion to what a two-line "type and go" note needs.
- **Home screen must have the widget placed for the "fastest path" to actually be fast**, and unlocking doesn't guarantee landing on the page with the widget. → Accepted; this was weighed against a Quick Settings tile during exploration and the user chose to ship only the widget for now. A tile can be added later as a separate change without touching this one, since both would ultimately just launch `QuickNoteActivity`.
- **An opaque backdrop means the quick-note screen no longer visually shows the app the user was in before tapping the widget** (a genuinely translucent overlay would have, if task-affinity leakage weren't a problem). → Accepted; the blank backdrop was chosen specifically to avoid depending on task/back-stack state, and this app has no requirement that the previous screen remain visible underneath.

- **`FLAG_KEEP_SCREEN_ON` means an abandoned quick-note screen holds the display awake indefinitely**, with no silence backstop or duration cap to end it the way a recording has. → Accepted; a lit screen is a visible, self-correcting failure, and it was preferred over an idle timeout silently committing a half-written note.

## Migration Plan

Purely additive — no existing behavior changes, no data migration. Ship widget + activity together; verify on-device (widget placement, tap → focused field → type → power off → confirm note appears in `00-Inbox/` with the expected frontmatter and haptic). Rollback is a straight revert; nothing else depends on this code.

## Open Questions

- Exact widget icon/visual — reuse the app launcher icon, or a distinct "quick note" glyph so it's visually distinguishable from the app icon itself on the home screen? Cosmetic, can be decided during implementation.
