## 1. Stop the recording on screen-off

- [x] 1.1 In `RecordingService`, add a `BroadcastReceiver` that calls `request()` on the capture's `ExternalStop` when it receives `Intent.ACTION_SCREEN_OFF`.
- [x] 1.2 Register it at runtime (it cannot be declared in the manifest) immediately after `CaptureController.activeStop` is assigned, and before the overlay is started.
- [x] 1.3 Unregister it on every exit path: normal stop, the `catch` around `audioRecorder.record`, and the early return when the vault is unavailable. Unregistering must be idempotent, since `unregisterReceiver` throws if called twice.
- [x] 1.4 Hold the receiver in a local, per-capture variable rather than a service field — `RecordingService` handles overlapping captures, so two captures must not share one receiver or one registration.

## 2. Keep the display awake while listening

- [x] 2.1 Set `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` on `TapToStopActivity`'s window in `onCreate`.
- [x] 2.2 Confirm nothing in the capture path waits on the overlay or fails if it is absent — the screen-off stop must remain owned by the service.

## 3. Verify on the device

These cannot be verified from code inspection or on the JVM; the failure they cover depends on real microphone input from a real pocket. Do not mark them done without running them on the Pixel 6 Pro.

- [ ] 3.1 Trigger a capture, speak, press the power button, pocket the phone. The stop buzz fires at the button press and a note appears in the vault containing the speech.
- [ ] 3.2 Repeat 3.1 while walking, so the pocket is genuinely noisy. Confirm the recording still ends at the button press and does not run to the 3-minute cap.
- [ ] 3.3 Trigger a capture, speak, and leave the phone flat on the table without touching the power button. Confirm the silence stop still fires after ~5 seconds and its buzz is unchanged.
- [ ] 3.4 Trigger a capture and tap the screen. Confirm tap-to-stop still ends the recording immediately.
- [ ] 3.5 Trigger a capture and leave it untouched, in silence, for longer than the device's display timeout without speaking. Confirm the screen stays on and the recording is not cut short by a timeout.
- [ ] 3.6 Trigger a second capture while the first is still transcribing, then turn off the screen. Confirm the screen-off stops only the in-progress recording and both captures produce their own notes.
- [ ] 3.7 Trigger a capture, then deny it audio (e.g. with the vault folder removed) so the failure path runs. Confirm no crash from an unregistered or doubly-unregistered receiver, and check logcat on `EasyNote` for `IllegalArgumentException`.

## 4. Follow-up left open

- [ ] 4.1 Confirm the pre-fix behaviour did eventually terminate at the 3-minute cap and produce a note. If it did not, a second defect exists in the recording loop that this change does not address.
