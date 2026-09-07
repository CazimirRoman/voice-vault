## Why

Voice Vault has no way to charge for anything. Making that possible is the prerequisite for every paid feature, and it is the part with the most ways to go quietly wrong: an unacknowledged purchase is auto-refunded by Google after three days, an over-eager verification check locks a paying user out on a plane, and a billing client that assumes it is running on a Play-installed build breaks every sideloaded developer install.

This change builds the entitlement layer on its own, with exactly one real feature gated behind it so the wiring is proven end to end rather than in theory. The paid features themselves (a settings screen, note templates, multiple vault destinations) follow in a separate change.

## What Changes

- Add Google Play Billing as a direct dependency. No third-party billing SDK: a single non-consumable product on one platform does not need subscription lifecycle management, and an SDK that reports purchase events to an external service would contradict the app's "nothing leaves the device" position for no functional gain.
- Add a `pro/` package owning one question, `is this install entitled to Pro?`, and the purchase flow that answers it. Nothing outside `pro/` talks to Play Billing.
- Define a single non-consumable in-app product (a one-time purchase, no subscription, no renewal).
- Persist the entitlement locally so it survives being offline, an app restart, and a Play Store that will not connect. The cached answer is authoritative for day-to-day use; Play is consulted to grant and to confirm, never as a per-launch gate.
- **Never revoke on failure.** Entitlement is withdrawn only on an explicit, successful Play response that says this account does not own the product. Any error, timeout, or unavailable service leaves an existing entitlement intact.
- Acknowledge every purchase immediately. An unacknowledged one-time purchase is refunded automatically after three days, which would silently un-Pro a paying user.
- Handle "billing is not available here" as a normal state, not an error. Sideloaded builds cannot purchase, so the app must run fully as free rather than showing a broken store or blocking startup.
- Add a debug-only entitlement override with three states (follow Play, force Pro, force free) so both tiers stay testable on the sideloaded development device, which Play Billing will always report as owning nothing.
- Add a purchase surface to `MainActivity`: what Pro unlocks, the price fetched from Play, a buy action, and a restore action. It is a section on the existing setup screen, not a new interruptive paywall, and it is never shown during a capture.
- Gate the quick-text widget (`quicknote/`) behind Pro as this change's single proof gate. Free keeps the full voice pipeline; typing a note becomes a Pro entry point. Tapping the widget without Pro opens the purchase surface instead of the text field.
- **Not** gated, now or later: the power-button capture path, transcription, writing a note to the vault, retrying pending audio, and failure reporting. Capture and durability stay free in every build.

## Capabilities

### New Capabilities
- `pro-entitlement`: purchasing a one-time Pro unlock through Google Play, caching the resulting entitlement so it survives offline and restart, the rules for when entitlement may be withdrawn, and the contract that gated features must satisfy (never gate capture, never gate durability, never make existing notes unreachable).

### Modified Capabilities
(none in `openspec/specs/`. The quick-text widget's gate is specified in `pro-entitlement` because `quick-text-capture` has not been archived into `openspec/specs/` yet. See Impact.)

## Impact

- **New code**: `pro/ProEntitlement.kt` (the single source of truth other packages read), `pro/EntitlementStore.kt` (local persistence), `pro/ProBilling.kt` (the only Play Billing caller), `pro/ProUpgradeScreen.kt` (purchase surface).
- **Modified code**: `MainActivity` gains the purchase section and a launch-time entitlement refresh. `quicknote/QuickNoteWidgetProvider` and `quicknote/QuickNoteActivity` gain the entitlement check. `Config.kt` gains the product id and the verification policy constants.
- **New dependency**: `com.android.billingclient:billing-ktx`, added to `gradle/libs.versions.toml`. First non-AndroidX, non-whisper runtime dependency in the project.
- **Manifest**: adds `com.android.vending.BILLING`. No new dangerous permissions.
- **Network**: adds a second reason the app touches the network, after the one-time model download. It stays narrow (Play Billing's own calls, no analytics, no audio, no note content) and needs saying plainly in `CLAUDE.md`, which currently documents the model download as the sole exception.
- **Coordination with `add-quick-text-capture`**: that change is implemented but unarchived, so its spec is not yet in `openspec/specs/`. When it is archived, its spec gains the Pro requirement and `pro-entitlement` can drop the widget-specific clause. Until then the gate is specified here.
- **Distribution (noted, not built here)**: Play Billing only functions for builds installed by Play, so shipping this for real requires a Play listing, a privacy policy, a data-safety declaration, and a justification for the microphone foreground service type. The current sideload-only, `arm64-v8a`-only posture is unaffected by this change and continues to work as free.
- **Unaffected**: `assistant/`, `capture/`, `transcribe/`, and `vault/` are not touched. No capture path calls into `pro/`.
