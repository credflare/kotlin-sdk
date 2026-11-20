package net.credflare.sdk

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID

class SessionDescriptor(val sessionId: UUID, internal val token: String, val apiBaseUrl: String, val expiresAt: String) {
    init {
        val url = URI(apiBaseUrl)
        require(url.scheme == "https" || (url.scheme == "http" && url.host in listOf("localhost", "127.0.0.1", "::1", "[::1]"))) { "Use HTTPS or HTTP loopback for development." }
        require(url.host != null && url.userInfo == null && url.query == null && url.fragment == null && token.isNotBlank()) { "Invalid session descriptor." }
    }
    override fun toString() = "Credflare session $sessionId"
    companion object {
        fun fromJson(value: JSONObject) = SessionDescriptor(UUID.fromString(value.getString("session_id")), value.getString("token"), value.getString("api_base_url"), value.getString("expires_at"))
    }
}
enum class CaptureSource(val value: String) { CAMERA("camera"), UPLOAD("upload") }
enum class DocumentSide(val value: String) { FRONT("front"), BACK("back") }
data class CaptureStep(val side: DocumentSide, val input: String) { val id get() = "$input:${side.value}" }
data class DocumentRequirement(val id: Int, val name: String, val policy: String, val sides: List<DocumentSide>, val groupKey: String?, val rule: String, val remainingRequired: Int) {
    val supported get() = policy in listOf("upload", "snapshot_or_upload", "snapshot", "snapshot_and_upload")
    val methodSummary get() = SpecCopy.text(when(policy) { "upload" -> CredflareSpec.CopyKey.DOCUMENTS_METHOD_UPLOAD; "snapshot_or_upload" -> CredflareSpec.CopyKey.DOCUMENTS_METHOD_EITHER; "snapshot" -> CredflareSpec.CopyKey.DOCUMENTS_METHOD_CAMERA; "snapshot_and_upload" -> CredflareSpec.CopyKey.DOCUMENTS_METHOD_BOTH; else -> CredflareSpec.CopyKey.DOCUMENTS_UNAVAILABLE }, "verifier" to SpecCopy.verifier(null))
    fun captureSteps(): List<CaptureStep> {
        require(supported) { "This capture method is not available in this app." }
        val sources = when(policy) { "snapshot_and_upload" -> listOf("camera", "upload"); "snapshot" -> listOf("camera"); "snapshot_or_upload" -> listOf("camera_or_upload"); else -> listOf("upload") }
        return sources.flatMap { source -> sides.map { CaptureStep(it, source) } }
    }
    companion object {
        fun fromJson(value: JSONObject): DocumentRequirement {
            val type = value.getJSONObject("document_type")
            val policy = value.optString("capture_method").ifEmpty { if(type.getString("capture_method") == "camera") "snapshot_or_upload" else "upload" }.let { if(it == "either") "snapshot_or_upload" else it }
            val sides = value.optJSONArray("requested_sides")?.let { array -> (0 until array.length()).map { DocumentSide.valueOf(array.getString(it).uppercase()) } } ?: if(type.getBoolean("two_sided")) listOf(DocumentSide.FRONT, DocumentSide.BACK) else listOf(DocumentSide.FRONT)
            return DocumentRequirement(type.getInt("id"), type.getString("name"), policy, sides, value.optString("group_key").takeIf { it.isNotEmpty() && it != "null" }, value.optString("rule", "all_of"), value.optInt("remaining_required", value.optInt("min_required", 1)))
        }
    }
}
data class FacialSettings(val gestures: List<String>, val gestureCount: Int, val recordVideo: Boolean, val videoRetentionDays: Int) {
    companion object {
        fun fromJson(value: JSONObject?): FacialSettings = FacialSettings(
            value?.optJSONArray("gestures")?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: CredflareSpec.Settings.FACIAL_VERIFICATION_GESTURES_DEFAULT,
            value?.optInt("gesture_count", CredflareSpec.Settings.FACIAL_VERIFICATION_GESTURE_COUNT_DEFAULT) ?: CredflareSpec.Settings.FACIAL_VERIFICATION_GESTURE_COUNT_DEFAULT,
            value?.optBoolean("record_video", CredflareSpec.Settings.FACIAL_VERIFICATION_RECORD_VIDEO_DEFAULT) ?: CredflareSpec.Settings.FACIAL_VERIFICATION_RECORD_VIDEO_DEFAULT,
            value?.optInt("video_retention_days", CredflareSpec.Settings.FACIAL_VERIFICATION_VIDEO_RETENTION_DAYS_DEFAULT) ?: CredflareSpec.Settings.FACIAL_VERIFICATION_VIDEO_RETENTION_DAYS_DEFAULT)
    }
}
data class LocationReference(val rule: String, val placeLabels: List<String>) {
    companion object {
        fun fromJson(value: JSONObject?): LocationReference? {
            if(value == null) return null
            val places = value.optJSONArray("places") ?: JSONArray()
            return LocationReference(value.optString("rule").ifEmpty { "within" }, (0 until places.length()).mapNotNull { places.optJSONObject(it)?.optString("label")?.takeIf { label -> label.isNotBlank() } })
        }
    }
}
data class LocationSettings(val maxAccuracyM: Int, val maxAgeS: Int, val mockLocation: String, val ipCountry: String, val reference: LocationReference?) {
    companion object {
        fun fromJson(value: JSONObject?): LocationSettings = LocationSettings(
            value?.optInt("max_accuracy_m", CredflareSpec.Settings.LOCATION_VERIFICATION_MAX_ACCURACY_M_DEFAULT) ?: CredflareSpec.Settings.LOCATION_VERIFICATION_MAX_ACCURACY_M_DEFAULT,
            value?.optInt("max_age_s", CredflareSpec.Settings.LOCATION_VERIFICATION_MAX_AGE_S_DEFAULT) ?: CredflareSpec.Settings.LOCATION_VERIFICATION_MAX_AGE_S_DEFAULT,
            value?.optString("mock_location")?.ifEmpty { null } ?: CredflareSpec.Settings.LOCATION_VERIFICATION_MOCK_LOCATION_DEFAULT,
            value?.optString("ip_country")?.ifEmpty { null } ?: CredflareSpec.Settings.LOCATION_VERIFICATION_IP_COUNTRY_DEFAULT,
            LocationReference.fromJson(value?.optJSONObject("reference")))
    }
}
data class VerificationRequirement(val type: String, val facial: FacialSettings? = null, val location: LocationSettings? = null) {
    val collectedInApp get() = facial != null || location != null
    companion object {
        fun fromJson(value: JSONObject): VerificationRequirement {
            val type = value.optString("verification_type")
            val settings = value.optJSONObject("settings")
            return when(type) {
                "facial_verification" -> VerificationRequirement(type, facial = FacialSettings.fromJson(settings))
                "location_verification" -> VerificationRequirement(type, location = LocationSettings.fromJson(settings))
                else -> VerificationRequirement(type)
            }
        }
    }
}
data class SessionInfo(val environment: String, val requesterName: String?, val maxFileSizeBytes: Long, val allowedFileTypes: List<String>, val documents: List<DocumentRequirement>, val requirements: List<VerificationRequirement> = emptyList(), val hasForms: Boolean = false, val hasProfile: Boolean = true) {
    val needsHostSteps get() = !hasProfile || hasForms || requirements.any { !it.collectedInApp }
    val requiresFacial get() = requirements.any { it.facial != null }
    val requiresLocation get() = requirements.any { it.location != null }
    val nextRequirement get() = requirements.firstOrNull { it.collectedInApp }
    companion object {
        fun fromJson(value: JSONObject): SessionInfo {
            val limits = value.getJSONObject("upload_limits")
            val types = limits.getJSONArray("allowed_file_types")
            val profile = value.optJSONObject("profile")
            val docs = profile?.optJSONArray("document_requirements") ?: JSONArray()
            val requirements = profile?.optJSONArray("verification_requirements") ?: JSONArray()
            val requester = value.optJSONObject("requester")?.let { if(it.isNull("name")) null else it.optString("name").takeIf { name -> name.isNotBlank() } }
            return SessionInfo(value.getString("environment"), requester, limits.getLong("max_file_size_bytes"), (0 until types.length()).map { types.getString(it).lowercase() },
                (0 until docs.length()).map { DocumentRequirement.fromJson(docs.getJSONObject(it)) },
                (0 until requirements.length()).map { VerificationRequirement.fromJson(requirements.getJSONObject(it)) },
                (profile?.optJSONArray("form_schema")?.length() ?: 0) > 0, profile != null)
        }
    }
}
data class LivenessChallenge(val challengeId: String, val gestures: List<String>, val expiresAt: String, val recordVideo: Boolean, val maxVideoSeconds: Int) {
    companion object {
        fun fromJson(value: JSONObject): LivenessChallenge {
            val gestures = value.getJSONArray("gestures")
            return LivenessChallenge(UUID.fromString(value.getString("challenge_id")).toString(), (0 until gestures.length()).map { gestures.getString(it) }, value.getString("expires_at"), value.optBoolean("record_video", false), value.optInt("max_video_seconds", CredflareSpec.Liveness.VIDEO_MAX_SECONDS))
        }
    }
}
enum class BiometricUploadType(val value: String) { GESTURE("gesture"), LIVENESS_VIDEO("liveness_video"), SELFIE("selfie") }
data class GestureResult(val challenge: String, val completed: Boolean, val timestamp: String) {
    internal fun json() = JSONObject().put("challenge", challenge).put("completed", completed).put("timestamp", timestamp)
}
data class FaceDetectionSummary(val detected: Boolean, val faceCount: Int, val isCentered: Boolean, val isCorrectSize: Boolean) {
    internal fun json() = JSONObject().put("detected", detected).put("face_count", faceCount).put("is_centered", isCentered).put("is_correct_size", isCorrectSize)
}
data class FacialSubmission(val challengeId: String, val imageFileId: Int, val gestureFileIds: List<Int>, val videoFileId: Int?, val provider: String, val livenessResults: List<GestureResult>, val faceDetection: FaceDetectionSummary, val capturedAt: String) {
    internal fun json(): JSONObject = JSONObject().put("type", "facial_verification").put("challenge_id", challengeId).put("image_file_id", imageFileId)
        .put("gesture_file_ids", JSONArray(gestureFileIds)).put("video_file_id", videoFileId ?: JSONObject.NULL)
        .put("metadata", JSONObject().put("provider", provider).put("liveness_results", JSONArray(livenessResults.map { it.json() })).put("face_detection", faceDetection.json()).put("captured_at", capturedAt))
}
data class LocationFix(val latitude: Double, val longitude: Double, val accuracy: Double, val altitude: Double?, val fixTime: String, val isMock: Boolean?, val provider: String?, val capturedAt: String) {
    internal fun json(): JSONObject = JSONObject().put("type", "location_verification").put("latitude", latitude).put("longitude", longitude)
        .put("metadata", JSONObject().put("accuracy", accuracy).put("altitude", altitude ?: JSONObject.NULL).put("fix_time", fixTime).put("is_mock", isMock ?: JSONObject.NULL).put("provider", provider ?: JSONObject.NULL).put("captured_at", capturedAt))
}
class CredflareException(val status: Int, message: String, cause: Throwable? = null): Exception(message, cause) {
    val type get() = when(status) { 0 -> "network"; 401 -> "session_unavailable"; 409 -> "verification_closed"; 422 -> "validation"; else -> "api" }
}
data class Evidence(val fileId: Int, val source: CaptureSource, val side: DocumentSide) {
    internal fun json() = JSONObject().put("file_id", fileId).put("source", source.value).put("side", side.value)
}
enum class VerificationResult { SUBMITTED, CANCELLED, HOST_ACTION_REQUIRED, FAILED }
