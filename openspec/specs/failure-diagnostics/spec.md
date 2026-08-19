## Purpose

Give every capture a classified outcome, ensure no failure is silently swallowed, and leave a durable, human-readable trail so a failure the user misses in the moment (phone pocketed, notification dismissed) can still be understood and acted on later.

## Requirements

### Requirement: Every capture outcome is classified

A transcription attempt SHALL report a typed outcome rather than a bare success/failure flag. The taxonomy SHALL distinguish at minimum: success, no speech detected, model unavailable, audio no longer present, transcription error, and note-write error. Each non-success outcome SHALL carry whatever detail is available at the point of classification — the underlying throwable for error outcomes, and the raw recognizer output for the no-speech outcome.

#### Scenario: Whisper returns a non-speech placeholder

- **WHEN** transcription completes and the recognizer returns only a bracketed non-speech placeholder such as `[ Pause ]`
- **THEN** the outcome SHALL be classified as no speech detected
- **AND** the raw recognizer output SHALL be retained on the outcome

#### Scenario: The recognizer throws

- **WHEN** the recognizer throws while transcribing
- **THEN** the outcome SHALL be classified as a transcription error
- **AND** the throwable SHALL be retained on the outcome

#### Scenario: The note cannot be written

- **WHEN** transcription produces usable text but writing the Markdown note throws
- **THEN** the outcome SHALL be classified as a note-write error, distinct from a transcription error
- **AND** the audio SHALL remain in the pending folder

#### Scenario: The model is not available

- **WHEN** the speech model cannot be located or loaded
- **THEN** the outcome SHALL be classified as model unavailable, distinct from a transcription error

### Requirement: Silent failure paths are logged

Every code path that catches a throwable and converts it into a non-fatal outcome SHALL log that throwable. No catch block in the capture, transcription, or vault-writing pipeline SHALL discard a throwable without logging it.

#### Scenario: A swallowed exception

- **WHEN** any stage of the pipeline catches a throwable and continues rather than crashing
- **THEN** the throwable SHALL be written to the system log with its stack trace and an identifier for the capture it concerns

#### Scenario: Preload failure

- **WHEN** the speculative model preload fails
- **THEN** the failure SHALL be logged even though it is deliberately not surfaced to the user at that moment

### Requirement: A failed capture leaves a durable diagnostic record

When a capture is left in the pending folder because it could not be turned into a note, a human-readable diagnostic record SHALL be written alongside the audio. The record SHALL survive process death and device reboot, and SHALL be readable without a tethered development machine.

#### Scenario: Failure while the phone is pocketed

- **WHEN** a capture fails and the user does not see the notification for several hours
- **THEN** the diagnostic record for that capture SHALL still be present next to its audio and SHALL state the classified outcome

#### Scenario: Repeated failure of the same capture

- **WHEN** the same pending audio fails again on a later retry sweep
- **THEN** the diagnostic record SHALL reflect the most recent attempt
- **AND** the record SHALL NOT grow without bound across sweeps

#### Scenario: The capture finally succeeds

- **WHEN** pending audio is successfully transcribed on a later attempt
- **THEN** its diagnostic record SHALL be removed together with its audio

#### Scenario: Records are not mistaken for notes or audio

- **WHEN** the retry sweep enumerates the pending folder
- **THEN** diagnostic records SHALL NOT be treated as pending audio
- **AND** they SHALL NOT be written where the vault's note indexer would treat them as notes

### Requirement: A failure alert identifies the capture well enough to act on

A failure notification SHALL carry enough information for the user to decide whether to keep or discard the recording without opening a file manager. It SHALL state the classified reason for the failure and the duration of the recording, and where the recognizer produced text that was rejected as non-speech, it SHALL state what the recognizer heard.

#### Scenario: No speech was detected

- **WHEN** a capture fails because the recognizer detected no speech
- **THEN** the notification SHALL say so explicitly rather than reporting a generic failure
- **AND** it SHALL state the duration of the recording

#### Scenario: A genuine error occurred

- **WHEN** a capture fails because of a transcription or note-write error
- **THEN** the notification SHALL distinguish it from a no-speech result
- **AND** it SHALL NOT invite the user to discard the audio as though nothing was said

#### Scenario: Deciding whether to discard

- **WHEN** the user reads a failure notification
- **THEN** the recording's duration SHALL be visible on the notification itself
