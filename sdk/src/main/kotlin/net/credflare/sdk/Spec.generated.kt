// GENERATED FILE, DO NOT EDIT.
// Source: credflare sdk-spec 1.0.0 (13c02ea43d7a). Regenerate with scripts/generate.mjs.
@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package net.credflare.sdk

/** One step of a gesture. value and holdFrames name a threshold key. */
data class SpecGestureStep(
    val signal: String,
    val op: String,
    val value: String,
    val holdFrames: String? = null,
)

/** One setting in the requirement settings schema. path uses dots, with [] for list items. defaultJson is the JSON encoded default. */
data class SpecSetting(
    val path: String,
    val type: String,
    val optional: Boolean,
    val min: Double? = null,
    val max: Double? = null,
    val options: List<String>,
    val defaultJson: String? = null,
)

data class SpecPalette(
    val primary: Long,
    val onPrimary: Long,
    val surface: Long,
    val ink: Long,
    val muted: Long,
    val soft: Long,
    val border: Long,
    val accent: Long,
    val success: Long,
    val error: Long,
    val warningSoft: Long,
    val warningInk: Long,
)

data class SpecTextStyle(
    val size: Double,
    val lineHeight: Double,
    val weight: Int,
)

/** Absolute path data using only M, L, C and Z, with values in viewBox units. */
data class SpecIconPath(
    val d: String,
    val stroke: Boolean,
    val fill: Boolean,
    val strokeWidth: Double,
    val strokeOpacity: Double,
    val fillOpacity: Double,
)

data class SpecIcon(
    val viewBox: Double,
    val strokeWidth: Double,
    val paths: List<SpecIconPath>,
)

object CredflareSpec {
    const val VERSION: String = "1.0.0"
    const val HASH: String = "13c02ea43d7a"

    object Liveness {
        const val VERSION: Int = 1
        const val CHALLENGE_TTL_S: Int = 120
        const val GESTURE_TIMEOUT_MS: Int = 10000
        const val SAMPLE_INTERVAL_MS: Int = 66
        const val VIDEO_MAX_SECONDS: Int = 8
        const val VIDEO_MAX_HEIGHT: Int = 480
        const val POSITION_FACE_AREA_MIN: Double = 0.15
        const val POSITION_FACE_AREA_MAX: Double = 0.65
        const val POSITION_CENTER_TOLERANCE: Double = 0.2
        const val POSITION_FACES: Int = 1
        const val FACE_LOST_RESET_MS: Int = 1000
        val GESTURE_NAMES: List<String> = listOf(
            "blink",
            "smile",
            "turn_left",
            "turn_right",
            "nod",
        )
        val THRESHOLDS: Map<String, Map<String, Double>> = mapOf(
            "default" to mapOf(
                "eye_open_baseline" to 0.6,
                "eye_closed" to 0.3,
                "eye_reopened" to 0.5,
                "smile" to 0.6,
                "smile_hold_frames" to 5.0,
                "turn" to 20.0,
                "nod" to 15.0,
                "return" to 10.0,
            ),
            "mediapipe" to mapOf(
                "smile" to 0.4,
            ),
            "mlkit" to mapOf(
                "smile" to 0.7,
            ),
            "vision" to emptyMap(),
        )
        val GESTURES: Map<String, List<SpecGestureStep>> = mapOf(
            "blink" to listOf(
                SpecGestureStep(
                    signal = "eye_open",
                    op = "gt",
                    value = "eye_open_baseline",
                    holdFrames = null,
                ),
                SpecGestureStep(
                    signal = "eye_open",
                    op = "lt",
                    value = "eye_closed",
                    holdFrames = null,
                ),
                SpecGestureStep(
                    signal = "eye_open",
                    op = "gt",
                    value = "eye_reopened",
                    holdFrames = null,
                ),
            ),
            "smile" to listOf(
                SpecGestureStep(
                    signal = "smile",
                    op = "gt",
                    value = "smile",
                    holdFrames = "smile_hold_frames",
                ),
            ),
            "turn_left" to listOf(
                SpecGestureStep(
                    signal = "yaw",
                    op = "gt",
                    value = "turn",
                    holdFrames = null,
                ),
                SpecGestureStep(
                    signal = "yaw",
                    op = "abs_lt",
                    value = "return",
                    holdFrames = null,
                ),
            ),
            "turn_right" to listOf(
                SpecGestureStep(
                    signal = "yaw",
                    op = "lt_neg",
                    value = "turn",
                    holdFrames = null,
                ),
                SpecGestureStep(
                    signal = "yaw",
                    op = "abs_lt",
                    value = "return",
                    holdFrames = null,
                ),
            ),
            "nod" to listOf(
                SpecGestureStep(
                    signal = "pitch",
                    op = "lt_neg",
                    value = "nod",
                    holdFrames = null,
                ),
                SpecGestureStep(
                    signal = "pitch",
                    op = "abs_lt",
                    value = "return",
                    holdFrames = null,
                ),
            ),
        )
    }

    object Location {
        const val VERSION: Int = 1
        const val CLOCK_SKEW_S: Int = 30
        const val FIX_TIMEOUT_S: Int = 30
        const val RETRY_ATTEMPTS: Int = 3
    }

