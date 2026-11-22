package net.credflare.sdk

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowIfCasesTest {
    private val cases = ContractServer.fixture("show-if-cases.json")
    @Suppress("UNCHECKED_CAST")
    private fun form(value: Any?): Map<String, Any?> = when(val plain = Conditions.plain(value)) { is Map<*, *> -> plain as Map<String, Any?>; else -> emptyMap() }

    @Test
    fun everyBackendCaseMatches() {
        val conditions = cases.getJSONArray("conditions")
        val showIf = cases.getJSONArray("show_if")
        assertEquals(62, conditions.length() + showIf.length())
        val failures = mutableListOf<String>()
        for(index in 0 until conditions.length()) {
            val case = conditions.getJSONObject(index)
            val condition = case.getJSONObject("condition").let { Condition(it.getString("path"), it.getString("op"), Conditions.plain(it.opt("value"))) }
            if(Conditions.test(condition, mapOf("form" to form(case.get("form")))) != case.getBoolean("expect")) failures += case.getString("name")
        }
        for(index in 0 until showIf.length()) {
            val case = showIf.getJSONObject(index)
            if(Conditions.visible(ShowIf.fromJson(case.optJSONObject("show_if")), form(case.get("form"))) != case.getBoolean("expect")) failures += case.getString("name")
        }
        assertEquals(emptyList<String>(), failures)
    }

    @Test
    fun casesCoverEverySpecOperator() {
        val conditions = cases.getJSONArray("conditions")
        val used = (0 until conditions.length()).map { conditions.getJSONObject(it).getJSONObject("condition").getString("op") }.toSet()
        assertEquals(CredflareSpec.Requirements.SHOW_IF_OPERATORS.toSet(), used)
    }

    @Test
    fun similarityCountsUtf8Bytes() {
        assertEquals(100.0, Conditions.similarity("café", "café"), 0.0)
        assertEquals(0.0, Conditions.similarity("", ""), 0.0)
        assertEquals(JSONObject.NULL.let { Conditions.plain(it) }, null)
    }
}
