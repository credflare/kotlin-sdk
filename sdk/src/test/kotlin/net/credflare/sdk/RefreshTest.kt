package net.credflare.sdk

import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.json.JSONObject
import java.util.UUID

class RefreshTest {
    companion object {
        private lateinit var server: ContractServer
        @JvmStatic @BeforeClass fun start() { server = ContractServer() }
        @JvmStatic @AfterClass fun stop() { server.close() }
    }

    private fun launch(token: String) = CredflareVerification.Launch({ error("unused") }) {}.apply { client = server.session(token); info = client!!.get() }
    private val answers = mapOf("country" to "DE", "employment" to "Student", "birth_date" to "1990-05-17", "terms" to true)

    @Test
    fun aRequirementAddedAfterTheFormIsCollectedNext() {
        val flow = launch("questionnaire")
        assertTrue(flow.info!!.asksQuestions)
        flow.answers += answers
        flow.sendAnswers(flow.info!!.questionnaire, "Acme")
        flow.refresh()
        val info = flow.info!!
        assertFalse(flow.pendingReceipt)
        assertEquals("in_progress", info.status)
        assertFalse(info.asksQuestions)
        assertTrue(info.outstanding)
        assertEquals("phone", info.nextRequirement!!.channel)
        val submit = server.stats().getJSONArray("submits").let { it.getJSONObject(it.length() - 1) }
        assertEquals("form_submission", submit.getString("type"))
        assertEquals(setOf("country", "employment", "birth_date", "terms"), submit.getJSONObject("fields").keys().asSequence().toSet())
    }

    @Test
    fun aFailedRefreshKeepsThePendingStateInsteadOfTheReceipt() {
        val flow = launch("refresh-fails")
        flow.answers += answers
        flow.sendAnswers(flow.info!!.questionnaire, "Acme")
        try { flow.refresh(); throw AssertionError("refresh did not fail") } catch(_: CredflareVerification.RefreshFailed) {}
        assertTrue(flow.pendingReceipt)
        assertTrue(flow.info!!.asksQuestions)
    }

    @Test
    fun closedOrEmptySessionsShowTheReceipt() {
        val added = ContractServer.fixture("requirement-added-session.json").getJSONObject("data")
        assertTrue(SessionInfo.fromJson(added).outstanding)
        val closed = SessionInfo.fromJson(JSONObject(added.toString()).put("status", "submitted"))
        assertFalse(closed.outstanding)
        assertEquals(null, closed.nextRequirement)
        val done = SessionInfo.fromJson(JSONObject(added.toString()).apply { getJSONObject("profile").put("verification_requirements", org.json.JSONArray()) })
        assertFalse(done.outstanding)
    }

    @Test
    fun aHiddenAnswerRejectedByTheServerShowsItsMessageWithRetry() {
        val flow = launch("questionnaire-hidden")
        try { flow.client!!.submitAnswers(JSONObject().put("employment", "Student").put("employer", "Shop"), UUID.randomUUID()); throw AssertionError("not rejected") }
        catch(error: CredflareException) {
            assertEquals(422, error.status)
            val screen = Rejections.screen(error, null, "Acme")
            assertEquals("This question is not shown for the answers given.", screen.message)
            assertEquals("retry", screen.action)
        }
    }
}
