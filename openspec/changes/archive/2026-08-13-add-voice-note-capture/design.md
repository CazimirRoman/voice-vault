## Context

The project is an unmodified Jetpack Compose scaffold (AGP 9.2.1, Compose BOM 2026.02, minSdk 24, targetSdk 36) containing only a `Greeting` composable. Everything described here is new construction.

The target is a single device: a Pixel 6 Pro (Tensor G1, arm64-v8a, 12 GB RAM, Android 16 / API 36), sideloaded. That collapses a large amount of normal Android engineering — no ABI matrix, no Play Store policy constraints, no wide API-level compatibility, no multi-device QA. Decisions below lean on this aggressively and would need revisiting if the app were ever distributed.

The defining constraint is that this flow is **eyes-free**. The user triggers capture, speaks, and pockets the phone without looking at the screen. No visual affordance can be load-bearing; the interaction has to be carried entirely by physical gesture and haptics. A second constraint follows from the purpose: captured thoughts must not be lost, because a note-taking tool that silently drops input is worse than no tool at all.

## Goals / Non-Goals

**Goals:**
- One physical gesture to start capture, zero screen interaction to complete it.
- Recording that never truncates a sentence the user is still speaking, and never runs indefinitely.
- Full offline operation. No network permission is requested at all.
- No captured audio is ever lost, regardless of how transcription, storage, or the process itself fails.
- Small enough to build and evaluate quickly — this exists to answer "does this feel good?", not to be finished software.

**Non-Goals:**
- Live or streaming partial transcripts. The user has explicitly said they do not want to watch words appear.
- Any settings, configuration, or note-management UI.
- Editing, tagging, frontmatter, or note organization beyond dropping a file in a folder.
- Languages other than English; ABIs other than arm64-v8a; devices other than the Pixel 6 Pro.
- Transcription accuracy tuning, custom vocabulary, or prompt conditioning.

## Decisions

### Take the assistant role rather than use a softer trigger

The app registers a `VoiceInteractionService` with a companion `VoiceInteractionSessionService` and `VoiceInteractionSession`, plus an `res/xml/interaction_service.xml` descriptor, making it selectable under Settings → Apps → Default apps → Digital assistant. An `android.intent.action.ASSIST` intent filter is also declared as a secondary entry point.

*Alternatives considered:* An ASSIST-intent activity alone is far less code, but on recent Android the power-button hold routes preferentially through a `VoiceInteractionService` when one exists, so it may never be reached. Pixel's Quick Tap gesture (double-tap the back of the phone) requires literally no code and preserves Google Assistant — it remains the designated fallback if the role binding misbehaves — but the user has stated they want to replace the assistant outright, and Quick Tap is a less reliable gesture for a pocketed phone.

*Accepted cost:* Holding the assistant role disables "Hey Google" hotword detection. The user has explicitly accepted this.

The session renders nothing. It starts the recording service and immediately dismisses itself, so the screen shows whatever it was already showing.

### A foreground service owns the recording, not an Activity

The capture session runs in a foreground service with `foregroundServiceType="microphone"`.

*Alternative considered:* A translucent Activity is simpler and avoids the notification channel and two extra permissions. It was rejected because an Activity is torn down when the screen locks or the phone goes into a pocket — precisely the situation this app is designed for. The recording would die mid-note.

The service's notification is not decoration: it is the durable surface for reporting a failed transcription, which a vibration alone cannot do (a buzz that fires while the phone is in a pocket during a conversation is simply missed).

### Haptics are the entire interface

Three signals, deliberately distinguishable by feel alone:

| Event | Pattern | Meaning |
|---|---|---|
| Microphone live | double tick | start speaking |
| Recording ended | single buzz | got it, you can pocket the phone |
| Note at risk | 5-second rumble | something needs attention |

Silence between the second and third signal means success. The user is only interrupted when there is a problem.

The start tick MUST fire *after* `AudioRecord` is actually capturing, otherwise the first word is clipped during microphone acquisition. The vibration bleeds into the recording; this is harmless, as whisper does not transcribe a buzz.

### Silence is the primary stop condition, at a generous threshold

Recording ends on ~5 seconds of silence, a screen tap, or a 3-minute hard cap.

The usual tension — a short timeout truncates thinking pauses, a long one makes the user wait — is resolved by the stop haptic rather than by better detection. Because the user *feels* the recording end, a mid-sentence cutoff is immediately known and the partial note is already safe, making it a degraded outcome rather than a lost one. That permits a threshold well past any natural pause.

*Alternative considered:* Silero VAD, which whisper.cpp now bundles. Rejected for the MVP: a plain RMS-over-buffer threshold is roughly fifteen lines and only guards the case where the user has already walked away. VAD earns its complexity only if chunked transcription is later added.

Tap-to-stop is retained as a free accelerator for when the user happens to be looking at the screen, but nothing depends on it.

### Prebuilt whisper AAR for the MVP, vendored source as a known escape hatch

`dev.ffmpegkit-maintained:whisper-android:1.0.0` (MIT, arm64-v8a, API 24+) exposes a file-in/text-out API — `loadModelFromAsset`, `transcribe(model, audioPath, config)`, `releaseModel` — which is exactly the shape this design needs.

*Alternative considered:* Vendoring `examples/whisper.android` from the whisper.cpp repository via NDK and CMake. This is the canonical, better-maintained route and the only way to reach VAD, streaming, or chunked transcription. It costs a native build setup that this MVP does not need, because no streaming is wanted. If the AAR proves unmaintained or limiting, swapping to vendored source touches only the transcription wrapper.

*Risk accepted:* a single-maintainer v1.0.0 dependency whose "free tier" wording implies a commercial tier.

