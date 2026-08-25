## MODIFIED Requirements

### Requirement: A note at risk raises a distinct alert

When a capture cannot be completed into a note, the app SHALL post a persistent notification. An alert SHALL be raised only when the capture genuinely has no note, so that a capture which has already been transcribed never produces a failure alert.

The 5-second continuous vibration, clearly distinguishable from the start and stop signals, SHALL be reserved for outcomes that put recorded speech at risk. An outcome that establishes there was no speech to lose SHALL be reported without that vibration and without any alerting notification behavior — no notification vibration, no sound, and no heads-up banner — so that the rumble continues to mean exactly one thing: a recording the user made is in danger.

#### Scenario: Transcription fails

- **WHEN** a capture fails to produce a note because of an error
- **THEN** a 5-second vibration SHALL be emitted
- **AND** a notification SHALL be posted stating that a capture needs attention

#### Scenario: No speech was in the recording

- **WHEN** a capture produces no note because the recognizer found no speech in the audio
- **THEN** no vibration beyond the stop signal SHALL be emitted
- **AND** the notification SHALL be posted silently, without vibrating, sounding, or interrupting the screen
- **AND** this SHALL hold on the capture's first transcription attempt and on every later retry sweep alike

#### Scenario: Alert survives being missed

- **WHEN** the failure vibration occurs while the phone is pocketed and goes unnoticed
- **THEN** the notification SHALL remain in the shade until the user dismisses or resolves it

#### Scenario: Success is silent

- **WHEN** a capture completes successfully into a note
- **THEN** no vibration beyond the stop signal SHALL be emitted
- **AND** no notification SHALL be posted

#### Scenario: A note already exists for the capture

- **WHEN** a failure would be reported for a capture whose Markdown note is already present in the vault
- **THEN** no notification SHALL be posted
- **AND** no vibration SHALL be emitted
