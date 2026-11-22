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

    @Test
    fun foundersQuestionsAreLeftToTheHostApp() {
        val founders = Questionnaire.fromJson(JSONArray("""[{"key":"founders","label":"Founders","type":"founders","required":true}]"""))
        assertFalse(founders.supported)
    }
}
