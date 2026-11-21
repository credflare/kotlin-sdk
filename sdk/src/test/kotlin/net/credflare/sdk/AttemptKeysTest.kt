package net.credflare.sdk

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.util.UUID

class AttemptKeysTest {
    companion object {
        private lateinit var server: ContractServer

        @JvmStatic @BeforeClass
        fun start() { server = ContractServer() }

        @JvmStatic @AfterClass
        fun stop() { server.close() }
    }

    private fun launch(token: String) = CredflareVerification.Launch(SessionProvider { error("Not used") }, {}).apply { client = server.session(token) }

    private fun untilSent(send: () -> Unit): Int {
        var failures = 0
        while(true) {
            try { send(); return failures }
            catch(error: CredflareException) {
                assertEquals("Only lost responses are expected", 0, error.status)
                failures += 1
                if(failures > 20) fail("The request never went through")
            }
        }
    }

    private fun liveness(): LivenessCapture {
        val challenge = LivenessChallenge.fromJson(ContractServer.fixture("liveness-challenge.json").getJSONObject("data"))
        val video = File.createTempFile("liveness", ".mp4").apply { writeBytes(byteArrayOf(0, 0, 0, 24)); deleteOnExit() }
        return LivenessCapture(challenge, "mlkit", challenge.gestures.map { byteArrayOf(1, 2, 3) }, byteArrayOf(4, 5, 6), video,
            challenge.gestures.map { GestureResult(it, true, "2026-11-21T00:40:00.000Z") }, FaceDetectionSummary(true, 1, true, true), "2026-11-21T00:40:01.000Z")
    }

    private val fix = LocationCapture.fix(4.0511, 9.7679, 12.5f, null, 1_795_221_600_000, false, "gps")

    @Test
    fun livenessRetriesReuseEveryKeyAndSkipFinishedUploads() {
        val current = launch("flaky")
        val capture = liveness()
        val video = capture.video!!
        current.liveness = capture
        current.challenge = capture.challenge

        val failures = untilSent { current.sendLiveness(capture, "Acme") }

        val keys = capture.gestureKeys + listOf(capture.videoKey, capture.selfieKey, capture.submitKey)
        assertEquals(keys.size, failures)
        val attempts = server.attempts(keys)
        assertEquals(keys.flatMap { listOf(it.toString(), it.toString()) }, attempts.map { it.getString("key") })
        val types = capture.challenge.gestures.map { "gesture" } + listOf("liveness_video", "selfie", "facial_verification")
        assertEquals(types.flatMap { listOf(it, it) }, attempts.map { it.getString("type") })
        assertEquals(listOf(3, 3, 3), capture.gestureIds)
        assertEquals(5, capture.videoId)
        assertEquals(4, capture.selfieId)
        assertNull(current.liveness)
        assertNull(current.challenge)
        assertFalse(video.exists())
    }

    @Test
    fun livenessRejectionDiscardsTheCaptureSoTheNextAttemptHasNewKeys() {
        val current = launch("reject:${CredflareSpec.ErrorCode.LIVENESS_CHALLENGE_EXPIRED}")
        val capture = liveness()
        val video = capture.video!!
        current.liveness = capture
        current.challenge = capture.challenge

        val error = try { current.sendLiveness(capture, "Acme"); fail(); throw IllegalStateException() } catch(error: IllegalArgumentException) { error }

        assertEquals(SpecCopy.text(CredflareSpec.CopyKey.LIVENESS_EXPIRED), error.message)
        assertNull(current.liveness)
        assertNull(current.challenge)
        assertFalse(video.exists())
        val next = liveness()
        val previous = (capture.gestureKeys + listOf(capture.videoKey, capture.selfieKey, capture.submitKey)).toSet()
        assertTrue((next.gestureKeys + listOf(next.videoKey, next.selfieKey, next.submitKey)).none { it in previous })
    }

