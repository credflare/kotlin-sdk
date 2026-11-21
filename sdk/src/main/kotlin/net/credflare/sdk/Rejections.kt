package net.credflare.sdk

import net.credflare.sdk.CredflareSpec.CopyKey as Key
import net.credflare.sdk.CredflareSpec.ErrorCode as Code

internal object Rejections {
    private val newChallenge = setOf(Code.LIVENESS_CHALLENGE_EXPIRED, Code.LIVENESS_CHALLENGE_INVALID, Code.LIVENESS_CHALLENGE_USED, Code.LIVENESS_FRAME_OUT_OF_WINDOW)

    fun livenessMessage(error: CredflareException, verifier: String): String =
        if(error.code in newChallenge) SpecCopy.text(Key.LIVENESS_EXPIRED, "verifier" to verifier) else error.message.orEmpty()

    fun locationMessage(error: CredflareException, reference: LocationReference?, verifier: String): String = when(error.code) {
        Code.LOCATION_REFERENCE_NOT_MATCHED -> if(reference != null && reference.placeLabels.isNotEmpty())
            SpecCopy.text(if(reference.rule == "outside") Key.LOCATION_PLACE_NOT_MATCHED_OUTSIDE else Key.LOCATION_PLACE_NOT_MATCHED_WITHIN, "place" to reference.placeLabels.joinToString(", "), "verifier" to verifier)
            else SpecCopy.text(Key.LOCATION_PLACE_REJECTED, "verifier" to verifier)
        Code.LOCATION_MOCK -> SpecCopy.text(Key.LOCATION_MOCK_DETECTED)
        Code.LOCATION_ACCURACY -> SpecCopy.text(if(reference != null) Key.LOCATION_PLACE_NOT_PRECISE else Key.LOCATION_LOW_ACCURACY)
        Code.LOCATION_STALE -> SpecCopy.text(Key.LOCATION_STALE)
        Code.LOCATION_IP_COUNTRY -> SpecCopy.text(Key.LOCATION_IP_COUNTRY_MISMATCH)
        else -> error.message.orEmpty()
    }
}
