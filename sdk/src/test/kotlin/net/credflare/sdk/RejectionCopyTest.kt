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
            val screens = listOf(noPlace, unnamed, within, outside).map { Rejections.screen(error, it, "Acme") }
            assertEquals(file.name, expected[error.code], screens.map { it.message })
            screens.forEach { assertFalse(file.name, it.message.contains("{")); assertEquals(file.name, "retry", it.action) }
        }
    }

    @Test
    fun everyCodeShowsItsSpecTitleMessageActionAndHelp() {
        val expectedActions = mapOf("liveness" to setOf("restart"), "location" to setOf("retry"), "email" to setOf("leave", "wait", "new_code", "reenter_code"), "phone" to setOf("leave", "wait", "new_code", "reenter_code"))
        for(file in fixtures) {
            val error = rejection(file)
            val entry = CredflareSpec.Rejection.CODES.getValue(error.code!!)
            val screen = Rejections.screen(error, null, "Acme")
            assertEquals(file.name, SpecCopy.text(entry.title, "verifier" to "Acme"), screen.title)
            assertEquals(file.name, SpecCopy.text(entry.message, "verifier" to "Acme"), screen.message)
            assertTrue(file.name, screen.action in expectedActions.getValue(error.code!!.substringBefore('.')))
            assertEquals(file.name, CredflareSpec.Copy.EN.getValue(CredflareSpec.Rejection.ACTION_LABELS.getValue(screen.action)), screen.actionLabel)
            listOf(screen.title!!, screen.message, screen.actionLabel, screen.requestedBy, screen.help).forEach { assertFalse(file.name, it.contains("{")) }
        }
        val notConfigured = Rejections.screen(rejection(fixtures.single { it.name == "${Code.PHONE_NOT_CONFIGURED}.json" }), null, "Acme")
        assertEquals("Confirming a phone number is not available for this request yet. Contact Acme.", notConfigured.message)
        assertEquals("leave", notConfigured.action)
        assertEquals("Close", notConfigured.actionLabel)
        assertEquals("wait", Rejections.screen(rejection(fixtures.single { it.name == "${Code.EMAIL_RESEND_TOO_SOON}.json" }), null, "Acme").action)
        assertEquals("reenter_code", Rejections.screen(rejection(fixtures.single { it.name == "${Code.PHONE_CODE_INVALID}.json" }), null, "Acme").action)
        assertEquals("new_code", Rejections.screen(rejection(fixtures.single { it.name == "${Code.EMAIL_TOO_MANY_ATTEMPTS}.json" }), null, "Acme").action)
    }

    @Test
    fun missingVerifierNameFallsBackToSpecCopy() {
        val error = rejection(fixtures.single { it.name == "${Code.LOCATION_REFERENCE_NOT_MATCHED}.json" })
        val screen = Rejections.screen(error, null, SpecCopy.verifier(null))
        assertEquals("Your location does not match the place requested by your verifier. Try again from the right place.", screen.message)
        assertEquals("Requested by your verifier.", screen.requestedBy)
        assertEquals("If you need help, contact your verifier.", screen.help)
        assertEquals(SpecCopy.verifier("  "), SpecCopy.verifier(null))
    }

    @Test
    fun helpLinePrefersTheRequesterEmailThenItsWebsite() {
        val info = SessionInfo.fromJson(ContractServer.fixture("help-contact-session.json").getJSONObject("data"))
        assertEquals(HelpContact("help@northwind.example", "https://northwind.example/support"), info.help)
        val error = rejection(fixtures.single { it.name == "${Code.EMAIL_CODE_INVALID}.json" })
        val email = Rejections.screen(error, null, info.requesterName!!, info.help)
        assertEquals("If you need help, contact Ada's Workspace at help@northwind.example.", email.help)
        assertEquals("mailto:help@northwind.example", email.helpLink)
        val site = Rejections.screen(error, null, "Acme", info.help.copy(email = null))
        assertEquals("If you need help, visit https://northwind.example/support.", site.help)
        assertEquals("https://northwind.example/support", site.helpLink)
        val none = Rejections.screen(error, null, "Acme", HelpContact(null, null))
        assertEquals("If you need help, contact Acme.", none.help)
        assertNull(none.helpLink)
        assertEquals(HelpContact(null, null), SessionInfo.fromJson(ContractServer.fixture("questionnaire-session.json").getJSONObject("data")).help)
    }

    @Test
    fun codelessValidationErrorShowsTheServerMessageWithRetry() {
        val error = CredflareException.fromEnvelope(422, ContractServer.fixture("questionnaire-hidden-answer.json"))
        assertNull(error.code)
        val screen = Rejections.screen(error, null, "Acme")
        assertNull(screen.title)
        assertEquals("This question is not shown for the answers given.", screen.message)
        assertEquals("retry", screen.action)
        assertEquals("Try again", screen.actionLabel)
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
