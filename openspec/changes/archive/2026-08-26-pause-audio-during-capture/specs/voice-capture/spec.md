## ADDED Requirements

### Requirement: Other apps' audio is paused for the duration of a recording

A capture SHALL request exclusive, transient audio focus before the microphone is started, so that media playing on the device is paused rather than recorded through the microphone. The focus SHALL be transient, so that the playing app is asked to resume when the capture ends, and exclusive, so that the system does not offer the playing app the option to duck instead of pausing — ducked playback is still loud enough at the microphone to hold the input above the silence threshold.

#### Scenario: Music is playing when a capture is triggered

- **WHEN** the user triggers a capture while another app is playing audio
- **THEN** the app SHALL request exclusive transient audio focus before audio capture begins
- **AND** the other app's playback SHALL be paused for the duration of the recording

#### Scenario: Silence stop is reachable with music playing

- **WHEN** a capture is triggered while music is playing and the user stops speaking
- **THEN** recording SHALL end on the silence threshold rather than running to the 3-minute cap

#### Scenario: Nothing is playing

- **WHEN** a capture is triggered while no other app is playing audio
- **THEN** the capture SHALL proceed exactly as it does today, with no added delay before the start haptic

### Requirement: Audio focus is released before transcription

Audio focus SHALL be abandoned as soon as the recording loop ends, before transcription is attempted, so that the user's playback resumes at the end of the recording rather than at the end of the transcription. Focus SHALL be abandoned on every exit path from a capture, including the path where the microphone could not be acquired at all.

#### Scenario: Recording ends normally

- **WHEN** recording ends by any stop condition
- **THEN** audio focus SHALL be abandoned before the captured audio is encoded, persisted, or transcribed

#### Scenario: Playback resumes while transcription is still running

- **WHEN** a recording ends and its transcription takes appreciable time to complete
- **THEN** the other app SHALL be free to resume playback while transcription is still in progress

#### Scenario: Microphone could not be acquired

- **WHEN** starting audio capture fails and the capture aborts before recording any audio
- **THEN** audio focus SHALL still be abandoned

### Requirement: Audio focus is held until the last overlapping capture ends

Audio focus SHALL be reference-counted across concurrent captures, so that focus is acquired when the first recording starts and abandoned only when the last recording ends. A capture that finishes SHALL NOT release focus while another recording is still in progress.

#### Scenario: Second capture starts while the first is still recording

- **WHEN** a capture is triggered while another recording is still in progress
- **THEN** audio focus SHALL remain held continuously, without the other app being told it may resume in between

#### Scenario: First capture ends before the second

- **WHEN** the earlier of two overlapping recordings ends
- **THEN** audio focus SHALL NOT be abandoned
- **AND** it SHALL be abandoned when the later recording ends

### Requirement: Losing audio focus stops the recording

Losing audio focus while recording is in progress SHALL end the recording as a stop, not a cancel. The audio captured up to that moment SHALL be persisted to the pending folder and transcribed exactly as under any other stop condition.

#### Scenario: Incoming call during a recording

- **WHEN** audio focus is lost while recording is in progress
- **THEN** recording SHALL end
- **AND** the stop haptic SHALL be emitted
- **AND** the audio captured before the loss SHALL be written to the pending folder and transcribed

### Requirement: A refused focus request never blocks a capture

If audio focus cannot be acquired, the capture SHALL proceed anyway. Recording the user's speech takes precedence over recording it cleanly, and a capture SHALL NOT be refused, delayed, or degraded because another app holds focus. The app SHALL NOT opt in to delayed focus gain, which would postpone a capture that must start immediately.

#### Scenario: Focus request is denied

- **WHEN** a capture is triggered and the audio focus request is refused
- **THEN** recording SHALL start normally with its usual start haptic
- **AND** the resulting note SHALL be produced as it would have been without the focus request

#### Scenario: No silent failure

- **WHEN** an audio focus request is refused
- **THEN** the failure haptic SHALL NOT be emitted
- **AND** no failure notification SHALL be posted