### `base.en` quantized to Q5_0, bundled in assets

whisper.cpp on this device is CPU-only. Tensor's TPU is not accessible to third-party apps, so none of the published NPU acceleration results apply.

| model | Q5_0 size | est. real-time factor | 30 s of speech |
|---|---|---|---|
| tiny.en | ~31 MB | ~0.15x | ~5 s |
| **base.en** | **~57 MB** | **~0.35x** | **~10 s** |
| small.en | ~190 MB | ~1.0x | ~30 s — too slow |

`base.en` is the accuracy/latency knee. The English-only model is meaningfully more accurate at English than multilingual at the same size, and multi-language support is a non-goal.

Assets are compressed by default and whisper requires a real filesystem path, so the model is copied to `filesDir` on first run behind an existence check. `noCompress` on the model extension avoids paying decompression on top of the copy.

Model loading is started concurrently with recording rather than before it. Load takes roughly a second and recording takes many; overlapping them makes the load effectively free.

### `MANAGE_EXTERNAL_STORAGE` over the Storage Access Framework

Direct `java.io.File` access against a hardcoded vault path.

*Alternative considered:* SAF with `ACTION_OPEN_DOCUMENT_TREE` and a persisted URI permission is the correct, portable, Play-Store-compatible answer. It was rejected because it introduces a class of "the URI permission was revoked and now writes fail silently" bugs that are hostile to an eyes-free app, in exchange for portability this app does not need. The vault path is a constant in source; changing it means editing one line and rebuilding, which is acceptable at this scale.

*Also considered:* the `obsidian://new` URI scheme, which delegates the write to Obsidian and guarantees index consistency. Rejected because it foregrounds Obsidian, destroying the speak-and-pocket flow.

### Audio is written before transcription is attempted

This is the actual answer to "notes must not get lost" — the warning rumble is a notification, not a safety net.

```
record ──▶ WAV into vault _pending/ ──▶ transcribe ──▶ .md into vault ──▶ delete WAV
                    │                        │
              safe from here            everything after this
                                        point is recoverable
```

Once the WAV is on disk inside the vault, no subsequent failure — model load, transcription, process death, storage error — can destroy the thought. The worst case is an audio file the user can play back, plus a notification. A pending WAV found at next launch is retried automatically.

Writes use temp-file-plus-rename, because the vault may be synced by Obsidian Sync or Syncthing and a partially written file must never be picked up mid-write.

### Overlapping captures queue

Triggering a capture while a previous one is still transcribing enqueues the new recording rather than refusing it. Refusing would require a distinct error haptic the user must learn, and would drop a thought — the opposite of the app's purpose. The service holds a list and drains it. Recording and transcription must therefore not assume they are singletons.

### Raise `minSdk` from 24 to 33

The scaffold default supports API levels this device will never run. Raising it removes compatibility branching around notification permissions, foreground service types, and vibration APIs. `abiFilters` is restricted to `arm64-v8a` to match the AAR and avoid shipping dead weight.

Audio is captured directly as 16 kHz mono PCM 16-bit, which is whisper's required input format, so no resampling stage exists.

## Risks / Trade-offs

- **The power-button hold may not route to the app despite a correct `VoiceInteractionService`.** This is the least certain assumption in the design and it is load-bearing for the trigger. → Validate first with a near-empty build that only vibrates on invocation, before any recording or transcription work exists. Fall back to Quick Tap, which requires no code and invalidates nothing else in this design.
- **Losing "Hey Google" is irreversible while the role is held.** → Accepted explicitly by the user; reverting is a single settings change.
- **The whisper AAR is a single-maintainer v1.0.0 artifact and may be abandoned or gated.** → The transcription wrapper is isolated behind one interface so swapping to vendored whisper.cpp touches one file.
- **A ~10 second gap between the stop buzz and the note existing.** → Deliberately unmitigated in the MVP; the user has stated they will have pocketed the phone by then. If this proves to be what kills the feel, chunked transcription with VAD is the known remedy and the reason the vendored-source path is kept open.
- **Silent failure is the worst possible outcome for a capture tool.** → Defended in three layers: audio persisted before transcription, a 5-second rumble, and a persistent notification that survives a missed buzz.
- **`MANAGE_EXTERNAL_STORAGE` can be revoked by app hibernation after extended disuse.** → Disable hibernation for the app; verify the permission at capture start and fire the failure signal rather than writing into the void.
- **RMS silence detection will misfire in loud environments**, either never detecting silence (caught by the 3-minute cap) or ending early (caught by the stop buzz being felt). → Both failure directions are already bounded; the threshold is a tunable constant.
- **Bundling a ~57 MB model doubles on disk after extraction to `filesDir`.** → Acceptable on this device; deleting the extracted copy is not possible since whisper needs the path.

## Migration Plan

No data or users exist to migrate. Deployment is a sideload to one device, followed by three manual steps that cannot be automated: granting microphone and all-files access, and selecting EasyNote as the digital assistant.

Rollback is selecting Google Assistant again in the same settings screen, then uninstalling. Any notes already written are plain Markdown in the vault and remain valid independently of the app.

## Open Questions

- Should the note filename be a timestamp (`2026-08-12 1423.md`) or derived from the first few words of the transcript? Timestamps are assumed for the MVP as they cannot collide or produce illegal filenames, but first-words titles are far more scannable in Obsidian.
- Does the stop haptic need to distinguish "stopped because you went quiet" from "stopped because you hit the 3-minute cap"? The cap should be rare enough not to warrant a fourth signal, but hitting it silently means a very long note ended without explanation.
- Is a distinct success signal wanted once the note is actually written? The current design says no — silence means success — but that relies on the user noticing an absence, which people are poor at.
