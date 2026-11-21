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
import java.util.UUID

class ContractServerTest {
    companion object {
        private lateinit var server: ContractServer
        private val sessionId = UUID.randomUUID()

        @JvmStatic @BeforeClass
        fun start() { server = ContractServer() }

        @JvmStatic @AfterClass
        fun stop() { server.close() }
    }

    private fun session(token: String) = server.session(token, sessionId)
    private fun stats() = server.stats()
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
    fun everyRejectionFixtureMapsItsCodeToApplicantCopy() {
        val directory = File("../example/src/androidTest/assets/rejections")
        val files = directory.listFiles { file -> file.name.endsWith(".json") }!!.sortedBy { it.name }
        assertEquals(CredflareSpec.ErrorCode.MEANINGS.keys.sorted(), files.map { it.name.removeSuffix(".json") })
        val place = LocationReference("within", "any", listOf("Head office"))
        val copy = { key: String -> SpecCopy.text(key) }
        val expected = mapOf(
            CredflareSpec.ErrorCode.LOCATION_ACCURACY to copy(CredflareSpec.CopyKey.LOCATION_LOW_ACCURACY),
            CredflareSpec.ErrorCode.LOCATION_STALE to copy(CredflareSpec.CopyKey.LOCATION_STALE),
            CredflareSpec.ErrorCode.LOCATION_MOCK to copy(CredflareSpec.CopyKey.LOCATION_MOCK_DETECTED),
            CredflareSpec.ErrorCode.LOCATION_IP_COUNTRY to copy(CredflareSpec.CopyKey.LOCATION_IP_COUNTRY_MISMATCH),
            CredflareSpec.ErrorCode.LOCATION_REFERENCE_NOT_MATCHED to "You do not seem to be at Head office yet. Move closer, then try again.",
            CredflareSpec.ErrorCode.LIVENESS_CHALLENGE_EXPIRED to copy(CredflareSpec.CopyKey.LIVENESS_EXPIRED),
            CredflareSpec.ErrorCode.LIVENESS_CHALLENGE_INVALID to copy(CredflareSpec.CopyKey.LIVENESS_EXPIRED),
            CredflareSpec.ErrorCode.LIVENESS_CHALLENGE_USED to copy(CredflareSpec.CopyKey.LIVENESS_EXPIRED),
            CredflareSpec.ErrorCode.LIVENESS_FRAME_OUT_OF_WINDOW to copy(CredflareSpec.CopyKey.LIVENESS_EXPIRED),
        )
        val challengeId = UUID.randomUUID().toString()
        for(file in files) {
            val code = file.name.removeSuffix(".json")
            val client = session("reject:$code")
            val error = try {
                if(code.startsWith("location.")) client.submitLocation(fix, UUID.randomUUID())
                else client.submitFacial(FacialSubmission(challengeId, 1, listOf(2), 3, "mlkit", listOf(GestureResult("smile", true, "2026-11-21T00:40:00.000Z")), FaceDetectionSummary(true, 1, true, true), "2026-11-21T00:40:00.000Z"), UUID.randomUUID())
                fail("$code was not rejected"); throw IllegalStateException()
            } catch(error: CredflareException) { error }
            assertEquals(code, 422, error.status)
            assertEquals(code, code, error.code)
            assertTrue(code, error.isKnownCode)
            val serverMessage = JSONObject(file.readText()).getString("message")
            assertEquals(code, serverMessage, error.message)
            val shown = if(code.startsWith("location.")) Rejections.locationMessage(error, if(code == CredflareSpec.ErrorCode.LOCATION_REFERENCE_NOT_MATCHED) place else null, "Acme") else Rejections.livenessMessage(error, "Acme")
            assertEquals(code, expected[code] ?: serverMessage, shown)
        }
    }

    @Test
    fun accuracyWithAPlaceAsksForAPreciseFix() {
        val error = try { session("reject:${CredflareSpec.ErrorCode.LOCATION_ACCURACY}").submitLocation(fix, UUID.randomUUID()); fail(); throw IllegalStateException() } catch(error: CredflareException) { error }
        assertEquals(SpecCopy.text(CredflareSpec.CopyKey.LOCATION_PLACE_NOT_PRECISE), Rejections.locationMessage(error, LocationReference("within", "any", listOf("Head office")), "Acme"))
        val outside = try { session("reject:${CredflareSpec.ErrorCode.LOCATION_REFERENCE_NOT_MATCHED}").submitLocation(fix, UUID.randomUUID()); fail(); throw IllegalStateException() } catch(error: CredflareException) { error }
        assertEquals("You seem to be too close to Depot. Move away, then try again.", Rejections.locationMessage(outside, LocationReference("outside", "all", listOf("Depot")), "Acme"))
    }

    @Test
    fun realFixtureWithUnknownCodeKeepsTheRawCodeAndShowsTheServerMessage() {
        val envelope = JSONObject(File("../example/src/androidTest/assets/rejections/location.accuracy.json").readText()).put("code", "location.not_in_spec")
        val error = CredflareException.fromEnvelope(422, envelope)
        assertEquals("location.not_in_spec", error.code)
        assertFalse(error.isKnownCode)
        assertEquals(envelope.getString("message"), Rejections.locationMessage(error, null, "Acme"))
        assertEquals(envelope.getString("message"), Rejections.livenessMessage(error, "Acme"))
    }
}
