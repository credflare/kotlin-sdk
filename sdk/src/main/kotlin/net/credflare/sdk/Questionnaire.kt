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

class Questionnaire(val questions: List<Question>) {
    val supported get() = questions.all { it.supported }

    fun visible(answers: Map<String, Any?>): List<Question> {
        val given = mutableMapOf<String, Any?>()
        val shown = mutableListOf<Question>()
        for(question in questions) {
            if(!Conditions.visible(question.showIf, given)) continue
            shown += question
            normalized(question, answers[question.key])?.let { given[question.key] = it }
        }
        return shown
    }

    fun errors(answers: Map<String, Any?>): Map<String, String> = visible(answers).mapNotNull { question -> error(question, answers[question.key])?.let { question.key to it } }.toMap()

    fun fields(answers: Map<String, Any?>): JSONObject {
        val fields = JSONObject()
        for(question in visible(answers)) normalized(question, answers[question.key])?.let { fields.put(question.key, if(it is List<*>) JSONArray(it) else it) }
        return fields
    }

    companion object {
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

        fun fromJson(schema: JSONArray?): Questionnaire = Questionnaire((0 until (schema?.length() ?: 0)).map { Question.fromJson(schema!!.getJSONObject(it)) })

        private fun empty(value: Any?) = value == null || (value is String && value.isBlank()) || (value is List<*> && value.isEmpty())

        internal fun normalized(question: Question, value: Any?): Any? {
            if(question.type == "consent") return value == true
            if(empty(value)) return null
            return when(question.type) {
                "number" -> value.toString().trim().let { if(Regex(R.QUESTIONS_NUMBER_PATTERN).matches(it)) BigDecimal(it) else it }
                "multi_select" -> (value as? List<*>)?.distinct() ?: listOf(value)
                "country" -> value.toString().trim().uppercase()
                else -> if(value is String) value.trim() else value
            }
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
                    if(answer !is BigDecimal) SpecCopy.text(Key.QUESTIONS_INVALID_NUMBER)
                    else if(question.min != null && answer.toDouble() < question.min) SpecCopy.text(Key.QUESTIONS_NUMBER_MIN, "min" to plain(question.min))
                    else if(question.max != null && answer.toDouble() > question.max) SpecCopy.text(Key.QUESTIONS_NUMBER_MAX, "max" to plain(question.max))
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
