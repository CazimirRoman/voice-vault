# Google Play listing

Everything the Play Console asks for, kept here so it is not lost. Graphics sit next to
this file: `icon-512.png` (512x512, square, no transparency; Play applies its own mask)
and `feature-graphic.png` (1024x500). Phone screenshots: `../docs/screenshot-setup.png`
and `../docs/screenshot-listening.png`.

Privacy policy URL (GitHub Pages, served from `/docs` on `main`):
https://cazimirroman.github.io/voice-vault/privacy-policy

## Store listing

**App name** (30 max)

```
Voice Vault
```

**Short description** (80 max)

```
Hold power, speak, pocket. Offline voice notes straight into your Obsidian vault.
```

**Full description** (4000 max)

```
Voice Vault is for the moment an idea shows up and reaching for a keyboard would lose it. Hold the power button, say what you want to remember, put the phone back in your pocket. A moment later a Markdown note appears in your Obsidian vault.

HOW IT WORKS
1. Hold the power button (with Voice Vault set as your digital assistant), or tap the Quick Note widget.
2. Speak. A short buzz tells you it is recording.
3. Turn the screen off or pocket the phone to stop. Silence stops it too.
4. The note is transcribed on your phone and saved as a plain .md file in the folder you picked.

You never have to look at the screen. Haptic patterns tell you when recording starts, when it stops, and when something needs your attention.

FULLY OFFLINE AND PRIVATE
Transcription runs on your device using an open-source Whisper model. Your audio and text never leave the phone. There is no account, no analytics, no ads and no tracking. The only network request is a one-time download of the speech model (about 57 MB) on first launch.

BUILT NOT TO LOSE A THOUGHT
The recording is saved before transcription starts, so a crash or a dead battery cannot lose it. Voice Vault retries on its own and tells you if a note could not be saved. Notes are written in a way that is safe for folders synced by Obsidian Sync, Syncthing and similar tools.

QUICK NOTE WIDGET
Would rather type? The home-screen widget opens a small text box. Type, turn off the screen, and the note is saved. You can also share text from any app into Voice Vault.

WORKS WITH ANY MARKDOWN FOLDER
Obsidian is the obvious fit, but any folder of Markdown files works: Logseq, Foam, or just a synced directory.

GOOD TO KNOW
- English speech only.
- The power-button trigger depends on your phone letting the assistant take the long press. It works on Pixel phones. Some other brands keep that button for their own menu; the Quick Note widget works everywhere.
- Needs about 60 MB for the speech model and a 64-bit (arm64) phone running Android 10 or newer.

Voice Vault is open source (GPL-3.0): github.com/CazimirRoman/voice-vault
```

**Category:** Productivity. **Tags:** note taking, voice recorder.

**Contact email:** cazimir.developer@gmail.com

## App content

**Privacy policy:** the URL above.

**Ads:** No.

**App access:** All functionality is available without special access (no login).

**Data safety:**
- Does the app collect or share any required user data types? **No.**
- Audio is processed on-device only and never transmitted, so under Play's definitions
  it is not collected.
- The one outbound request (model download over HTTPS) sends no user data.
- Data deletion: no account exists; uninstalling removes app data.

**Content rating (IARC questionnaire):** category Utility, Productivity, Communication or
Other. Answer No to every content question. Expected result: Everyone / PEGI 3.

**Target audience:** 18 and over (or 13+ brackets only). Not designed for children.

**News app:** No. **Government app:** No. **Financial features:** None. **Health:** No.

**Foreground service permission** (`FOREGROUND_SERVICE_MICROPHONE`):
- Task type: user-initiated microphone recording.
- Description: "Voice Vault records a voice note the user starts by holding the power
  button or tapping its widget. Recording runs in a microphone foreground service so it
  continues after the user turns off the screen and pockets the phone, which is how the
  user signals they are done. The service runs only for the duration of that capture."
- Video: a short screen recording of hold power, speak, screen off, note appears in
  the folder. Upload it unlisted to YouTube and paste the link.

## App signing

Play App Signing uses the existing `voicevault` key (alias `voicevault` in
`cazimir_dev_key.jks`), so GitHub and Play builds share a signature and can update each
other. In the Console choose "Use a different key / export and upload a key from Java
keystore" and follow the PEPK instructions it shows. Do this before the first upload:
once Play generates its own key, it cannot be swapped for this one.

## Release checklist

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Tag and push (`git tag vX.Y.Z && git push origin vX.Y.Z`). The workflow attaches
   the APK, its `.sha256`, and the `.aab`.
3. Upload the `.aab` from the GitHub Release to the Play Console.
