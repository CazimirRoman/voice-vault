## Purpose

Guarantee durable persistence into the Obsidian vault — audio-first write ordering, Markdown note naming and format, retry of pending transcriptions, and failure notification.

## Requirements

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

Audio files left in the pending folder by a previous failed or interrupted capture SHALL be transcribed on the next capture or app launch. The sweep SHALL tolerate the pending folder changing underneath it while it runs, and SHALL NOT report a failure for audio that left the folder during the sweep.

#### Scenario: Retry after failure

- **WHEN** a capture is triggered and pending audio from an earlier failed transcription exists
- **THEN** the pending audio SHALL be queued for transcription

#### Scenario: Successful retry cleans up

- **WHEN** pending audio is successfully transcribed into a note
- **THEN** the pending audio file SHALL be deleted

#### Scenario: A swept file is transcribed by the capture that owns it

- **WHEN** a sweep enumerates a pending file that the in-flight capture goes on to transcribe and delete
- **THEN** the sweep SHALL NOT report that file as a failure

#### Scenario: Concurrent sweeps

- **WHEN** two retry sweeps run against the same pending folder
- **THEN** at most one failure SHALL be reported per genuinely failing file

### Requirement: A note at risk raises a distinct alert

When a capture cannot be completed into a note, the app SHALL emit a 5-second continuous vibration, clearly distinguishable from the start and stop signals, and SHALL post a persistent notification. An alert SHALL be raised only when the capture genuinely has no note, so that a capture which has already been transcribed never produces a failure alert.

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

#### Scenario: A note already exists for the capture

- **WHEN** a failure would be reported for a capture whose Markdown note is already present in the vault
- **THEN** no notification SHALL be posted
- **AND** no vibration SHALL be emitted

### Requirement: Stale failure alerts are cleared

A failure notification SHALL be cancelled once its capture is no longer pending, whatever the reason it stopped being pending. Clearing SHALL NOT depend on a later sweep finding and successfully transcribing that same audio, because audio that has already been removed will never be swept again.

#### Scenario: The capture succeeded after the alert was posted

- **WHEN** audio that has an outstanding failure notification is successfully transcribed into a note
- **THEN** that notification SHALL be cancelled

#### Scenario: The audio is gone but the alert remains

- **WHEN** a retry sweep or app launch finds an outstanding failure notification whose audio is no longer in the pending folder
- **THEN** that notification SHALL be cancelled

#### Scenario: Recovering from previously posted phantom alerts

- **WHEN** the app starts and failure notifications exist for captures that already have notes in the vault
- **THEN** those notifications SHALL be cancelled

### Requirement: Discard is only offered for audio that exists

The Discard action SHALL be offered only when the pending audio it refers to is genuinely present and genuinely unrecoverable by retry, so the action never presents the user with a choice about a file that is not there.

#### Scenario: Audio is still pending

- **WHEN** a failure notification refers to audio that is present in the pending folder and the speech model is available
- **THEN** the Discard action SHALL be offered

#### Scenario: Audio is no longer pending

- **WHEN** a failure notification would refer to audio that is not present in the pending folder
- **THEN** no Discard action SHALL be offered

#### Scenario: Discarding removes the capture's diagnostic record

- **WHEN** the user discards pending audio
- **THEN** the audio and its diagnostic record SHALL both be removed
- **AND** the notification SHALL be cancelled

### Requirement: Storage access is verified before capture

The app SHALL verify it holds all-files access at the start of a capture and SHALL raise the failure signal if the vault is not writable, rather than recording into a path that cannot be persisted.

#### Scenario: Permission was revoked

- **WHEN** a capture is triggered and all-files access has been revoked
- **THEN** the failure signal SHALL be raised
- **AND** recording SHALL NOT proceed silently

#### Scenario: Vault folder missing

- **WHEN** the configured vault folder does not exist
- **THEN** the failure signal SHALL be raised
