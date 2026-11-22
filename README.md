# Credflare Kotlin SDK

Native Android collection of documents, a liveness challenge with selfie and video, and location, with a scoped applicant session client. Development SDK for Android 7.0 (API 24) and newer. The example targets API 36. Requires the scoped-session backend; no server client credentials belong in the app.

## Guided collection

```kotlin
import net.credflare.sdk.CredflareVerification
import net.credflare.sdk.SessionProvider

CredflareVerification.launch(activity, SessionProvider {
    authenticatedBackend.createApplicantSession()
}) { result ->
    handleCollectionResult(result)
}
```

The launcher returns false when a flow is already open. The provider runs on a worker thread and returns a `SessionDescriptor`. The host backend creates or resumes the correct verification case and returns its temporary descriptor. Closing the flow preserves the case. `SUBMITTED` confirms collection, `HOST_ACTION_REQUIRED` returns control for checks the SDK cannot collect, or when a rejection leaves nothing to do in the app, and `CANCELLED` closes the native interface. Receipts are separate from verification decisions.

The flow supports camera photos, file selection, front/back steps, preview, replacement and explicit submission. Each document follows its backend capture rule: `upload`, `snapshot`, `snapshot_or_upload`, or `snapshot_and_upload`. Required document groups remain distinct from capture-source choices. Server file types and size limits are applied before upload and enforced again by the API.

Camera source is client reported. The liveness challenge shows that the camera recorded a server chosen sequence in time; it does not prove a live person and does not detect deepfakes. Questions of the `founders` type and checks the SDK does not collect return control to the host app. Unsent evidence survives activity recreation in process memory; after process termination, restart from the host app. Tokens and evidence are not saved in preferences or diagnostic logs.

## Session client

`VerificationSession.get()`, `upload()`, `createLivenessChallenge()`, `uploadBiometric()`, `submit()`, `submitFacial()`, `submitLocation()`, `requestCode()`, `confirmCode()`, `submitAnswers()`, `replaceSession()` and `cancel()` are blocking methods and must run off the main thread. Each upload and submission accepts a UUID retry key. Keep the same key with unchanged evidence after a network failure. A replacement session must use the same API; the host backend must bind renewal to the same case. A `422` raises `CredflareException` with the server's reason as its message and the rejection code in `code`; compare it with the constants in `CredflareSpec.ErrorCode`, and pass it to `Rejections.screen()` for the title, message, action and help line from the spec (the help line uses the requester's help email or website from `SessionInfo.help` when the session has one). A `resend_too_soon` rejection also carries `resendAvailableAt`. `isKnownCode` is false for codes newer than the generated spec.

`SessionInfo.requirements` carries each outstanding verification requirement with its parsed settings: gestures, gesture count and video for facial checks; accuracy, age, mock location and IP country rules for location checks, and the reference rule with place labels (never coordinates).

Network operations have a total deadline covering connection, upload and response body reading. Redirects are rejected. HTTPS is required except for loopback development URLs. The camera uses private temporary files exposed through scoped URI grants. Camera photos and liveness videos are not saved to the device photo library.

## Permissions

The SDK manifest merges `INTERNET`, `CAMERA`, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION` into the host app. Camera and location are requested at runtime only when a step needs them. The liveness video is recorded without audio, so `RECORD_AUDIO` is not needed.

## Shared spec

Copy, colours, type, spacing, icons, liveness rules and location limits come from `Spec.generated.kt`, generated from `credflare/sdk-spec`. Do not edit it by hand; see [TESTING.md](TESTING.md) for the drift check. [docs/SDK-FLOW.md](docs/SDK-FLOW.md) describes every step.

## Development

Use JDK 17 and an Android SDK with API 36 installed. Add this repository's `sdk` module as a Gradle project dependency. No Maven release has been published. The module namespace is `net.credflare.sdk`; repository names do not affect imports.

```sh
./gradlew :sdk:assembleDebug :example:assembleDebug :example:assembleDebugAndroidTest
```

The example asks the local demo backend on port 3107 for a session. On an Android emulator, forward ports 3107 and the configured API port with `adb reverse`. Use synthetic files from the JavaScript SDK under `test/fixtures/documents`, copied to Downloads.

The contract suite, unit tests, emulator runs and the face signal probe are described in [TESTING.md](TESTING.md).

## Developer configured facial and location collection

Choose the compliance profile on your server when creating the case. The recipient does not choose a profile or verification mode. The SDK collects outstanding documents first, then the questionnaire, then the outstanding facial, location, email and phone requirements in profile order. After every submission and code confirmation it fetches the session again: a requirement the workflow added in the meantime is collected next, and the receipt appears only when nothing is outstanding or the case is no longer open. If that fetch fails, the SDK says so and offers to try again instead of showing the receipt.

The questionnaire shows each question whose `show_if` conditions match the answers given above it. Hidden questions are not validated and their answers are not sent. `Questionnaire` and `Conditions` evaluate the same rules for a host that builds its own form.

The email and phone steps ask for the address or number, send a six digit code, and confirm it. The new code button counts down to the time the server allows, and every rejection shows the screen and action the spec defines for its code.

The facial step asks the server for a liveness challenge, runs its gestures in order on the front camera with the shared gesture engine and ML Kit face detection (bundled model), records a short video without audio when the profile asks for one, and captures a selfie. Frames, video and selfie are uploaded against the challenge, then submitted together. Retrying after a timeout or rejection requests a new challenge.

The location step explains why the location is needed (naming any reference places by label), asks for permission, waits for a fix within the profile's accuracy and age limits, and reports whether the fix came from a mock provider. The server applies the profile's rules and the SDK shows its reason when it rejects a fix.

A receipt confirms collection for review, not a face match or a decision.