    object Settings {
        const val VERSION: Int = 1
        val FACIAL_VERIFICATION_GESTURES_DEFAULT: List<String> = listOf(
            "blink",
            "smile",
            "turn_left",
            "turn_right",
            "nod",
        )
        const val FACIAL_VERIFICATION_GESTURE_COUNT_DEFAULT: Int = 3
        const val FACIAL_VERIFICATION_RECORD_VIDEO_DEFAULT: Boolean = true
        const val FACIAL_VERIFICATION_VIDEO_RETENTION_DAYS_DEFAULT: Int = 30
        const val LOCATION_VERIFICATION_MAX_ACCURACY_M_DEFAULT: Int = 100
        const val LOCATION_VERIFICATION_MAX_AGE_S_DEFAULT: Int = 60
        const val LOCATION_VERIFICATION_MOCK_LOCATION_DEFAULT: String = "reject"
        const val LOCATION_VERIFICATION_IP_COUNTRY_DEFAULT: String = "flag"
        val SCHEMA: List<SpecSetting> = listOf(
            SpecSetting(
                path = "facial_verification.gestures",
                type = "set",
                optional = false,
                min = 1.0,
                max = null,
                options = listOf(
                    "blink",
                    "smile",
                    "turn_left",
                    "turn_right",
                    "nod",
                ),
                defaultJson = "[\"blink\",\"smile\",\"turn_left\",\"turn_right\",\"nod\"]",
            ),
            SpecSetting(
                path = "facial_verification.gesture_count",
                type = "int",
                optional = false,
                min = 1.0,
                max = 5.0,
                options = emptyList(),
                defaultJson = "3",
            ),
            SpecSetting(
                path = "facial_verification.record_video",
                type = "bool",
                optional = false,
                min = null,
                max = null,
                options = emptyList(),
                defaultJson = "true",
            ),
            SpecSetting(
                path = "facial_verification.video_retention_days",
                type = "int",
                optional = false,
                min = 1.0,
                max = 365.0,
                options = emptyList(),
                defaultJson = "30",
            ),
            SpecSetting(
                path = "location_verification.max_accuracy_m",
                type = "int",
                optional = false,
                min = 5.0,
                max = 5000.0,
                options = emptyList(),
                defaultJson = "100",
            ),
            SpecSetting(
                path = "location_verification.max_age_s",
                type = "int",
                optional = false,
                min = 5.0,
                max = 3600.0,
                options = emptyList(),
                defaultJson = "60",
            ),
            SpecSetting(
                path = "location_verification.mock_location",
                type = "enum",
                optional = false,
                min = null,
                max = null,
                options = listOf(
                    "reject",
                    "flag",
                    "allow",
                ),
                defaultJson = "\"reject\"",
            ),
            SpecSetting(
                path = "location_verification.ip_country",
                type = "enum",
                optional = false,
                min = null,
                max = null,
                options = listOf(
                    "reject",
                    "flag",
                    "off",
                ),
                defaultJson = "\"flag\"",
            ),
            SpecSetting(
                path = "location_verification.reference",
                type = "object",
                optional = true,
                min = null,
                max = null,
                options = emptyList(),
                defaultJson = "null",
            ),
            SpecSetting(
                path = "location_verification.reference.places",
                type = "list",
                optional = false,
                min = 1.0,
                max = 50.0,
                options = emptyList(),
                defaultJson = null,
            ),
            SpecSetting(
                path = "location_verification.reference.places[].id",
                type = "string",
                optional = false,
                min = null,
                max = 64.0,
                options = emptyList(),
                defaultJson = null,
            ),
            SpecSetting(
                path = "location_verification.reference.places[].label",
                type = "string",
                optional = false,
                min = null,
                max = 120.0,
                options = emptyList(),
                defaultJson = null,
            ),
            SpecSetting(
                path = "location_verification.reference.places[].latitude",
                type = "number",
                optional = false,
                min = -90.0,
                max = 90.0,
                options = emptyList(),
                defaultJson = null,
            ),
            SpecSetting(
                path = "location_verification.reference.places[].longitude",
                type = "number",
                optional = false,
                min = -180.0,
                max = 180.0,
                options = emptyList(),
                defaultJson = null,
            ),
            SpecSetting(
                path = "location_verification.reference.places[].radius_m",
                type = "int",
                optional = false,
                min = 0.0,
                max = 100000.0,
                options = emptyList(),
                defaultJson = null,
            ),
            SpecSetting(
                path = "location_verification.reference.rule",
                type = "enum",
                optional = false,
                min = null,
                max = null,
                options = listOf(
                    "within",
                    "outside",
                ),
                defaultJson = "\"within\"",
            ),
            SpecSetting(
                path = "location_verification.reference.match",
                type = "enum",
                optional = false,
                min = null,
                max = null,
                options = listOf(
                    "any",
                    "all",
                ),
                defaultJson = "\"any\"",
            ),
            SpecSetting(
                path = "location_verification.reference.confidence",
                type = "enum",
                optional = false,
                min = null,
                max = null,
                options = listOf(
                    "strict",
                    "lenient",
                ),
                defaultJson = "\"strict\"",
            ),
            SpecSetting(
                path = "location_verification.reference.on_mismatch",
                type = "enum",
                optional = false,
                min = null,
                max = null,
                options = listOf(
                    "reject",
                    "flag",
                ),
                defaultJson = "\"reject\"",
            ),
        )
    }

