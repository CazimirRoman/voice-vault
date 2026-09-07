## ADDED Requirements

### Requirement: Capture parameters are read from settings at capture time

The silence amplitude threshold, the silence duration, the hard recording duration cap, and the haptic intensity SHALL be read from stored settings when a capture starts. The corresponding `Config` constants SHALL remain in the code as the default value for each setting.

#### Scenario: No settings stored

- **WHEN** a capture starts on an install that has never stored a setting
- **THEN** every parameter SHALL take its `Config` default
- **AND** capture behaviour SHALL be identical to the behaviour before settings existed

#### Scenario: A setting has been changed

- **WHEN** a capture starts after the silence duration has been set to a different value
- **THEN** that capture SHALL use the stored value

#### Scenario: Settings change mid-capture

- **WHEN** a setting is changed while a capture is in progress
- **THEN** the in-progress capture SHALL continue with the values it started with

### Requirement: Every setting is clamped to a safe range

Each stored value SHALL be clamped to a documented range when read, so that a corrupt, out-of-range, or hostile stored value can never produce a capture that cannot stop or cannot start.

#### Scenario: Stored value is out of range

- **WHEN** a stored value falls outside its documented range
- **THEN** the value used SHALL be clamped to the nearest end of that range

#### Scenario: Stored value is missing or unparseable

- **WHEN** a stored value is absent or cannot be parsed
- **THEN** the default SHALL be used
- **AND** no error SHALL be surfaced to the user

#### Scenario: Duration cap cannot be removed

- **WHEN** the hard duration cap is configured
- **THEN** it SHALL remain bounded above, and no configuration SHALL permit an unbounded recording

### Requirement: Editing settings requires Pro, reading them does not

The settings screen SHALL permit changes only on an entitled install. Stored values SHALL be honoured by the capture path regardless of entitlement, and no code on the capture path SHALL consult the entitlement.

#### Scenario: Free user opens settings

- **WHEN** a user without Pro opens the settings screen
- **THEN** the current values SHALL be visible and read-only
- **AND** the screen SHALL offer the purchase surface

#### Scenario: Entitlement lapses after settings were changed

- **WHEN** an entitlement is withdrawn on an install with customised settings
- **THEN** captures SHALL continue to use the customised values
- **AND** no setting SHALL be reset or reverted

### Requirement: Settings can be reset to defaults

The settings screen SHALL offer a single action that restores every capture parameter to its `Config` default.

#### Scenario: User resets after a bad tuning

- **WHEN** the user triggers reset to defaults
- **THEN** every capture parameter SHALL return to its `Config` default
- **AND** the next capture SHALL behave as it did before any setting was changed
