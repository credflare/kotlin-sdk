# Credflare Kotlin SDK

Native Android document and selfie collection and a scoped applicant session client. Development SDK for Android 7.0 (API 24) and newer. The example targets API 36. Requires the scoped-session backend; no server client credentials belong in the app.

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

The launcher returns false when a flow is already open. The provider runs on a worker thread and returns a `SessionDescriptor`. The host backend creates or resumes the correct verification case and returns its temporary descriptor. Closing the flow preserves the case. `SUBMITTED` confirms collection, `HOST_ACTION_REQUIRED` returns control for forms or other checks, and `CANCELLED` closes the native interface. Receipts are separate from verification decisions.

The flow supports camera photos, file selection, front/back steps, preview, replacement and explicit submission. Each document follows its backend capture rule: `upload`, `snapshot`, `either`, or `snapshot_and_upload`. Required document groups remain distinct from capture-source choices. Server file types and size limits are applied before upload and enforced again by the API.

Camera source is client-reported. Trusted camera, liveness and deepfake checks are unavailable. The current flow does not collect forms or location checks. Unsent evidence survives activity recreation in process memory; after process termination, restart from the host app. Tokens and evidence are not saved in preferences or diagnostic logs.

## Session client

`VerificationSession.get()`, `upload()`, `submit()`, `replaceSession()` and `cancel()` are blocking methods and must run off the main thread. Each upload and submission accepts a UUID retry key. Keep the same key with unchanged evidence after a network failure. A replacement session must use the same API; the host backend must bind renewal to the same case.

Network operations have a total deadline covering connection, upload and response-body reading. Redirects are rejected. HTTPS is required except for loopback development URLs. The camera uses private temporary files exposed through scoped URI grants. Camera photos are not saved to the device photo library.

## Development

Use JDK 17 and an Android SDK with API 36 installed. Add this repository's `sdk` module as a Gradle project dependency. No Maven release has been published. The module namespace is `net.credflare.sdk`; repository names do not affect imports.

```sh
./gradlew :sdk:assembleDebug :example:assembleDebug :example:assembleDebugAndroidTest
```

The example asks the local demo backend on port 3107 for a session. On an Android emulator, forward ports 3107 and the configured API port with `adb reverse`. Use synthetic files from the JavaScript SDK under `test/fixtures/documents`, copied to Downloads.

The Android contract suite uses recorded API responses over a local HTTP server. Start `PORT=3108 node scripts/contract-server.mjs example/src/androidTest/assets`, forward port 3108, install both example APKs, then run:

```sh
adb shell am instrument -w -e contract_base http://127.0.0.1:3108/api/v1 net.credflare.kotlindemo.test/net.credflare.kotlindemo.ContractInstrumentation
```

Physical-device capture, camera permission recovery and upload interruptions need additional device testing before release.

## Developer-configured facial collection

Choose the compliance profile on your server when creating the case. The recipient does not choose a profile or verification mode. The SDK collects outstanding documents first, then opens the selfie step only when the case requests `facial_verification`. Selfie-only cases go directly to that step. Document alternatives and capture-source choices follow the server's requirement rules.

The selfie step uses the front camera, provides a preview and retake action, and sends only after confirmation. Uploads use the scoped biometric endpoint; facial submissions reference the uploaded file ID. Retrying a send reuses its upload and submission keys. A receipt confirms collection for review, not a face match or liveness result.

The backend currently reports liveness, trusted camera and deepfake detection as unavailable. Automated face matching and validated liveness processing are not implemented by this capture interface. Forms and location checks return control to the host app. Example apps obtain their requirements from the demo server without a recipient-facing profile selector.
