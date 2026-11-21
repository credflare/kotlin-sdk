package net.credflare.sdk

import org.json.JSONArray
import org.json.JSONObject
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.net.URL
import java.util.UUID

class ContractServerTest {
    companion object {
        private var server: Process? = null
        private var root = ""
        private val sessionId = UUID.randomUUID()

        @JvmStatic @BeforeClass
        fun start() {
            val repo = File("..").canonicalFile
            val process = ProcessBuilder("node", File(repo, "scripts/contract-server.mjs").path, File(repo, "example/src/androidTest/assets").path)
                .apply { environment()["PORT"] = "0" }.redirectErrorStream(true).start()
            server = process
            val port = process.inputStream.bufferedReader().readLine()?.trim()?.toIntOrNull() ?: error("The contract server did not start")
            root = "http://127.0.0.1:$port"
        }

        @JvmStatic @AfterClass
        fun stop() { server?.destroy() }
    }

    private fun session(token: String) = VerificationSession(SessionDescriptor(sessionId, token, "$root/api/v1", "2030-01-01T00:00:00Z"))
    private fun stats() = JSONObject(URL("$root/stats").readText())
    private fun last(array: JSONArray, count: Int) = (array.length() - count until array.length()).map { array.getJSONObject(it) }
    private fun rejection(token: String, fix: LocationFix): CredflareException {
        try { session(token).submitLocation(fix, UUID.randomUUID()) } catch(error: CredflareException) { return error }
        fail("Expected a rejection"); throw IllegalStateException()
    }
    private val fix = LocationFix(4.0511, 9.7679, 12.5, null, "2026-11-21T00:40:00.000Z", false, "gps", "2026-11-21T00:40:01.000Z")

    @Test
    fun sendsIdempotencyKeysOnEveryUploadAndSubmit() {
        val capture = session("capture")
        val info = capture.get()
        val challenge = capture.createLivenessChallenge()
        val gestureKeys = challenge.gestures.map { UUID.randomUUID() }
        val bytes = byteArrayOf(1, 2, 3)
        val frames = challenge.gestures.mapIndexed { index, gesture -> capture.uploadBiometric(bytes, "$gesture.jpg", BiometricUploadType.GESTURE, challenge.challengeId, gesture, gestureKeys[index]) }
        val videoKey = UUID.randomUUID(); val selfieKey = UUID.randomUUID(); val facialKey = UUID.randomUUID(); val locationKey = UUID.randomUUID(); val documentKey = UUID.randomUUID(); val documentUploadKey = UUID.randomUUID()
        val video = capture.uploadBiometric(bytes, "clip.mp4", BiometricUploadType.LIVENESS_VIDEO, challenge.challengeId, key = videoKey)
        val selfie = capture.uploadBiometric(bytes, "selfie.jpg", BiometricUploadType.SELFIE, challenge.challengeId, key = selfieKey)
        capture.submitFacial(FacialSubmission(challenge.challengeId, selfie, frames, video, "mlkit", challenge.gestures.map { GestureResult(it, true, "2026-11-21T00:40:00.000Z") }, FaceDetectionSummary(true, 1, true, true), "2026-11-21T00:40:00.000Z"), facialKey)
        capture.submitLocation(fix.copy(isMock = null), locationKey)
        val document = session("document")
        val documentInfo = document.get()
        val file = document.upload(bytes, "front.png", CaptureSource.UPLOAD, DocumentSide.FRONT, documentInfo.documents[0].id, documentUploadKey)
        document.submit(documentInfo.documents[0].id, listOf(Evidence(file, CaptureSource.UPLOAD, DocumentSide.FRONT)), documentKey)

        assertTrue(info.requiresFacial && info.requiresLocation)
        val stats = stats()
        val uploads = last(stats.getJSONArray("biometrics"), 5)
        assertEquals(gestureKeys.map { it.toString() } + listOf(videoKey.toString(), selfieKey.toString()), uploads.map { it.getString("key") })
        assertEquals(documentUploadKey.toString(), stats.getString("requestKey"))
        val submits = last(stats.getJSONArray("submits"), 3)
        assertEquals(listOf("facial_verification", "location_verification", "document_verification"), submits.map { it.getString("type") })
        assertEquals(listOf(facialKey, locationKey, documentKey).map { it.toString() }, submits.map { it.getString("key") })
        assertTrue(JSONObject(stats.getString("locationBody")).getJSONObject("metadata").isNull("is_mock"))
    }

    @Test
    fun rejectionCodesFromTheRealFixturesMapToSpecCopy() {
        val accuracy = rejection("reject-location", fix)
        assertEquals(422, accuracy.status)
        assertEquals(CredflareSpec.ErrorCode.LOCATION_ACCURACY, accuracy.code)
        assertTrue(accuracy.isKnownCode)
        assertEquals(SpecCopy.text(CredflareSpec.CopyKey.LOCATION_LOW_ACCURACY), Rejections.locationMessage(accuracy, null, "Acme"))

        val place = rejection("reject-place", fix)
        assertEquals(CredflareSpec.ErrorCode.LOCATION_REFERENCE_NOT_MATCHED, place.code)
        assertEquals("You do not seem to be at Head office yet. Move closer, then try again.", Rejections.locationMessage(place, LocationReference("within", "any", listOf("Head office")), "Acme"))
        assertEquals("You seem to be too close to Depot. Move away, then try again.", Rejections.locationMessage(place, LocationReference("outside", "all", listOf("Depot")), "Acme"))
    }

    @Test
    fun challengeCodesAskForANewChallenge() {
        fun error(code: String?) = CredflareException(422, "Server reason", code = code)
        for(code in listOf("liveness.challenge_expired", "liveness.challenge_invalid", "liveness.challenge_used", "liveness.frame_out_of_window")) {
            assertEquals(SpecCopy.text(CredflareSpec.CopyKey.LIVENESS_EXPIRED), Rejections.livenessMessage(error(code), "Acme"))
        }
        assertEquals("Server reason", Rejections.livenessMessage(error("liveness.video_missing"), "Acme"))
        val unknown = CredflareException.fromEnvelope(422, JSONObject("""{"success":false,"message":"New reason","errors":[],"code":"liveness.future_rule"}"""))
        assertEquals("liveness.future_rule", unknown.code)
        assertFalse(unknown.isKnownCode)
        assertEquals("New reason", Rejections.locationMessage(unknown, null, "Acme"))
        assertEquals(null, CredflareException.fromEnvelope(422, JSONObject("""{"success":false,"message":"x","errors":[]}""")).code)
    }
}