    object Copy {
        val LOCALES: List<String> = listOf(
            "en",
        )
        val EN: Map<String, String> = mapOf(
            "common.back" to "Back",
            "common.cancel" to "Cancel",
            "common.change" to "Change",
            "common.close" to "Close",
            "common.continue" to "Continue",
            "common.done" to "Done",
            "common.loading" to "Getting ready…",
            "common.return_to_app" to "Return to app",
            "common.secured_by" to "Secured by Credflare",
            "common.start" to "Start",
            "common.step_progress" to "Step {current} of {total}",
            "common.test_mode" to "Test mode",
            "common.try_again" to "Try again",
            "common.verifier_fallback" to "your verifier",
            "intro.title" to "Verify your identity",
            "intro.body" to "Follow the steps requested by {verifier}. Review each item before you send it.",
            "intro.start" to "Start",
            "intro.checking" to "Checking what is still needed…",
            "exit.title" to "Leave for now?",
            "exit.body" to "Evidence you already sent is kept. Unsent photos and files will be discarded. You can come back through the app that sent you here.",
            "exit.stay" to "Keep going",
            "exit.leave" to "Leave for now",
            "exit.discard_title" to "Discard these photos and files?",
            "exit.discard_body" to "You have not sent this document yet.",
            "exit.discard_stay" to "Keep editing",
            "exit.discard_leave" to "Discard",
            "documents.title" to "Documents requested",
            "documents.requested_by" to "Requested by {verifier}.",
            "documents.back" to "Documents",
            "documents.section_required" to "Required",
            "documents.section_one_of" to "Provide one of these",
            "documents.section_count_of" to "Provide {count} of these",
            "documents.method_camera" to "Camera photos",
            "documents.method_upload" to "Saved files",
            "documents.method_either" to "Photo or file",
            "documents.method_both" to "Photo and saved file",
            "documents.unavailable" to "Not available in this app. Contact {verifier}.",
            "documents.selfie_next" to "After your documents, you will take a selfie.",
            "documents.location_next" to "After your documents, you will share your location.",
            "documents.selfie_location_next" to "After your documents, you will take a selfie and share your location.",
            "documents.location_selfie_next" to "After your documents, you will share your location and take a selfie.",
            "documents.host_steps_next" to "After your documents, the app will guide you through the remaining steps.",
            "capture.side_front" to "Front",
            "capture.side_back" to "Back",
            "capture.source_camera" to "Camera photo",
            "capture.source_upload" to "Saved file",
            "capture.instruction_camera" to "Place the {document} on a flat surface. Keep all four corners visible and avoid glare.",
            "capture.instruction_either" to "Take a photo or choose a saved file of the {side}.",
            "capture.instruction_upload" to "Choose a clear photo or scan of the {side}.",
            "capture.instruction_upload_pdf" to "Choose a clear photo, scan or PDF of the {side}.",
            "capture.also_saved_file" to "You also need to add a saved file of each side.",
            "capture.check_photo" to "Check that all four corners are visible, the text is sharp and there is no glare.",
            "capture.take_photo" to "Take photo",
            "capture.choose_file" to "Choose a file",
            "capture.retake_photo" to "Retake photo",
            "capture.change_file" to "Change file",
            "capture.use_file_instead" to "Use a file instead",
            "capture.use_photo_instead" to "Take a photo instead",
            "capture.file_limits" to "{types}, up to {size}",
            "capture.preview_pdf" to "PDF",
            "capture.preview_document" to "Document",
            "capture.preview_label" to "Preview of the {side}",
            "review.title" to "Review before sending",
            "review.body" to "Check each photo and file before you send them.",
            "review.item" to "{side} · {source}",
            "review.change" to "Change",
            "review.send" to "Send document",
            "sending.uploading" to "Uploading…",
            "sending.uploading_progress" to "Uploading {current} of {total}…",
            "sending.sending" to "Sending…",
            "sending.sent_title" to "Your evidence was sent",
            "sending.sent_body" to "We are checking that everything arrived.",
            "sending.check_again" to "Check again",
            "receipt.title" to "Evidence received",
            "receipt.body" to "Your evidence will be reviewed by {verifier}. You can close this screen.",
            "receipt.document" to "{document} received",
            "receipt.selfie" to "Selfie received",
            "receipt.location" to "Location received",
            "receipt.host_steps" to "The app will guide you through the remaining steps.",
            "receipt.done" to "Done",
            "selfie.title" to "Take a selfie",
            "selfie.guidance" to "Face the camera in even light. Keep your whole face visible and remove anything covering it.",
            "selfie.tip_face_camera" to "Face the camera directly.",
            "selfie.tip_light" to "Find even light on your face.",
            "selfie.tip_neutral" to "Keep a neutral expression.",
            "selfie.tip_glasses" to "Take off glasses if you can.",
            "selfie.open_camera" to "Open front camera",
            "selfie.starting_camera" to "Starting the camera…",
            "selfie.take" to "Take selfie",
            "selfie.review_title" to "Review your selfie",
            "selfie.review_body" to "Make sure your face is clear and fully visible before you send it.",
            "selfie.retake" to "Retake selfie",
            "selfie.send" to "Send selfie",
            "selfie.sending" to "Sending your selfie…",
            "selfie.preview_label" to "Your selfie preview",
            "selfie.privacy" to "Your selfie will be sent to {verifier} for review.",
            "liveness.title" to "Follow the prompts",
            "liveness.body" to "You will be asked to make {total} simple movements. Keep your face inside the frame.",
            "liveness.start" to "Start",
            "liveness.progress" to "Movement {current} of {total}",
            "liveness.position.no_face" to "Look at the camera",
            "liveness.position.move_closer" to "Move closer",
            "liveness.position.move_further" to "Move back a little",
            "liveness.position.center" to "Center your face in the frame",
            "liveness.position.one_face" to "Make sure only your face is in the frame",
            "liveness.position.hold_still" to "Hold still",
            "liveness.position.more_light" to "Move to a brighter place",
            "liveness.position.ready" to "Good. Hold still",
            "liveness.gesture.blink" to "Blink",
            "liveness.gesture.smile" to "Smile",
            "liveness.gesture.turn_left" to "Slowly turn your head to your left, then back",
            "liveness.gesture.turn_right" to "Slowly turn your head to your right, then back",
            "liveness.gesture.nod" to "Nod: look down, then back up",
            "liveness.gesture_done" to "Done",
            "liveness.face_lost" to "We lost sight of your face. Look at the camera.",
            "liveness.success" to "All movements recorded",
            "liveness.timeout" to "Time ran out for this movement.",
            "liveness.expired" to "This step timed out. Start it again.",
            "liveness.retry" to "Try again",
            "liveness.sending" to "Sending your recording…",
            "video.consent" to "This step records a short video of your face. It is sent to {verifier} for review.",
            "video.recording" to "Recording",
            "location.title" to "Share your location",
            "location.why" to "{verifier} asks where you are right now. Your location is used only for this request.",
            "location.what_shared" to "We send your coordinates, how accurate they are and when they were taken.",
            "location.permission_prompt" to "Your device will ask for permission. Choose Allow.",
            "location.share" to "Share location",
            "location.waiting" to "Finding your location…",
            "location.waiting_accuracy" to "Getting a more accurate reading. Stay where you are, near a window or outside if you can.",
            "location.found_title" to "Location found",
            "location.found_body" to "Check it looks right, then send it.",
            "location.accuracy_label" to "Accuracy",
            "location.accuracy_value" to "Within {meters} m",
            "location.coordinates_label" to "Coordinates",
            "location.time_label" to "Taken at",
            "location.send" to "Send location",
            "location.permission_denied" to "Location access is off. Turn it on in Settings, then try again.",
            "location.permission_denied_browser" to "Location access is blocked. Allow it for this site in your browser settings, then reload the page.",
            "location.unavailable" to "Location is not available on this device.",
            "location.timeout" to "We could not find your location in time. Try again.",
            "location.low_accuracy" to "Your location is not accurate enough yet. Move near a window or outside, then try again.",
            "location.mock_detected" to "An app or setting is changing your device location. Turn it off, then try again.",
            "location.retry" to "Try again",
            "location.retries_exhausted" to "We could not get your location. Contact {verifier}.",
            "location.place.title_within" to "Share your location at {place}",
            "location.place.title_outside" to "Share your location away from {place}",
            "location.place.need_within" to "You need to be at {place}.",
            "location.place.need_within_any" to "You need to be at one of these places: {places}.",
            "location.place.need_within_all" to "You need to be at each of these places: {places}.",
            "location.place.need_outside" to "You need to be away from {place}.",
            "location.place.need_outside_all" to "You need to be away from these places: {places}.",
            "location.place.need_outside_any" to "You need to be away from at least one of these places: {places}.",
            "location.place.not_matched_within" to "You do not seem to be at {place} yet. Move closer, then try again.",
            "location.place.not_matched_outside" to "You seem to be too close to {place}. Move away, then try again.",
            "location.place.not_precise" to "Your location is not precise enough to check this place. Move near a window or outside, then try again.",
            "location.place.rejected" to "Your location does not match the place requested by {verifier}. Try again from the right place.",
            "location.place.retry" to "Try again",
            "errors.generic" to "Something went wrong. Try again.",
            "errors.network" to "Could not connect. Check your connection and try again.",
            "errors.session_expired" to "Your session has expired. Close this screen and start again.",
            "errors.start_failed" to "We could not start. Check your connection and try again.",
            "errors.refresh_failed" to "We could not check what is still needed. Try again.",
            "errors.camera_denied" to "Camera access is off. Turn it on in Settings, then try again.",
            "errors.camera_denied_browser" to "Camera access is blocked. Allow it in your browser settings, then try again.",
            "errors.camera_unavailable" to "The camera is unavailable. Try again on a device with a camera.",
            "errors.front_camera_required" to "This step needs a front camera. Try another device.",
            "errors.camera_no_photo" to "The camera did not return a readable photo. Try again.",
            "errors.file_unreadable" to "This file could not be read. Choose another file.",
            "errors.file_type" to "Choose a {types} file.",
            "errors.file_too_large" to "This file is larger than {size}. Choose a smaller file.",
            "errors.file_empty" to "This file is empty. Choose another file.",
            "errors.photo_format" to "This photo format is not accepted by {verifier}.",
            "errors.photo_invalid" to "This photo is empty or too large. Retake it.",
            "errors.document_unsupported" to "This document cannot be collected in this app. Contact {verifier}.",
            "errors.add_file_failed" to "Could not add this file. Try again.",
            "errors.send_document_failed" to "Could not send this document. Check your connection and try again.",
            "errors.selfie_failed" to "Could not take your selfie. Try again.",
            "errors.send_selfie_failed" to "Could not send your selfie. Check your connection and try again.",
            "errors.challenge_failed" to "We could not start this step. Check your connection and try again.",
            "errors.send_location_failed" to "Could not send your location. Check your connection and try again.",
        )
    }

