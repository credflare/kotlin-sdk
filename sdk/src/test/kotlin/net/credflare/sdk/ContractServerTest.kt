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
    fun everyRejectionFixtureMapsItsCodeToTheSpecScreen() {
        val directory = File("../example/src/androidTest/assets/rejections")
        val files = directory.listFiles { file -> file.name.endsWith(".json") }!!.sortedBy { it.name }
        assertEquals(CredflareSpec.ErrorCode.MEANINGS.keys.sorted(), files.map { it.name.removeSuffix(".json") })
        val challengeId = UUID.randomUUID().toString()
        for(file in files) {
            val code = file.name.removeSuffix(".json")
            val client = session("reject:$code")
            val error = try {
                when(code.substringBefore('.')) {
                    "location" -> client.submitLocation(fix, UUID.randomUUID())
                    "liveness" -> client.submitFacial(FacialSubmission(challengeId, 1, listOf(2), 3, "mlkit", listOf(GestureResult("smile", true, "2026-11-21T00:40:00.000Z")), FaceDetectionSummary(true, 1, true, true), "2026-11-21T00:40:00.000Z"), UUID.randomUUID())
                    else -> if(code.endsWith("resend_too_soon") || code.endsWith("send_limit") || code.endsWith("not_configured")) client.requestCode(code.substringBefore('.'), "+237650000001")
                        else client.confirmCode(code.substringBefore('.'), "123456")
                }
                fail("$code was not rejected"); throw IllegalStateException()
            } catch(error: CredflareException) { error }
            assertEquals(code, 422, error.status)
            assertEquals(code, code, error.code)
            assertTrue(code, error.isKnownCode)
            assertEquals(code, JSONObject(file.readText()).getString("message"), error.message)
            val entry = CredflareSpec.Rejection.CODES.getValue(code)
            val screen = Rejections.screen(error, null, "Acme")
            assertEquals(code, SpecCopy.text(entry.title, "verifier" to "Acme"), screen.title)
            assertEquals(code, SpecCopy.text(entry.message, "verifier" to "Acme"), screen.message)
            assertEquals(code, entry.action, screen.action)
            assertEquals(code, SpecCopy.text(CredflareSpec.Rejection.ACTION_LABELS.getValue(entry.action)), screen.actionLabel)
            assertEquals("Requested by Acme.", screen.requestedBy)
            assertEquals("If you need help, contact Acme.", screen.help)
        }
    }

    @Test
    fun accuracyWithAPlaceAsksForAPreciseFix() {
        val error = try { session("reject:${CredflareSpec.ErrorCode.LOCATION_ACCURACY}").submitLocation(fix, UUID.randomUUID()); fail(); throw IllegalStateException() } catch(error: CredflareException) { error }
        assertEquals(SpecCopy.text(CredflareSpec.CopyKey.LOCATION_PLACE_NOT_PRECISE), Rejections.screen(error, LocationReference("within", "any", listOf("Head office")), "Acme").message)
        val outside = try { session("reject:${CredflareSpec.ErrorCode.LOCATION_REFERENCE_NOT_MATCHED}").submitLocation(fix, UUID.randomUUID()); fail(); throw IllegalStateException() } catch(error: CredflareException) { error }
        assertEquals("You seem to be too close to Depot. Move away, then try again.", Rejections.screen(outside, LocationReference("outside", "all", listOf("Depot")), "Acme").message)
    }

    @Test
    fun realFixtureWithUnknownCodeKeepsTheRawCodeAndShowsTheServerMessage() {
        val envelope = JSONObject(File("../example/src/androidTest/assets/rejections/location.accuracy.json").readText()).put("code", "location.not_in_spec")
        val error = CredflareException.fromEnvelope(422, envelope)
        assertEquals("location.not_in_spec", error.code)
        assertFalse(error.isKnownCode)
        val screen = Rejections.screen(error, null, "Acme")
        assertEquals(envelope.getString("message"), screen.message)
        assertEquals(null, screen.title)
        assertEquals("retry", screen.action)
        assertEquals(SpecCopy.text(CredflareSpec.CopyKey.REJECTION_ACTION_RETRY), screen.actionLabel)
    }
}
