## ADDED Requirements

### Requirement: The listening overlay is never load-bearing

The overlay shown during recording MAY carry status information, but SHALL remain optional to the capture flow. Nothing it displays, fails to display, or fails to compute may delay, block, or alter a recording, and no capture path may wait on it.

#### Scenario: Overlay fails to start

- **WHEN** the overlay activity cannot be started or is dismissed by the system
- **THEN** recording SHALL start, run, and stop exactly as it otherwise would
- **AND** the start, stop, and at-risk haptics SHALL be unaffected

#### Scenario: Overlay content cannot be computed

- **WHEN** any status the overlay would display cannot be determined
- **THEN** the overlay SHALL still present the recording indicator and the stop instruction
- **AND** the capture SHALL proceed unchanged

#### Scenario: User never looks at the overlay

- **WHEN** the user completes a capture with the phone pocketed and never sees the screen
- **THEN** the capture SHALL complete normally, and no information carried by the overlay SHALL have been required
