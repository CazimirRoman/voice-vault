## ADDED Requirements

### Requirement: The app can hold more than one vault destination

The app SHALL be able to hold several vault folder grants at once. Each destination SHALL have a user-visible name, its own persisted SAF grant, and an optional set of trigger words. Exactly one destination SHALL be marked default at all times.

#### Scenario: Single existing grant is migrated

- **WHEN** the app starts on an install that holds one vault grant from before this capability existed
- **THEN** that grant SHALL become the default destination
- **AND** no re-selection SHALL be required from the user

#### Scenario: Adding a destination

- **WHEN** the user selects an additional folder
- **THEN** it SHALL be added as a destination without disturbing the existing ones

#### Scenario: Removing the default destination

- **WHEN** the destination currently marked default is removed and others remain
- **THEN** another destination SHALL be marked default immediately

#### Scenario: Removing the last destination

- **WHEN** the only destination is removed
- **THEN** the app SHALL report vault access as not configured, exactly as it does before any folder has been selected

### Requirement: A spoken trigger word routes a note to a destination

When a transcript's first word matches a destination's trigger word, that note SHALL be written to that destination, and the trigger word SHALL be removed from the note body.

#### Scenario: Transcript opens with a trigger word

- **WHEN** a transcript's first word matches a destination's trigger word, ignoring case and trailing punctuation
- **THEN** the note SHALL be written to that destination
- **AND** the note body SHALL begin at the word after the trigger

#### Scenario: Transcript does not open with a trigger word

- **WHEN** no destination's trigger word matches the first word
- **THEN** the note SHALL be written to the default destination
- **AND** the body SHALL be unmodified

#### Scenario: Trigger word appears later in the transcript

- **WHEN** a trigger word appears anywhere other than as the first word
- **THEN** it SHALL NOT route the note
- **AND** it SHALL NOT be removed from the body

#### Scenario: Two destinations claim the same trigger word

- **WHEN** a trigger word is configured on more than one destination
- **THEN** the note SHALL be written to the default destination
- **AND** no trigger word SHALL be stripped

#### Scenario: Trigger word is the entire transcript

- **WHEN** stripping the trigger word would leave an empty body
- **THEN** the note SHALL NOT be routed or stripped, and SHALL be written to the default destination with its original text

### Requirement: Routing can never prevent a note from being written

Every routing outcome SHALL end in a note being written somewhere. A destination that cannot be written to SHALL cause a fall back to the default destination, and a default that cannot be written to SHALL be handled by the existing at-risk reporting rather than by discarding the capture.

#### Scenario: Routed destination's grant is lost

- **WHEN** a note routes to a destination whose grant no longer resolves to a writable folder
- **THEN** the note SHALL be written to the default destination instead

#### Scenario: Default destination's grant is lost

- **WHEN** the default destination cannot be written to
- **THEN** the capture's audio SHALL remain in the pending folder for retry
- **AND** the existing at-risk alert SHALL be raised

#### Scenario: Routing logic fails unexpectedly

- **WHEN** any error occurs while deciding a destination
- **THEN** the default destination SHALL be used

### Requirement: Editing destinations requires Pro, routing by them does not

Adding, removing, renaming a destination, and editing trigger words SHALL be permitted only on an entitled install. Stored destinations and trigger words SHALL be used for routing regardless of entitlement, and no code on the capture or vault write path SHALL consult the entitlement.

#### Scenario: Entitlement lapses with several destinations configured

- **WHEN** an entitlement is withdrawn on an install holding several destinations
- **THEN** routing SHALL continue to work as configured
- **AND** no destination SHALL be removed or merged

#### Scenario: Free user opens destination settings

- **WHEN** a user without Pro opens the destination settings
- **THEN** the configured destinations SHALL be visible and read-only
- **AND** selecting a vault folder SHALL remain possible when no destination is configured at all
