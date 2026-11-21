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
        var busyMessage: String? = null
        var challenge: LivenessChallenge? = null
        @Volatile var liveness: LivenessCapture? = null
        @Volatile var livenessError: String? = null
        var locationFix: LocationFix? = null
        var locationKey = UUID.randomUUID()
        var locationFailures = 0
        val received = mutableListOf<String>()

        fun selectDocument(doc: DocumentRequirement) { document = doc; index = 0; captures.clear(); submitKey = UUID.randomUUID(); error = null }
        fun addCapture(step: CaptureStep, file: SelectedFile) { captures[step.id] = file; submitKey = UUID.randomUUID() }
        fun sendDocument(doc: DocumentRequirement, steps: List<CaptureStep>, progress: (Int) -> Unit) {
            val evidence = steps.mapIndexed { index, step ->
                val file = captures[step.id]!!
                progress(index)
                if(file.fileId == null) file.fileId = client!!.upload(file.bytes, file.name, file.source, step.side, doc.id, file.key)
                Evidence(file.fileId!!, file.source, step.side)
            }
            client!!.submit(doc.id, evidence, submitKey)
        }
        fun discardLiveness() { liveness?.video?.delete(); liveness = null; challenge = null }
        fun sendLiveness(capture: LivenessCapture, verifier: String) {
            try { capture.send(client!!) }
            catch(error: CredflareException) {
                if(error.status != 422) throw error
                discardLiveness()
                throw IllegalArgumentException(Rejections.livenessMessage(error, verifier))
            }
            discardLiveness()
        }
        fun acceptLocation(fix: LocationFix) { locationFix = fix; locationKey = UUID.randomUUID() }
        fun sendLocation(fix: LocationFix, reference: LocationReference?, verifier: String) {
            try { client!!.submitLocation(fix, locationKey) }
            catch(error: CredflareException) {
                if(error.status != 422) throw error
                locationFix = null; locationFailures += 1
                throw IllegalArgumentException(Rejections.locationMessage(error, reference, verifier))
            }
            locationFix = null
        }
    }
    internal class SelectedFile(val bytes: ByteArray, val name: String, val source: CaptureSource) {
        val key = UUID.randomUUID()
        var fileId: Int? = null
    }
    internal val active = AtomicReference<Launch?>()
    @Volatile var faceSignalProvider: () -> FaceSignalProvider = { MlKitFaceSignalProvider() }
    fun launch(activity: Activity, provider: SessionProvider, onResult: (VerificationResult) -> Unit): Boolean {
        val launch = Launch(provider, onResult)
        if(!active.compareAndSet(null, launch)) return false
        try { activity.startActivity(Intent(activity, VerificationActivity::class.java).putExtra("flow_id", launch.id)); return true }
        catch(_: Exception) { active.compareAndSet(launch, null); onResult(VerificationResult.FAILED); return false }
    }
}
