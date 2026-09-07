## Purpose

Obtain and keep access to the Obsidian vault's inbox folder through the system folder picker (SAF), so the app never assumes a fixed filesystem path and never needs all-files access. Covers selecting the folder, persisting the grant across restarts and reboots, and detecting and recovering from a grant that no longer resolves.

## Requirements

### Requirement: User selects the vault folder via system picker

The app SHALL obtain access to the Obsidian vault's inbox folder by launching the system's `ACTION_OPEN_DOCUMENT_TREE` folder picker rather than assuming a fixed filesystem path. The app SHALL NOT declare or request `MANAGE_EXTERNAL_STORAGE`.

#### Scenario: First launch

- **WHEN** the app is launched and no vault folder has been selected yet
- **THEN** the setup screen SHALL show the vault-folder step as not yet done, with a control that launches the system folder picker
- **AND** the app SHALL NOT open the picker automatically on launch

#### Scenario: Launched from the "vault not accessible" notification

- **WHEN** the app is opened via the recovery action on a vault-access failure notification and no valid grant is held
- **THEN** the app SHALL open the system folder picker directly

#### Scenario: Folder already selected

- **WHEN** the app is launched and a vault folder was already selected in a previous session
- **THEN** the app SHALL NOT prompt or open the folder picker
- **AND** the setup screen SHALL show the vault-folder step as done

#### Scenario: Selected folder is shown for confirmation

- **WHEN** a valid vault folder grant is held
- **THEN** the setup screen SHALL display the selected folder's path relative to its storage volume, so the user can spot a wrong pick without opening Obsidian
- **AND** IF that folder directly contains a `.obsidian` directory (the user picked the whole vault rather than a subfolder inside it) the setup screen SHALL note that notes will be written into the folder itself

### Requirement: The folder grant is persisted across restarts

Once the user selects a folder, the app SHALL take a persistable URI permission and store a reference to that folder, so the grant survives app restarts and device reboots without the user re-selecting it.

#### Scenario: App restarts after selection

- **WHEN** the app is relaunched after the user has already selected a vault folder
- **THEN** the previously granted folder SHALL remain usable for reading and writing without a new picker prompt

#### Scenario: Device reboots

- **WHEN** the device reboots after a vault folder has been selected
- **THEN** the persisted permission SHALL still resolve to the same folder

### Requirement: Invalid or revoked grants are detected and recoverable

The app SHALL detect when the persisted folder grant no longer resolves to a writable folder — because the folder was moved, deleted, or renamed, the owning app was uninstalled, or the permission was revoked in system settings — and SHALL let the user re-select a folder without losing audio already captured.

#### Scenario: Grant revoked in system settings

- **WHEN** the user revokes the app's persisted URI permission in Android Settings
- **THEN** the next capture attempt SHALL detect the folder is no longer accessible
- **AND** the app SHALL surface this through the existing failure-notification path rather than failing silently

#### Scenario: Folder deleted or moved

- **WHEN** the previously selected folder can no longer be resolved from its persisted URI
- **THEN** the app SHALL treat this the same as a revoked grant

#### Scenario: Re-selecting after loss

- **WHEN** the user re-selects a vault folder after a detected grant loss
- **THEN** any audio already captured and pending SHALL remain intact and become writable again once the new folder is confirmed
