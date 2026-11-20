package net.credflare.sdk

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class VerificationSession(private var descriptor: SessionDescriptor, private val timeoutMillis: Int = 60000) {
    init { require(timeoutMillis > 0) }
    fun replaceSession(next: SessionDescriptor) {
        require(next.apiBaseUrl.trimEnd('/') == descriptor.apiBaseUrl.trimEnd('/')) { "Renew access using the same API." }
        descriptor = next
    }
    private fun request(method: String, suffix: String = "", body: ByteArray? = null, contentType: String? = null, key: UUID? = null): JSONObject {
        val connection = URL("${descriptor.apiBaseUrl.trimEnd('/')}/sdk/sessions/${descriptor.sessionId}$suffix").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false
        connection.connectTimeout = timeoutMillis; connection.readTimeout = timeoutMillis
        connection.requestMethod = method; connection.useCaches = false
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Accept-Encoding", "identity")
        connection.setRequestProperty("Authorization", "Bearer ${descriptor.token}")
        if(contentType != null) connection.setRequestProperty("Content-Type", contentType)
        if(key != null) connection.setRequestProperty("X-Idempotency-Key", key.toString())
        val executor = Executors.newSingleThreadExecutor()
        val pending = executor.submit<JSONObject> {
            try {
                if(body != null) { connection.doOutput = true; connection.setFixedLengthStreamingMode(body.size); connection.outputStream.use { it.write(body) } }
                val status = connection.responseCode
                val stream = if(status in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.use { it.readBytes() } ?: ByteArray(0)
                val expected = connection.contentLengthLong
                if(expected >= 0 && expected != raw.size.toLong()) throw IOException("Incomplete response body")
                val text = raw.toString(Charsets.UTF_8)
                val envelope = try { JSONObject(text) } catch(error: JSONException) { throw CredflareException(status, "Credflare returned an invalid response.", error) }
                if(status !in 200..299 || !envelope.optBoolean("success")) throw CredflareException(status, envelope.optString("message", "Request failed."))
                envelope
            } catch(error: IOException) { throw CredflareException(0, "Could not reach Credflare. Retry with the same evidence.", error) }
            finally { connection.disconnect() }
        }
        try { return pending.get(timeoutMillis.toLong(), TimeUnit.MILLISECONDS) }
        catch(error: TimeoutException) { pending.cancel(true); connection.disconnect(); throw CredflareException(0, "Credflare timed out. Retry with the same evidence.", error) }
        catch(error: InterruptedException) { pending.cancel(true); connection.disconnect(); Thread.currentThread().interrupt(); throw CredflareException(0, "Request cancelled.", error) }
        catch(error: ExecutionException) { throw error.cause ?: error }
        finally { executor.shutdownNow() }
    }
    fun get(): SessionInfo = SessionInfo.fromJson(request("GET").getJSONObject("data"))
    fun cancel() { request("DELETE") }
    fun upload(bytes: ByteArray, filename: String, source: CaptureSource, side: DocumentSide, documentTypeId: Int, key: UUID): Int = uploadEvidence(bytes,filename,source,side,documentTypeId,key)
    fun uploadSelfie(bytes: ByteArray, filename: String, key: UUID): Int {
        require(filename.substringAfterLast('.').lowercase() in listOf("jpg","jpeg","png","webp")) { "Use a supported selfie image format." }
        return uploadEvidence(bytes,filename,CaptureSource.CAMERA,DocumentSide.FRONT,null,key)
    }
    private fun uploadEvidence(bytes: ByteArray, filename: String, source: CaptureSource, side: DocumentSide, documentTypeId: Int?, key: UUID): Int {
        require(filename.isNotBlank() && filename.none { it in "\r\n\"/\\" }) { "Choose a file with a valid name." }
        val mime = when(filename.substringAfterLast('.').lowercase()) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; else -> throw IllegalArgumentException("This file type is not supported.") }
        val boundary = "Credflare-${UUID.randomUUID()}"
        val body = ByteArrayOutputStream()
        fun text(value: String) { body.write(value.toByteArray(Charsets.UTF_8)) }
        for((name,value) in listOf("side" to side.value, "capture_source" to source.value, "document_type_id" to documentTypeId?.toString().orEmpty()).filter { it.second.isNotEmpty() }) text("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n")
        text("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: $mime\r\n\r\n")
        body.write(bytes); text("\r\n--$boundary--\r\n")
        return request("POST", if(documentTypeId == null) "/uploads/biometrics" else "/uploads/documents", body.toByteArray(), "multipart/form-data; boundary=$boundary", key).getJSONObject("data").getInt("id")
    }
    fun submitSelfie(imageFileId: Int, key: UUID): Int {
        val payload = JSONObject().put("type", "facial_verification").put("image_file_id", imageFileId)
        return request("POST", "/submit", payload.toString().toByteArray(Charsets.UTF_8), "application/json", key).getJSONObject("data").getInt("id")
    }
    fun submit(documentTypeId: Int, evidence: List<Evidence>, key: UUID): Int {
        val payload = JSONObject().put("type", "document_verification").put("document_type_id", documentTypeId).put("evidence", JSONArray(evidence.map { it.json() }))
        return request("POST", "/submit", payload.toString().toByteArray(Charsets.UTF_8), "application/json", key).getJSONObject("data").getInt("id")
    }
}
