package net.credflare.sdk

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import net.credflare.sdk.CredflareSpec.CopyKey as Key

class QuestionnaireTest {
    private val info = SessionInfo.fromJson(ContractServer.fixture("questionnaire-session.json").getJSONObject("data"))
    private val questionnaire = info.questionnaire
    private fun shown(answers: Map<String, Any?>) = questionnaire.visible(answers).map { it.key }
    private val complete = mapOf("country" to "de", "employment" to "Student", "birth_date" to "1990-05-17", "terms" to true)

    @Test
    fun parsesEveryQuestionTypeFromTheSession() {
        assertEquals(listOf("country", "single_select", "text", "number", "date", "email", "phone", "multi_select", "text", "consent"), questionnaire.questions.map { it.type })
        assertTrue(questionnaire.supported)
        assertTrue(info.asksQuestions)
        assertFalse(info.needsHostSteps)
        assertEquals("email", info.nextRequirement!!.channel)
        val income = questionnaire.questions.single { it.key == "income" }
        assertEquals(0.0, income.min!!, 0.0); assertEquals(10000000.0, income.max!!, 0.0)
        assertEquals("any", income.showIf!!.match)
        assertEquals("all", questionnaire.questions.single { it.key == "sources_other" }.showIf!!.match)
    }

    @Test
    fun hiddenQuestionsAreNotShownValidatedOrSent() {
        assertFalse("employer" in shown(mapOf("employment" to "Student")))
        assertTrue("employer" in shown(mapOf("employment" to "Employed")))
        assertTrue("income" in shown(mapOf("employment" to "Self employed")))
        assertFalse("income" in shown(mapOf("employment" to "Retired")))
        assertTrue("sources_other" in shown(mapOf("sources" to listOf("Salary", "Other"))))
        assertFalse("sources_other" in shown(emptyMap()))

        val answers = complete + mapOf("employer" to "First Bank", "income" to "5000", "sources_other" to "Gift")
        assertEquals(emptyMap<String, String>(), questionnaire.errors(answers))
        val fields = questionnaire.fields(answers)
        assertEquals(setOf("country", "employment", "birth_date", "terms"), fields.keys().asSequence().toSet())
        assertEquals("DE", fields.getString("country"))
        assertEquals(true, fields.getBoolean("terms"))
    }

    @Test
    fun shownRequiredQuestionsAreValidatedWithSpecCopy() {
        val errors = questionnaire.errors(mapOf("employment" to "Employed", "sources" to listOf("Other"), "income" to "-5", "birth_date" to "2023-02-30", "contact_email" to "not-an-email", "contact_phone" to "call me"))
        assertEquals(SpecCopy.text(Key.QUESTIONS_REQUIRED), errors["country"])
        assertEquals(SpecCopy.text(Key.QUESTIONS_REQUIRED), errors["employer"])
        assertEquals(SpecCopy.text(Key.QUESTIONS_REQUIRED), errors["sources_other"])
        assertEquals("Enter at least 0.", errors["income"])
        assertEquals(SpecCopy.text(Key.QUESTIONS_INVALID_DATE), errors["birth_date"])
        assertEquals(SpecCopy.text(Key.QUESTIONS_INVALID_EMAIL), errors["contact_email"])
        assertEquals(SpecCopy.text(Key.QUESTIONS_INVALID_PHONE), errors["contact_phone"])
        assertEquals(SpecCopy.text(Key.QUESTIONS_CONSENT_REQUIRED), errors["terms"])
        assertEquals("Enter at most 10000000.", questionnaire.errors(complete + mapOf("employment" to "Employed", "employer" to "Shop", "income" to "20000000"))["income"])
        assertEquals(SpecCopy.text(Key.QUESTIONS_INVALID_NUMBER), questionnaire.errors(complete + mapOf("employment" to "Employed", "employer" to "Shop", "income" to "a lot"))["income"])
    }

    @Test
    fun answersForQuestionsHiddenEarlierDoNotOpenLaterOnes() {
        val chained = Questionnaire.fromJson(JSONArray("""[
            {"key":"a","label":"A","type":"single_select","required":true,"options":["yes","no"]},
            {"key":"b","label":"B","type":"text","required":false,"show_if":{"conditions":[{"path":"form.a","op":"equals","value":"yes"}]}},
            {"key":"c","label":"C","type":"text","required":true,"show_if":{"conditions":[{"path":"form.b","op":"is_not_empty"}]}}]"""))
        assertEquals(listOf("a", "b", "c"), chained.visible(mapOf("a" to "yes", "b" to "x")).map { it.key })
        assertEquals(listOf("a"), chained.visible(mapOf("a" to "no", "b" to "x")).map { it.key })
    }

    private fun schema() = ContractServer.fixture("questionnaire-session.json").getJSONObject("data").getJSONObject("profile").getJSONArray("form_schema")
    private fun question(key: String) = schema().let { all -> (0 until all.length()).map { all.getJSONObject(it) }.single { it.getString("key") == key } }

