# Voice Vault

Hold the power button, speak, pocket the phone. A moment later a Markdown note
appears in your Obsidian vault. No screen, no typing, fully offline.

Voice Vault is an eyes-free voice-capture app for the moment an idea shows up and
reaching for a keyboard would lose it. Trigger it, talk, put the phone away. That
is the whole interaction.

## How it works

1. Hold the power button to launch Voice Vault as your device assistant, or tap the
   Quick Note home-screen widget.
2. Speak. A short buzz confirms recording started.
3. Turn the screen off (pocket the phone) or tap once to stop. Silence also stops it
   after a few seconds.
4. The audio is saved to disk first, then transcribed on-device, then written as a
   Markdown note into your vault folder.

Feedback is haptic (distinct patterns for start, stop, and needs-attention) plus a
status notification. There is no interface to learn and nothing to configure.

## Privacy

Voice Vault collects nothing. No account, no analytics, no advertising, no telemetry.
Your audio is transcribed on the device and never leaves it, and your notes are
written straight into a folder you choose.

The app touches the network exactly once: on first launch it downloads the ~57 MB
speech-recognition model (`ggml-base.en-q5_1.bin`) from Hugging Face so it does not
have to ship in the APK. That request downloads a file and sends none of your data.
After that, capture and transcription are fully offline.

## Requirements and scope

- Android 13 (API 33) or newer.
- `arm64-v8a` devices. Built and tested on a Pixel 6 Pro; other arm64 devices are
  unverified.
- English speech only (an English Whisper model is used).
- About 60 MB of free space for the model.
- Permissions: microphone (to record), notifications (to report status), internet
  (one-time model download only).

## Install

The app is distributed as a signed APK on the [Releases](../../releases) page. Two
ways to install it:

### Obtainium (recommended, auto-updates)

[Obtainium](https://github.com/ImranR98/Obtainium) installs and auto-updates apps
directly from GitHub Releases. Add this repository's URL as an app source and
Obtainium will keep Voice Vault up to date for you.

### Manual sideload

Download the latest `voice-vault-vX.Y.Z.apk` from
[Releases](../../releases) and open it on the device. You will need to allow
installing from unknown sources when prompted.

## Verifying the build

This is a privacy app, so you should be able to check it rather than trust it:

- **Watch what it does.** Run it behind a network monitor (PCAPdroid, NetGuard, a
  Pi-hole) and confirm it makes no network calls except the one-time model download.
- **Check the artifact.** Each release ships a `.sha256` alongside the APK. Verify it
  with `sha256sum -c voice-vault-vX.Y.Z.apk.sha256`.
- **Read the source.** Every release is built from the tagged commit by the GitHub
  Actions workflow in `.github/workflows/release.yml`.

## Build from source

```bash
./gradlew assembleDebug          # debug APK
./gradlew installDebug           # build and install on a connected device
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (needs a device)
./gradlew lint                   # Android lint
```
