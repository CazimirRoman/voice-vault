## Context

Voice Vault today is a single-device, sideloaded, offline capture tool with no monetisation, no accounts, and no settings UI. It makes exactly one network call in its life, to download the whisper model. Introducing a paid tier changes three of those facts at once, so the design below is mostly about limiting the blast radius.

Two constraints shape everything here:

1. **The app's job is to not lose a thought.** Any entitlement logic that can stand between a spoken sentence and a file in the vault is a defect, regardless of how correct it is about billing.
2. **The only device that runs this is a sideloaded Pixel 6 Pro.** Play Billing reports "service unavailable" there and will never report a purchase, so the development device is permanently in the state that a naive implementation handles worst.

The relevant existing structure: `TranscriptionQueue` is the house pattern for a process-wide coordinator (a singleton `object`, not a class, with its own locking). `MainActivity` is not a real UI, only a setup and permissions screen, and it is the natural and only place a purchase surface belongs. `quicknote/` is already built and shipped to the device, so gating it is a change to an existing entry point rather than new feature work.

## Goals / Non-Goals

**Goals:**

- One place in the codebase that knows whether this install is Pro, readable synchronously and offline.
- A purchase flow that cannot silently lose a paying user's money (acknowledgement) or their access (fail-open verification).
- A free tier that is complete: full voice capture, transcription, vault writing, retry, and failure reporting.
- Both tiers testable on a sideloaded build, where real purchases are impossible.
- One real feature gated, so the wiring is exercised rather than assumed.

**Non-Goals:**

- Server-side receipt validation. There is no server and there will not be one for this.
- Meaningful protection against a patched APK. See Decisions.
- Subscriptions, trials, tiers, promo codes, or regional pricing logic beyond what Play returns.
- The paid features themselves. Settings, note templates, and vault destinations are `add-pro-features`.
- Getting the app onto Play. Recorded as a prerequisite below, built separately.

## Decisions

### Play Billing directly, not RevenueCat

RevenueCat's value is concentrated in subscription lifecycle, renewal and churn analytics, cross-platform entitlement sync, and server-side receipt validation. This product has one non-consumable, on one platform, with no server and no renewals, so almost none of that applies. The costs do: an additional SDK that transmits an app user id and purchase events to a third party, in an app whose stated position is that nothing leaves the device, plus an account, a privacy-policy disclosure, a data-safety entry, and a revenue share above the free tier.

For a single non-consumable, the Play Billing surface actually used is small: connect, `queryProductDetailsAsync` for one product, `launchBillingFlow`, a `PurchasesUpdatedListener`, `queryPurchasesAsync`, `acknowledgePurchase`.

Revisit if iOS or subscriptions ever appear. Migrating one non-consumable to RevenueCat later is cheap.

### Entitlement is cached locally in plain SharedPreferences, with no obfuscation

The cache is app-private `SharedPreferences` holding a boolean, the purchase token, and a last-verified timestamp. No encryption, no signature check, no server round-trip.

This is trivially bypassable by anyone who can patch the APK or write to app-private storage on a rooted device. That is accepted deliberately. The realistic audience for a sideloadable, arm64-only, Obsidian-shaped voice note app overlaps heavily with people who could bypass any client-side scheme regardless of effort spent, and every hardening measure available here trades a small reduction in casual piracy for a real increase in the chance of locking out a legitimate paying user. The asymmetry is not close: a bypassed copy costs one sale, a false negative costs a refund, a review, and the trust of the one kind of user this app is for.

Alternatives considered: `EncryptedSharedPreferences` (protects against nothing relevant, since the code that reads it is equally patchable, and adds a failure mode where a corrupt keystore entry revokes a real purchase), and server-side validation (no server, and it makes the offline story worse for a feature set that is entirely offline).

### Entitlement never degrades on an inconclusive answer

The only transition from granted to not-granted is an `OK` response from `queryPurchasesAsync` whose purchase list does not contain the product. Every other outcome, including `SERVICE_DISCONNECTED`, `SERVICE_UNAVAILABLE`, `BILLING_UNAVAILABLE`, `ERROR`, a timeout, and an exception, leaves the cached value untouched.

The alternative, re-validating and revoking on failure, produces the single worst bug this feature can have: a paying user on a plane or in a dead zone discovers their app has quietly reverted to free. Since Play only reports ownership authoritatively when it succeeds, treating "I could not ask" as "you do not own it" is a category error.

The cost is a refund window: a user who refunds keeps Pro until the next successful verification. Verification runs when `MainActivity` opens, which for this app is rare. For a single low-price non-consumable this is not worth engineering against.

### `ProEntitlement` is a singleton object holding a StateFlow, mirroring `TranscriptionQueue`

