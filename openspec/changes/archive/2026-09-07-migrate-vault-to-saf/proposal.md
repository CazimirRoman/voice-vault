## Why

The app currently reaches its Obsidian vault through `MANAGE_EXTERNAL_STORAGE` ("all files access") against a hardcoded absolute path (`Config.vaultDir` = `Obsidian/obsidian-personal/00-Inbox`). That was a deliberate MVP trade-off (see `openspec/changes/archive/2026-08-13-add-voice-note-capture/design.md`, "Decisions") acceptable for a single sideloaded device with one owner. It stops being acceptable the moment this app has more than one user: Google Play's policy reserves `MANAGE_EXTERNAL_STORAGE` for file managers, backup tools, and similar — a voice-note app writing Markdown into a third-party app's folder does not qualify, and a Play Store submission using it would very likely be rejected. The hardcoded path is also a second, separate problem: it only happens to work because it matches this developer's own vault layout, and cannot work for any other user's device as-is.

This change migrates vault access to the Storage Access Framework (SAF), removing `MANAGE_EXTERNAL_STORAGE` entirely and letting each user pick their own vault folder.

## What Changes

- Replace the `MANAGE_EXTERNAL_STORAGE` grant with a one-time `ACTION_OPEN_DOCUMENT_TREE` folder picker; persist the returned URI permission via `ContentResolver.takePersistableUriPermission`.
- Rework `vault/` (`VaultWriter`, `AtomicFileWriter`, pending-folder enumeration, note/diagnostic lookups) to operate against a `DocumentFile`/`ContentResolver` root instead of `java.io.File` and `Config.vaultDir`.
- Re-implement atomic writes for SAF: create a temp `DocumentFile`, write it, then rename via `DocumentsContract.renameDocument` (SAF has no `File.renameTo` equivalent).
- Add permission-loss detection and recovery: the persisted URI can stop resolving (folder moved/deleted/renamed, Obsidian uninstalled, grant revoked in Settings). Detect this at capture start and surface it through the existing failure-notification path, with a way for the user to re-pick the folder without losing already-pending audio.
- Extend `MainActivity`'s first-run flow to launch the folder picker in place of the "all files access" prompt.
- **BREAKING**: `Config.vaultDir` / `Config.inboxDir` / `Config.pendingDir` as fixed `File` paths go away. Any code relying on a filesystem-path vault (tests included) needs to move to the SAF-backed accessor.
- Remove the `MANAGE_EXTERNAL_STORAGE` permission and its usage-declaration from the manifest/Play Console once no code path depends on it.

## Capabilities

### New Capabilities
- `vault-folder-selection`: the user selects their Obsidian vault folder via a system folder picker, the app persists that grant across restarts/reboots, and the user can be prompted to re-select if the grant becomes invalid.

### Modified Capabilities
- `vault-writing`: the "Storage access is verified before capture" requirement changes from checking `MANAGE_EXTERNAL_STORAGE`/`Environment.isExternalStorageManager()` to checking that the persisted SAF permission still resolves to a writable folder; the "Permission was revoked" and "Vault folder missing" scenarios are restated in terms of SAF grant loss rather than all-files-access loss. Read/write/rename mechanics move from `java.io.File` to `DocumentFile`/`ContentResolver`, but the durability guarantees (audio-before-transcription, atomic rename, retry sweep, failure notification) are unchanged in observable behavior.

## Impact

- **Code**: `vault/VaultWriter.kt`, `vault/AtomicFileWriter.kt`, `Config.kt` (vault path constants), `MainActivity` (first-run permission flow), `capture/RecordingService.kt` and `transcribe/TranscriptionQueue.kt` wherever they currently pass or assume a `File`-based vault path, `notify/CaptureNotifications` (new "vault access lost, tap to re-select folder" failure case), `AndroidManifest.xml` (drop `MANAGE_EXTERNAL_STORAGE`).
- **Persisted state**: a new place to store the chosen vault folder's URI (e.g. `SharedPreferences` or a small file in `filesDir`) — there is no existing persistence for this today since the path was a compile-time constant.
- **Tests**: any test currently constructing `VaultWriter`/`AtomicFileWriter` calls against real `File`s (see `writeNote(..., inboxDir: File = Config.inboxDir)`-style default params) needs a SAF-compatible seam, likely a fake `DocumentFile` tree or a small storage abstraction interface.
- **Play Console**: removes the need for a `MANAGE_EXTERNAL_STORAGE` Permissions Declaration Form entry, clearing the main policy blocker to Play Store distribution identified in prior discussion.
- **Out of scope**: the assistant-role (`BIND_VOICE_INTERACTION`) registration and its reinstall-clears-the-setting flakiness are a separate, unrelated concern and are not addressed by this change.
