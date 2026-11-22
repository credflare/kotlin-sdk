package net.credflare.sdk

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ContactCodeTest {
    companion object {
        private lateinit var server: ContractServer
        @JvmStatic @BeforeClass fun start() { server = ContractServer() }
        @JvmStatic @AfterClass fun stop() { server.close() }
    }

    @Test
    fun requestsAndConfirmsCodesThroughTheSessionRoutes() {
        val client = server.session("codes")
        val email = client.requestCode("email", "Jane.Doe@example.test")
        assertEquals(CodeRequest("email", "J*******@example.test", "2026-11-22T00:08:07.000000Z", "2026-11-21T23:59:07.000000Z", 4), email)
        assertEquals(CredflareSpec.Requirements.CONTACT_CODES_RESEND_SECONDS * 1000L, email.resendDelayMillis)
        val phone = client.requestCode("phone", "+237650000001")
        assertEquals("+237*******01", phone.destination)
        assertEquals(1, client.confirmCode("email", "123456"))
        val codes = server.stats().getJSONArray("codes")
        val sent = (0 until codes.length()).map { codes.getJSONObject(it) }.takeLast(3)
        assertEquals("Jane.Doe@example.test", sent[0].getJSONObject("body").getString("email"))
        assertEquals("+237650000001", sent[1].getJSONObject("body").getString("phone"))
        assertEquals("123456", sent[2].getJSONObject("body").getString("code"))
        assertTrue(sent[2].getBoolean("confirm"))
    }

    @Test
    fun normalizesDestinationsWithTheSpecRules() {
        assertEquals("+237650000001", ContactCodes.normalize("phone", " +237 (650) 000-0.01 "))
        assertNull(ContactCodes.normalize("phone", "650000001"))
        assertNull(ContactCodes.normalize("phone", "+0237650000001"))
        assertEquals("jane@example.test", ContactCodes.normalize("email", " jane@example.test "))
        assertNull(ContactCodes.normalize("email", "jane"))
        assertNull(ContactCodes.normalize("email", "a".repeat(250) + "@example.test"))
    }

    @Test
    fun resendTimingFollowsTheLastCodeRequest() {
        val launch = CredflareVerification.Launch({ error("unused") }) {}
        launch.client = server.session("codes")
        launch.requestCode("phone", "+237650000001", "Acme", 1_000)
        assertEquals(60_000L, launch.resendIn(1_000))
        assertEquals(15_000L, launch.resendIn(46_000))
        assertEquals(0L, launch.resendIn(61_000))
        assertEquals("+237650000001", launch.destinations["phone"])
    }

    @Test
    fun codeRejectionsBecomeSpecScreensAndKeepTheLastRequest() {
        val launch = CredflareVerification.Launch({ error("unused") }) {}
        launch.client = server.session("codes")
        launch.requestCode("email", "jane@example.test", "Acme", 0)
        val request = launch.codeRequest
        launch.client = server.session("reject:email.resend_too_soon")
        val wait = try { launch.requestCode("email", "jane@example.test", "Acme", 5_000, IsoTime.parse("2026-11-22T00:57:28.000Z")!!.time); null } catch(error: CredflareVerification.Rejected) { error.screen }
        assertEquals("wait", wait!!.action)
        assertEquals(request, launch.codeRequest)
        assertEquals(55_000L, launch.resendIn(5_000))
        launch.client = server.session("reject:email.code_invalid")
        val reenter = try { launch.confirmCode("email", "000000", "Acme"); null } catch(error: CredflareVerification.Rejected) { error.screen }
        assertEquals("reenter_code", reenter!!.action)
        assertEquals("Code not correct", reenter.title)
    }

    @Test
    fun aResendTooSoonRejectionCarriesTheNextSendTime() {
        val error = CredflareException.fromEnvelope(422, ContractServer.fixture("rejections/phone.resend_too_soon.json"))
        assertEquals(CredflareSpec.ErrorCode.PHONE_RESEND_TOO_SOON, error.code)
        assertEquals("2026-11-22T00:58:23.000000Z", error.resendAvailableAt)
        assertNull(CredflareException.fromEnvelope(422, ContractServer.fixture("rejections/phone.code_invalid.json")).resendAvailableAt)
        val launch = CredflareVerification.Launch({ error("unused") }) {}
        launch.client = server.session("resend-with-time")
        val wall = IsoTime.parse("2026-11-22T00:57:53.000Z")!!.time
        try { launch.requestCode("phone", "+237650000002", "Acme", 1_000, wall); throw AssertionError("not rejected") } catch(_: CredflareVerification.Rejected) {}
        assertEquals(30_000L, launch.resendIn(1_000))
    }

    @Test
    fun leavingFromANotConfiguredScreenReportsItsCode() {
        val outcomes = mutableListOf<VerificationOutcome>()
        val launch = CredflareVerification.Launch({ error("unused") }) { outcomes += it }
        launch.client = server.session("reject:phone.not_configured")
        val screen = try { launch.requestCode("phone", "+237650000001", "Acme", 0); null } catch(error: CredflareVerification.Rejected) { error.screen }
        assertEquals("leave", screen!!.action)
        launch.result(VerificationResult.HOST_ACTION_REQUIRED, screen.code)
        assertEquals(listOf(VerificationOutcome(VerificationResult.HOST_ACTION_REQUIRED, "phone.not_configured")), outcomes)
    }
}
