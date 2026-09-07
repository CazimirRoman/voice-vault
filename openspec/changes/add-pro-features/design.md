## Context

Voice Vault has never had a settings screen, and `CLAUDE.md` lists "no settings/config UI" as a design non-goal rather than an omission. Every tunable lives in `Config.kt`, and there is exactly one vault grant. This change reverses that non-goal for paying users while keeping the free tier byte-for-byte identical to today's behaviour.

It builds on `add-pro-entitlement` and inherits its central constraint: `capture/` and `vault/` must not import `pro/`, enforced by a unit test. That single rule drives most of the structure below.

## Goals / Non-Goals

**Goals:**

- Give a paying user enough control to fit the app to an existing Obsidian vault rather than reshaping the vault around the app.
- Keep the default behaviour of an unconfigured install exactly what it is today, so the free tier is not quietly degraded to make Pro look better.
- Never let a configuration mistake, a corrupt stored value, or a lapsed entitlement cost the user a capture.
- Keep entitlement out of the capture and vault write paths entirely.

**Non-Goals:**

- Arbitrary template languages. A closed placeholder set only, no expressions, no conditionals, no scripting.
- Per-capture destination pickers, tags, or metadata prompts. The trigger word is the whole routing vocabulary, because the capture flow is eyes-free.
- Editing or reorganising existing notes.
- Syncing settings anywhere. They live on the device, like everything else.

## Decisions

### The Pro gate is on writing settings, never on reading them

`settings/` knows nothing about `pro/`. It stores values, clamps them, and hands them to `capture/` and `vault/`. Only `SettingsScreen` consults `ProEntitlement`, and only to decide whether its controls are editable.

This is what makes the architecture work at all. If reading were gated, `AudioRecorder` would need to know about entitlement, which would break the invariant test from `add-pro-entitlement` and put billing state on the path between a spoken sentence and a file.

It also gives the better user behaviour for free: a lapsed entitlement changes nothing about how the app captures or where notes land. Reverting to defaults on lapse was considered and rejected, since silently changing a user's silence timing or destination folder because a refund went through is a data-integrity problem dressed up as a business rule.

### `Config` constants stay, as defaults

Every constant in `Config.kt` remains, and each becomes the default for its setting. Nothing is deleted or moved. An install with no stored settings reads exactly the values it reads today, so "did settings break the free tier?" is answerable by inspection.

### Every setting is clamped on read, not validated on write

Ranges are enforced where the value is consumed. A corrupt preferences file, a value stored by an older build, or a hand-edited backup can therefore never produce a recording that will not stop or a silence threshold that ends every capture instantly.

The hard duration cap is clamped above as well as below: there is no "unlimited" option. An unbounded recording is a stuck microphone and a flat battery, and the cap's whole purpose is to be the last stop condition that cannot fail.

### The placeholder set is closed and expansion never fails

Filename patterns and frontmatter templates share one small placeholder set (date parts, time parts, capture id, source entry point). Expansion has no failure mode: an unrecognised placeholder is left as literal text, and an expansion that yields an unusable filename falls back to the default pattern.

A template engine that can throw sits directly on the path to writing a note. The rule is that any template problem degrades to today's behaviour and still writes the file.

### Daily-note append reads, appends, and writes atomically under a lock

Append is the riskiest thing in this change: it is the only vault operation that reads existing content and writes it back, over a folder that Obsidian Sync or Syncthing may be modifying at the same time.

The rules are:

1. Serialise appends through a mutex in `vault/`, since quick-text saves happen outside `TranscriptionQueue` and can otherwise race a voice capture.
2. Compare the note's content or modification time between read and write. If it changed underneath, do not merge, and write the capture as its own separate note instead.
3. Never rewrite bytes the app did not add. Appending only ever adds at the end.
4. Write through `AtomicFileWriter` like every other vault write.

Failing over to a separate note is deliberately blunt. A stray extra file in the inbox is an annoyance; a merge that drops someone's paragraph is the failure this app exists to prevent.

### Routing is first-word only, with the default as a universal fallback

Trigger matching looks at the first word of the transcript, case-insensitively, with trailing punctuation stripped. Anything else routes to the default.

Whisper output is not reliable enough for anything cleverer. Scanning the whole transcript for keywords would misroute a note that merely mentions "work". Fuzzy matching would misroute more, less predictably. A first-word convention is something a user can learn and say deliberately, and its worst failure is a note landing in the default folder, which is where it would have gone anyway.

Ambiguity resolves to the default rather than to a guess: a trigger word claimed by two destinations routes nowhere and strips nothing.

### Destinations are a list where there was a single grant

`VaultGrant` becomes a list of records (name, persisted URI, trigger words, default flag) and `SafVaultStorage` is constructed per destination. Migration is one step at startup: an existing single grant becomes the default destination, keeping its URI, with no user action.

`VaultAccess` semantics are preserved by treating "no destinations at all" as `NotConfigured` and a default whose grant will not resolve as `Lost`, so the existing setup screen and at-risk reporting keep working without being rewritten.

## Risks / Trade-offs

- **A user tunes the silence threshold badly and captures stop instantly or never stop** → clamped ranges, a visible reset-to-defaults action, and a cap that cannot be disabled. Worth stating plainly in the settings screen that these are the numbers that end a recording.
- **Daily-note append corrupts a note being synced concurrently** → the read-compare-write rule and the fall back to a separate note. This is the risk that justifies most of the complexity in this change, and the mitigation deliberately prefers a duplicate file over a merge.
- **Routing sends a note to the wrong vault** → first-word-only matching, ambiguity resolving to default, and every failure resolving to default. A misrouted note is still a written note.
- **A destination grant is revoked in Android settings without the user noticing** → routed writes fall back to the default, and a lost default surfaces through the existing at-risk alert rather than a new mechanism.
- **The settings screen becomes the app's largest UI, in an app whose UI is deliberately nearly absent** → keep it a plain list on the existing setup screen's visual language, with no navigation hierarchy beyond one level.
- **Scope**: this is three features in one change. They share the settings screen and the entitlement gate, but if implementation stalls, capture tuning is the piece that can ship alone, and destinations plus routing are the piece to cut first.

## Migration Plan

No user action, and no data migration beyond one startup step: a single existing vault grant is promoted to the default destination, keeping its URI and its persisted permission. Stored settings are absent on every existing install, so every default applies and behaviour is unchanged until something is edited.

Rollback per feature: capture tuning reverts by reading `Config` directly again, templates revert by hardcoding the existing filename and frontmatter, and destinations revert by using the default destination's grant only. The settings screen can be removed independently of all three.

## Open Questions

- Whether haptic intensity is worth a setting, or whether the three patterns should stay fixed. Intensity is included here because it does not alter the patterns' shapes and therefore does not touch the voice-capture requirement that they stay distinguishable, but it is the least valuable item in the tuning list.
- Whether the quick-text widget stays Pro-gated once these features exist, carried over from `add-pro-entitlement`.
- Whether daily-note append should also apply to quick-text captures, or only to voice notes. Appending several short typed notes to one daily file is arguably the more natural fit, but it multiplies the concurrent-append cases.
