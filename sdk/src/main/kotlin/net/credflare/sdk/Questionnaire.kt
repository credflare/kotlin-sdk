package net.credflare.sdk

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone
import net.credflare.sdk.CredflareSpec.CopyKey as Key
import net.credflare.sdk.CredflareSpec.Requirements as R

data class Question(val key: String, val label: String, val type: String, val required: Boolean, val options: List<String> = emptyList(), val min: Double? = null, val max: Double? = null, val showIf: ShowIf? = null) {
    val supported get() = type in SUPPORTED
    companion object {
        val SUPPORTED = R.QUESTIONS_TYPES - "founders"
        fun fromJson(value: JSONObject): Question {
            val options = value.optJSONArray("options") ?: JSONArray()
            fun number(name: String) = if(value.isNull(name)) null else value.optDouble(name).takeIf { !it.isNaN() }
            return Question(value.getString("key"), value.optString("label").ifEmpty { value.getString("key") }, value.optString("type", "text"), value.optBoolean("required", false),
                (0 until options.length()).map { options.getString(it) }, number("min"), number("max"), ShowIf.fromJson(value.optJSONObject("show_if")))
        }
    }
}

class Questionnaire(val questions: List<Question>, val earlier: Map<String, Any?> = emptyMap()) {
    val supported get() = supported(emptyMap())

    fun supported(answers: Map<String, Any?>, earlier: Map<String, Any?> = emptyMap()) = visible(answers, earlier).all { it.supported }

    fun visible(answers: Map<String, Any?>, earlier: Map<String, Any?> = emptyMap()): List<Question> {
        val asked = questions.map { it.key }.toSet()
        val given = (this.earlier + earlier).filterKeys { it !in asked }.toMutableMap()
        val shown = mutableListOf<Question>()
        for(question in questions) {
            if(!Conditions.visible(question.showIf, given)) continue
            shown += question
            normalized(question, answer(question.key, answers))?.let { given[question.key] = it }
        }
        return shown
    }

    fun errors(answers: Map<String, Any?>, earlier: Map<String, Any?> = emptyMap()): Map<String, String> = visible(answers, earlier).mapNotNull { question -> error(question, answer(question.key, answers))?.let { question.key to it } }.toMap()

    fun answer(key: String, answers: Map<String, Any?>): Any? = if(answers.containsKey(key)) answers[key] else earlier[key]

    fun submitted(answers: Map<String, Any?>, earlier: Map<String, Any?> = emptyMap()): Map<String, Any?> =
        visible(answers, earlier).mapNotNull { question -> normalized(question, answer(question.key, answers))?.let { question.key to it } }.toMap()

    fun fields(answers: Map<String, Any?>, earlier: Map<String, Any?> = emptyMap()): JSONObject {
        val fields = JSONObject()
        for((key, value) in submitted(answers, earlier)) fields.put(key, json(value))
        return fields
    }

    companion object {
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        private fun json(value: Any?): Any? = when(value) {
            is List<*> -> JSONArray(value.map { json(it) })
            is Map<*, *> -> JSONObject().apply { value.forEach { (k, v) -> put(k.toString(), json(v)) } }
            else -> value
        }

        internal fun flag(hasForms: Boolean) = Questionnaire(if(hasForms) listOf(Question("", "", "", true)) else emptyList())

        fun fromJson(schema: JSONArray?, earlier: JSONObject? = null): Questionnaire = Questionnaire((0 until (schema?.length() ?: 0)).map { Question.fromJson(schema!!.getJSONObject(it)) },
            earlier?.let { Conditions.plain(it) as Map<*, *> }.orEmpty().mapKeys { it.key.toString() })

        private fun empty(value: Any?) = value == null || (value is String && value.isBlank()) || (value is List<*> && value.isEmpty())

        internal fun normalized(question: Question, value: Any?): Any? {
            if(question.type == "consent") return value == true
            if(question.type == "founders") return founders(value)
            if(empty(value)) return null
            return when(question.type) {
                "number" -> (if(value is BigDecimal) value.toPlainString() else value.toString()).trim()
                "multi_select" -> (value as? List<*>)?.distinct() ?: listOf(value)
                "country" -> value.toString().trim().uppercase()
                else -> if(value is String) value.trim() else value
            }
        }

        private fun founders(value: Any?): Map<String, Any?>? {
            val map = value as? Map<*, *> ?: return null
            val includes = map["includes_applicant"] == true
            val names = (map["names"] as? List<*>).orEmpty().map { it?.toString()?.trim().orEmpty() }.filter { it.isNotEmpty() }
            return if(!includes && names.isEmpty()) null else mapOf("includes_applicant" to includes, "names" to names)
        }

        private fun realDate(value: String): Boolean {
            if(!Regex(R.QUESTIONS_DATE_PATTERN).matches(value)) return false
            val (y, m, d) = value.split('-').map { it.toInt() }
            return try { Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { isLenient = false; clear(); set(y, m - 1, d) }.timeInMillis; true } catch(_: IllegalArgumentException) { false }
        }

        private fun plain(value: Double) = if(value == Math.floor(value) && Math.abs(value) < 1e15) value.toLong().toString() else value.toString()

        fun error(question: Question, value: Any?): String? {
            val answer = normalized(question, value)
            if(question.type == "consent") return if(question.required && answer != true) SpecCopy.text(Key.QUESTIONS_CONSENT_REQUIRED) else null
            if(answer == null) return if(question.required) SpecCopy.text(Key.QUESTIONS_REQUIRED) else null
            return when(question.type) {
                "text", "textarea" -> if((answer as String).length > R.QUESTIONS_TEXT_MAX_LENGTH) SpecCopy.text(Key.QUESTIONS_TEXT_TOO_LONG) else null
                "number" -> {
                    val number = (answer as String).takeIf { Regex(R.QUESTIONS_NUMBER_PATTERN).matches(it) }?.let { BigDecimal(it) }
                    if(number == null) SpecCopy.text(Key.QUESTIONS_INVALID_NUMBER)
                    else if(question.min != null && number < BigDecimal(question.min)) SpecCopy.text(Key.QUESTIONS_NUMBER_MIN, "min" to plain(question.min))
                    else if(question.max != null && number > BigDecimal(question.max)) SpecCopy.text(Key.QUESTIONS_NUMBER_MAX, "max" to plain(question.max))
                    else null
                }
                "date" -> if(!realDate(answer as String)) SpecCopy.text(Key.QUESTIONS_INVALID_DATE) else null
                "email" -> if((answer as String).length > R.QUESTIONS_EMAIL_MAX_LENGTH || !EMAIL.matches(answer)) SpecCopy.text(Key.QUESTIONS_INVALID_EMAIL) else null
                "phone", "tel" -> if(!Regex(R.QUESTIONS_PHONE_PATTERN).matches(answer as String)) SpecCopy.text(Key.QUESTIONS_INVALID_PHONE) else null
                "select", "single_select" -> if(answer !in question.options) SpecCopy.text(Key.QUESTIONS_REQUIRED) else null
                "multi_select" -> if((answer as List<*>).any { it !in question.options }) SpecCopy.text(Key.QUESTIONS_REQUIRED) else null
                "country" -> if(!Regex(R.QUESTIONS_COUNTRY_PATTERN).matches(answer as String)) SpecCopy.text(Key.QUESTIONS_REQUIRED) else null
                else -> null
            }
        }
    }
}
