# SDK flow

`CredflareVerification.launch` opens one native flow for a verification case. The host backend chooses the compliance profile; the applicant never picks one. Every screen centres its text and uses the colours, type sizes, spacing and icons generated from the shared SDK spec (`Spec.generated.kt`). Light and dark palettes follow the system setting.

## Order of steps

1. **Intro.** Names the requester (`intro.body`). When the requester name is unknown the copy uses `common.verifier_fallback`.
2. **Documents.** Outstanding document requirements appear as centred cards with the `id_card` or `document` icon. Each document follows its capture rule (`upload`, `snapshot`, `snapshot_or_upload`, `snapshot_and_upload`): capture or choose each side, review, then send. One line names what follows the documents: `documents.selfie_next`, `location_next`, `selfie_location_next` or `location_selfie_next` in profile order, or `documents.host_steps_next` when only host steps remain.
3. **Verification requirements.** After the documents, the SDK runs the outstanding `verification_requirements` in profile order:
   * `facial_verification`: the liveness step below.
   * `location_verification`: the location step below.
   * Any other type, and profile forms, stay with the host app.
4. **Receipt.** Shown only when nothing collectable is left. It lists what this flow sent (documents, selfie, location) with the received illustration. When forms or other checks remain, the receipt says so and the button returns `HOST_ACTION_REQUIRED`; otherwise it returns `SUBMITTED`.

After each submission the SDK reloads the session, so the server decides what is still outstanding.

## Liveness step

1. Intro screen with the number of movements from `settings.gesture_count`, the selfie guidance and, when `record_video` is on, the recording consent line (`video.consent`) shown before anything starts.
2. On start the SDK asks the server for a new challenge (`POST /sdk/sessions/{session}/liveness-challenges`). The applicant cannot choose the gestures.
3. The camera screen uses the front camera through Camera2 with three outputs on one session: the preview (mirrored for display by the system), a YUV `ImageReader` for analysis, and a `MediaRecorder` surface when video is required.
4. Frames are sampled every `sample_interval_ms` and passed to the face signal provider. The default provider is ML Kit face detection (bundled model). Its samples feed the shared gesture engine:
   * position gate with hints from `liveness.position.*`;
   * each server gesture in order with its prompt and `gesture_timeout_ms` timeout;
   * a JPEG of the frame that completed each gesture;
   * a second position gate that captures the selfie.
5. Video recording starts when the first gesture starts and stops after the last gesture or at `max_video_seconds`, whichever comes first. The recording is H.264 in MP4, at most 480 pixels on the short side, with no audio track.
6. Uploads: one `gesture` frame per gesture, the `liveness_video` when required and the `selfie`, each with `challenge_id`, `capture_source=camera` and its own `X-Idempotency-Key`. Then the facial submission with the frame IDs in challenge order.
7. A timeout shows `liveness.timeout`; leaving the camera or passing the challenge lifetime shows `liveness.expired`. **Try again** always requests a new challenge. A network failure while sending retries the same uploads with the same keys.

The engine checks that the camera saw the server's sequence in time. It does not prove a live person and does not detect deepfakes, and the screens never claim either.

### Face signal provider

`FaceSignalProvider` turns a camera frame into the contract sample `{t, faces, box, eye_open, smile, yaw, pitch}`. Replace `CredflareVerification.faceSignalProvider` to use another detector. The ML Kit provider reports:

* `box` as fractions of the upright, unmirrored frame;
* `eye_open` as the mean of both eye open probabilities and `smile` as the smiling probability (classification mode all);
* `yaw` as `headEulerAngleY` and `pitch` as `headEulerAngleX`. ML Kit documents positive Y as the face turning toward the right of the processed image and positive X as looking up. Camera frames are passed unmirrored, so the right of the image is the person's own left, which matches the contract.

## Location step

1. Consent screen: `location.title`, or `location.place.title_within` or `title_outside` naming the place when the requirement has a reference. With several places a second line uses `need_within_any`, `need_within_all`, `need_outside_any` or `need_outside_all` according to `match`. Only labels are shown; the SDK never receives coordinates of reference places and never calls geocoding or IP services.
2. The SDK requests `ACCESS_FINE_LOCATION` (with coarse, as Android 12 and newer require).
3. It listens to the GPS and network providers of the platform `LocationManager` (no Play services). Each attempt lasts `fix_timeout_s`; up to `retry_attempts` attempts run until a fix has `accuracy <= max_accuracy_m` and is no older than `max_age_s`.
4. The applicant sees the accuracy, coordinates and time, then sends. The payload carries `accuracy`, `altitude`, `fix_time`, `is_mock` (from `Location.isMock` on Android 12 and newer, `isFromMockProvider` before), `provider` (`gps`, `network` or `fused`) and `captured_at`.
5. A `422` carries a rejection `code` from the spec and the SDK chooses the copy from it, always with **Try again** and a new fix:

   | Code | Copy |
   | --- | --- |
   | `location.reference_not_matched` | `location.place.not_matched_within` or `not_matched_outside` with the place labels |
   | `location.mock` | `location.mock_detected` |
   | `location.accuracy` | `location.low_accuracy`, or `location.place.not_precise` when a place is configured |
   | `location.stale` | `location.stale` |
   | `location.ip_country` | `location.ip_country_mismatch` |
   | any other code | The server's message |

## Rejections and retries

Each upload and submission sends an `X-Idempotency-Key`. A retry of the same request after a network failure reuses its key; a new capture, fix or document attempt gets a new key.

A liveness upload or submission rejected with `liveness.challenge_expired`, `challenge_invalid`, `challenge_used` or `frame_out_of_window` shows `liveness.expired`; any other `422` shows the server's message. In both cases the capture is discarded and **Try again** requests a new challenge. Unknown codes are kept as raw strings on `CredflareException.code` and fall back to the server's message.

## Permissions

| Permission | Used for |
| --- | --- |
| `CAMERA` | Document photos, the liveness camera and the liveness video |
| `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION` | The location step |
| `INTERNET` | API calls |

The SDK does not request `RECORD_AUDIO`; the liveness video has no audio source.
