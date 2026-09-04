## MODIFIED Requirements

### Requirement: Storage access is verified before capture

The app SHALL verify it holds a valid, resolvable SAF permission for the selected vault folder at the start of a capture and SHALL raise the failure signal if the vault is not writable, rather than recording into a path that cannot be persisted.

#### Scenario: Permission was revoked

- **WHEN** a capture is triggered and the persisted SAF permission for the vault folder has been revoked
- **THEN** the failure signal SHALL be raised
- **AND** recording SHALL NOT proceed silently

#### Scenario: Vault folder missing

- **WHEN** the configured vault folder no longer resolves from its persisted URI (moved, deleted, or renamed)
- **THEN** the failure signal SHALL be raised
