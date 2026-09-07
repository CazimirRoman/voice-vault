## ADDED Requirements

### Requirement: Home screen widget launches a text-entry surface
The system SHALL provide a home-screen widget that, when tapped, launches a text-entry activity with an auto-focused, empty, plain-text input field and no title field, save button, or confirmation control of any kind.

#### Scenario: Tapping the widget opens a ready-to-type field
- **WHEN** the user taps the home-screen quick-note widget
- **THEN** a text-entry surface appears over the current screen with an empty text field already focused and the keyboard shown, and with no save/confirm button visible anywhere on the surface

### Requirement: Dismissal saves non-blank text as a new inbox note
The system SHALL save the current text as a new note in the vault's hardcoded inbox folder the moment the text-entry surface is dismissed by any normal means — screen turned off via the power button, home pressed, another app brought to the foreground via the recents/overview mechanism, back pressed, or the dialog otherwise dismissed (e.g. tapping outside it, if the surface's theme permits that) — without requiring any explicit confirmation action, provided the text is not blank or whitespace-only.

#### Scenario: Power button dismissal saves the note
- **WHEN** the user has typed non-blank text into the quick-note field and then presses the power button to turn off the screen
- **THEN** a new Markdown note is written into the inbox folder containing the typed text as its body, using the same frontmatter schema (Created/Priority/Area/Action) as voice-captured notes

#### Scenario: Home press dismissal saves the note
- **WHEN** the user has typed non-blank text into the quick-note field and then presses the home button
- **THEN** a new Markdown note is written into the inbox folder containing the typed text as its body

#### Scenario: Back press dismissal saves the note
- **WHEN** the user has typed non-blank text into the quick-note field and then presses back
- **THEN** a new Markdown note is written into the inbox folder containing the typed text as its body

#### Scenario: Switching to another app via recents saves the note
- **WHEN** the user has typed non-blank text into the quick-note field and then switches to a different app via the recents/overview screen (or any other app-switch mechanism that brings another app to the foreground)
- **THEN** a new Markdown note is written into the inbox folder containing the typed text as its body

#### Scenario: Dismissing the surface directly saves the note
- **WHEN** the user has typed non-blank text into the quick-note field and dismisses the surface itself (e.g. tapping outside its bounds, if the theme allows the surface to be dismissed that way) rather than using back/home/recents/power
- **THEN** a new Markdown note is written into the inbox folder containing the typed text as its body

#### Scenario: Blank text is not saved
- **WHEN** the quick-note field is dismissed by any means while empty or containing only whitespace
- **THEN** no note is written to the vault

### Requirement: The five most recently captured notes are shown as read-only recall
The system SHALL display, above the text-entry field, the five most recently captured notes from the vault's inbox folder (voice-captured or quick-text alike), each shown with its capture id and a short body preview with frontmatter stripped, and SHALL NOT allow editing, deleting, or navigating into any of them from this surface.

#### Scenario: Recent notes appear above the input
- **WHEN** the quick-note text-entry surface is opened
- **THEN** up to five most-recently-captured notes from the inbox folder are displayed above the text field, each showing its capture id and a preview of its body text with frontmatter removed

#### Scenario: Fewer than five notes exist
- **WHEN** the inbox folder contains fewer than five notes (including zero)
- **THEN** only the existing notes are shown, with no placeholder entries for the missing ones

#### Scenario: Tapping a recent note does nothing
- **WHEN** the user taps on one of the displayed recent notes
- **THEN** no navigation, editing, or deletion occurs, and the quick-note surface remains open

### Requirement: Successful save is confirmed with the existing capture-ended haptic
The system SHALL fire the same haptic pattern already used to signal a completed voice capture whenever a quick note is successfully saved, and SHALL NOT fire any haptic when no note is written because the text was blank.

#### Scenario: Haptic fires on save
- **WHEN** a quick note is successfully written to the vault following dismissal of the text-entry surface
- **THEN** the device vibrates using the same pattern already used to signal the end of a successful voice capture

#### Scenario: No haptic on blank dismissal
- **WHEN** the text-entry surface is dismissed with blank/whitespace-only text
- **THEN** no haptic feedback is triggered

### Requirement: Typed text is saved at most once
The system SHALL write a given piece of typed text to the vault at most once, regardless of how many times the text-entry surface is stopped and resumed. After a successful save the system SHALL clear the input field, so that a subsequent dismissal of the same surface has no text to write.

#### Scenario: Waking the phone does not re-save an already-saved note
- **WHEN** the user types non-blank text, presses the power button (which saves the note), then turns the screen back on and unlocks the device
- **THEN** no second note containing that text is written to the vault, and no second haptic fires

#### Scenario: The field is empty after a save
- **WHEN** the text-entry surface is still visible after its text has been saved
- **THEN** the input field is empty rather than still showing the saved text

### Requirement: The text-entry surface does not outlive a dismissal
The system SHALL finish the text-entry activity once it has been dismissed by any normal means, whether or not a note was written, so that the surface is never resumed with the state of a previous quick note.

#### Scenario: Screen-off dismissal disposes of the surface
- **WHEN** the user dismisses the text-entry surface by pressing the power button to turn off the screen
- **THEN** the surface is finished, and turning the screen back on returns the user to whatever was on screen before the widget was tapped rather than to the quick-note surface

#### Scenario: Blank dismissal also disposes of the surface
- **WHEN** the text-entry surface is dismissed while empty or containing only whitespace
- **THEN** the surface is finished without writing a note

### Requirement: The display stays awake while the text-entry surface is showing
The system SHALL keep the display awake for as long as the text-entry surface is showing, so that the screen turning off is always a deliberate power press and never an idle display timeout.

#### Scenario: A thinking pause does not commit a partial note
- **WHEN** the user has typed partial text into the quick-note field and then stops interacting with the device for longer than the system display timeout
- **THEN** the display remains on, the surface remains open with the partial text intact, and no note is written