Callers ask `ProEntitlement.isPro` (a synchronous read of in-memory state) or collect `ProEntitlement.state` in Compose. It is loaded from the cache on first access, updated by `ProBilling` and by the debug override, and never performs I/O on the calling thread. This matches the existing coordinator pattern and keeps every caller free of billing types.

`ProBilling` is the only file in the project that imports `com.android.billingclient`. It owns connection lifecycle, product details, the purchase flow, acknowledgement, and verification, and reports results by updating `ProEntitlement`.

### The billing client connects on demand, not for the process lifetime

The app spends nearly all its life as a background capture service with no UI. A persistent billing connection would exist almost entirely during captures, which are the one time `pro/` must not be doing anything. So the client connects when `MainActivity` starts and disconnects when it stops. Verification and acknowledgement retry happen there.

This means entitlement can be stale for a long time, which is fine and consistent with the fail-open policy above.

### The widget gate lives in `QuickNoteActivity`, not in the widget's PendingIntent

`QuickNoteWidgetProvider` keeps pointing unconditionally at `QuickNoteActivity`; the entitlement check happens in `QuickNoteActivity.onCreate`, which redirects to `MainActivity`'s purchase surface and finishes when unentitled.

The alternative, building a different `PendingIntent` depending on entitlement, requires pushing a widget update on every entitlement change and leaves a widget that still opens the paywall after a successful purchase until something refreshes it. Checking at launch is one branch and cannot go stale.

The in-flight text rule in the spec follows from the same placement: `onStop`'s save path is never gated, so a purchase expiring mid-typing still writes the note.

### The capture path's independence is enforced by a test, not by convention

A JVM unit test walks `assistant/`, `capture/`, `transcribe/`, and `vault/` sources and fails if any of them imports `dev.cazimir.voicevault.pro` or `com.android.billingclient`. The invariant that entitlement cannot reach the capture path is exactly the kind of thing that erodes through a plausible-looking one-line addition six months from now, and it is cheap to assert mechanically. This follows the existing habit in `CLAUDE.md` of naming load-bearing invariants explicitly.

### The debug override is a three-state setting, not a boolean

Follow Play / force Pro / force free. A boolean "debug is always Pro" would make the free tier untestable on the only device that exists, which is how a paywall ships with a broken free path. The override is read behind `BuildConfig.DEBUG` and is absent from release builds.

## Risks / Trade-offs

- **An unacknowledged purchase is auto-refunded by Google after three days** → acknowledge as soon as a purchase is observed, from both the purchase flow and the launch-time query, and retry the acknowledgement on every launch while an unacknowledged purchase exists.
- **A paying user is locked out while offline** → fail-open verification plus a local cache that is authoritative for reads. This is the design's primary defensive choice.
- **The real purchase flow cannot be tested on the development device**, because Play Billing only works for Play-installed builds. The debug override exercises the gates but not the billing code itself → the purchase flow must be verified through an internal testing track with a licence-tester account before any release, and until then `ProBilling` is the least-tested code in the project. Tasks that need a Play-installed build are marked as such and must not be ticked from code inspection.
- **A patched build gets Pro for free** → accepted, see Decisions.
- **A refunded user keeps Pro until the next launch-time verification** → accepted for a single low-price purchase.
- **The offline story in `CLAUDE.md` becomes inaccurate** → update it in this change. The claim becomes: no audio, transcript, or telemetry ever leaves the device, and the network is used only for the one-time model download and Play's own billing calls.
- **Play review surface grows** → this change adds `com.android.vending.BILLING` on top of an app that already holds the assistant role, a microphone foreground service, and SAF vault access. Listing work is out of scope here but blocks any real release.
- **Gating a feature that already exists on the device** → the quick-note widget is currently free and installed. After this change it opens the purchase surface unless the debug override is set. There are no other users to affect, but the development device will need the override set to keep using it.

## Migration Plan

No data migration. An install with no cached entitlement is free, which is the correct default for every existing install. Rollback is deleting the gate check in `QuickNoteActivity` and the purchase section from `MainActivity`; the `pro/` package can stay in place unused, since nothing else depends on it.

Sequence: dependency and product definition, then `EntitlementStore` and `ProEntitlement` with the debug override (both gates and the free tier become testable at this point without any billing code), then `ProBilling`, then the purchase surface, then the widget gate, then the invariant test and the `CLAUDE.md` update.

## Open Questions

- Product id and price. Neither blocks implementation; the id goes in `Config` and the price comes from Play at runtime.
- Which Play Billing Library version to pin. Check the current release when adding the dependency rather than copying a version from memory, since the library deprecates aggressively and the API for product details has changed between recent majors.
- Whether the quick-text widget stays a Pro feature once `add-pro-features` lands, or whether it returns to free and the paid tier rests entirely on settings, templates, and destinations. Worth revisiting once there is more behind the paywall.
