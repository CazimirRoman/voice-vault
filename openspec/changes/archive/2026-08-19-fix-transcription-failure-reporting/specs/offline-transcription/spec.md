## MODIFIED Requirements

### Requirement: Transcription failure does not lose the capture

If transcription fails for any reason, the recorded audio SHALL remain on disk and the capture SHALL be reported as needing attention. The report SHALL identify which class of failure occurred rather than reporting an undifferentiated failure.

#### Scenario: Model fails to load

- **WHEN** the model cannot be loaded
- **THEN** the recorded audio file SHALL remain in the pending location
- **AND** the failure signal SHALL be raised
- **AND** the reported outcome SHALL identify the model as unavailable

#### Scenario: Transcription returns empty text

- **WHEN** transcription completes but produces no text, or only whitespace
- **THEN** no empty note SHALL be written
- **AND** the recorded audio SHALL remain in the pending location
- **AND** the failure signal SHALL be raised
- **AND** the reported outcome SHALL identify that no speech was detected

#### Scenario: Transcription returns only a non-speech placeholder

- **WHEN** transcription completes and returns only a bracketed non-speech placeholder
- **THEN** no note SHALL be written
- **AND** the reported outcome SHALL identify that no speech was detected, distinct from an error

#### Scenario: The recognizer or the note write throws

- **WHEN** the recognizer throws, or the transcript cannot be written to the vault
- **THEN** the recorded audio SHALL remain in the pending location
- **AND** the reported outcome SHALL identify which of the two stages failed, and SHALL carry the underlying cause

#### Scenario: Process is killed mid-transcription

- **WHEN** the app process is terminated while transcription is in progress
- **THEN** the recorded audio SHALL remain in the pending location for later retry

## ADDED Requirements

### Requirement: Audio that is gone before its turn is not a failure

The transcription queue serializes work behind a single loaded model, so an entry can wait an arbitrary time before it runs. A queued entry whose audio file no longer exists when its turn arrives SHALL be treated as already handled and SHALL NOT be reported as a failure. Existence SHALL be verified after the queue lock is acquired, not only when the entry was enqueued.

#### Scenario: The file was transcribed by another queue entry while waiting

- **WHEN** a queued entry acquires the transcription lock and its audio file has been deleted by the entry that transcribed it
- **THEN** the outcome SHALL be reported as already handled
- **AND** no failure SHALL be raised for that capture

#### Scenario: The user discarded the audio while it was queued

- **WHEN** the user discards pending audio and a queued entry for that same audio subsequently acquires the lock
- **THEN** no failure SHALL be raised for that capture

#### Scenario: The recognizer is never invoked on a missing path

- **WHEN** a queued entry's audio file does not exist at the moment it would be transcribed
- **THEN** the recognizer SHALL NOT be invoked with that path

### Requirement: The same capture is not transcribed concurrently

The queue SHALL NOT hold more than one outstanding entry for the same audio file, so that a retry sweep cannot enqueue work that is already in flight.

#### Scenario: A sweep overlaps an in-flight transcription

- **WHEN** a retry sweep enumerates pending audio while one of those files is already being transcribed
- **THEN** that file SHALL NOT be enqueued a second time
