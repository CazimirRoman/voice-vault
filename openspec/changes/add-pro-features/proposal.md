## Why

`add-pro-entitlement` builds the ability to charge and gates one already-built feature to prove the wiring. It does not give anyone a reason to pay. This change is that reason.

The three things worth paying for here all share a shape: the app is deliberately opinionated, and every opinion is a compile-time constant. Silence timing, the hard duration cap, the frontmatter block, the filename, and the single destination folder are all hardcoded, with no settings UI by design. That is the right default for an eyes-free capture tool and the wrong ceiling for someone who has built a vault around their own conventions. Pro becomes the tier where the app adapts to an existing Obsidian setup instead of imposing one.

## What Changes

- **Add a settings screen**, reachable from `MainActivity`, the first configuration surface the app has ever had. Editing any setting requires Pro; the free tier keeps today's fixed behaviour.
- **Capture tuning**: silence amplitude threshold, silence duration, hard duration cap, and haptic intensity become runtime values, each with today's `Config` constant as its default, each clamped to a safe range, with a reset-to-defaults action.
- **Note formatting**: a configurable filename pattern and frontmatter template, replacing the fixed `Created`/`Priority`/`Area`/`Action` block and the fixed `<captureId>.md` name. Templates support a small, closed set of placeholders (capture timestamp parts, capture id, source entry point).
- **Daily-note append**: an alternative to one-file-per-capture that appends the transcript to a dated note, creating it if absent. Appending never rewrites content it did not add, and falls back to writing a separate note rather than risking a lost capture.
- **Multiple vault destinations**: more than one SAF folder grant, each with a name and its own trigger words, one marked default.
- **Spoken routing**: when a transcript's first word matches a destination's trigger word, the note goes to that destination and the trigger is stripped from the body. No match, an unreadable grant, or any ambiguity routes to the default. Routing can never cause a note not to be written.
- **Stored settings are honoured regardless of entitlement.** The gate is on editing, not on reading. A lapsed entitlement never silently changes how captures behave or where notes land.
- **Not** changed: the capture pipeline's structure, the pending-audio-before-note ordering, atomic writes, retry, and failure reporting. Free users keep every one of them, unaltered.

## Capabilities

### New Capabilities
- `capture-tuning`: runtime, user-adjustable capture parameters with safe defaults and clamped ranges, replacing compile-time constants as the source of truth while preserving today's values as defaults.
- `note-formatting`: a configurable filename pattern and frontmatter template for vault notes, plus the daily-note append mode and its safety rules.
- `vault-destinations`: holding more than one vault folder grant, selecting between them by spoken trigger word, and the fallback rules that guarantee a note is always written somewhere.

### Modified Capabilities
- `voice-capture`: the silence duration and the hard duration cap become configured values rather than fixed 5 seconds and 3 minutes. The defaults, and every other stop condition, are unchanged.
- `vault-writing`: a note's filename and frontmatter come from the configured template rather than being fixed, and the destination folder is the routed one rather than the single configured folder. Atomicity, ordering, and retry are unchanged.

## Impact

- **New code**: `settings/SettingsStore.kt` (persistence plus clamping), `settings/CaptureSettings.kt` and `settings/NoteSettings.kt` (typed accessors read by `capture/` and `vault/`), `settings/SettingsScreen.kt`, `vault/NoteTemplate.kt` (placeholder expansion), `vault/DestinationRouter.kt` (trigger matching and fallback).
- **Modified code**: `capture/AudioRecorder` and `capture/RecordingService` read tuning values instead of `Config` constants. `vault/VaultWriter.writeNote` takes a resolved destination and expands a template. `vault/VaultGrant` and `vault/SafVaultStorage` hold a list of grants rather than one. `vault/VaultFolderSelector` gains add and remove. `Config.kt` constants stay as the default values.
- **Dependency on `add-pro-entitlement`**: this change consumes `pro/ProEntitlement` for the edit gate. It cannot be implemented before that change lands.
- **Architectural constraint carried over**: `capture/` and `vault/` must not import `pro/`, which the invariant test from `add-pro-entitlement` enforces. Settings are read without reference to entitlement; only the settings screen consults it.
- **Migration**: an install with no stored settings behaves exactly as it does today. A single existing vault grant becomes the default destination.
- **Unaffected**: `assistant/`, `transcribe/`, `notify/`, `quicknote/`'s save path.
