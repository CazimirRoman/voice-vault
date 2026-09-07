## 1. Settings foundation

- [ ] 1.1 Create `settings/SettingsStore.kt`: app-private `SharedPreferences` persistence with typed keys, defaulting every value to its existing `Config` constant. No imports from `pro/`.
- [ ] 1.2 Create `settings/CaptureSettings.kt` exposing silence threshold, silence duration, max duration, and haptic intensity, each clamped to a documented range on read.
- [ ] 1.3 JVM unit tests for clamping: out-of-range, absent, and unparseable stored values all resolve to a safe value, and the duration cap stays finite for every input.
- [ ] 1.4 JVM unit test asserting an unconfigured store returns exactly the `Config` constants.

## 2. Capture tuning

- [ ] 2.1 Change `capture/AudioRecorder` to take its silence threshold, silence duration, and max duration as constructor or start parameters instead of reading `Config` directly.
- [ ] 2.2 Have `capture/RecordingService` resolve settings once at capture start and pass them in, so a settings change mid-capture cannot affect the capture in flight.
- [ ] 2.3 Apply haptic intensity in `capture/Haptics` without altering the shape of the three patterns, so they stay distinguishable from one another.
- [ ] 2.4 Confirm no file under `capture/` imports `dev.cazimir.voicevault.pro`, and that the invariant test from `add-pro-entitlement` still passes.

## 3. Note formatting

- [ ] 3.1 Create `vault/NoteTemplate.kt`: the closed placeholder set and an expansion function that cannot throw. Unknown placeholders pass through as literal text.
- [ ] 3.2 Add `settings/NoteSettings.kt` for the filename pattern, the frontmatter template, and the daily-note append flag, with defaults reproducing today's output exactly.
- [ ] 3.3 Change `vault/VaultWriter.writeNote` to build its filename and frontmatter through `NoteTemplate`, falling back to the default pattern when an expanded name would be empty or unusable.
- [ ] 3.4 Add filename uniquing so an expanded name can never overwrite an existing note.
- [ ] 3.5 JVM unit tests: default template output is byte-for-byte identical to today's note, empty frontmatter template yields a bare transcript, unknown placeholders survive as text, and a colliding name is made unique.

## 4. Daily-note append

- [ ] 4.1 Add `listNotes`-based read plus append support to `vault/VaultStorage` and `SafVaultStorage`, keeping the temp-file-then-rename write path.
- [ ] 4.2 Serialise appends through a mutex in `vault/`, covering quick-text saves as well as transcription-queue writes.
- [ ] 4.3 Implement the read-compare-write rule: if the target note changed between read and write, abandon the append and write the capture as its own separate note.
- [ ] 4.4 JVM unit tests with a fake storage: creates the daily note when absent, appends without altering existing content, both of two concurrent captures survive, and a note modified mid-append falls back to a separate note.
- [ ] 4.5 Confirm appended writes still go through `AtomicFileWriter`.

## 5. Multiple destinations

- [ ] 5.1 Change `vault/VaultGrant` to hold a list of destination records (name, persisted URI, trigger words, default flag), with a startup migration promoting an existing single grant to the default.
- [ ] 5.2 Construct `SafVaultStorage` per destination, and keep `VaultAccess` semantics: no destinations means `NotConfigured`, an unresolvable default means `Lost`.
- [ ] 5.3 Extend `vault/VaultFolderSelector` with add and remove, including reassigning the default when the current default is removed.
- [ ] 5.4 JVM unit tests for the migration, for removing the default, and for removing the last destination.

## 6. Spoken routing

- [ ] 6.1 Create `vault/DestinationRouter.kt`: first-word matching, case-insensitive, trailing punctuation stripped, returning both the destination and the body with the trigger removed.
- [ ] 6.2 Implement the fallback rules: no match, duplicate trigger words, an empty body after stripping, an unresolvable routed grant, and any unexpected error all resolve to the default destination with the body unmodified.
- [ ] 6.3 Wire routing into the note write path for both voice captures and quick-text saves.
- [ ] 6.4 JVM unit tests covering every scenario in the `vault-destinations` spec, including a trigger word appearing later in the transcript and a transcript that is only a trigger word.

## 7. Settings screen

- [ ] 7.1 Create `settings/SettingsScreen.kt` as a one-level list matching the existing `SetupStep` visual language, reachable from `MainActivity`.
- [ ] 7.2 Render capture tuning controls with their ranges visible, and a reset-to-defaults action.
- [ ] 7.3 Render the filename pattern and frontmatter template editors with the supported placeholders listed, plus a live preview of the resulting note name and frontmatter.
- [ ] 7.4 Render the destinations list with add, remove, rename, set-default, and trigger-word editing.
- [ ] 7.5 Gate editing on `ProEntitlement`: without Pro every control is visible and read-only, and the screen links to the purchase surface. Selecting a first vault folder stays available to everyone.
- [ ] 7.6 Confirm `SettingsScreen` is the only new file that imports `pro/`.

## 8. Verification

- [ ] 8.1 Run `./gradlew test lint` and fix what they surface.
- [ ] 8.2 Update `CLAUDE.md`: record that `Config` constants are now defaults rather than the source of truth, add the `settings/` package to the architecture section, and add the never-gate-reads and append-falls-back-to-a-separate-note rules to the invariants list.
- [ ] 8.3 Update the "no settings/config UI" entry in the non-goals section, which this change deliberately reverses for Pro.

## 9. Device verification (requires a human and the physical Pixel 6 Pro)

- [ ] 9.1 With no settings stored, run a voice capture and confirm the note is identical in name and frontmatter to one captured before this change.
- [ ] 9.2 Shorten the silence duration, capture, and confirm the recording ends sooner. Reset to defaults and confirm it returns to roughly 5 seconds.
- [ ] 9.3 Set a custom filename pattern and frontmatter template, capture, and confirm the note appears correctly in Obsidian.
- [ ] 9.4 Enable daily-note append and make three captures in a day, confirming all three land in one note in order with nothing lost.
- [ ] 9.5 Add a second destination with a trigger word, speak a note starting with that word, and confirm it lands in the second folder with the trigger stripped.
- [ ] 9.6 Revoke the second destination's folder permission in Android settings, capture with its trigger word, and confirm the note lands in the default folder instead.
- [ ] 9.7 With the debug entitlement override set to force free, confirm the settings screen is read-only, and that a capture still uses the previously customised values.
