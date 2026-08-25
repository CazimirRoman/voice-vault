## 1. QuickNoteActivity

- [x] 1.1 Create `QuickNoteActivity` (new `quicknote/` package or alongside `capture/`, per implementer's call) with an opaque blank-backdrop theme, `exported="false"`, `excludeFromRecents`, `noHistory`, `taskAffinity=""` in the manifest (revised from an initial translucent theme, which leaked `MainActivity` through when task affinity wasn't isolated — see design.md).
- [x] 1.2 Build the UI: a single auto-focused, auto-growing plain-text field, no title, no save/confirm button, centered in a card over a plain blank backdrop (not full-bleed, no `FLAG_KEEP_SCREEN_ON`).
- [x] 1.3 On `onStop()`, if the field's text is blank/whitespace-only, skip saving entirely (no note, no haptic).
- [x] 1.4 On `onStop()`, if the text is non-blank, call `VaultWriter.captureIdFor()` then `VaultWriter.writeNote(text, captureId)` and fire `Haptics.stopped()` on success.
- [ ] 1.5 Verify back press, home press, and app-switch all route through the same `onStop()` save path (no separate handlers needed, but confirm empirically on device).

## 1a. Recent notes recall

- [x] 1a.1 Add `VaultWriter.recentNotes(limit = 5)`, returning the newest `.md` files in the inbox folder by `lastModified()`, frontmatter stripped, as `NoteSummary(captureId, body)`.
- [x] 1a.2 Load recent notes off the main thread when `QuickNoteActivity` starts and render them above the input in a scrollable, read-only list (capture id + up to 3 lines of body preview per card).
- [x] 1a.3 Ensure tapping a recent-note card is a no-op (no navigation/edit) and does not trigger the screen's tap-outside-to-dismiss handler.

## 2. Home screen widget

- [x] 2.1 Add a minimal `AppWidgetProvider` (single click target, 1x1, icon-only `RemoteViews` layout), with its `res/xml` widget metadata (`minWidth`/`minHeight` for 1x1, `resizeMode="none"`, no configuration activity).
- [x] 2.2 Wire `onUpdate` to set a `PendingIntent.getActivity` targeting `QuickNoteActivity` on the widget's root view.
- [x] 2.3 Register the widget provider in `AndroidManifest.xml` with the `APPWIDGET_UPDATE` intent-filter and metadata pointing at the widget's XML descriptor.
- [x] 2.4 Pick/create the widget icon asset (reused `@mipmap/ic_launcher` — resolved the open question from design.md in favor of the simpler option; a distinct glyph can be swapped in later without touching code).

## 3. Manual verification on device

- [ ] 3.1 Place the widget on the home screen; confirm a tap opens `QuickNoteActivity` with the field already focused and keyboard shown.
- [ ] 3.2 Type text, press the power button; confirm the screen turns off, a haptic fires, and the note appears in `00-Inbox/` with the typed text as body and the standard frontmatter.
- [ ] 3.3 Repeat with home-button dismissal, back-button dismissal, and switching to another app via the recents/overview screen; confirm all three save correctly.
- [ ] 3.4 Open the widget and dismiss without typing anything (or typing only whitespace); confirm no note is created and no haptic fires.
- [ ] 3.5 Confirm the note's frontmatter and filename scheme match voice-captured notes exactly (same `captureIdFor` collision handling against both `00-Inbox/` and `_pending/`).
- [ ] 3.6 Confirm the recent-notes list shows the correct newest-5 notes (mix of voice and quick-text), with frontmatter stripped and previews truncated at 3 lines; confirm it shows fewer than 5 gracefully when the inbox has fewer notes.
