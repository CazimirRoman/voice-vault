## Purpose

Provide on-device speech-to-text via bundled whisper.cpp, including model provisioning on first run and behavior when transcription fails.

## Requirements

### Requirement: Transcription is fully offline

Speech-to-text SHALL be performed entirely on device using a bundled whisper.cpp model. The app SHALL NOT declare the `INTERNET` permission.

#### Scenario: Capture with no network

- **WHEN** the device is in airplane mode with no network of any kind
- **THEN** a capture SHALL transcribe and produce a note normally

#### Scenario: No network permission

- **WHEN** the app manifest is inspected
- **THEN** it SHALL NOT contain the `android.permission.INTERNET` permission

### Requirement: Model is bundled and provisioned on first run

A quantized `base.en` GGML model SHALL ship in the APK assets and SHALL be extracted to internal storage on first use, because whisper requires a filesystem path.

#### Scenario: First capture after install

- **WHEN** a capture is transcribed for the first time after installation
- **THEN** the model SHALL be extracted from assets to internal storage
- **AND** transcription SHALL proceed using the extracted file

#### Scenario: Subsequent captures reuse the extracted model

- **WHEN** a capture is transcribed and the model has already been extracted
- **THEN** the extraction SHALL be skipped

#### Scenario: Extraction is interrupted

- **WHEN** model extraction fails or is interrupted partway
- **THEN** the partial file SHALL NOT be treated as valid
- **AND** extraction SHALL be reattempted on the next capture

### Requirement: Model loading overlaps recording

Model loading SHALL begin concurrently with recording rather than blocking it, so that load time is absorbed by the time the user spends speaking.

#### Scenario: Load during speech

- **WHEN** a capture session starts
- **THEN** model loading SHALL be initiated without delaying the start of audio capture or the start haptic

### Requirement: Transcription failure does not lose the capture

If transcription fails for any reason, the recorded audio SHALL remain on disk and the capture SHALL be reported as needing attention.

#### Scenario: Model fails to load

- **WHEN** the model cannot be loaded
- **THEN** the recorded audio file SHALL remain in the pending location
- **AND** the failure signal SHALL be raised

#### Scenario: Transcription returns empty text

- **WHEN** transcription completes but produces no text, or only whitespace
- **THEN** no empty note SHALL be written
- **AND** the recorded audio SHALL remain in the pending location
- **AND** the failure signal SHALL be raised

#### Scenario: Process is killed mid-transcription

- **WHEN** the app process is terminated while transcription is in progress
- **THEN** the recorded audio SHALL remain in the pending location for later retry

### Requirement: Native resources are released

Loaded model resources SHALL be released once the transcription queue is empty, so that a long-lived service does not retain native memory indefinitely.

#### Scenario: Queue drains

- **WHEN** the last pending transcription completes and no further captures are queued
- **THEN** the loaded model SHALL be released
