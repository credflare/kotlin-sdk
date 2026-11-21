package net.credflare.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import net.credflare.sdk.CredflareSpec.CopyKey as Key
import net.credflare.sdk.CredflareSpec.ErrorCode as Code

class RejectionCopyTest {
    private val fixtures = File("../example/src/androidTest/assets/rejections").listFiles { file -> file.name.endsWith(".json") }!!.sortedBy { it.name }
    private fun rejection(file: File) = CredflareException.fromEnvelope(422, JSONObject(file.readText()))

    private val noPlace: LocationReference? = null
    private val unnamed = LocationReference("within", "any", emptyList())
    private val within = LocationReference("within", "any", listOf("Head office", "Depot"))
    private val outside = LocationReference("outside", "all", listOf("Head office", "Depot"))

    @Test
    fun everySpecCodeHasACapturedRejection() {
        assertEquals(Code.MEANINGS.keys.sorted(), fixtures.map { it.name.removeSuffix(".json") })
        for(file in fixtures) {
            val error = rejection(file)
            assertEquals(file.name, file.name.removeSuffix(".json"), error.code)
            assertTrue(file.name, error.isKnownCode)
            assertEquals(file.name, "validation", error.type)
            assertTrue(file.name, error.message!!.isNotBlank())
        }
    }

    @Test
    fun locationCodesMapToCopyForEveryPlaceSetup() {
        val copy = { key: String -> SpecCopy.text(key) }
        val rejected = "Your location does not match the place requested by Acme. Try again from the right place."
        val expected = mapOf(
            Code.LOCATION_ACCURACY to listOf(copy(Key.LOCATION_LOW_ACCURACY), copy(Key.LOCATION_PLACE_NOT_PRECISE), copy(Key.LOCATION_PLACE_NOT_PRECISE), copy(Key.LOCATION_PLACE_NOT_PRECISE)),
            Code.LOCATION_STALE to List(4) { copy(Key.LOCATION_STALE) },
            Code.LOCATION_MOCK to List(4) { copy(Key.LOCATION_MOCK_DETECTED) },
            Code.LOCATION_IP_COUNTRY to List(4) { copy(Key.LOCATION_IP_COUNTRY_MISMATCH) },
            Code.LOCATION_REFERENCE_NOT_MATCHED to listOf(rejected, rejected,
                "You do not seem to be at Head office, Depot yet. Move closer, then try again.",
                "You seem to be too close to Head office, Depot. Move away, then try again."),
        )
        val location = fixtures.filter { it.name.startsWith("location.") }
        assertEquals(expected.keys.sorted(), location.map { it.name.removeSuffix(".json") })
        for(file in location) {
            val error = rejection(file)
            val shown = listOf(noPlace, unnamed, within, outside).map { Rejections.locationMessage(error, it, "Acme") }
            assertEquals(file.name, expected[error.code], shown)
            shown.forEach { assertFalse(file.name, it.contains("{")) }
            assertEquals(file.name, error.message, Rejections.livenessMessage(error, "Acme"))
        }
    }

    @Test
    fun livenessCodesAskForANewChallengeOrShowTheServerMessage() {
        val restart = setOf(Code.LIVENESS_CHALLENGE_EXPIRED, Code.LIVENESS_CHALLENGE_INVALID, Code.LIVENESS_CHALLENGE_USED, Code.LIVENESS_FRAME_OUT_OF_WINDOW)
        val liveness = fixtures.filter { it.name.startsWith("liveness.") }
        assertEquals(Code.MEANINGS.keys.filter { it.startsWith("liveness.") }.sorted(), liveness.map { it.name.removeSuffix(".json") })
        for(file in liveness) {
            val error = rejection(file)
            val expected = if(error.code in restart) "This step timed out. Start it again." else error.message
            assertEquals(file.name, expected, Rejections.livenessMessage(error, "Acme"))
            assertEquals(file.name, error.message, Rejections.locationMessage(error, within, "Acme"))
        }
    }

    @Test
    fun missingVerifierNameFallsBackToSpecCopy() {
        val error = rejection(fixtures.single { it.name == "${Code.LOCATION_REFERENCE_NOT_MATCHED}.json" })
        assertEquals("Your location does not match the place requested by your verifier. Try again from the right place.", Rejections.locationMessage(error, null, SpecCopy.verifier(null)))
        assertEquals(SpecCopy.verifier("  "), SpecCopy.verifier(null))
    }

    @Test
    fun topLevelRejectionFixturesCarryTheirCodes() {
        val accuracy = CredflareException.fromEnvelope(422, ContractServer.fixture("location-rejection.json"))
        assertEquals(Code.LOCATION_ACCURACY, accuracy.code)
        val place = CredflareException.fromEnvelope(422, ContractServer.fixture("reference-rejection.json"))
        assertEquals(Code.LOCATION_REFERENCE_NOT_MATCHED, place.code)
        val unauthorized = CredflareException.fromEnvelope(401, ContractServer.fixture("unauthorized.json"))
        assertNull(unauthorized.code)
        assertFalse(unauthorized.isKnownCode)
        assertEquals("session_unavailable", unauthorized.type)
        assertEquals("Invalid verification session.", unauthorized.message)
    }
}
