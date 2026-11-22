package net.credflare.sdk

import net.credflare.sdk.CredflareSpec.Rejection as Spec

data class RejectionScreen(val code: String?, val title: String?, val message: String, val action: String, val actionLabel: String, val requestedBy: String, val help: String, val helpLinkText: String? = null, val helpLink: String? = null)

object Rejections {
    fun screen(error: CredflareException, reference: LocationReference?, verifier: String, contact: HelpContact = HelpContact(null, null)): RejectionScreen {
        val values = arrayOf<Pair<String, Any?>>("verifier" to verifier)
        val entry = error.code?.let { Spec.CODES[it] }
        val action = entry?.action ?: "retry"
        val placeKey = reference?.let { if(it.rule == "outside") entry?.placeMessageOutside else entry?.placeMessageWithin }
            ?.takeIf { key -> reference.placeLabels.isNotEmpty() || CredflareSpec.Copy.EN[key]?.contains("{place}") != true }
        val message = when {
            entry == null -> error.message.orEmpty().ifBlank { SpecCopy.text(CredflareSpec.CopyKey.ERRORS_GENERIC) }
            placeKey != null -> SpecCopy.text(placeKey, "place" to reference!!.placeLabels.joinToString(", "), *values)
            else -> SpecCopy.text(entry.message, *values)
        }
        val help = when {
            contact.email != null -> Triple(SpecCopy.text(Spec.HELP_EMAIL, "help_email" to contact.email, *values), contact.email, "mailto:${contact.email}")
            contact.url != null -> Triple(SpecCopy.text(Spec.HELP_URL, "help_url" to contact.url, *values), contact.url, contact.url)
            else -> Triple(SpecCopy.text(Spec.HELP, *values), null, null)
        }
        return RejectionScreen(error.code, entry?.let { SpecCopy.text(it.title, *values) }, message, action, SpecCopy.text(Spec.ACTION_LABELS.getValue(action)),
            SpecCopy.text(Spec.REQUESTED_BY, *values), help.first, help.second, help.third)
    }
}
