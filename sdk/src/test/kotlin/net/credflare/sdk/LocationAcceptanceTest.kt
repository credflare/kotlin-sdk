package net.credflare.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationAcceptanceTest {
    private val settings = SessionInfo.fromJson(ContractServer.fixture("capture-session.json").getJSONObject("data")).requirements.firstNotNullOf { it.location }

    @Test
    fun acceptsAFixWithinTheProfileAccuracyAndAge() {
        assertEquals(50, settings.maxAccuracyM)
        assertEquals(60, settings.maxAgeS)
        assertTrue(LocationCapture.acceptable(true, 50f, 60.0, settings))
        assertTrue(LocationCapture.acceptable(true, 3f, 0.0, settings))
    }

    @Test
    fun rejectsAnInaccurateFixButKeepsItAsACandidate() {
        assertFalse(LocationCapture.acceptable(true, 50.5f, 10.0, settings))
        assertTrue(LocationCapture.fresh(true, 10.0, settings))
    }

    @Test
    fun rejectsAStaleFixEvenWhenAccurate() {
        assertFalse(LocationCapture.acceptable(true, 5f, 60.5, settings))
        assertFalse(LocationCapture.fresh(true, 60.5, settings))
    }

    @Test
    fun rejectsAFixWithoutAccuracy() {
        assertFalse(LocationCapture.acceptable(false, 0f, 0.0, settings))
        assertFalse(LocationCapture.fresh(false, 0.0, settings))
    }

    @Test
    fun usesSpecDefaultsWhenTheProfileHasNoLocationSettings() {
        val defaults = LocationSettings.fromJson(null)
        val accuracy = CredflareSpec.Settings.LOCATION_VERIFICATION_MAX_ACCURACY_M_DEFAULT.toFloat()
        val age = CredflareSpec.Settings.LOCATION_VERIFICATION_MAX_AGE_S_DEFAULT.toDouble()
        assertTrue(LocationCapture.acceptable(true, accuracy, age, defaults))
        assertFalse(LocationCapture.acceptable(true, accuracy + 1, age, defaults))
        assertFalse(LocationCapture.acceptable(true, accuracy, age + 1, defaults))
    }

    @Test
    fun mockFixesAreAcceptedLocallyAndReportedToTheServer() {
        assertEquals("reject", settings.mockLocation)
        assertTrue(LocationCapture.acceptable(true, 5f, 1.0, settings))
        val mocked = LocationCapture.fix(4.0511, 9.7679, 5f, 12.0, 0, true, "gps", "2026-11-21T00:40:01.000Z").json().getJSONObject("metadata")
        assertTrue(mocked.getBoolean("is_mock"))
        val real = LocationCapture.fix(4.0511, 9.7679, 5f, null, 0, false, "network", "2026-11-21T00:40:01.000Z").json().getJSONObject("metadata")
        assertFalse(real.getBoolean("is_mock"))
        val unknown = LocationCapture.fix(4.0511, 9.7679, 5f, null, 0, null, "fused", "2026-11-21T00:40:01.000Z").json().getJSONObject("metadata")
        assertTrue(unknown.isNull("is_mock"))
    }

    @Test
    fun buildsTheFixPayloadFromTheReading() {
        val fix = LocationCapture.fix(4.0511, 9.7679, 12.5f, 30.0, 1_795_221_600_000, false, "gps", "2026-11-21T00:40:01.000Z")
        assertEquals(LocationFix(4.0511, 9.7679, 12.5, 30.0, "2026-11-21T00:40:00.000Z", false, "gps", "2026-11-21T00:40:01.000Z"), fix)
        assertNull(LocationCapture.fix(0.0, 0.0, 1f, null, 0, false, "passive").provider)
        assertNull(LocationCapture.fix(0.0, 0.0, 1f, null, 0, false, null).provider)
        assertEquals("1970-01-01T00:00:00.000Z", LocationCapture.fix(0.0, 0.0, 1f, null, 0, false, "gps").fixTime)
    }
}
