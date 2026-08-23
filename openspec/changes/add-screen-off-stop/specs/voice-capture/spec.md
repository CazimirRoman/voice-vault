## ADDED Requirements

### Requirement: Recording stops when the user turns off the screen

Turning the screen off while a capture is in progress SHALL end the recording. This is a stop, not a cancel: the audio captured up to that moment SHALL be persisted and transcribed exactly as it would have been under any other stop condition.

This condition exists because pressing the power button and pocketing the phone is the user's actual "I am done" gesture, and it is the same action that fills the microphone with fabric noise — making the silence threshold unreachable on the app's primary flow.

#### Scenario: User pockets the phone after speaking

- **WHEN** the user finishes speaking, presses the power button to turn off the screen, and pockets the phone
- **THEN** recording SHALL end without waiting for the silence threshold
- **AND** the stop buzz vibration SHALL be emitted
- **AND** the speech captured before the button press SHALL be present in the resulting note

#### Scenario: Continuous noise does not defeat the stop

- **WHEN** the microphone input is continuously above the silence threshold and the user turns off the screen
- **THEN** recording SHALL end at the button press rather than at the 3-minute cap

#### Scenario: Stop is not a cancel

- **WHEN** recording ends because the screen was turned off
- **THEN** the captured audio SHALL be written to the pending folder before transcription is attempted
- **AND** transcription and note writing SHALL proceed unchanged

#### Scenario: Screen was already off when the capture was triggered

- **WHEN** a capture is triggered while the screen is already off, so no screen-off transition occurs during recording
- **THEN** recording SHALL still be bounded by the silence threshold and the 3-minute cap

### Requirement: The screen-off stop does not depend on the overlay

The screen-off stop condition SHALL be owned by the recording session itself, not by the listening overlay, so that it functions whether or not the overlay was ever shown.

#### Scenario: Overlay never launched

- **WHEN** the overlay activity fails to start and the user turns off the screen while recording
- **THEN** recording SHALL still end at the button press

#### Scenario: Listener is released with the recording

- **WHEN** a recording ends by any stop condition, or fails before capture begins
- **THEN** the app SHALL stop listening for screen-off transitions for that capture

### Requirement: The display does not sleep on its own during a capture

While the listening overlay is shown, the display SHALL be kept awake, so that a screen-off transition can be read as a deliberate user action rather than an idle timeout.

#### Scenario: Long pause mid-thought

- **WHEN** the user triggers a capture and pauses for longer than the device's display timeout without speaking or touching the screen
- **THEN** the display SHALL remain on
- **AND** recording SHALL NOT end as a result of the display timing out

## MODIFIED Requirements

### Requirement: Recording runs in a foreground service

Audio capture SHALL be performed by a foreground service declaring `foregroundServiceType="microphone"`, so that recording survives the app losing visibility, the device locking, and the phone being pocketed.

#### Scenario: App loses visibility during recording

- **WHEN** the recording session's window is dismissed, replaced, or destroyed while recording is in progress
- **THEN** recording SHALL continue uninterrupted

#### Scenario: Device locks during recording

- **WHEN** the device locks while recording is in progress and the display remains on
- **THEN** recording SHALL continue uninterrupted

#### Scenario: Phone is pocketed during recording

- **WHEN** the user pockets the phone while speaking, without having turned off the screen
- **THEN** recording SHALL continue until a stop condition is met

### Requirement: Stop haptic signals the end of recording

The app SHALL emit a single buzz vibration, distinguishable from the start signal, at the moment recording ends, regardless of which stop condition triggered it.

#### Scenario: Recording ends on silence

- **WHEN** recording stops because the silence threshold was reached
- **THEN** a single buzz vibration SHALL be emitted

#### Scenario: Recording ends on tap

- **WHEN** recording stops because the user tapped the screen
- **THEN** the same single buzz vibration SHALL be emitted

#### Scenario: Recording ends on screen-off

- **WHEN** recording stops because the user turned off the screen
- **THEN** the same single buzz vibration SHALL be emitted
- **AND** it SHALL be perceptible to a user who has already begun pocketing the phone
