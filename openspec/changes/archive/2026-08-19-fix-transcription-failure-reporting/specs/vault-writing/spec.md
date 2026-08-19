## MODIFIED Requirements

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

## ADDED Requirements

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
