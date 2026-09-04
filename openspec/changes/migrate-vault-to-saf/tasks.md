## 1. Persisted grant and folder selection

- [x] 1.1 Add a small persistence spot (e.g. `SharedPreferences` key) for the selected vault folder's tree `Uri`
- [x] 1.2 Add a `VaultFolderSelector` (or similar) that launches `ACTION_OPEN_DOCUMENT_TREE`, takes a persistable read/write URI permission on result, and stores it
- [x] 1.3 Replace `MainActivity`'s `MANAGE_EXTERNAL_STORAGE` prompt with a folder-picker step. The picker is NOT auto-launched on first run - the setup screen shows all four steps (permissions, vault folder, assistant, model), each with a right-aligned check mark when done, re-checked on resume, and the user launches the picker from the step's button. When all four are done, a line tells the user they can long-press the power button to start a recording.
- [x] 1.4 Add a "re-select vault folder" entry point in `MainActivity` reachable from the vault-access failure notification action (`EXTRA_RESELECT_VAULT`), which opens the picker directly since the user asked for it by tapping the action
- [x] 1.5 Show the picked folder on the setup screen: its volume-relative path (from the tree document id) as a "Writing notes into: …" line, plus a note when the folder directly contains a `.obsidian` dir (picked the vault root, not a subfolder). Exposed via `VaultStorage.folderInfo()` / `VaultFolderInfo`.

## 2. Storage seam

- [x] 2.1 Define a `VaultStorage` interface for the SAF note operations only (resolve access as `Available`/`NotConfigured`/`Lost`, write note atomically, note exists, list note base names, list notes with content) — plain `String`/value signatures, no `DocumentFile` in the interface. Pending audio + diagnostics stay `java.io.File`-based against `filesDir` and do not go through this seam.
- [x] 2.2 Implement `SafVaultStorage(context)` against `DocumentFile` / `ContentResolver`, resolving the inbox tree from the persisted `Uri` on each call and reporting unresolvable/unwritable as `VaultAccess.Lost` rather than throwing
- [x] 2.3 Add a SAF atomic note write alongside the existing `File`-based `AtomicFileWriter`: create `<name>.md.partial` as a new document, write bytes via `ContentResolver.openOutputStream`, then `DocumentsContract.renameDocument` into `<name>.md` (deleting any stale partial/final first). `AtomicFileWriter` itself is unchanged and still backs pending audio + diagnostics.
- [x] 2.4 Move `_pending/` to `context.filesDir/pending/`; give `VaultWriter`'s vault-touching methods a `Context` parameter (build `SafVaultStorage` from it, with `internal` `VaultStorage`-taking overloads for tests). `writeNote` returns `Unit`; `TranscriptionOutcome.Success` carries the note base name instead of a `File`.

## 3. Callers

- [x] 3.1 Update `capture/RecordingService` call sites that consume `VaultWriter` results as `File`
- [x] 3.2 Update `transcribe/TranscriptionQueue`: thread `Context` to the SAF note write, move post-success `.wav`/diagnostic cleanup to the context-aware wrapper, adjust `Success` payload. Pending audio enumeration stays `File`-based.
- [x] 3.3 Update `notify/CaptureNotifications` to add the new "vault access lost" failure case (distinct action: re-select folder) alongside the existing Discard action

## 4. Capture-start verification

- [x] 4.1 Replace `VaultWriter.hasStorageAccess()` / `isVaultReady()` (built on `Environment.isExternalStorageManager()`) with a check that resolves the persisted `DocumentFile` tree and confirms `canWrite()`
- [x] 4.2 Confirm the failure signal (5-second vibration + persistent notification) fires when this check fails, per the modified `vault-writing` "Storage access is verified before capture" requirement
- [x] 4.3 Confirm a revoked/invalid grant is distinguished from "no folder ever selected" so first-run and recovery don't get conflated

## 5. Manifest and permission cleanup

- [x] 5.1 Remove `MANAGE_EXTERNAL_STORAGE` from `AndroidManifest.xml`
- [x] 5.2 Delete dead code paths referencing `Environment.isExternalStorageManager()` / the old permission-request flow
- [x] 5.3 Remove `Config.vaultDir` / `Config.inboxDir` / `Config.pendingDir` as fixed `File` paths once no caller depends on them

## 6. Tests and manual verification

- [x] 6.1 Update or replace tests that construct `VaultWriter`/`AtomicFileWriter` calls against real `File`s with a SAF-compatible fake (fake `DocumentFile` tree or fake `VaultStorage`)
- [x] 6.2 Add tests for grant-loss detection (resolution failure, `canWrite() == false`) triggering the failure signal
- [ ] 6.3 Manual on-device pass (per project convention, since there is no CI): first-run folder selection, a full capture round-trip writing a note into the picked folder, revoking the permission in system Settings and confirming the failure notification + re-select flow, and confirming audio captured while the grant was invalid (still in `filesDir/pending/`) transcribes successfully once a folder is re-selected
