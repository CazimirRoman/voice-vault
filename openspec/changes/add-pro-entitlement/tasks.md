## 1. Dependency and product definition

- [ ] 1.1 Look up the current Google Play Billing Library release and add `com.android.billingclient:billing-ktx` to `gradle/libs.versions.toml` with a pinned version, then wire it into `app/build.gradle.kts`. Do not copy a version from memory: confirm the product-details API shape against the version being pinned.
- [ ] 1.2 Add `<uses-permission android:name="com.android.vending.BILLING" />` to `AndroidManifest.xml`.
- [ ] 1.3 Add the Pro product id and the entitlement preference keys to `Config.kt`, alongside the existing tunables.
- [ ] 1.4 Confirm the project still builds with `./gradlew assembleDebug` before any Pro code exists.

## 2. Entitlement state, independent of billing

- [ ] 2.1 Create `pro/EntitlementStore.kt`: app-private `SharedPreferences` holding granted flag, purchase token, and last-verified timestamp. Plain preferences, no encryption (see design).
- [ ] 2.2 Create `pro/ProEntitlement.kt` as a singleton `object` mirroring `TranscriptionQueue`'s shape: a `StateFlow<Boolean>` plus a synchronous `isPro` read, loaded from the store on first access, never doing I/O on the caller's thread.
- [ ] 2.3 Implement the withdrawal rule in one function: entitlement clears only on an authoritative denial, and every error, timeout, or unavailable-service outcome leaves the cached value untouched.
- [ ] 2.4 Add the debug-only three-state override (follow Play / force Pro / force free), read behind `BuildConfig.DEBUG` and unreadable in release builds.
- [ ] 2.5 JVM unit tests for the state machine: granted survives an error response, granted survives a timeout, granted clears on an OK-with-no-purchase response, and a release build ignores the override.

## 3. Play Billing integration

- [ ] 3.1 Create `pro/ProBilling.kt` as the only file importing `com.android.billingclient`: connect and disconnect on demand, tied to `MainActivity`'s lifecycle rather than the process.
- [ ] 3.2 Query product details for the single non-consumable product and expose the localised formatted price, plus a distinct state for "billing unavailable on this install".
- [ ] 3.3 Implement the purchase flow with a `PurchasesUpdatedListener`, mapping user-cancelled, already-owned, and error outcomes to distinct results.
- [ ] 3.4 Acknowledge every observed purchase immediately, from both the purchase flow and the launch-time query, and retry acknowledgement on launch whenever an unacknowledged purchase exists.
- [ ] 3.5 Treat a pending purchase as not entitled, and surface it as still processing rather than as a failure.
- [ ] 3.6 Implement launch-time verification and an explicit restore action, both routing their results through the withdrawal rule from 2.3.
- [ ] 3.7 Verify that a billing setup failure, including on a sideloaded build, never crashes, blocks startup, or produces a dialog outside the purchase surface.

## 4. Purchase surface

- [ ] 4.1 Create `pro/ProUpgradeScreen.kt` as a section composable for the existing setup screen, matching the surrounding `SetupStep` style rather than introducing a new visual language.
- [ ] 4.2 Render the four states: not purchased with a price and buy action, purchased and active, purchase pending, and purchasing unavailable on this install.
- [ ] 4.3 Add a restore action, with a non-error message when no purchase is found.
- [ ] 4.4 Wire the section into `MainActivity`, and start billing connection, verification, and acknowledgement retry on launch.
- [ ] 4.5 Show the debug override control in the section on debug builds only.
- [ ] 4.6 Confirm no purchase or upgrade surface can appear during a capture, over `TapToStopActivity`, or on the lock screen.

## 5. The proof gate: quick-text widget

- [ ] 5.1 Add the entitlement check to `QuickNoteActivity.onCreate`: when unentitled, launch `MainActivity` on the purchase surface and finish without showing the text field.
- [ ] 5.2 Leave `QuickNoteWidgetProvider`'s `PendingIntent` unconditional, so the gate cannot go stale after a purchase.
- [ ] 5.3 Verify the save path in `onStop` is not gated, so text typed before an entitlement change is still written to the vault.

## 6. Invariants and documentation

- [ ] 6.1 Add a JVM test that scans `assistant/`, `capture/`, `transcribe/`, and `vault/` sources and fails on any import of `dev.cazimir.voicevault.pro` or `com.android.billingclient`.
- [ ] 6.2 Update `CLAUDE.md`: add the `pro/` package to the architecture section, record the never-gate-capture and never-revoke-on-failure invariants alongside the existing ones, and correct the network claim to cover Play's billing calls as well as the model download.
- [ ] 6.3 Run `./gradlew test lint` and fix what they surface.

## 7. Device verification (requires a human and the physical Pixel 6 Pro)

- [ ] 7.1 On a debug build with the override set to force free, tap the quick-note widget and confirm it opens the purchase surface instead of the text field.
- [ ] 7.2 With the override set to force Pro, confirm the widget opens the text field and saving still works exactly as before.
- [ ] 7.3 With the override on force free, run a full power-button voice capture and confirm the note lands in the vault unchanged.
- [ ] 7.4 With the device in airplane mode and a granted entitlement cached, open the app and confirm Pro stays active and no error is shown.
- [ ] 7.5 On the sideloaded build, confirm the purchase surface reports that purchasing is unavailable, and that nothing else in the app is degraded.

## 8. Real billing verification (requires a Play-installed build, blocked on listing work)

- [ ] 8.1 Create the internal testing track and the in-app product in Play Console, and add a licence-tester account.
- [ ] 8.2 Complete a real test purchase on a Play-installed build and confirm the entitlement is granted and acknowledged.
- [ ] 8.3 Uninstall, reinstall, and confirm restore re-grants Pro without a second charge.
- [ ] 8.4 Refund the test purchase and confirm entitlement clears on the next launch-time verification, and only then.
