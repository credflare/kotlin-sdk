package net.credflare.sdk

import android.app.Activity
import android.content.Intent
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

fun interface SessionProvider { fun createSession(): SessionDescriptor }
object CredflareVerification {
    internal class Launch(val provider: SessionProvider, val result: (VerificationResult) -> Unit) {
        val id = UUID.randomUUID().toString()
        var changed: (() -> Unit)? = null
        var cameraPath: String? = null
        var cameraUri: String? = null
        var client: VerificationSession? = null
        var info: SessionInfo? = null
        var document: DocumentRequirement? = null
        var index = 0
        var editing = false
        var pendingReceipt = false
        var busy = false
        var error: String? = null
        var submitKey = UUID.randomUUID()
        val captures = mutableMapOf<String, SelectedFile>()
    }
    internal class SelectedFile(val bytes: ByteArray, val name: String, val source: CaptureSource) {
        val key = UUID.randomUUID()
        var fileId: Int? = null
    }
    internal val active = AtomicReference<Launch?>()
    fun launch(activity: Activity, provider: SessionProvider, onResult: (VerificationResult) -> Unit): Boolean {
        val launch = Launch(provider, onResult)
        if(!active.compareAndSet(null, launch)) return false
        try { activity.startActivity(Intent(activity, VerificationActivity::class.java).putExtra("flow_id", launch.id)); return true }
        catch(_: Exception) { active.compareAndSet(launch, null); onResult(VerificationResult.FAILED); return false }
    }
}
