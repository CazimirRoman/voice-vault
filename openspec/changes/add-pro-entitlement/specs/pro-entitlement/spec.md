## ADDED Requirements

### Requirement: Pro is a single one-time purchase

The app SHALL offer exactly one paid product: a non-consumable in-app purchase that unlocks Pro permanently for the purchasing Google account. There SHALL be no subscription, no renewal, no trial period, and no consumable or tiered products.

#### Scenario: Product catalogue

- **WHEN** the purchase surface queries Play for available products
- **THEN** it SHALL query exactly one product id, of type in-app (non-consumable)

#### Scenario: Purchase is permanent

- **WHEN** the user has purchased Pro and later reinstalls the app or installs it on another device with the same Google account
- **THEN** restoring SHALL re-grant Pro without a second charge

### Requirement: Entitlement is cached locally and readable offline

The app SHALL persist the entitlement result in app-private storage and SHALL answer "is this install Pro?" from that cache without requiring a network call or a connection to Play.

#### Scenario: Entitled user launches offline

- **WHEN** the app launches with no network available and the cached entitlement is granted
- **THEN** Pro features SHALL be available immediately, without waiting for any billing call

#### Scenario: Entitlement survives restart

- **WHEN** the app process is killed and relaunched after a successful purchase
- **THEN** the cached entitlement SHALL still report Pro

#### Scenario: Reading entitlement never blocks

- **WHEN** any caller asks whether the install is Pro
- **THEN** the answer SHALL be returned from local state without blocking on I/O or on a billing connection

### Requirement: Entitlement is withdrawn only on an authoritative denial

A granted entitlement SHALL be cleared only when Play returns a successful purchase query whose result does not include the Pro product. Any other outcome, including connection failure, timeout, service unavailability, a billing-unavailable device, or an unparseable response, SHALL leave an existing granted entitlement unchanged.

#### Scenario: Verification fails while offline

- **WHEN** a launch-time verification is attempted with no network and the cached entitlement is granted
- **THEN** the entitlement SHALL remain granted
- **AND** no Pro feature SHALL become unavailable

#### Scenario: Play reports the purchase is gone

- **WHEN** a purchase query completes successfully and does not contain the Pro product
- **THEN** the cached entitlement SHALL be cleared
- **AND** Pro features SHALL become unavailable on the next check

#### Scenario: Billing service returns an error

- **WHEN** a purchase query returns any non-OK billing response code
- **THEN** the cached entitlement SHALL be left exactly as it was

### Requirement: Purchases are acknowledged immediately

The app SHALL acknowledge every Pro purchase as soon as it is observed, whether it arrives from a completed purchase flow or from a launch-time purchase query. Acknowledgement SHALL be retried on the next launch if it has not succeeded.

#### Scenario: Purchase completes

- **WHEN** a purchase reaches the purchased state
- **THEN** the app SHALL acknowledge it before treating the entitlement as settled

#### Scenario: Acknowledgement failed previously

- **WHEN** a launch-time query returns a purchased, unacknowledged Pro purchase
- **THEN** the app SHALL attempt acknowledgement again

#### Scenario: Pending purchase

- **WHEN** a purchase is in the pending state
- **THEN** Pro SHALL NOT be granted
- **AND** the purchase surface SHALL indicate that the purchase is still being processed

### Requirement: Unavailable billing is a supported state

The app SHALL treat the inability to reach or use Play Billing, including on builds not installed by Google Play, as a normal free-tier state. It SHALL NOT crash, block startup, show an error dialog, or degrade any free feature.

#### Scenario: Sideloaded install

- **WHEN** the app runs on a build that Play did not install and billing setup reports the service is unavailable
- **THEN** the app SHALL run normally as free
- **AND** the purchase surface SHALL state that purchasing is not available on this install, in place of a price and buy action

#### Scenario: Billing connection drops mid-session

- **WHEN** the billing connection is lost after startup
- **THEN** no user-visible failure SHALL be surfaced outside the purchase surface

### Requirement: Capture and durability are never gated

Triggering a capture, recording audio, writing audio to the pending folder, transcribing, writing a note to the vault, retrying pending audio, and reporting a failure SHALL function identically regardless of entitlement. No code on the capture path SHALL consult the entitlement.

#### Scenario: Free user captures a note

- **WHEN** a user without Pro triggers a capture by the power-button gesture
- **THEN** the capture SHALL record, transcribe, and write a note exactly as it would with Pro

#### Scenario: Entitlement is lost with pending audio present

- **WHEN** the entitlement is withdrawn while audio is waiting in the pending folder
- **THEN** that audio SHALL still be transcribed and written to the vault

#### Scenario: Existing notes stay reachable

- **WHEN** the entitlement is absent or withdrawn
- **THEN** every note already written to the vault SHALL remain present and unmodified

### Requirement: The purchase surface stays off the capture path

The app SHALL present purchasing as a section of the existing setup screen. It SHALL NOT be shown during a capture, over the tap-to-stop surface, on the lock screen, or as an interstitial on launch.

#### Scenario: Capture in progress

- **WHEN** a capture is running
- **THEN** no purchase or upgrade surface SHALL be displayed

#### Scenario: Setup screen shows the offer

- **WHEN** a user without Pro opens the app
- **THEN** the setup screen SHALL show what Pro unlocks, the localised price fetched from Play, a buy action, and a restore action

#### Scenario: Already purchased

- **WHEN** a user with Pro opens the app
- **THEN** the purchase surface SHALL show that Pro is active and SHALL NOT offer to buy it again

### Requirement: Restoring a purchase is always available

The app SHALL provide an explicit restore action that re-runs the purchase query and grants Pro if the account owns the product.

#### Scenario: Reinstall

- **WHEN** a user who previously purchased Pro reinstalls the app and taps restore
- **THEN** the entitlement SHALL be granted without a charge

#### Scenario: Restore finds nothing

- **WHEN** restore completes successfully and the account does not own Pro
- **THEN** the surface SHALL say that no purchase was found
- **AND** SHALL NOT present this as an error

### Requirement: Debug builds can override the entitlement

Debug builds SHALL provide an override with three states: follow Play, force Pro, and force free. Release builds SHALL ignore the override entirely and SHALL derive entitlement only from Play and the cache.

#### Scenario: Developer tests Pro on a sideloaded build

- **WHEN** a debug build sets the override to force Pro
- **THEN** every gated feature SHALL behave as entitled, with no billing connection required

#### Scenario: Developer tests the free tier

- **WHEN** a debug build sets the override to force free while a purchase exists
- **THEN** every gated feature SHALL behave as unentitled

#### Scenario: Release build

- **WHEN** a release build is running
- **THEN** no override SHALL be readable or writable, and entitlement SHALL come only from Play and the cache

### Requirement: The quick-text widget requires Pro

The quick-text capture entry point SHALL be available only to entitled installs. Without Pro, activating it SHALL lead to the purchase surface rather than to the text field.

#### Scenario: Free user taps the widget

- **WHEN** a user without Pro taps the quick-note home-screen widget
- **THEN** the text-entry surface SHALL NOT open
- **AND** the app SHALL open on the purchase surface

#### Scenario: Pro user taps the widget

- **WHEN** a user with Pro taps the quick-note home-screen widget
- **THEN** the text-entry surface SHALL open with its existing behaviour unchanged

#### Scenario: Entitlement lost with text in flight

- **WHEN** the entitlement is withdrawn while the text-entry surface is open with non-blank text
- **THEN** the in-flight text SHALL still be saved to the vault on dismissal
