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
        var rejection: RejectionScreen? = null
        private val help get() = info?.help ?: HelpContact(null, null)
        val answers = mutableMapOf<String, Any?>()
        var answerErrors: Map<String, String> = emptyMap()
        var answersKey = UUID.randomUUID()
        val destinations = mutableMapOf<String, String>()
        var codeChannel: String? = null
        var codeRequest: CodeRequest? = null
        var resendAt = 0L
        var codeDraft = ""

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
                throw Rejected(Rejections.screen(error, null, verifier, help))
            }
            discardLiveness()
        }
        fun acceptLocation(fix: LocationFix) { locationFix = fix; locationKey = UUID.randomUUID() }
        fun sendLocation(fix: LocationFix, reference: LocationReference?, verifier: String) {
            try { client!!.submitLocation(fix, locationKey) }
            catch(error: CredflareException) {
                if(error.status != 422) throw error
                locationFix = null; locationFailures += 1
                throw Rejected(Rejections.screen(error, reference, verifier, help))
            }
            locationFix = null
        }
        fun requestCode(channel: String, destination: String, verifier: String, now: Long, wallClock: Long = System.currentTimeMillis()) {
            destinations[channel] = destination
            val sent = try { client!!.requestCode(channel, destination) }
            catch(error: CredflareException) {
                val next = error.resendAvailableAt?.let { IsoTime.parse(it) }?.time
                if(next != null) resendAt = now + (next - wallClock).coerceIn(0L, CredflareSpec.Requirements.CONTACT_CODES_RESEND_SECONDS * 1000L)
                throw if(error.status == 422) Rejected(Rejections.screen(error, null, verifier, help)) else error
            }
            codeRequest = sent; codeChannel = channel; resendAt = now + sent.resendDelayMillis; codeDraft = ""
        }
        fun confirmCode(channel: String, code: String, verifier: String) {
            try { client!!.confirmCode(channel, code) }
            catch(error: CredflareException) { throw if(error.status == 422) Rejected(Rejections.screen(error, null, verifier, help)) else error }
            codeChannel = null; codeRequest = null; codeDraft = ""
        }
        fun resendIn(now: Long): Long = (resendAt - now).coerceAtLeast(0)
        fun sendAnswers(questionnaire: Questionnaire, verifier: String) {
            answerErrors = questionnaire.errors(answers)
            if(answerErrors.isNotEmpty()) throw IllegalArgumentException(answerErrors.values.first())
            try { client!!.submitAnswers(questionnaire.fields(answers), answersKey) }
            catch(error: CredflareException) { if(error.status == 422) { answersKey = UUID.randomUUID(); throw Rejected(Rejections.screen(error, null, verifier, help)) }; throw error }
            answers.clear(); answersKey = UUID.randomUUID()
        }
        fun refresh() {
            pendingReceipt = true
            info = try { client!!.get() } catch(error: Exception) { throw RefreshFailed(error) }
            pendingReceipt = false
        }
    }
    internal class Rejected(val screen: RejectionScreen): Exception(screen.message)
    internal class RefreshFailed(cause: Exception): Exception(cause.message, cause)
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
