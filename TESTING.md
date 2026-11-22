# Testing

Use JDK 17 and an Android SDK with API 36. Node 20 or newer runs the spec check and the contract server.

## CI checks

```sh
./gradlew :sdk:assembleDebug :sdk:testDebugUnitTest :example:assembleDebug :example:assembleDebugAndroidTest lint
```

`:sdk:testDebugUnitTest` runs on the JVM:

* `ContractServerTest` starts `scripts/contract-server.mjs` with Node and calls the real client against the spec fixtures. It asserts the idempotency key on every upload and submission, and replays every backend rejection in `example/src/androidTest/assets/rejections` (one per code in the spec) through the matching route (submit, code request or code confirmation) to check the parsed code and the title, message, action and help line from `spec/rejections.json`.
* `GestureEngineTest` replays every trace in `sdk/src/test/resources/liveness-traces` (copied from `sdk-spec/fixtures/liveness-traces`) and asserts the expected outcome and position status.
* `SettingsParsingTest` parses sessions, document requirements and their capture steps, requirement settings, reference place labels and liveness challenges from the captured API fixtures.
* `RejectionCopyTest` parses every file in `example/src/androidTest/assets/rejections` and checks the rejection screen for each code, the verifier fallback and the server message with **Try again** for a validation error without a code, and the location copy with no place, an unnamed place, places to be at and places to stay away from.
* `LocationAcceptanceTest` checks the accuracy and age limits from the captured profile and the spec defaults, and that mock and unknown mock readings are sent to the server, which applies the `mock_location` policy.
* `ShowIfCasesTest` replays all 62 backend cases in `show-if-cases.json` (copied from `sdk-spec/fixtures`) through `Conditions` and checks that they cover every spec operator.
* `QuestionnaireTest` parses `questionnaire-session.json` and checks which questions are shown, validated and sent for different answers.
* `ContactCodeTest` requests and confirms codes through the contract server, checks the resend countdown from the `202` and from the `resend_available_at` of a `resend_too_soon` rejection, and the spec rules for phone numbers and email addresses.
* `RefreshTest` submits answers and fetches the session again: the phone requirement added in `requirement-added-session.json` comes next, a failed fetch keeps the pending state instead of the receipt, a `409` because the last submission closed the case shows the receipt, and a hidden answer rejected by the server shows its message with **Try again**.
* `AttemptKeysTest` runs the document, liveness and location submissions against the contract server with every first response dropped. A retry reuses the idempotency key of each upload and submission and skips finished uploads; a new location fix, a changed document file or a liveness rejection starts a new attempt with new keys.

## Spec drift

With `credflare/sdk-spec` checked out next to this repository:

```sh
./scripts/check-spec.sh
```

It runs the generator in check mode for `Spec.generated.kt` and compares the contract fixtures and liveness traces with the spec copies. Set `SDK_SPEC_DIR` to use another checkout. To update after a spec change:

```sh
node ../sdk-spec/scripts/generate.mjs --target kotlin --out sdk/src/main/kotlin/net/credflare/sdk/Spec.generated.kt
cp ../sdk-spec/fixtures/*.json example/src/androidTest/assets/
cp ../sdk-spec/fixtures/rejections/*.json example/src/androidTest/assets/rejections/
cp ../sdk-spec/fixtures/liveness-traces/*.json sdk/src/test/resources/liveness-traces/
```

`cancelled.json`, `capture-snapshot-or-upload.json`, `capture-snapshot_and_upload.json` and `sample-front.png` are local to this repository.

## Contract suite on a device

CI runs this suite on an API 35 emulator with `./scripts/run-contract-instrumentation.sh`, which starts the contract server, installs both APKs, runs the instrumentation and fails unless it reports `PASS`. The manual steps are:

The instrumentation calls the real client against `scripts/contract-server.mjs`, which serves the spec fixtures and records what the client sent. It covers sessions, document uploads, requirement settings, the liveness challenge, gesture, video and selfie uploads with their multipart fields and idempotency keys, facial and location submissions with their idempotency keys, `422` location rejections with their codes, a real `422` for every spec rejection code with its screen, email and phone code requests and confirmations, and questionnaire answers followed by the requirement the workflow added.

```sh
PORT=3118 node scripts/contract-server.mjs example/src/androidTest/assets &
adb reverse tcp:3118 tcp:3118
adb install -r -t example/build/outputs/apk/debug/example-debug.apk
adb install -r -t example/build/outputs/apk/androidTest/debug/example-debug-androidTest.apk
adb shell am instrument -w -e contract_base http://127.0.0.1:3118/api/v1 net.credflare.kotlindemo.test/net.credflare.kotlindemo.ContractInstrumentation
adb reverse --remove tcp:3118
```

## Face signal conventions

The same instrumentation can run the ML Kit provider on a photo and print the samples for the upright image, a mirrored copy and a rotated copy:

```sh
adb push portrait.jpg /data/local/tmp/credflare-face.jpg
adb shell am instrument -w -e face_probe /data/local/tmp/credflare-face.jpg net.credflare.kotlindemo.test/net.credflare.kotlindemo.ContractInstrumentation
```

Use a photo where the person clearly turns their head. A person turning to their own right must give a negative `yaw`, looking down a negative `pitch`, and the mirrored copy must flip the sign of `yaw`.

## Emulator runs

The example asks a backend for a session at `http://127.0.0.1:3107/session`, or at the URL in the `demo_url` launch extra:

```sh
adb reverse tcp:3107 tcp:3107
adb reverse tcp:8018 tcp:8018
adb shell am start -n net.credflare.kotlindemo/.MainActivity -e demo_url http://127.0.0.1:3107
```

The emulator's virtual front camera shows a test scene without a face, so the liveness screen stays on the position hint and then shows the timeout with **Try again**. Running the gestures to completion needs a physical device.

To test location, use a profile with `location_verification`:

* A real GPS reading: `adb emu geo fix <longitude> <latitude>`. The fix is not a mock.
* A mock location (Android 12 and newer):

  ```sh
  adb shell appops set com.android.shell android:mock_location allow
  adb shell cmd location providers add-test-provider gps
  adb shell cmd location providers set-test-provider-enabled gps true
  adb shell cmd location providers set-test-provider-location gps --location 4.0511,9.7679 --accuracy 5
  ```

  The SDK sends `is_mock: true`; with `mock_location: reject` the server answers `422` and the SDK shows the mock location message. Afterwards remove the provider with `adb shell cmd location providers remove-test-provider gps` and reset the shell's mock location permission with `adb shell appops set com.android.shell android:mock_location default`. `python3 scripts/device-flow.py location` runs these steps.

`scripts/device-selfie.py` drives the older selfie screen and needs updating for the liveness screen.

Physical device testing is still needed for the liveness gestures, video recording on devices with limited camera stream support, and camera or location permission recovery.