    object CopyKey {
        const val COMMON_BACK: String = "common.back"
        const val COMMON_CANCEL: String = "common.cancel"
        const val COMMON_CHANGE: String = "common.change"
        const val COMMON_CLOSE: String = "common.close"
        const val COMMON_CONTINUE: String = "common.continue"
        const val COMMON_DONE: String = "common.done"
        const val COMMON_LOADING: String = "common.loading"
        const val COMMON_RETURN_TO_APP: String = "common.return_to_app"
        const val COMMON_SECURED_BY: String = "common.secured_by"
        const val COMMON_START: String = "common.start"
        const val COMMON_STEP_PROGRESS: String = "common.step_progress"
        const val COMMON_TEST_MODE: String = "common.test_mode"
        const val COMMON_TRY_AGAIN: String = "common.try_again"
        const val COMMON_VERIFIER_FALLBACK: String = "common.verifier_fallback"
        const val INTRO_TITLE: String = "intro.title"
        const val INTRO_BODY: String = "intro.body"
        const val INTRO_START: String = "intro.start"
        const val INTRO_CHECKING: String = "intro.checking"
        const val EXIT_TITLE: String = "exit.title"
        const val EXIT_BODY: String = "exit.body"
        const val EXIT_STAY: String = "exit.stay"
        const val EXIT_LEAVE: String = "exit.leave"
        const val EXIT_DISCARD_TITLE: String = "exit.discard_title"
        const val EXIT_DISCARD_BODY: String = "exit.discard_body"
        const val EXIT_DISCARD_STAY: String = "exit.discard_stay"
        const val EXIT_DISCARD_LEAVE: String = "exit.discard_leave"
        const val DOCUMENTS_TITLE: String = "documents.title"
        const val DOCUMENTS_REQUESTED_BY: String = "documents.requested_by"
        const val DOCUMENTS_BACK: String = "documents.back"
        const val DOCUMENTS_SECTION_REQUIRED: String = "documents.section_required"
        const val DOCUMENTS_SECTION_ONE_OF: String = "documents.section_one_of"
        const val DOCUMENTS_SECTION_COUNT_OF: String = "documents.section_count_of"
        const val DOCUMENTS_METHOD_CAMERA: String = "documents.method_camera"
        const val DOCUMENTS_METHOD_UPLOAD: String = "documents.method_upload"
        const val DOCUMENTS_METHOD_EITHER: String = "documents.method_either"
        const val DOCUMENTS_METHOD_BOTH: String = "documents.method_both"
        const val DOCUMENTS_UNAVAILABLE: String = "documents.unavailable"
        const val DOCUMENTS_SELFIE_NEXT: String = "documents.selfie_next"
        const val DOCUMENTS_LOCATION_NEXT: String = "documents.location_next"
        const val DOCUMENTS_SELFIE_LOCATION_NEXT: String = "documents.selfie_location_next"
        const val DOCUMENTS_LOCATION_SELFIE_NEXT: String = "documents.location_selfie_next"
        const val DOCUMENTS_HOST_STEPS_NEXT: String = "documents.host_steps_next"
        const val CAPTURE_SIDE_FRONT: String = "capture.side_front"
        const val CAPTURE_SIDE_BACK: String = "capture.side_back"
        const val CAPTURE_SOURCE_CAMERA: String = "capture.source_camera"
        const val CAPTURE_SOURCE_UPLOAD: String = "capture.source_upload"
        const val CAPTURE_INSTRUCTION_CAMERA: String = "capture.instruction_camera"
        const val CAPTURE_INSTRUCTION_EITHER: String = "capture.instruction_either"
        const val CAPTURE_INSTRUCTION_UPLOAD: String = "capture.instruction_upload"
        const val CAPTURE_INSTRUCTION_UPLOAD_PDF: String = "capture.instruction_upload_pdf"
        const val CAPTURE_ALSO_SAVED_FILE: String = "capture.also_saved_file"
        const val CAPTURE_CHECK_PHOTO: String = "capture.check_photo"
        const val CAPTURE_TAKE_PHOTO: String = "capture.take_photo"
        const val CAPTURE_CHOOSE_FILE: String = "capture.choose_file"
        const val CAPTURE_RETAKE_PHOTO: String = "capture.retake_photo"
        const val CAPTURE_CHANGE_FILE: String = "capture.change_file"
        const val CAPTURE_USE_FILE_INSTEAD: String = "capture.use_file_instead"
        const val CAPTURE_USE_PHOTO_INSTEAD: String = "capture.use_photo_instead"
        const val CAPTURE_FILE_LIMITS: String = "capture.file_limits"
        const val CAPTURE_PREVIEW_PDF: String = "capture.preview_pdf"
        const val CAPTURE_PREVIEW_DOCUMENT: String = "capture.preview_document"
        const val CAPTURE_PREVIEW_LABEL: String = "capture.preview_label"
        const val REVIEW_TITLE: String = "review.title"
        const val REVIEW_BODY: String = "review.body"
        const val REVIEW_ITEM: String = "review.item"
        const val REVIEW_CHANGE: String = "review.change"
        const val REVIEW_SEND: String = "review.send"
        const val SENDING_UPLOADING: String = "sending.uploading"
        const val SENDING_UPLOADING_PROGRESS: String = "sending.uploading_progress"
        const val SENDING_SENDING: String = "sending.sending"
        const val SENDING_SENT_TITLE: String = "sending.sent_title"
        const val SENDING_SENT_BODY: String = "sending.sent_body"
        const val SENDING_CHECK_AGAIN: String = "sending.check_again"
        const val RECEIPT_TITLE: String = "receipt.title"
        const val RECEIPT_BODY: String = "receipt.body"
        const val RECEIPT_DOCUMENT: String = "receipt.document"
        const val RECEIPT_SELFIE: String = "receipt.selfie"
        const val RECEIPT_LOCATION: String = "receipt.location"
        const val RECEIPT_HOST_STEPS: String = "receipt.host_steps"
        const val RECEIPT_DONE: String = "receipt.done"
        const val SELFIE_TITLE: String = "selfie.title"
        const val SELFIE_GUIDANCE: String = "selfie.guidance"
        const val SELFIE_TIP_FACE_CAMERA: String = "selfie.tip_face_camera"
        const val SELFIE_TIP_LIGHT: String = "selfie.tip_light"
        const val SELFIE_TIP_NEUTRAL: String = "selfie.tip_neutral"
        const val SELFIE_TIP_GLASSES: String = "selfie.tip_glasses"
        const val SELFIE_OPEN_CAMERA: String = "selfie.open_camera"
        const val SELFIE_STARTING_CAMERA: String = "selfie.starting_camera"
        const val SELFIE_TAKE: String = "selfie.take"
        const val SELFIE_REVIEW_TITLE: String = "selfie.review_title"
        const val SELFIE_REVIEW_BODY: String = "selfie.review_body"
        const val SELFIE_RETAKE: String = "selfie.retake"
        const val SELFIE_SEND: String = "selfie.send"
        const val SELFIE_SENDING: String = "selfie.sending"
        const val SELFIE_PREVIEW_LABEL: String = "selfie.preview_label"
        const val SELFIE_PRIVACY: String = "selfie.privacy"
        const val LIVENESS_TITLE: String = "liveness.title"
        const val LIVENESS_BODY: String = "liveness.body"
        const val LIVENESS_START: String = "liveness.start"
        const val LIVENESS_PROGRESS: String = "liveness.progress"
        const val LIVENESS_POSITION_NO_FACE: String = "liveness.position.no_face"
        const val LIVENESS_POSITION_MOVE_CLOSER: String = "liveness.position.move_closer"
        const val LIVENESS_POSITION_MOVE_FURTHER: String = "liveness.position.move_further"
        const val LIVENESS_POSITION_CENTER: String = "liveness.position.center"
        const val LIVENESS_POSITION_ONE_FACE: String = "liveness.position.one_face"
        const val LIVENESS_POSITION_HOLD_STILL: String = "liveness.position.hold_still"
        const val LIVENESS_POSITION_MORE_LIGHT: String = "liveness.position.more_light"
        const val LIVENESS_POSITION_READY: String = "liveness.position.ready"
        const val LIVENESS_GESTURE_BLINK: String = "liveness.gesture.blink"
        const val LIVENESS_GESTURE_SMILE: String = "liveness.gesture.smile"
        const val LIVENESS_GESTURE_TURN_LEFT: String = "liveness.gesture.turn_left"
        const val LIVENESS_GESTURE_TURN_RIGHT: String = "liveness.gesture.turn_right"
        const val LIVENESS_GESTURE_NOD: String = "liveness.gesture.nod"
        const val LIVENESS_GESTURE_DONE: String = "liveness.gesture_done"
        const val LIVENESS_FACE_LOST: String = "liveness.face_lost"
        const val LIVENESS_SUCCESS: String = "liveness.success"
        const val LIVENESS_TIMEOUT: String = "liveness.timeout"
        const val LIVENESS_EXPIRED: String = "liveness.expired"
        const val LIVENESS_RETRY: String = "liveness.retry"
        const val LIVENESS_SENDING: String = "liveness.sending"
        const val VIDEO_CONSENT: String = "video.consent"
        const val VIDEO_RECORDING: String = "video.recording"
        const val LOCATION_TITLE: String = "location.title"
        const val LOCATION_WHY: String = "location.why"
        const val LOCATION_WHAT_SHARED: String = "location.what_shared"
        const val LOCATION_PERMISSION_PROMPT: String = "location.permission_prompt"
        const val LOCATION_SHARE: String = "location.share"
        const val LOCATION_WAITING: String = "location.waiting"
        const val LOCATION_WAITING_ACCURACY: String = "location.waiting_accuracy"
        const val LOCATION_FOUND_TITLE: String = "location.found_title"
        const val LOCATION_FOUND_BODY: String = "location.found_body"
        const val LOCATION_ACCURACY_LABEL: String = "location.accuracy_label"
        const val LOCATION_ACCURACY_VALUE: String = "location.accuracy_value"
        const val LOCATION_COORDINATES_LABEL: String = "location.coordinates_label"
        const val LOCATION_TIME_LABEL: String = "location.time_label"
        const val LOCATION_SEND: String = "location.send"
        const val LOCATION_PERMISSION_DENIED: String = "location.permission_denied"
        const val LOCATION_PERMISSION_DENIED_BROWSER: String = "location.permission_denied_browser"
        const val LOCATION_UNAVAILABLE: String = "location.unavailable"
        const val LOCATION_TIMEOUT: String = "location.timeout"
        const val LOCATION_LOW_ACCURACY: String = "location.low_accuracy"
        const val LOCATION_MOCK_DETECTED: String = "location.mock_detected"
        const val LOCATION_RETRY: String = "location.retry"
        const val LOCATION_RETRIES_EXHAUSTED: String = "location.retries_exhausted"
        const val LOCATION_PLACE_TITLE_WITHIN: String = "location.place.title_within"
        const val LOCATION_PLACE_TITLE_OUTSIDE: String = "location.place.title_outside"
        const val LOCATION_PLACE_NEED_WITHIN: String = "location.place.need_within"
        const val LOCATION_PLACE_NEED_WITHIN_ANY: String = "location.place.need_within_any"
        const val LOCATION_PLACE_NEED_WITHIN_ALL: String = "location.place.need_within_all"
        const val LOCATION_PLACE_NEED_OUTSIDE: String = "location.place.need_outside"
        const val LOCATION_PLACE_NEED_OUTSIDE_ALL: String = "location.place.need_outside_all"
        const val LOCATION_PLACE_NEED_OUTSIDE_ANY: String = "location.place.need_outside_any"
        const val LOCATION_PLACE_NOT_MATCHED_WITHIN: String = "location.place.not_matched_within"
        const val LOCATION_PLACE_NOT_MATCHED_OUTSIDE: String = "location.place.not_matched_outside"
        const val LOCATION_PLACE_NOT_PRECISE: String = "location.place.not_precise"
        const val LOCATION_PLACE_REJECTED: String = "location.place.rejected"
        const val LOCATION_PLACE_RETRY: String = "location.place.retry"
        const val ERRORS_GENERIC: String = "errors.generic"
        const val ERRORS_NETWORK: String = "errors.network"
        const val ERRORS_SESSION_EXPIRED: String = "errors.session_expired"
        const val ERRORS_START_FAILED: String = "errors.start_failed"
        const val ERRORS_REFRESH_FAILED: String = "errors.refresh_failed"
        const val ERRORS_CAMERA_DENIED: String = "errors.camera_denied"
        const val ERRORS_CAMERA_DENIED_BROWSER: String = "errors.camera_denied_browser"
        const val ERRORS_CAMERA_UNAVAILABLE: String = "errors.camera_unavailable"
        const val ERRORS_FRONT_CAMERA_REQUIRED: String = "errors.front_camera_required"
        const val ERRORS_CAMERA_NO_PHOTO: String = "errors.camera_no_photo"
        const val ERRORS_FILE_UNREADABLE: String = "errors.file_unreadable"
        const val ERRORS_FILE_TYPE: String = "errors.file_type"
        const val ERRORS_FILE_TOO_LARGE: String = "errors.file_too_large"
        const val ERRORS_FILE_EMPTY: String = "errors.file_empty"
        const val ERRORS_PHOTO_FORMAT: String = "errors.photo_format"
        const val ERRORS_PHOTO_INVALID: String = "errors.photo_invalid"
        const val ERRORS_DOCUMENT_UNSUPPORTED: String = "errors.document_unsupported"
        const val ERRORS_ADD_FILE_FAILED: String = "errors.add_file_failed"
        const val ERRORS_SEND_DOCUMENT_FAILED: String = "errors.send_document_failed"
        const val ERRORS_SELFIE_FAILED: String = "errors.selfie_failed"
        const val ERRORS_SEND_SELFIE_FAILED: String = "errors.send_selfie_failed"
        const val ERRORS_CHALLENGE_FAILED: String = "errors.challenge_failed"
        const val ERRORS_SEND_LOCATION_FAILED: String = "errors.send_location_failed"
    }

