## Purpose

Own the recording session lifecycle — microphone acquisition, the haptic signal vocabulary, the four stop conditions, and queueing of overlapping captures.

## Requirements

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

### Requirement: Audio is captured in whisper's native format

Audio SHALL be captured as 16 kHz mono PCM 16-bit so that no resampling stage is required before transcription.

#### Scenario: Capture format

- **WHEN** a recording starts
- **THEN** the audio source SHALL be configured at 16 kHz, mono, PCM 16-bit

### Requirement: Start haptic fires only once the microphone is live

The app SHALL emit a double-tick vibration to signal that recording has begun, and SHALL emit it only after audio capture is actually active.

#### Scenario: No clipped first word

- **WHEN** a capture session is triggered
- **THEN** the double-tick vibration SHALL be emitted after the audio source has started capturing
- **AND** audio spoken immediately following the vibration SHALL be present in the recording

#### Scenario: Microphone unavailable

- **WHEN** a capture session is triggered but the microphone cannot be acquired
- **THEN** the start vibration SHALL NOT be emitted
- **AND** the failure signal SHALL be emitted instead

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

### Requirement: Recording stops after sustained silence

Recording SHALL end automatically after approximately 5 seconds of continuous silence, measured by a root-mean-square amplitude threshold over successive audio buffers.

#### Scenario: User finishes speaking

- **WHEN** the user stops speaking and the input remains below the amplitude threshold for the configured duration
- **THEN** recording SHALL end

#### Scenario: Natural pause mid-thought

- **WHEN** the user pauses for less than the configured silence duration and resumes speaking
- **THEN** recording SHALL NOT end
- **AND** the speech following the pause SHALL be present in the recording

### Requirement: Recording can be stopped by tapping

A screen tap SHALL end recording immediately, as an optional accelerator. No part of the capture flow may depend on this interaction.

#### Scenario: User taps to finish early

- **WHEN** the user taps the screen while recording
- **THEN** recording SHALL end immediately without waiting for the silence threshold

#### Scenario: User never looks at the screen

- **WHEN** the user completes a capture without ever touching the screen
- **THEN** the capture SHALL complete successfully via the silence stop condition

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

### Requirement: Recording is bounded by a hard duration cap

Recording SHALL end unconditionally after 3 minutes, so that a stuck or noisy session cannot record indefinitely.

#### Scenario: Continuous noise prevents silence detection

- **WHEN** the ambient environment keeps the input above the silence threshold for 3 minutes
- **THEN** recording SHALL end
- **AND** the audio captured up to that point SHALL be persisted

### Requirement: Overlapping captures are queued

Triggering a capture while a previous capture is still being transcribed SHALL start a new recording and enqueue its transcription behind the in-progress one. A capture SHALL NOT be refused because another is in flight.

#### Scenario: Second trigger during transcription

- **WHEN** the user triggers a capture while a previous recording is still transcribing
- **THEN** a new recording SHALL start normally with its usual haptic signals
- **AND** both recordings SHALL ultimately produce their own notes

#### Scenario: Queue drains in order

- **WHEN** multiple recordings are pending transcription
- **THEN** they SHALL be transcribed one at a time until the queue is empty

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