    @Test
    fun answersSentEarlierKeepLaterQuestionsShownWithoutBeingSentAgain() {
        val refreshed = Questionnaire.fromJson(JSONArray().put(question("employer")))
        assertEquals(emptyList<String>(), refreshed.visible(emptyMap()).map { it.key })
        val earlier = mapOf<String, Any?>("employment" to "Employed")
        assertEquals(listOf("employer"), refreshed.visible(emptyMap(), earlier).map { it.key })
        assertEquals(SpecCopy.text(Key.QUESTIONS_REQUIRED), refreshed.errors(emptyMap(), earlier)["employer"])
        assertEquals(setOf("employer"), refreshed.fields(mapOf("employer" to "First Bank"), earlier).keys().asSequence().toSet())
        val askedAgain = Questionnaire.fromJson(JSONArray().put(question("employment")).put(question("employer")))
        assertEquals(listOf("employment"), askedAgain.visible(mapOf("employment" to "Student"), earlier).map { it.key })
    }

    @Test
    fun earlierAnswersFromTheSessionRevealQuestionsWithoutBeingSentAgain() {
        val data = ContractServer.fixture("questionnaire-earlier-answers-session.json").getJSONObject("data")
        val session = SessionInfo.fromJson(data)
        val form = session.questionnaire
        assertEquals(mapOf<String, Any?>("employment" to "Employed"), form.earlier)
        assertTrue(session.asksQuestions)
        assertEquals(listOf("employer"), form.visible(emptyMap()).map { it.key })
        assertEquals(SpecCopy.text(Key.QUESTIONS_REQUIRED), form.errors(emptyMap())["employer"])
        assertEquals(setOf("employer"), form.fields(mapOf("employer" to "First Bank")).keys().asSequence().toSet())

        data.getJSONObject("profile").put("form_schema", JSONArray().put(question("employment")).put(question("employer")))
        val askedAgain = SessionInfo.fromJson(data).questionnaire
        assertEquals("Employed", askedAgain.answer("employment", emptyMap()))
        assertEquals(listOf("employment", "employer"), askedAgain.visible(emptyMap()).map { it.key })
        assertEquals(listOf("employment"), askedAgain.visible(mapOf("employment" to "Student")).map { it.key })
        assertEquals("Employed", askedAgain.fields(mapOf("employer" to "First Bank")).getString("employment"))
    }

    @Test
    fun showIfSeesTheValuesThatAreSent() {
        val answers = complete + mapOf("employment" to "Self employed", "income" to " 9999999.123456789012345678901 ", "sources" to emptyList<String>())
        val fields = questionnaire.fields(answers)
        assertEquals("9999999.123456789012345678901", fields.get("income"))
        assertFalse(fields.has("sources"))
        assertEquals(questionnaire.submitted(answers), questionnaire.submitted(questionnaire.submitted(answers)))
        assertEquals(null, questionnaire.errors(answers)["income"])
        assertEquals("Enter at most 10000000.", questionnaire.errors(answers + ("income" to "10000000.0000000000000000001"))["income"])
    }

    @Test
    fun onlyVisibleUnsupportedQuestionsSendTheFormBack() {
        val unknown = org.json.JSONObject(question("employer").toString()).put("key", "signature").put("type", "signature")
        val form = Questionnaire.fromJson(JSONArray().put(question("employment")).put(unknown))
        assertTrue(form.supported)
        assertTrue(form.supported(mapOf("employment" to "Student")))
        assertFalse(form.supported(mapOf("employment" to "Employed")))
    }

    @Test
    fun foundersAnswersAreSentAsTheSpecPayload() {
        val founders = Questionnaire.fromJson(JSONArray("""[{"key":"founders","label":"Founders","type":"founders","required":true}]"""))
        assertTrue(founders.supported)
        assertEquals(SpecCopy.text(Key.QUESTIONS_REQUIRED), founders.errors(mapOf("founders" to mapOf("includes_applicant" to false, "names" to listOf(" ", ""))))["founders"])
        val answers = mapOf("founders" to mapOf("includes_applicant" to true, "names" to listOf(" Ada Obi ", "", "Ben Tah")))
        assertEquals(emptyMap<String, String>(), founders.errors(answers))
        val sent = founders.fields(answers).getJSONObject("founders")
        assertEquals(true, sent.getBoolean("includes_applicant"))
        assertEquals(listOf("Ada Obi", "Ben Tah"), sent.getJSONArray("names").let { n -> (0 until n.length()).map { n.getString(it) } })
    }

    @Test
    fun deprecatedFlagConstructorsKeepTheirMeaning() {
        @Suppress("DEPRECATION") val flagged = SessionInfo("sandbox", null, 1L, listOf("pdf"), emptyList(), hasForms = true)
        assertTrue(flagged.hasForms)
        assertTrue(flagged.needsHostSteps)
        assertFalse(flagged.asksQuestions)
        @Suppress("DEPRECATION") val cleared = flagged.copy(hasForms = false)
        assertFalse(cleared.hasForms)
        assertFalse(cleared.needsHostSteps)
        assertEquals(info.questionnaire, info.copy(requesterName = "Acme").questionnaire)
    }
}
