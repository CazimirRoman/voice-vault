## ADDED Requirements

### Requirement: Capture health is derived from pending audio alone

The health of the capture pipeline SHALL be derived solely by enumerating the pending folder. Deriving it SHALL NOT require reading the contents of any note, any transcript, or any audio file. The derivation SHALL classify the pipeline into exactly one of: clear, in flight, needs attention, or unknown.

#### Scenario: No pending audio

- **WHEN** the pending folder contains no audio files
- **THEN** the health state SHALL be clear

#### Scenario: Audio awaiting transcription

- **WHEN** the pending folder contains audio with no diagnostic record beside it, and that audio is not stale
- **THEN** the health state SHALL be in flight

#### Scenario: Audio with a recorded failure

- **WHEN** the pending folder contains audio with a diagnostic record beside it
- **THEN** the health state SHALL be needs attention

#### Scenario: A discarded capture

- **WHEN** a capture's audio and diagnostic record have both been removed by the discard action
- **THEN** that capture SHALL NOT contribute to the health state

#### Scenario: An orphan diagnostic record

- **WHEN** the pending folder contains a diagnostic record with no corresponding audio file
- **THEN** that record alone SHALL NOT make the health state anything other than clear

### Requirement: Silently stuck audio reads as needing attention

Pending audio that has no diagnostic record beside it and has remained in the pending folder longer than a configured staleness threshold SHALL be classified as needing attention rather than in flight. The threshold SHALL be a single configuration constant.

#### Scenario: Capture wedged by process death

- **WHEN** audio was written to the pending folder but never enqueued for transcription, and the staleness threshold has elapsed
- **THEN** the health state SHALL be needs attention

#### Scenario: A long transcription still running

- **WHEN** audio is pending and being transcribed, and the staleness threshold has not yet elapsed
- **THEN** the health state SHALL be in flight

### Requirement: Needs attention outranks in flight

When captures in different states are pending simultaneously, the health state SHALL be the most severe of them. Needs attention SHALL take precedence over in flight, and in flight SHALL take precedence over clear.

#### Scenario: A failure alongside a healthy capture

- **WHEN** one pending capture has a diagnostic record and another is transcribing normally
- **THEN** the health state SHALL be needs attention

### Requirement: A failed read never reports clear

If the pending folder cannot be enumerated for any reason, the health state SHALL be unknown. The unknown state SHALL NOT be reported as clear, and SHALL NOT be reported as needs attention.

#### Scenario: Storage access unavailable

- **WHEN** enumerating the pending folder fails or is denied
- **THEN** the health state SHALL be unknown

#### Scenario: The vault folder does not exist

- **WHEN** the pending folder is absent from the filesystem entirely
- **THEN** the health state SHALL be clear, since no capture is unaccounted for

### Requirement: The listening overlay displays the health state as a single indicator

The overlay shown during recording SHALL display the health state as one small indicator positioned clear of the existing recording affordances. The indicator SHALL convey state through colour, opacity, and motion only.

#### Scenario: Everything has landed

- **WHEN** the health state is clear
- **THEN** the indicator SHALL be rendered in a subdued, motionless form distinguishable from the other states

#### Scenario: Something needs attention

- **WHEN** the health state is needs attention
- **THEN** the indicator SHALL be rendered at full prominence and SHALL be the only element on the overlay in motion besides the recording indicator

#### Scenario: The health state is unknown

- **WHEN** the health state is unknown
- **THEN** no indicator SHALL be drawn

#### Scenario: Existing overlay elements are untouched

- **WHEN** the indicator is displayed in any state
- **THEN** the recording indicator, the "Listening…" label, and the stop instruction SHALL retain their existing size, position, and behaviour

### Requirement: The indicator reveals no note content

The indicator SHALL NOT display transcript text, note titles, capture identifiers, timestamps, filenames, or counts. It SHALL be safe to display above the lock screen.

#### Scenario: Locked device

- **WHEN** the overlay is shown over the keyguard on a locked device
- **THEN** the indicator SHALL disclose nothing beyond the health state itself

### Requirement: Health reporting never interferes with recording

Reading and displaying the health state SHALL NOT delay the start of recording, block the main thread, or cause a capture to fail. The overlay SHALL render before the health state is known.

#### Scenario: Slow filesystem

- **WHEN** enumerating the pending folder is slow
- **THEN** the recording indicator and stop instruction SHALL already be on screen
- **AND** the recording SHALL start and signal its start haptic without waiting for the read

#### Scenario: The read throws

- **WHEN** deriving the health state throws
- **THEN** the recording SHALL continue unaffected
- **AND** the failure SHALL be logged

### Requirement: The displayed state stays current while the overlay is up

The health state SHALL be re-derived periodically for as long as the overlay is displayed, so that a capture resolving during the current recording is reflected without waiting for the next capture.

#### Scenario: A queued capture completes mid-recording

- **WHEN** a previously pending capture is transcribed into a note while the current recording is still running
- **THEN** the indicator SHALL update to reflect the new health state before the overlay closes

#### Scenario: The current capture is not counted

- **WHEN** the overlay is displayed during a recording
- **THEN** the health state SHALL reflect only captures made before the current one, whose audio has not yet been written