    @Test
    fun locationRetryKeepsTheKeyAndANewFixGetsANewOne() {
        val current = launch("flaky")
        current.acceptLocation(fix)
        val first = current.locationKey

        assertEquals(1, untilSent { current.sendLocation(fix, null, "Acme") })
        assertNull(current.locationFix)
        assertEquals(0, current.locationFailures)
        assertEquals(listOf(first, first).map { it.toString() }, server.attempts(listOf(first)).map { it.getString("key") })

        val next = fix.copy(accuracy = 8.0)
        current.acceptLocation(next)
        assertSame(next, current.locationFix)
        assertNotEquals(first, current.locationKey)
    }

    @Test
    fun locationRejectionClearsTheFixAndCountsAFailure() {
        val current = launch("reject:${CredflareSpec.ErrorCode.LOCATION_STALE}")
        current.acceptLocation(fix)
        val key = current.locationKey

        val error = try { current.sendLocation(fix, null, "Acme"); fail(); throw IllegalStateException() } catch(error: IllegalArgumentException) { error }

        assertEquals(SpecCopy.text(CredflareSpec.CopyKey.LOCATION_STALE), error.message)
        assertNull(current.locationFix)
        assertEquals(1, current.locationFailures)
        current.acceptLocation(fix)
        assertNotEquals(key, current.locationKey)
    }

    @Test
    fun locationNetworkFailureKeepsTheFixForARetry() {
        val current = launch("broken-body")
        current.acceptLocation(fix)
        val key = current.locationKey

        try { current.sendLocation(fix, null, "Acme"); fail() } catch(error: CredflareException) { assertEquals(0, error.status) }

        assertSame(fix, current.locationFix)
        assertEquals(key, current.locationKey)
        assertEquals(0, current.locationFailures)
    }

    @Test
    fun documentRetriesReuseKeysAndChangingAFileStartsANewSubmission() {
        val doc = SessionInfo.fromJson(ContractServer.fixture("combined-session.json").getJSONObject("data")).documents.single()
        val steps = doc.captureSteps()
        val current = launch("flaky")

        current.selectDocument(doc)
        val selected = current.submitKey
        val front = CredflareVerification.SelectedFile(byteArrayOf(1), "front.png", CaptureSource.UPLOAD)
        current.addCapture(steps[0], front)
        val afterFront = current.submitKey
        val back = CredflareVerification.SelectedFile(byteArrayOf(2), "back.jpg", CaptureSource.CAMERA)
        current.addCapture(steps[1], back)
        val submitKey = current.submitKey
        assertEquals(3, setOf(selected, afterFront, submitKey).size)

        val progress = mutableListOf<Int>()
        assertEquals(3, untilSent { current.sendDocument(doc, steps) { progress += it } })
        assertEquals(listOf(front.key, front.key, back.key, back.key, submitKey, submitKey).map { it.toString() }, server.attempts(listOf(front.key, back.key, submitKey)).map { it.getString("key") })
        assertEquals(listOf(0, 0, 1, 0, 1, 0, 1), progress)
        assertEquals(1, front.fileId)
        assertEquals(1, back.fileId)

        val replacement = CredflareVerification.SelectedFile(byteArrayOf(3), "back.png", CaptureSource.UPLOAD)
        current.addCapture(steps[1], replacement)
        val resubmitKey = current.submitKey
        assertNotEquals(submitKey, resubmitKey)
        assertEquals(2, untilSent { current.sendDocument(doc, steps) {} })
        assertEquals(listOf(replacement.key, replacement.key, resubmitKey, resubmitKey).map { it.toString() }, server.attempts(listOf(replacement.key, resubmitKey)).map { it.getString("key") })
        assertEquals(2, server.attempts(listOf(front.key)).size)

        current.selectDocument(doc)
        assertTrue(current.captures.isEmpty())
        assertNotEquals(resubmitKey, current.submitKey)
    }
}