    object ErrorCode {
        const val LOCATION_ACCURACY: String = "location.accuracy"
        const val LOCATION_STALE: String = "location.stale"
        const val LOCATION_MOCK: String = "location.mock"
        const val LOCATION_IP_COUNTRY: String = "location.ip_country"
        const val LOCATION_REFERENCE_NOT_MATCHED: String = "location.reference_not_matched"
        const val LIVENESS_CHALLENGE_INVALID: String = "liveness.challenge_invalid"
        const val LIVENESS_CHALLENGE_EXPIRED: String = "liveness.challenge_expired"
        const val LIVENESS_CHALLENGE_USED: String = "liveness.challenge_used"
        const val LIVENESS_GESTURE_MISMATCH: String = "liveness.gesture_mismatch"
        const val LIVENESS_FRAME_OUT_OF_WINDOW: String = "liveness.frame_out_of_window"
        const val LIVENESS_NOT_CAMERA: String = "liveness.not_camera"
        const val LIVENESS_VIDEO_MISSING: String = "liveness.video_missing"
        const val LIVENESS_VIDEO_TOO_LONG: String = "liveness.video_too_long"
        const val LIVENESS_VIDEO_UNREADABLE: String = "liveness.video_unreadable"
        val MEANINGS: Map<String, String> = mapOf(
            "location.accuracy" to "Accuracy is missing or larger than max_accuracy_m",
            "location.stale" to "fix_time is missing, older than max_age_s plus clock_skew_s, or in the future",
            "location.mock" to "is_mock is true and mock_location is reject",
            "location.ip_country" to "The IP country does not match and ip_country is reject",
            "location.reference_not_matched" to "The place rule failed and on_mismatch is reject",
            "liveness.challenge_invalid" to "The challenge is unknown, belongs to another case or was replaced",
            "liveness.challenge_expired" to "The challenge expired",
            "liveness.challenge_used" to "The challenge was already used",
            "liveness.gesture_mismatch" to "A gesture frame is missing, extra, out of order or for a gesture the challenge does not include",
            "liveness.frame_out_of_window" to "A file was uploaded before the challenge was issued or after it expired",
            "liveness.not_camera" to "Evidence was not uploaded with capture_source=camera for this challenge",
            "liveness.video_missing" to "record_video is on and no video for this challenge was sent",
            "liveness.video_too_long" to "The video is longer than max_video_seconds plus one second",
            "liveness.video_unreadable" to "The server cannot read the video duration",
        )
    }

