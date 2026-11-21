package net.credflare.sdk

object SpecCopy {
    fun text(key: String, vararg values: Pair<String, Any?>): String {
        var result = CredflareSpec.Copy.EN[key] ?: key
        for((name, value) in values) result = result.replace("{$name}", value?.toString().orEmpty())
        return result.replaceFirstChar { it.uppercase() }
    }
    fun verifier(name: String?): String = name?.takeIf { it.isNotBlank() } ?: CredflareSpec.Copy.EN.getValue(CredflareSpec.CopyKey.COMMON_VERIFIER_FALLBACK)
}
