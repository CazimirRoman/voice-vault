## ADDED Requirements

### Requirement: Audio is persisted before transcription is attempted

The recorded audio SHALL be written to a pending folder inside the Obsidian vault as soon as recording ends, before any transcription work begins. This is the durability guarantee for the entire app.

#### Scenario: Recording ends

- **WHEN** recording stops for any reason
- **THEN** the audio SHALL be written to the vault's pending folder before transcription starts

#### Scenario: Total failure after recording

- **WHEN** every stage after recording fails, including transcription and note writing
- **THEN** the audio file SHALL still be present in the vault and playable by the user

### Requirement: Transcript is written as a Markdown note

On successful transcription, a `.md` file containing the transcript SHALL be written into the configured vault folder.

#### Scenario: Successful capture

- **WHEN** transcription produces non-empty text
- **THEN** a Markdown file SHALL be created in the configured vault folder containing that text

#### Scenario: Note is named by capture time

- **WHEN** a note is written
- **THEN** its filename SHALL be derived from the capture timestamp and SHALL NOT collide with an existing note

### Requirement: Writes are atomic

All files written into the vault SHALL be written to a temporary path and then renamed into place, so that a sync client observing the vault never reads a partially written file.

#### Scenario: Vault is being synced

- **WHEN** a note or audio file is written while a sync client is watching the vault folder
- **THEN** the file SHALL only become visible at its final path once fully written

### Requirement: Pending audio is retried

Audio files left in the pending folder by a previous failed or interrupted capture SHALL be transcribed on the next capture or app launch.

#### Scenario: Retry after failure

- **WHEN** a capture is triggered and pending audio from an earlier failed transcription exists
- **THEN** the pending audio SHALL be queued for transcription

#### Scenario: Successful retry cleans up

- **WHEN** pending audio is successfully transcribed into a note
- **THEN** the pending audio file SHALL be deleted

### Requirement: A note at risk raises a distinct alert

When a capture cannot be completed into a note, the app SHALL emit a 5-second continuous vibration, clearly distinguishable from the start and stop signals, and SHALL post a persistent notification.

#### Scenario: Transcription fails

- **WHEN** a capture fails to produce a note
- **THEN** a 5-second vibration SHALL be emitted
- **AND** a notification SHALL be posted stating that a capture needs attention

#### Scenario: Alert survives being missed

- **WHEN** the failure vibration occurs while the phone is pocketed and goes unnoticed
- **THEN** the notification SHALL remain in the shade until the user dismisses or resolves it

#### Scenario: Success is silent

- **WHEN** a capture completes successfully into a note
- **THEN** no vibration beyond the stop signal SHALL be emitted
- **AND** no notification SHALL be posted

### Requirement: Storage access is verified before capture

The app SHALL verify it holds all-files access at the start of a capture and SHALL raise the failure signal if the vault is not writable, rather than recording into a path that cannot be persisted.

#### Scenario: Permission was revoked

- **WHEN** a capture is triggered and all-files access has been revoked
- **THEN** the failure signal SHALL be raised
- **AND** recording SHALL NOT proceed silently

#### Scenario: Vault folder missing

- **WHEN** the configured vault folder does not exist
- **THEN** the failure signal SHALL be raised
