package net.credflare.sdk

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.Calendar
import java.util.TimeZone
import java.util.regex.Pattern

data class Condition(val path: String, val op: String, val value: Any?)
data class ShowIf(val match: String, val conditions: List<Condition>) {
    companion object {
        fun fromJson(value: JSONObject?): ShowIf? {
            if(value == null) return null
            val list = value.optJSONArray("conditions") ?: JSONArray()
            return ShowIf(value.optString("match").ifEmpty { CredflareSpec.Requirements.SHOW_IF_DEFAULT_MATCH },
                (0 until list.length()).map { list.getJSONObject(it).let { c -> Condition(c.getString("path"), c.getString("op"), Conditions.plain(c.opt("value"))) } })
        }
    }
}

object Conditions {
    private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}([T ][0-9:.]+(Z|[+-]\\d{2}:?\\d{2})?)?$")
    private val NUMERIC = Regex("^[ \\t\\n\\r\\u000B\\u000C]*[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?[ \\t\\n\\r\\u000B\\u000C]*$")
    private val TIME = Regex("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d+))?)?(Z|([+-])(\\d{2}):(\\d{2}))$")

    fun plain(value: Any?): Any? = when(value) {
        null, JSONObject.NULL -> null
        is JSONObject -> value.keys().asSequence().associateWith { plain(value.get(it)) }
        is JSONArray -> (0 until value.length()).map { plain(value.get(it)) }
        else -> value
    }

    private fun isList(v: Any?) = v is List<*>
    private fun isMap(v: Any?) = v is List<*> || v is Map<*, *>
    private fun isScalar(v: Any?) = v is String || v is Number || v is Boolean
    private fun finite(v: Number) = v.toDouble().isFinite()
    private fun isNumeric(v: Any?) = (v is Number && finite(v)) || (v is String && NUMERIC.matches(v))
    private fun number(v: Any?): Double = if(v is Number) v.toDouble() else (v as String).trim().toDouble()
    private fun size(v: Any?) = when(v) { is List<*> -> v.size; is Map<*, *> -> v.size; else -> 0 }
    private fun values(v: Any?): Collection<Any?> = when(v) { is List<*> -> v; is Map<*, *> -> v.values; else -> emptyList() }

    private fun numberText(v: Number): String {
        if(v is BigDecimal) return numberText(v.toDouble())
        val d = v.toDouble()
        if(d == Math.floor(d) && Math.abs(d) < 1e21) return BigDecimal(d).toPlainString()
        return d.toString()
    }
    private fun text(v: Any?): String = when(v) { true -> "1"; false, null -> ""; is Number -> numberText(v); else -> v.toString() }

    private fun boolean(v: Any?): Boolean? {
        if(v is Boolean) return v
        if(isMap(v)) return null
        return when(text(v).trim().lowercase()) { "1", "true", "on", "yes" -> true; "0", "false", "off", "no", "" -> false; else -> null }
    }

    private fun truthy(v: Any?): Boolean = if(isMap(v)) size(v) > 0 else !((v is Number && v.toDouble() == 0.0) || v == "" || v == "0" || v == false)

    private fun loose(a: Any?, b: Any?): Boolean {
        if(a == null || b == null) {
            val other = a ?: b ?: return true
            if(isMap(other)) return size(other) == 0
            return boolean(other) == false && (other !is String || other == "")
        }
        if(a is Boolean || b is Boolean) return truthy(a) == truthy(b)
        if(isMap(a) || isMap(b)) {
            if(!isMap(a) || !isMap(b)) return false
            val ka = keys(a); val kb = keys(b)
            if(ka.size != kb.size) return false
            return ka.all { it in kb && loose(member(a, it), member(b, it)) }
        }
        if(isNumeric(a) && isNumeric(b)) return number(a) == number(b)
        return text(a) == text(b)
    }
    private fun keys(v: Any?): List<String> = when(v) { is List<*> -> v.indices.map { it.toString() }; is Map<*, *> -> v.keys.map { it.toString() }; else -> emptyList() }
    private fun member(v: Any?, key: String): Any? = when(v) { is List<*> -> v[key.toInt()]; is Map<*, *> -> v[key]; else -> null }

    private fun equalValues(actual: Any?, expected: Any?): Boolean {
        if(isMap(actual) || isMap(expected)) return loose(actual, expected)
        if(actual is Boolean || expected is Boolean) return boolean(actual) == boolean(expected)
        if(isNumeric(actual) && isNumeric(expected)) return number(actual) == number(expected)
        return text(actual) == text(expected)
    }

    private fun time(value: String): Long? {
        var iso = value.replaceFirst(' ', 'T')
        if(!iso.contains('T')) iso += "T00:00:00"
        if(!Regex("(Z|[+-]\\d{2}:?\\d{2})$").containsMatchIn(iso)) iso += "Z"
        iso = iso.replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
        val m = TIME.matchEntire(iso) ?: return null
        val g = m.groupValues
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            isLenient = false; clear()
            set(g[1].toInt(), g[2].toInt() - 1, g[3].toInt(), g[4].toInt(), g[5].toInt(), g[6].ifEmpty { "0" }.toInt())
        }
        val base = try { calendar.timeInMillis } catch(_: IllegalArgumentException) { return null }
        val millis = g[7].take(3).padEnd(3, '0').ifEmpty { "0" }.toInt()
        val offset = if(g[8] == "Z") 0L else (if(g[9] == "-") -1 else 1) * (g[10].toLong() * 60 + g[11].toLong()) * 60000
        return base + millis - offset
    }

    private fun compare(actual: Any?, expected: Any?): Int? {
        if(isNumeric(actual) && isNumeric(expected)) return number(actual).compareTo(number(expected)).coerceIn(-1, 1)
        if(actual is String && expected is String && DATE.matches(actual) && DATE.matches(expected)) {
            val a = time(actual) ?: return null; val b = time(expected) ?: return null
            return a.compareTo(b).coerceIn(-1, 1)
        }
        return null
    }

    private fun within(needle: Any?, haystack: Any?) = values(haystack).any { !isMap(it) && !isMap(needle) && equalValues(needle, it) }

    private fun contains(actual: Any?, expected: Any?): Boolean {
        if(isMap(actual)) return within(expected, actual)
        if(isScalar(actual) && isScalar(expected) && text(expected) != "") return text(actual).lowercase().contains(text(expected).lowercase())
        return false
    }

    private fun regex(actual: Any?, pattern: Any?): Boolean {
        if(!isScalar(actual) || pattern !is String || pattern.length > CredflareSpec.Requirements.SHOW_IF_MAX_PATTERN_LENGTH) return false
        return try { Pattern.compile(pattern).matcher(text(actual)).find() } catch(_: Exception) { false }
    }

    private fun common(a: ByteArray, aFrom: Int, aTo: Int, b: ByteArray, bFrom: Int, bTo: Int): Int {
        var max = 0; var p1 = 0; var p2 = 0
        for(i in aFrom until aTo) for(j in bFrom until bTo) {
            var l = 0
            while(i + l < aTo && j + l < bTo && a[i + l] == b[j + l]) l++
            if(l > max) { max = l; p1 = i; p2 = j }
        }
        if(max == 0) return 0
        return max + common(a, aFrom, p1, b, bFrom, p2) + common(a, p1 + max, aTo, b, p2 + max, bTo)
    }

    fun similarity(a: String, b: String): Double {
        val x = a.toByteArray(Charsets.UTF_8); val y = b.toByteArray(Charsets.UTF_8)
        return if(x.size + y.size == 0) 0.0 else common(x, 0, x.size, y, 0, y.size) * 2.0 * 100 / (x.size + y.size)
    }

    private fun fuzzy(actual: Any?, expected: Any?): Boolean {
        if(!isScalar(actual) || expected !is Map<*, *> || !isScalar(expected["text"])) return false
        fun normalize(v: Any?) = text(v).lowercase().trim().replace(Regex("\\s+"), " ")
        val raw = expected["min_score"]
        val min = when { raw == null -> CredflareSpec.Requirements.SHOW_IF_FUZZY_DEFAULT_MIN_SCORE.toDouble(); isNumeric(raw) -> number(raw); else -> return false }
        return similarity(normalize(actual), normalize(expected["text"])) >= min
    }

    private fun resolve(path: String, context: Any?): Pair<Boolean, Any?> {
        var value = context
        for(segment in path.split('.')) {
            value = when(value) {
                is Map<*, *> -> if(value.containsKey(segment)) value[segment] else return false to null
                is List<*> -> segment.toIntOrNull()?.takeIf { it in value.indices && it.toString() == segment }?.let { value[it] } ?: return false to null
                else -> return false to null
            }
        }
        return true to value
    }

    fun test(condition: Condition, context: Map<String, Any?>): Boolean {
        val (exists, actual) = resolve(condition.path, context)
        val expected = condition.value
        val missing = !exists || actual == null
        val empty = missing || actual == "" || (actual is List<*> && actual.isEmpty())
        when(condition.op) { "is_empty" -> return empty; "is_not_empty" -> return !empty }
        if(missing) return false
        return when(condition.op) {
            "equals" -> equalValues(actual, expected)
            "not_equals" -> !equalValues(actual, expected)
            "gt" -> compare(actual, expected)?.let { it > 0 } ?: false
            "gte" -> compare(actual, expected)?.let { it >= 0 } ?: false
            "lt" -> compare(actual, expected)?.let { it < 0 } ?: false
            "lte" -> compare(actual, expected)?.let { it <= 0 } ?: false
            "contains" -> contains(actual, expected)
            "not_contains" -> !contains(actual, expected)
            "in" -> isList(expected) && within(actual, expected)
            "not_in" -> isList(expected) && !within(actual, expected)
            "regex" -> regex(actual, expected)
            "fuzzy_match" -> fuzzy(actual, expected)
            else -> false
        }
    }

    fun matches(showIf: ShowIf, context: Map<String, Any?>): Boolean {
        val results = showIf.conditions.map { test(it, context) }
        return if(showIf.match == "any") true in results else false !in results
    }

    fun visible(showIf: ShowIf?, answers: Map<String, Any?>): Boolean =
        showIf == null || showIf.conditions.isEmpty() || matches(showIf, mapOf("form" to answers))
}
