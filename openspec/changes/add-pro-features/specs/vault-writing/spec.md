## MODIFIED Requirements

### Requirement: Transcript is written as a Markdown note

On successful transcription, a `.md` file containing the transcript SHALL be written into the routed destination folder, with its filename and frontmatter produced by expanding the configured patterns. With nothing configured, this is the single selected vault folder, today's filename, and today's frontmatter.

#### Scenario: Successful capture

- **WHEN** transcription produces non-empty text
- **THEN** a Markdown file SHALL be created in the routed destination folder containing that text

#### Scenario: Note is named by capture time

- **WHEN** a note is written with no filename pattern configured
- **THEN** its filename SHALL be derived from the capture timestamp and SHALL NOT collide with an existing note

#### Scenario: Note is named by a configured pattern

- **WHEN** a note is written with a filename pattern configured
- **THEN** its filename SHALL be the expanded pattern
- **AND** it SHALL NOT collide with an existing note

#### Scenario: Daily-note append is enabled

- **WHEN** a note is written with daily-note append enabled
- **THEN** the transcript SHALL be added to the routed destination's note for that date, which SHALL be created if absent