    object Tokens {
        val LIGHT: SpecPalette = SpecPalette(
            primary = 0xFF20315F,
            onPrimary = 0xFFFFFFFF,
            surface = 0xFFFFFFFF,
            ink = 0xFF18233C,
            muted = 0xFF52617A,
            soft = 0xFFF0F3FA,
            border = 0xFFCAD4E5,
            accent = 0xFFDC6E11,
            success = 0xFF126749,
            error = 0xFFAA2525,
            warningSoft = 0xFFFDF1E6,
            warningInk = 0xFF7A3B06,
        )
        val DARK: SpecPalette = SpecPalette(
            primary = 0xFFB9CDFF,
            onPrimary = 0xFF16294E,
            surface = 0xFF111C31,
            ink = 0xFFF4F7FE,
            muted = 0xFFB7C4DA,
            soft = 0xFF1A2942,
            border = 0xFF566983,
            accent = 0xFFFFB579,
            success = 0xFF8FE0B8,
            error = 0xFFFFB4AB,
            warningSoft = 0xFF3A2A1A,
            warningInk = 0xFFFFD3AE,
        )
        const val FONT_FAMILY: String = "Ubuntu"
        const val FONT_WEIGHTS_REGULAR: Int = 400
        const val FONT_WEIGHTS_MEDIUM: Int = 500
        const val FONT_WEIGHTS_BOLD: Int = 700
        const val TEXT_ALIGN: String = "center"
        val TYPE_TITLE: SpecTextStyle = SpecTextStyle(
            size = 24.0,
            lineHeight = 30.0,
            weight = 700,
        )
        val TYPE_SUBTITLE: SpecTextStyle = SpecTextStyle(
            size = 17.0,
            lineHeight = 24.0,
            weight = 500,
        )
        val TYPE_BODY: SpecTextStyle = SpecTextStyle(
            size = 16.0,
            lineHeight = 24.0,
            weight = 400,
        )
        val TYPE_BUTTON: SpecTextStyle = SpecTextStyle(
            size = 16.0,
            lineHeight = 22.0,
            weight = 500,
        )
        val TYPE_LABEL: SpecTextStyle = SpecTextStyle(
            size = 14.0,
            lineHeight = 20.0,
            weight = 500,
        )
        val TYPE_SMALL: SpecTextStyle = SpecTextStyle(
            size = 13.0,
            lineHeight = 19.0,
            weight = 400,
        )
        val TYPE_EYEBROW: SpecTextStyle = SpecTextStyle(
            size = 13.0,
            lineHeight = 18.0,
            weight = 500,
        )
        val TYPE_CHIP: SpecTextStyle = SpecTextStyle(
            size = 12.0,
            lineHeight = 16.0,
            weight = 500,
        )
        const val SPACING_XXS: Int = 2
        const val SPACING_XS: Int = 4
        const val SPACING_SM: Int = 8
        const val SPACING_MD: Int = 12
        const val SPACING_LG: Int = 16
        const val SPACING_XL: Int = 24
        const val SPACING_XXL: Int = 32
        const val SPACING_GUTTER: Int = 24
        const val SPACING_GAP: Int = 16
        const val SPACING_SECTION: Int = 10
        const val RADIUS_XS: Int = 3
        const val RADIUS_SM: Int = 6
        const val RADIUS_MD: Int = 10
        const val RADIUS_LG: Int = 14
        const val RADIUS_XL: Int = 16
        const val RADIUS_SHEET: Int = 24
        const val RADIUS_PILL: Int = 999
        const val SIZE_BUTTON_HEIGHT: Int = 56
        const val SIZE_LINK_HEIGHT: Int = 48
        const val SIZE_OPTION_HEIGHT: Int = 72
        const val SIZE_HEADER_HEIGHT: Int = 56
        const val SIZE_MAX_CONTENT_WIDTH: Int = 600
        const val SIZE_MARK: Int = 30
        const val SIZE_ICON: Int = 24
        const val SIZE_ILLUSTRATION: Int = 64
        const val SIZE_ICON_STROKE: Double = 1.75
    }

