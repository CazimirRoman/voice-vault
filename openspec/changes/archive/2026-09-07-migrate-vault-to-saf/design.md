## Context

`vault/VaultWriter.kt` and `vault/AtomicFileWriter.kt` currently do direct `java.io.File` I/O against `Config.vaultDir`, a compile-time constant path, gated on `MANAGE_EXTERNAL_STORAGE` (`Environment.isExternalStorageManager()`). This was an explicit, documented trade-off for the single-device MVP (`openspec/changes/archive/2026-08-13-add-voice-note-capture/design.md`, "`MANAGE_EXTERNAL_STORAGE` over the Storage Access Framework"): SAF was rejected there specifically because a revoked/invalid URI permission fails writes silently, which is hostile to an eyes-free app with no UI to surface the problem.

That risk hasn't gone away — this design has to actually close it, not just accept it, because the reason for making this change (Play Store distribution to multiple users) is stronger than the reason it was rejected the first time (portability this app didn't yet need).

Everything downstream of `vault/` — `capture/RecordingService`, `transcribe/TranscriptionQueue`, `notify/CaptureNotifications` — currently reasons about pending audio and notes as `File` objects with filesystem paths. SAF replaces that with `Uri`/`DocumentFile` objects scoped under one granted tree.

## Goals / Non-Goals

**Goals:**
- Remove `MANAGE_EXTERNAL_STORAGE` from the app entirely.
- Let each user pick their own vault folder once, with the grant persisting indefinitely without re-prompting.
- Make SAF permission loss a detected, notified failure (reusing the existing failure-notification machinery), never a silent one.
- Preserve every existing durability guarantee in `vault-writing`: audio-before-transcription ordering, atomic writes, retry-on-relaunch, phantom-notification cleanup.

**Non-Goals:**
- Multi-vault support, or letting the user change vaults after initial setup for reasons other than recovering from a lost grant (a "change vault folder" settings entry can come later; this change only needs re-selection as a *recovery* path).
- Any change to note format, filename scheme, or the assistant/capture pipeline upstream of `vault/`.
- Fixing the assistant-role (`BIND_VOICE_INTERACTION`) reinstall flakiness — unrelated, tracked separately if ever addressed.
- Supporting cloud-backed document providers (Google Drive, etc.) as the vault folder. The picker technically allows selecting one, but `DocumentsContract.renameDocument` and write latency are unverified against non-local providers; this design targets a folder on local/shared device storage, which is where an Obsidian vault actually lives.

## Decisions

### Pending audio and diagnostics stay in app-internal `filesDir`; only finished notes go through SAF

Whisper (`WhisperTranscriber.transcribe`) needs a real filesystem path — it calls `Whisper.transcribe(model, wavFile.absolutePath, ...)`, and `ModelProvisioner` already keeps the model in `filesDir` for the same reason. A `DocumentFile` under a user-picked tree has no usable `absolutePath`, so a pending `.wav` living in the SAF tree could not be handed to whisper without an extra copy on every attempt. `TranscriptionQueue` also keys its in-flight dedup set on `wavFile.canonicalPath` and calls `.exists()` / `.length()` / `.delete()` throughout.

So `_pending/` moves out of the vault and into `context.filesDir/pending/`:

- `writePendingAudio`, `pendingAudioFiles`, `writeDiagnostic`, `deleteDiagnostic`, `wavDurationSeconds` stay `java.io.File`-based against `filesDir`, and `AtomicFileWriter` (temp-file + `renameTo`) is unchanged for them.
- Only `writeNote` (and reads of past notes — `recentNotes`, `noteExists`, `captureIdFor`'s `.md` collision check) go through SAF into the user-selected inbox folder.

The durability guarantee is unchanged: audio is still written to real disk, atomically, before transcription is attempted. What changes is that orphan/partial `.wav` and `.log` files never land in the user's synced vault, and pending audio is app-private (lost on uninstall — acceptable for this app; a pending capture only lives minutes-to-hours before it transcribes or the user sees the failure notification).

*Trade-off:* a diagnostic `.log` no longer sits next to a note's would-be location in the vault for a curious user to find; it lives beside its orphan audio in `filesDir` instead, reachable only via `adb`. Acceptable — the failure notification already carries the human-readable reason.

### A thin `VaultStorage` seam behind the `VaultWriter` note API

Introduce a small interface (`VaultStorage`) that `VaultWriter` calls into for the SAF note operations: resolve access (`Available` / `NotConfigured` / `Lost`), write a note atomically, check a note exists, list note base names, list notes with content. Its methods take plain `String` file names and return plain values (no `DocumentFile` / `Uri` in the signatures), so a JVM `FakeVaultStorage` can stand in for unit tests. `SafVaultStorage(context)` is the real implementation over `DocumentFile` / `ContentResolver` / the persisted grant. `VaultWriter`'s public methods gain a `Context` parameter (matching `ModelProvisioner` / `TranscriptionQueue` convention) and build a `SafVaultStorage` from it; `internal` overloads take a `VaultStorage` directly for tests.

`writeNote`'s return type drops from `File` to `Unit` — no caller used it (`TranscriptionQueue` only needed "did it throw", `QuickNoteActivity` ignored it). `TranscriptionOutcome.Success` carries the note's base name (a `String`) instead of a `File`.

*Alternative considered:* Push `DocumentFile` directly into every caller. Rejected — it would spread SAF plumbing into `RecordingService`, `TranscriptionQueue`, and `CaptureNotifications`, all of which only ever needed "does this capture have a note," not filesystem mechanics. The seam keeps the blast radius inside `vault/`.

### Persisted grant stored as a URI string in `SharedPreferences`

Store the selected tree URI's string form (`Uri.toString()`) in a small `SharedPreferences` file (or a single key in an existing one). On each access, resolve it back to `DocumentFile.fromTreeUri(context, Uri.parse(stored))` and confirm `.canWrite()` before use.

*Alternative considered:* A file in `filesDir` mirroring how `ModelProvisioner` tracks state. Rejected only for consistency — `SharedPreferences` is the conventional place for a single small persisted setting and needs no `.complete`-marker-style atomicity dance, since it's a single key write, not a large download.

### Atomic write via temp document + `DocumentsContract.renameDocument`

Mirror `AtomicFileWriter`'s existing shape: create `<name>.tmp` as a new `DocumentFile` under the target directory, write bytes to it via `ContentResolver.openOutputStream`, then call `DocumentsContract.renameDocument(resolver, tempDocumentUri, finalName)`. This keeps the "sync client never observes a partial file" guarantee, since the local storage document provider backing a normal device folder supports atomic rename the same way the filesystem does underneath it.

*Alternative considered:* Write directly to the final name and accept the small partial-write window. Rejected — this is the exact property `vault-writing`'s "Writes are atomic" requirement exists to prevent, and SAF's rename support makes there no reason to regress it.

*Risk carried over from the archived design, now actually mitigated:* the "revoked permission fails silently" concern is addressed by treating `DocumentFile` resolution failure and `ContentResolver` I/O exceptions as the same failure-signal path as today's "vault folder missing" scenario, not as an uncaught exception.

### Grant-loss detection at capture start, reusing the existing failure signal

Extend the existing `VaultWriter.isVaultReady()`-style check (called before a capture proceeds) to attempt `DocumentFile.fromTreeUri(...).let { it != null && it.canWrite() }`. On failure, raise the same failure signal (5-second vibration + persistent notification) `vault-writing` already defines for "storage access is verified before capture," with notification text/action pointing at re-running the folder picker rather than re-granting all-files access.

*Alternative considered:* A background `ContentObserver`/periodic check that proactively detects revocation. Rejected as unnecessary complexity — capture-start verification already exists as the enforcement point for this requirement today; SAF doesn't need a new enforcement point, only a new check inside the same one.

### Re-selection recovery flow lives in `MainActivity`

`MainActivity` already owns the first-run permission/model-download flow. Reuse it: a failure notification's action (parallel to the existing Discard action) opens `MainActivity` in a "re-select vault folder" state, which re-launches `ACTION_OPEN_DOCUMENT_TREE` and, on success, overwrites the persisted URI and re-triggers the pending-audio retry sweep — pending audio already durably written under the *old* granted tree is unaffected by a grant change to a *new* tree; only future writes use the new grant, so nothing already-written needs migrating. If the old tree's pending audio itself becomes unreadable (the whole folder was deleted, not just un-granted), that audio is genuinely lost — the same as it would be today if someone deleted `_pending/` out from under the app.

## Risks / Trade-offs

- **Cloud-backed document providers may not support `renameDocument` or may have high write latency.** → Out of scope per Non-Goals; if a user picks a cloud provider anyway, atomic-write failures surface through the same failure-notification path rather than corrupting state, since `renameDocument` returning null/throwing is treated as a write failure, not ignored.
- **`SharedPreferences` corruption or clearing (e.g. "Clear storage" in system settings) loses the grant even though the OS-level URI permission may still be valid.** → Treated identically to a revoked grant: capture-start verification fails, failure signal fires, user re-selects. No silent data loss since audio-before-transcription ordering is unaffected by this change.
- **Migrating existing users (i.e., this developer's own device) from the `MANAGE_EXTERNAL_STORAGE` path to SAF requires one manual re-selection.** → Acceptable one-time step; see Migration Plan.
- **`DocumentFile` operations (existence checks, listing) are slower than raw `File` calls**, each being a `ContentResolver` round-trip. → Not performance-sensitive here: capture-start checks and retry-sweep listing happen at most a few times per capture, not in a hot loop.

## Migration Plan

1. Ship the SAF picker and storage seam alongside the existing `MANAGE_EXTERNAL_STORAGE` path behind a check: if no SAF grant is persisted yet, prompt for one on next launch.
2. On this developer's own device, run the app once post-upgrade to select the existing vault inbox folder through the picker.
3. Once the SAF path is confirmed working (a real capture round-trips end to end), remove `MANAGE_EXTERNAL_STORAGE` from the manifest and delete the now-dead `Environment.isExternalStorageManager()` code path.
4. No server-side or shared state exists to migrate — this is a single-device, single-user app today, and each future user starts fresh with the picker on first launch.

Rollback: revert to the prior commit; `MANAGE_EXTERNAL_STORAGE` grant, if still held, resumes working immediately since the vault folder itself is untouched by this change (SAF and direct `File` access read/write the same underlying directory).

## Open Questions

- Should the folder picker default-navigate to a likely starting point (e.g. `Environment.DIRECTORY_DOCUMENTS`) via `EXTRA_INITIAL_URI`, to save the user from hunting through `Obsidian/obsidian-personal/00-Inbox` by hand? Worth doing but not required for correctness.
- Where exactly should "re-select vault folder" live — only reachable from a failure notification, or also as an always-available entry point in `MainActivity` for a user who wants to move their vault voluntarily (not just recover from a lost grant)? Proposal scopes this to recovery only; revisit if that proves too narrow in practice.
