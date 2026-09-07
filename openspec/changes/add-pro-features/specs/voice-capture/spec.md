## MODIFIED Requirements

### Requirement: Recording stops after sustained silence

Recording SHALL end automatically after the configured silence duration of continuous silence, measured by a root-mean-square amplitude threshold over successive audio buffers. Both the duration and the threshold SHALL be read from settings at capture start, defaulting to approximately 5 seconds and the `Config` threshold.

#### Scenario: User finishes speaking

- **WHEN** the user stops speaking and the input remains below the amplitude threshold for the configured duration
- **THEN** recording SHALL end

#### Scenario: Natural pause mid-thought

- **WHEN** the user pauses for less than the configured silence duration and resumes speaking
- **THEN** recording SHALL NOT end
- **AND** the speech following the pause SHALL be present in the recording

#### Scenario: No silence setting has been stored

- **WHEN** a capture starts on an install where neither value has been configured
- **THEN** recording SHALL end after approximately 5 seconds of silence, as before settings existed

### Requirement: Recording is bounded by a hard duration cap

Recording SHALL end unconditionally after the configured maximum duration, so that a stuck or noisy session cannot record indefinitely. The cap SHALL be read from settings at capture start, defaulting to 3 minutes, and SHALL always remain bounded.

#### Scenario: Continuous noise prevents silence detection

- **WHEN** the ambient environment keeps the input above the silence threshold for the configured maximum duration
- **THEN** recording SHALL end
- **AND** the audio captured up to that point SHALL be persisted

#### Scenario: No cap setting has been stored

- **WHEN** a capture starts on an install where the cap has not been configured
- **THEN** recording SHALL end unconditionally after 3 minutes

#### Scenario: Cap cannot be disabled

- **WHEN** any value is configured for the maximum duration
- **THEN** a finite cap SHALL still apply to every capture
