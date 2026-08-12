## Why

Capturing a fleeting thought on a phone currently costs unlock → find app → open vault → navigate to folder → tap new note → type. By the time that finishes, the thought is often gone or not worth the effort. The goal is to reduce note capture to a single physical gesture followed by speaking, with no screen interaction at all, so the phone can be triggered and pocketed while the idea is still forming.

This is an MVP whose purpose is to find out whether the resulting flow actually feels good enough to use daily. It targets exactly one device — a Pixel 6 Pro — and is sideloaded, not distributed.

## What Changes

- EasyNote becomes the device's **default digital assistant**, so holding the power button launches it instead of Google Assistant. **BREAKING** for the user's device: taking the assistant role disables "Hey Google" hotword detection. This is an accepted, intentional trade.
- A **headless capture session** starts on trigger: no UI is rendered. The session immediately starts a foreground service and dismisses itself.
- Recording is driven entirely by **haptic feedback**, not visuals: a double tick when the microphone goes live, a single buzz when recording ends, and a distinct 5-second rumble only when a note is at risk.
- Recording stops on **~5 seconds of silence** (primary), a screen tap (accelerator, when the user happens to be looking), or a 3-minute hard cap (runaway guard).
- Speech is transcribed **fully offline** using whisper.cpp with a quantized `base.en` model bundled in the app. No network access at any point.
- The transcript is written as a Markdown file into a configured folder inside the Obsidian vault on device storage.
- **Durability before convenience**: the raw audio is written into the vault's pending folder the moment recording ends, before transcription is attempted. No failure path can lose a captured thought — the worst outcome is an untranscribed audio file plus a notification.
- Triggering a new capture while a previous one is still transcribing **queues** the new recording rather than refusing it.

Explicitly out of scope for this MVP: live/streaming partial transcripts, any settings UI (the vault path is a constant), note editing, multi-language support, tag or frontmatter generation, and support for any device other than arm64-v8a.

## Capabilities

### New Capabilities

- `assistant-trigger`: Registering as the system digital assistant so a power-button hold starts a capture, and handing off to the capture session without rendering UI.
- `voice-capture`: The recording session lifecycle — microphone acquisition, the haptic signal vocabulary, the three stop conditions, and queueing of overlapping captures.
- `offline-transcription`: On-device speech-to-text via bundled whisper.cpp, including model provisioning on first run and behavior when transcription fails.
- `vault-writing`: Durable persistence into the Obsidian vault — audio-first write ordering, Markdown note naming and format, retry of pending transcriptions, and failure notification.

### Modified Capabilities

None. This is the first change in the project; `openspec/specs/` is empty.

## Impact

**Code** — the project is currently an unmodified Compose scaffold (`MainActivity` renders `Greeting("Android")`). Essentially all of it is new:
- New: voice interaction service, session service, and session; recording foreground service; audio capture and silence detection; whisper wrapper; vault writer; haptics helper.
- Modified: `AndroidManifest.xml` (services, intent filters, permissions, assistant metadata), `app/build.gradle.kts` (dependencies, asset packaging, ABI filter).
- Removed: the scaffold `Greeting` composable and its preview. `MainActivity` is retained only as a launcher entry point for granting permissions and picking up the "all files access" toggle.

**Permissions** — `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS`, `MANAGE_EXTERNAL_STORAGE`, `VIBRATE`, and `BIND_VOICE_INTERACTION` on the service.

**Dependencies** — one new artifact dependency for the whisper.cpp binding, plus a ~57 MB quantized `base.en` GGML model shipped in assets. APK size grows accordingly. No network dependency is introduced.

**Device configuration** — the user must, once and manually: grant microphone and all-files access, and select EasyNote under Settings → Apps → Default apps → Digital assistant.

**Risk** — the assistant role binding is the least certain part of the design. If the power-button hold does not reach the app, the fallback is Pixel's Quick Tap gesture (double-tap the back of the phone), which requires no code and preserves every other part of this change.
