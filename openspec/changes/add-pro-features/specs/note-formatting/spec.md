## ADDED Requirements

### Requirement: Note filename comes from a configured pattern

A note's filename SHALL be produced by expanding a stored filename pattern. The pattern SHALL support a closed set of placeholders covering the capture timestamp's parts, the capture id, and the entry point that produced the note. The default pattern SHALL reproduce today's `<captureId>.md` name exactly.

#### Scenario: Default pattern

- **WHEN** no filename pattern has been stored
- **THEN** the filename SHALL be the capture id followed by `.md`

#### Scenario: Custom pattern

- **WHEN** a filename pattern containing supported placeholders is stored
- **THEN** the written note's name SHALL be that pattern with each placeholder replaced by its value for this capture

#### Scenario: Unknown placeholder

- **WHEN** a stored pattern contains a placeholder that is not in the supported set
- **THEN** the placeholder SHALL be left as literal text rather than causing the write to fail

#### Scenario: Pattern produces an unusable name

- **WHEN** an expanded pattern is empty, or contains characters the storage layer cannot accept
- **THEN** the note SHALL be written under the default pattern instead
- **AND** the note SHALL NOT be lost

#### Scenario: Expanded name collides with an existing note

- **WHEN** an expanded filename matches a note that already exists
- **THEN** the written name SHALL be made unique
- **AND** the existing note SHALL NOT be overwritten

### Requirement: Frontmatter comes from a configured template

A note's frontmatter SHALL be produced by expanding a stored template using the same placeholder set. The default template SHALL reproduce today's `Created`, `Priority`, `Area`, and `Action` block exactly.

#### Scenario: Default template

- **WHEN** no frontmatter template has been stored
- **THEN** the written note SHALL carry today's frontmatter block, byte for byte

#### Scenario: Empty template

- **WHEN** the frontmatter template is stored as empty
- **THEN** the note SHALL contain the transcript with no frontmatter block

#### Scenario: Transcript body is never templated

- **WHEN** any template is applied
- **THEN** the transcript text SHALL be written unmodified, apart from a stripped routing trigger word

### Requirement: Daily-note append is an alternative to one note per capture

When daily-note append is enabled, a capture's text SHALL be appended to a note named for the capture's date, creating that note if it does not exist. When it is disabled, each capture SHALL produce its own note as today.

#### Scenario: Daily note does not exist yet

- **WHEN** the first capture of the day completes with append enabled
- **THEN** a note for that date SHALL be created containing the configured frontmatter and the transcript

#### Scenario: Daily note already exists

- **WHEN** a later capture on the same date completes
- **THEN** its transcript SHALL be added after the existing content
- **AND** the existing content SHALL be unchanged

#### Scenario: Concurrent appends

- **WHEN** two captures complete close enough together to append to the same note
- **THEN** both transcripts SHALL be present in the note
- **AND** neither SHALL overwrite the other

#### Scenario: Append cannot be completed safely

- **WHEN** the existing daily note cannot be read, or changed between being read and being written
- **THEN** the capture SHALL be written as its own separate note instead
- **AND** the capture SHALL NOT be lost

#### Scenario: Atomicity is preserved

- **WHEN** a daily note is appended to
- **THEN** the resulting file SHALL be written to a temporary path and renamed into place, as every other vault write is

### Requirement: Editing templates requires Pro, applying them does not

Template editing SHALL be permitted only on an entitled install. Stored templates SHALL be applied by the vault writer regardless of entitlement, and no code on the vault write path SHALL consult the entitlement.

#### Scenario: Entitlement lapses with a custom template stored

- **WHEN** an entitlement is withdrawn on an install with a custom filename pattern and frontmatter template
- **THEN** subsequent notes SHALL still use those stored values

#### Scenario: Free user views templates

- **WHEN** a user without Pro opens the template settings
- **THEN** the current pattern and template SHALL be visible and read-only
