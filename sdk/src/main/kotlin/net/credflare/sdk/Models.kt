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
    val supported get() = policy in listOf("upload", "either", "snapshot", "snapshot_and_upload")
    val methodSummary get() = when(policy) { "upload" -> "Saved files"; "either" -> "Photo or file"; "snapshot" -> "Camera photos"; "snapshot_and_upload" -> "Photo and saved file"; else -> "Not available in this app" }
    fun captureSteps(): List<CaptureStep> {
        require(supported) { "This capture method is not available in this app." }
        val sources = when(policy) { "snapshot_and_upload" -> listOf("camera", "upload"); "snapshot" -> listOf("camera"); "either" -> listOf("either"); else -> listOf("upload") }
        return sources.flatMap { source -> sides.map { CaptureStep(it, source) } }
    }
    companion object {
        fun fromJson(value: JSONObject): DocumentRequirement {
            val type = value.getJSONObject("document_type")
            val policy = value.optString("capture_method").ifEmpty { if(type.getString("capture_method") == "camera") "either" else "upload" }
            val sides = value.optJSONArray("requested_sides")?.let { array -> (0 until array.length()).map { DocumentSide.valueOf(array.getString(it).uppercase()) } } ?: if(type.getBoolean("two_sided")) listOf(DocumentSide.FRONT, DocumentSide.BACK) else listOf(DocumentSide.FRONT)
            return DocumentRequirement(type.getInt("id"), type.getString("name"), policy, sides, value.optString("group_key").takeIf { it.isNotEmpty() && it != "null" }, value.optString("rule", "all_of"), value.optInt("remaining_required", value.optInt("min_required", 1)))
        }
    }
}
data class SessionInfo(val environment: String, val requesterName: String?, val maxFileSizeBytes: Long, val allowedFileTypes: List<String>, val documents: List<DocumentRequirement>, val needsHostSteps: Boolean, val requiresSelfie: Boolean = false) {
    companion object {
        fun fromJson(value: JSONObject): SessionInfo {
            val limits = value.getJSONObject("upload_limits")
            val types = limits.getJSONArray("allowed_file_types")
            val profile = value.optJSONObject("profile")
            val docs = profile?.optJSONArray("document_requirements") ?: JSONArray()
            return SessionInfo(value.getString("environment"), value.optJSONObject("requester")?.optString("name"), limits.getLong("max_file_size_bytes"), (0 until types.length()).map { types.getString(it).lowercase() }, (0 until docs.length()).map { DocumentRequirement.fromJson(docs.getJSONObject(it)) }, profile == null || (profile.optJSONArray("form_schema")?.length() ?: 0) > 0 || (0 until (profile.optJSONArray("verification_requirements")?.length() ?: 0)).any { profile.getJSONArray("verification_requirements").getJSONObject(it).optString("verification_type") != "facial_verification" },
                (0 until (profile?.optJSONArray("verification_requirements")?.length() ?: 0)).any { profile!!.getJSONArray("verification_requirements").getJSONObject(it).optString("verification_type") == "facial_verification" })
        }
    }
}
class CredflareException(val status: Int, message: String, cause: Throwable? = null): Exception(message, cause) {
    val type get() = when(status) { 0 -> "network"; 401 -> "session_unavailable"; 409 -> "verification_closed"; 422 -> "validation"; else -> "api" }
}
data class Evidence(val fileId: Int, val source: CaptureSource, val side: DocumentSide) {
    internal fun json() = JSONObject().put("file_id", fileId).put("source", source.value).put("side", side.value)
}
enum class VerificationResult { SUBMITTED, CANCELLED, HOST_ACTION_REQUIRED, FAILED }
