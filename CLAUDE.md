# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

EasyNote is a single-device (Pixel 6 Pro, sideloaded, arm64-v8a only), MVP Android app that turns a power-button hold into a fully offline, eyes-free voice note: trigger → speak → pocket the phone → a Markdown file appears in an Obsidian vault. No screen interaction, no settings UI, and no network once the speech model has been downloaded. See `openspec/changes/archive/2026-08-13-add-voice-note-capture/proposal.md` and `design.md` for the full rationale and accepted trade-offs — read `design.md`'s "Decisions" and "Risks / Trade-offs" sections before changing behavior in `assistant/`, `capture/`, or `vault/`, since most choices there (e.g. `MANAGE_EXTERNAL_STORAGE` over SAF, silence-based stop, audio-before-transcription ordering) were deliberate rejections of the "more correct" alternative.

## Commands

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # build and install on connected device (only real target: Pixel 6 Pro)
./gradlew test                   # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest   # instrumented tests on device (app/src/androidTest)
./gradlew lint                   # Android lint
```

There is no CI and no distribution — every change is verified by sideloading to the physical Pixel 6 Pro. Many tasks in `openspec/changes/archive/2026-08-13-add-voice-note-capture/tasks.md` are marked incomplete specifically because they require a human with the physical device (feeling haptics, triggering via the real power button, observing the notification shade, etc.) — don't mark them done from code inspection alone.

## Git workflow

- **Never create branches.** Commit directly to `main`, even for multi-commit changes. This overrides any default about branching before committing on the default branch.
- **No PRs.** There is no CI, no remote review, and one developer — a PR would only add a step between a commit and a sideload.
- Stage deliberately rather than with `git add -A`: OpenSpec changes authored in another session can be sitting untracked in the tree, and sweeping them into an unrelated commit is easy to do and annoying to unpick.

## OpenSpec workflow

This repo uses OpenSpec (`openspec/`) for spec-driven change proposals. `openspec/specs/` holds the finalized specs (`assistant-trigger`, `offline-transcription`, `vault-writing`, `voice-capture`); `openspec/changes/archive/2026-08-13-add-voice-note-capture/` holds the proposal, design doc, and task checklist that produced them. Use the `openspec-*` skills for proposing, exploring, or archiving changes rather than editing these files free-hand.

## Architecture

The flow is a strict pipeline with a durability checkpoint in the middle. Each stage is owned by one package:

```
power-button hold
  → assistant/  (VoiceInteractionService, renders no UI, immediately starts a foreground service)
  → capture/    (RecordingService: foreground service, owns the mic, haptics, stop conditions)
  → vault/      (WAV written into vault's _pending/ folder — audio is now unlosable)
  → transcribe/ (TranscriptionQueue serializes access to one loaded whisper model)
  → vault/      (Markdown note written, pending WAV deleted)
```

- **`assistant/`** — `EasyNoteVoiceInteractionService` + `EasyNoteVoiceInteractionSessionService` + `EasyNoteVoiceInteractionSession` register EasyNote as the system digital assistant (`res/xml/interaction_service.xml`, `BIND_VOICE_INTERACTION`). `AssistIntentActivity` is a secondary `ACTION_ASSIST` entry point. `NoOpRecognitionService` exists only to satisfy the recognition-service requirement of holding the assistant role. The session's `onShow` starts `RecordingService` and calls `hide()` immediately — no window is ever shown.
- **`capture/`** — `RecordingService` (foreground service, `foregroundServiceType="microphone"`) owns one capture end-to-end: it is NOT torn down when the screen locks or the phone is pocketed, unlike an Activity. `AudioRecorder` captures 16 kHz mono PCM16 and stops on ~5s of RMS silence, the user turning off the screen, a screen tap (`TapToStopActivity`, a transparent accelerator that nothing else depends on), or a 3-minute hard cap. Screen-off is the happy path: pocketing the phone is the real "I'm done" gesture *and* the thing that buries the mic in fabric noise, so silence detection alone never fires there — it is now only the walk-away backstop. The receiver lives in `RecordingService` (registered per capture, since captures overlap) rather than the overlay, and `TapToStopActivity` sets `FLAG_KEEP_SCREEN_ON` so a screen-off is always a deliberate power press and never an idle timeout. `Haptics` is the entire user-facing interface — three distinguishable patterns (start tick, stop buzz, at-risk rumble), no visuals. `CaptureEvents`/`CaptureController` coordinate the tap-to-stop overlay with the in-progress recording.
- **`vault/`** — `VaultWriter` does direct `java.io.File` I/O against a hardcoded path (`Config.vaultDir`), not SAF. `AtomicFileWriter` always writes temp-file-then-rename, because the vault may be live-synced by Obsidian Sync/Syncthing and a partial file must never be observed mid-write. The pending-audio-before-note ordering is the actual data-loss defense in this app; everything after the WAV lands in `_pending/` is considered recoverable.
- **`transcribe/`** — `Transcriber` is a one-method interface wrapping the `whisper-android` AAR (`WhisperTranscriber`); this indirection exists so the AAR can be swapped for vendored whisper.cpp source without touching callers. `TranscriptionQueue` is a singleton object (not a class) that serializes all transcription through a `Mutex`, loads the model lazily/concurrently with recording, and releases it once the queue drains to zero — recording and transcription must never assume they are the only capture in flight, since a new capture triggered mid-transcription is queued, not refused. `ModelProvisioner` owns the model's location and readiness in `filesDir` (whisper needs a real filesystem path), gated on a `.complete` marker — the `.bin` exists on disk while still being written, so its presence alone proves nothing. It also deletes any previously downloaded `ggml-*` model, which otherwise strands ~57 MB on the device every time the model is swapped. `ModelDownloader` fetches the GGML model (`ggml-base.en-q5_1.bin`, ~57 MB) from `Config.MODEL_DOWNLOAD_URL` on first launch — it is deliberately **not** in `assets/` or in git — writing to a temp file and renaming into place only after both the pinned byte count and SHA-256 match. Until it lands, transcription failing is the *expected* state: captures still record and wait in `_pending/`, and `MainActivity` re-runs the retry scan the moment the download completes.
- **`notify/`** — `CaptureNotifications` owns the foreground-service status notification and failure notifications. The notification is load-bearing, not decorative: it's the only durable way to report a failure the user might miss (e.g. a buzz that fires while the phone is in a pocket during a conversation).
- **`Config.kt`** (root package) — every tunable constant lives here: vault path (`Obsidian/obsidian-personal/00-Inbox`, with `_pending/` beneath it), sample rate, silence threshold/duration, max recording duration, model filenames. There is no settings UI; changing behavior means editing this file and rebuilding.
- **`MainActivity`** — not a real UI. It exists only as a launcher entry point to request permissions, prompt for "all files access" (`MANAGE_EXTERNAL_STORAGE`), and show the first-launch model download.

### Key invariants to preserve when touching this code

- The start haptic must fire only *after* `AudioRecord` is confirmed capturing (an earlier tick clips the first spoken word).
- Audio must be durably written to `_pending/` before transcription is attempted — never reorder this.
- `TranscriptionQueue` and `RecordingService` must keep supporting overlapping/queued captures; don't reintroduce a singleton assumption.
- Whisper does not return an empty string for silence/noise — it returns bracketed placeholders like `[ Pause ]`, `[Silence]`, `[BLANK_AUDIO]`. `TranscriptionQueue.isNonSpeechPlaceholder` filters these; treat this as the actual "no real speech" check, not `text.isBlank()`.
- All vault writes go through `AtomicFileWriter` (temp file + rename), never a direct write.
- `FLAG_KEEP_SCREEN_ON` on the overlay and the screen-off stop are a pair: removing the flag makes an idle display timeout indistinguishable from a deliberate power press, and silently cuts recordings short mid-thought.

### Non-goals (by design, not by omission)

No settings/config UI, no note editing, no streaming/partial transcripts, no multi-language support (the build targets exactly one language — English, via `Config.TRANSCRIPTION_LANGUAGE` plus an `.en` model; a non-English build additionally requires swapping in a multilingual model, and Romanian was tried and rejected on quality), no ABI beyond `arm64-v8a`, no device beyond the Pixel 6 Pro.

The one exception to "no network": the app holds `INTERNET` solely so `ModelDownloader` can fetch the ~57 MB model once on first launch, keeping it out of the APK and the repo. Capture and transcription themselves never touch the network, and no audio, transcript, or telemetry is ever sent anywhere. This supersedes the archived `offline-transcription` spec's "SHALL NOT declare the `INTERNET` permission" requirement — that spec has not been re-proposed yet.
