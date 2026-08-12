## ADDED Requirements

### Requirement: App is selectable as the system digital assistant

The app SHALL register a `VoiceInteractionService` guarded by the `android.permission.BIND_VOICE_INTERACTION` permission, together with a `VoiceInteractionSessionService` and an `res/xml` interaction service descriptor, so that it appears in the system list of digital assistant apps.

#### Scenario: App appears in assistant picker

- **WHEN** the user opens Settings → Apps → Default apps → Digital assistant app
- **THEN** EasyNote SHALL be listed as a selectable option

#### Scenario: App is selected as assistant

- **WHEN** the user selects EasyNote as the digital assistant
- **THEN** the system SHALL bind the app's voice interaction service without error

### Requirement: Power button hold starts a capture

While the app holds the assistant role, a long press of the power button SHALL start a voice capture session.

#### Scenario: Power hold while device unlocked

- **WHEN** the user long-presses the power button and the device is unlocked
- **THEN** a capture session SHALL start

#### Scenario: Power hold while screen is off

- **WHEN** the user long-presses the power button and the screen is off
- **THEN** a capture session SHALL start

### Requirement: Trigger renders no user interface

The voice interaction session SHALL start the recording service and dismiss itself without drawing any window, overlay, or activity.

#### Scenario: Screen contents are undisturbed

- **WHEN** a capture session is triggered while another app is visible
- **THEN** no EasyNote window SHALL be shown and the previously visible content SHALL remain on screen

#### Scenario: Session dismisses immediately

- **WHEN** the voice interaction session has started the recording service
- **THEN** the session SHALL finish itself and SHALL NOT remain active for the duration of the recording

### Requirement: Secondary assist entry point

The app SHALL declare a handler for the `android.intent.action.ASSIST` intent that starts a capture session identical to the one started by the power button hold.

#### Scenario: ASSIST intent received

- **WHEN** the system dispatches `android.intent.action.ASSIST` to the app
- **THEN** a capture session SHALL start with the same behavior as a power button trigger

### Requirement: Trigger works without a launcher visit

Capture SHALL be startable without the user having opened the app, provided the required permissions were previously granted.

#### Scenario: Trigger after device reboot

- **WHEN** the device has been rebooted and the app has not been opened since
- **AND** the user long-presses the power button
- **THEN** a capture session SHALL start
