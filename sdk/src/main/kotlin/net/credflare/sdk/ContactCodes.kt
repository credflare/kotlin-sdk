package net.credflare.sdk

import net.credflare.sdk.CredflareSpec.Requirements as R

object ContactCodes {
    private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

    fun normalize(channel: String, value: String): String? = when(channel) {
        "email" -> value.trim().takeIf { it.length <= R.CONTACT_CODES_EMAIL_MAX_LENGTH && EMAIL.matches(it) }
        "phone" -> value.trim().takeIf { it.length <= R.CONTACT_CODES_PHONE_MAX_LENGTH }?.replace(Regex(R.CONTACT_CODES_PHONE_STRIP), "")?.takeIf { Regex(R.CONTACT_CODES_PHONE_PATTERN).matches(it) }
        else -> null
    }
}
