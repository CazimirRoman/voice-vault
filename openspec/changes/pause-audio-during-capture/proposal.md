## Why

EasyNote never requests audio focus, so a capture triggered while music is playing records over the music instead of pausing it. This is not only an annoyance: the speaker output is picked up acoustically by the microphone, which holds the RMS above `Config.SILENCE_RMS_THRESHOLD` for the entire session. The silence stop condition is therefore dead whenever audio is playing, leaving only screen-off, tap, and the 3-minute hard cap — and whisper transcribes the song into the note alongside the user's voice.

## What Changes

- `RecordingService` requests `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` immediately before recording begins and abandons it as soon as the recording loop returns — before transcription, so the user's music resumes while whisper is still working rather than minutes later.
- The focus request is reference-counted across overlapping captures, so a capture that ends does not un-pause the music while another recording is still in flight.
- Focus is abandoned on every exit path from a capture, including the failure path where `AudioRecorder.record` throws before capture begins.
- Losing audio focus mid-recording (an incoming call, another assistant) ends the recording as a stop, not a cancel: the audio captured so far is persisted to `_pending/` and transcribed normally.
- Failing to *acquire* focus never blocks a capture — a noisy note is strictly better than no note.
- No new permissions, no new dependencies, no user-visible UI. `minSdk = 33` already covers `AudioFocusRequest` (API 26), so there is no version branch and no compat shim.

## Capabilities

### New Capabilities

None. This extends the existing recording session lifecycle rather than introducing a new one.

### Modified Capabilities

- `voice-capture`: adds requirements for holding exclusive audio focus for the duration of a recording, reference-counting it across overlapping captures, treating focus loss as a stop condition, and tolerating a refused focus request.

## Impact

- **Code**: `capture/RecordingService.kt` (request/abandon around `audioRecorder.record`, wire focus loss into `ExternalStop`); one new small type in `capture/` owning the reference-counted `AudioFocusRequest`, in the shape of the existing `CaptureController`.
- **Invariants touched**: the existing `Registration` idempotent-teardown idiom and the two release points in `runCapture` (the `catch` path and the normal path) both gain a focus counterpart. The "start haptic fires only after `AudioRecord` is confirmed capturing" invariant is unchanged — focus is requested before `startRecording`, and the tick still follows it.
- **Not in scope**: switching `MediaRecorder.AudioSource.MIC` to `VOICE_RECOGNITION`, and discarding a short pre-roll of PCM to swallow the other app's fade-out. Both are related to the same symptom and are recorded as deferred options in `design.md`.
- **Out of our control**: resuming playback is the media app's decision on `AUDIOFOCUS_GAIN`. Mainstream players resume after a transient loss; this change makes the request correctly but cannot guarantee the other app honours it.
