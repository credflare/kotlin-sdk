package net.credflare.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsParsingTest {
    private fun fixture(name: String) = JSONObject(javaClass.classLoader!!.getResource(name)!!.readText()).getJSONObject("data")

    @Test
    fun parsesFacialAndLocationSettingsInProfileOrder() {
        val info = SessionInfo.fromJson(fixture("capture-session.json"))
        assertEquals(listOf("facial_verification", "location_verification"), info.requirements.map { it.type })
        val facial = info.requirements[0].facial!!
        assertEquals(listOf("blink", "smile", "turn_left", "turn_right", "nod"), facial.gestures)
        assertEquals(3, facial.gestureCount)
        assertTrue(facial.recordVideo)
        assertEquals(30, facial.videoRetentionDays)
        val location = info.requirements[1].location!!
        assertEquals(50, location.maxAccuracyM)
        assertEquals(60, location.maxAgeS)
        assertEquals("reject", location.mockLocation)
        assertEquals("flag", location.ipCountry)
        assertEquals(LocationReference("within", "any", listOf("Head office")), location.reference)
        assertTrue(info.requiresFacial && info.requiresLocation)
        assertFalse(info.needsHostSteps)
        assertEquals("Mrs. Jakayla Kris's Workspace", info.requesterName)
    }

    @Test
    fun appliesSpecDefaultsWhenSettingsAreMissing() {
        val info = SessionInfo.fromJson(fixture("facial-session.json"))
        val facial = info.requirements.single().facial!!
        assertEquals(CredflareSpec.Settings.FACIAL_VERIFICATION_GESTURE_COUNT_DEFAULT, facial.gestureCount)
        assertEquals(CredflareSpec.Settings.FACIAL_VERIFICATION_GESTURES_DEFAULT, facial.gestures)
        val location = LocationSettings.fromJson(null)
        assertEquals(CredflareSpec.Settings.LOCATION_VERIFICATION_MAX_ACCURACY_M_DEFAULT, location.maxAccuracyM)
        assertNull(location.reference)
    }

    @Test
    fun parsesLivenessChallenge() {
        val challenge = LivenessChallenge.fromJson(fixture("liveness-challenge.json"))
        assertEquals("01a1fb8d-6c01-7160-a4fe-31853bb8dbdd", challenge.challengeId)
        assertEquals(listOf("nod", "turn_right", "turn_left"), challenge.gestures)
        assertTrue(challenge.recordVideo)
        assertEquals(8, challenge.maxVideoSeconds)
    }

    @Test
    fun formsAreLeftToTheHostApp() {
        val info = SessionInfo.fromJson(fixture("session.json"))
        assertTrue(info.requirements.isEmpty())
        assertTrue(info.hasForms && info.needsHostSteps)
    }

    @Test
    fun locationPayloadSendsNullForUnknownMockState() {
        val payload = LocationFix(4.05, 9.76, 12.5, null, "2026-11-20T10:00:00Z", null, "gps", "2026-11-20T10:00:01Z").json()
        assertEquals("location_verification", payload.getString("type"))
        val metadata = payload.getJSONObject("metadata")
        assertTrue(metadata.isNull("is_mock") && metadata.isNull("altitude"))
        assertEquals(12.5, metadata.getDouble("accuracy"), 0.0)
    }
}
