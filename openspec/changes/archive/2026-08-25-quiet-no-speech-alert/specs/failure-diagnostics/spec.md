## MODIFIED Requirements

### Requirement: A failure alert identifies the capture well enough to act on

A failure notification SHALL carry enough information for the user to decide whether to keep or discard the recording without opening a file manager. It SHALL state the classified reason for the failure, the duration of the recording, and the capture's timestamp, and where the recognizer produced text that was rejected as non-speech, it SHALL state what the recognizer heard.

The timestamp SHALL be the same identifier the capture's pending audio and diagnostic record are named by, so that the shade entry, the audio file, and the diagnostic record can be matched to each other by eye.

A no-speech alert SHALL be distinguishable from a capture at risk on the face of the notification, without the user opening or expanding it.

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

#### Scenario: Telling two failed captures apart

- **WHEN** two captures have outstanding failure notifications in the shade
- **THEN** each notification SHALL show the timestamp of the recording it concerns
- **AND** that timestamp SHALL match the name of that capture's file in the pending folder

#### Scenario: Reading the shade at a glance

- **WHEN** the user looks at the notification shade without opening any entry
- **THEN** a no-speech alert SHALL be identifiable as such from its collapsed form
- **AND** it SHALL NOT be presented in the same terms as a capture whose audio is at risk