    object Icons {
        val CAMERA: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 4 8.5 C 4 7.395 4.895 6.5 6 6.5 L 7.6 6.5 C 7.915 6.5 8.211 6.352 8.4 6.1 L 9.6 4.4 C 9.789 4.148 10.085 4 10.4 4 L 13.6 4 C 13.915 4 14.211 4.148 14.4 4.4 L 15.6 6.1 C 15.789 6.352 16.085 6.5 16.4 6.5 L 18 6.5 C 19.105 6.5 20 7.395 20 8.5 L 20 17.5 C 20 18.605 19.105 19.5 18 19.5 L 6 19.5 C 4.895 19.5 4 18.605 4 17.5 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 15.25 12.75 C 15.25 14.545 13.795 16 12 16 C 10.205 16 8.75 14.545 8.75 12.75 C 8.75 10.955 10.205 9.5 12 9.5 C 13.795 9.5 15.25 10.955 15.25 12.75 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val CHEVRON_LEFT: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 14.5 6 L 8.5 12 L 14.5 18",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val CHEVRON_RIGHT: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 9.5 6 L 15.5 12 L 9.5 18",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val DOCUMENT: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 14 3 L 7 3 C 5.895 3 5 3.895 5 5 L 5 19 C 5 20.105 5.895 21 7 21 L 17 21 C 18.105 21 19 20.105 19 19 L 19 8 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 14 3 L 14 7 C 14 7.552 14.448 8 15 8 L 19 8",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 9 13 L 15 13",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 9 17 L 13 17",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val ERROR: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 21 12 C 21 16.971 16.971 21 12 21 C 7.029 21 3 16.971 3 12 C 3 7.029 7.029 3 12 3 C 16.971 3 21 7.029 21 12 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 12 7.75 L 12 12.75",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 12 16.25 L 12 16.26",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val ID_CARD: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 5 5 L 19 5 C 20.105 5 21 5.895 21 7 L 21 17 C 21 18.105 20.105 19 19 19 L 5 19 C 3.895 19 3 18.105 3 17 L 3 7 C 3 5.895 3.895 5 5 5 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 10.25 10 C 10.25 10.966 9.466 11.75 8.5 11.75 C 7.534 11.75 6.75 10.966 6.75 10 C 6.75 9.034 7.534 8.25 8.5 8.25 C 9.466 8.25 10.25 9.034 10.25 10 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 5.75 15.5 C 6.25 14.1 7.25 13.25 8.5 13.25 C 9.75 13.25 10.75 14.1 11.25 15.5",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 14 10 L 18 10",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 14 14 L 16.5 14",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val LOCATION: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 12 21.25 C 12 21.25 5.5 15.6 5.5 10 C 5.5 6.41 8.41 3.5 12 3.5 C 15.59 3.5 18.5 6.41 18.5 10 C 18.5 15.6 12 21.25 12 21.25 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 14.25 10 C 14.25 11.243 13.243 12.25 12 12.25 C 10.757 12.25 9.75 11.243 9.75 10 C 9.75 8.757 10.757 7.75 12 7.75 C 13.243 7.75 14.25 8.757 14.25 10 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val RECEIVED: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 14 3 L 7 3 C 5.895 3 5 3.895 5 5 L 5 19 C 5 20.105 5.895 21 7 21 L 17 21 C 18.105 21 19 20.105 19 19 L 19 8 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 14 3 L 14 7 C 14 7.552 14.448 8 15 8 L 19 8",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 9 14.75 L 11.25 17 L 15.25 12.5",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val RECEIVED_ILLUSTRATION: SpecIcon = SpecIcon(
            viewBox = 64.0,
            strokeWidth = 2.5,
            paths = listOf(
                SpecIconPath(
                    d = "M 62 32 C 62 48.569 48.569 62 32 62 C 15.431 62 2 48.569 2 32 C 2 15.431 15.431 2 32 2 C 48.569 2 62 15.431 62 32 Z",
                    stroke = false,
                    fill = true,
                    strokeWidth = 2.5,
                    strokeOpacity = 1.0,
                    fillOpacity = 0.08,
                ),
                SpecIconPath(
                    d = "M 36 12 L 23 12 C 20.791 12 19 13.791 19 16 L 19 48 C 19 50.209 20.791 52 23 52 L 41 52 C 43.209 52 45 50.209 45 48 L 45 21 Z",
                    stroke = true,
                    fill = true,
                    strokeWidth = 2.5,
                    strokeOpacity = 1.0,
                    fillOpacity = 0.16,
                ),
                SpecIconPath(
                    d = "M 36 12 L 36 18 C 36 19.657 37.343 21 39 21 L 45 21",
                    stroke = true,
                    fill = false,
                    strokeWidth = 2.5,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 25 24 L 31 24",
                    stroke = true,
                    fill = false,
                    strokeWidth = 2.5,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 25 30 L 39 30",
                    stroke = true,
                    fill = false,
                    strokeWidth = 2.5,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 25.5 39.5 L 30 44 L 38.5 34.5",
                    stroke = true,
                    fill = false,
                    strokeWidth = 3.0,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val SELFIE: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 4 8 L 4 6 C 4 4.895 4.895 4 6 4 L 8 4",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 16 4 L 18 4 C 19.105 4 20 4.895 20 6 L 20 8",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 20 16 L 20 18 C 20 19.105 19.105 20 18 20 L 16 20",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 8 20 L 6 20 C 4.895 20 4 19.105 4 18 L 4 16",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 15 10 C 15 11.657 13.657 13 12 13 C 10.343 13 9 11.657 9 10 C 9 8.343 10.343 7 12 7 C 13.657 7 15 8.343 15 10 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 7.5 17.5 C 8.4 15.6 10.1 14.5 12 14.5 C 13.9 14.5 15.6 15.6 16.5 17.5",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val UPLOAD: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 12 15 L 12 4",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 7.5 8.5 L 12 4 L 16.5 8.5",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 4 15 L 4 18 C 4 19.105 4.895 20 6 20 L 18 20 C 19.105 20 20 19.105 20 18 L 20 15",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val VIDEO: SpecIcon = SpecIcon(
            viewBox = 24.0,
            strokeWidth = 1.75,
            paths = listOf(
                SpecIconPath(
                    d = "M 3 7.5 C 3 6.395 3.895 5.5 5 5.5 L 13 5.5 C 14.105 5.5 15 6.395 15 7.5 L 15 16.5 C 15 17.605 14.105 18.5 13 18.5 L 5 18.5 C 3.895 18.5 3 17.605 3 16.5 Z",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
                SpecIconPath(
                    d = "M 15 10.5 L 20.2 7.6 C 20.352 7.486 20.554 7.468 20.724 7.553 C 20.893 7.637 21 7.811 21 8 L 21 16 C 21 16.189 20.893 16.363 20.724 16.447 C 20.554 16.532 20.352 16.514 20.2 16.4 L 15 13.5",
                    stroke = true,
                    fill = false,
                    strokeWidth = 1.75,
                    strokeOpacity = 1.0,
                    fillOpacity = 1.0,
                ),
            ),
        )
        val ALL: Map<String, SpecIcon> = mapOf(
            "camera" to CAMERA,
            "chevron_left" to CHEVRON_LEFT,
            "chevron_right" to CHEVRON_RIGHT,
            "document" to DOCUMENT,
            "error" to ERROR,
            "id_card" to ID_CARD,
            "location" to LOCATION,
            "received" to RECEIVED,
            "received_illustration" to RECEIVED_ILLUSTRATION,
            "selfie" to SELFIE,
            "upload" to UPLOAD,
            "video" to VIDEO,
        )
    }
}
