## Why

Voice capture is eyes-free by design, but that's the wrong tool for a short thought you'd rather type than speak — standing next to someone, in a quiet room, or just faster to type than to narrate and wait for transcription. Today the only way to get a note into the vault is the full power-button → record → transcribe pipeline. This adds a second, minimal entry point: a home-screen widget that opens a text field, and dismissing it (in any of the normal ways a screen gets dismissed) saves whatever was typed as a new inbox note. No mic, no model, no confirmation tap.

## What Changes

- Add a 1x1 icon-only home-screen widget (`AppWidgetProvider`) that, on tap, launches a new `QuickNoteActivity`.
- `QuickNoteActivity` is a lightweight, dialog-style activity shown over whatever is currently on screen: a single auto-focused, auto-growing plain-text field, no title field, no save/confirm button.
- Save is implicit: `onStop()` (screen off, home press, back press, app switch — anything short of the OS hard-killing the process) writes the current text as a new vault note via the existing `vault/VaultWriter.writeNote(text, captureId)` and `captureIdFor()`, unchanged. If the text is blank/whitespace-only at that point, nothing is written.
- On a successful save, fire the same haptic pattern already used to confirm a saved voice note (no new haptic vocabulary).
- Above the input, show the last 5 captured notes (voice or quick-text) from the inbox folder as read-only recall — capture id plus a short body preview, frontmatter stripped. No editing, no navigation, no search; it exists purely so the user can see what they've already jotted down without leaving the screen.
- This bypasses `assistant/`, `capture/`, and `transcribe/` entirely — it is a new, independent entry point that only touches the last stage of the existing pipeline (`vault/`).
- Explicitly not building in this change: a launcher long-press static shortcut, a Quick Settings tile, or hooking into Pixel's "Quick Tap" gesture — considered and set aside in favor of shipping the widget alone first.

## Capabilities

### New Capabilities
- `quick-text-capture`: a home-screen widget launches a minimal text-entry surface; dismissing it by any normal means saves non-blank text as a new inbox note, with no confirmation step.

### Modified Capabilities
(none — `vault-writing`'s requirements are reused as-is via `VaultWriter.writeNote`; no vault-writing behavior changes)

## Impact

- **Code**: new `quicknote/QuickNoteActivity.kt`, new `AppWidgetProvider` + its widget layout/metadata XML, `AndroidManifest.xml` (new activity + widget receiver), `notify/Haptics` (reuse existing pattern, no changes expected), `vault/VaultWriter` (new `recentNotes()` read-only accessor, additive).
- **No changes** to `assistant/`, `capture/RecordingService`, `transcribe/`, or any existing `vault/VaultWriter` function's signature/behavior — this is purely additive at the entry-point layer plus one new read accessor.
- **Unaffected by** `openspec/changes/migrate-vault-to-saf` (in progress, unimplemented): that change preserves `VaultWriter.writeNote`'s signature, so this feature rides along transparently whenever SAF migration lands.
- **Play Store / manifest**: no new dangerous permissions; `AppWidgetProvider` and an internal activity require no permission beyond what's already declared.
