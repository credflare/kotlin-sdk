package net.credflare.kotlindemo

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import net.credflare.sdk.*
import org.json.JSONObject
import java.net.URL
import java.util.UUID

class ContractInstrumentation: Instrumentation() {
    private var base = "http://127.0.0.1:3108/api/v1"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); base=arguments?.getString("contract_base") ?: base; start() }
    override fun onStart() {
        val output=Bundle()
        try {
            val fixture=JSONObject(context.assets.open("capture-either.json").bufferedReader().use { it.readText() }).getJSONObject("data")
            fun descriptor(token: String = "test-session-token") = SessionDescriptor(UUID.fromString(fixture.getString("session_id")),token,base,fixture.getString("expires_at"))
            val info=VerificationSession(descriptor()).get()
            check(info.documents[0].captureSteps().map { it.id } == listOf("either:front","either:back"))
            val combined=JSONObject(context.assets.open("capture-snapshot_and_upload.json").bufferedReader().use { it.readText() }).getJSONObject("data")
            check(SessionInfo.fromJson(combined).documents[0].captureSteps().map { it.id } == listOf("camera:front","camera:back","upload:front","upload:back"))
            fun expect(type: String, operation: () -> Unit) {
                try { operation(); error("Expected $type failure") } catch(error: CredflareException) { check(error.type == type) { "Expected $type, received ${error.type}" } }
            }
            expect("session_unavailable") { VerificationSession(descriptor("unauthorized")).get() }
            expect("network") { VerificationSession(descriptor("slow"),200).get() }
            expect("network") { VerificationSession(descriptor("broken-body")).get() }
            expect("api") { VerificationSession(descriptor("redirect")).get() }
            val stats=JSONObject(URL(base.substringBefore("/api/v1")+"/stats").readText())
            check(stats.getInt("redirectHits") == 0)
            val session=VerificationSession(descriptor())
            val key=UUID.randomUUID()
            val bytes=context.assets.open("sample-front.png").use { it.readBytes() }
            check(session.upload(bytes,"sample-front.png",CaptureSource.UPLOAD,DocumentSide.FRONT,info.documents[0].id,key) == 1)
            val uploaded=JSONObject(URL(base.substringBefore("/api/v1")+"/stats").readText())
            check(uploaded.getString("requestKey") == key.toString() && uploaded.getString("uploadSource") == "upload")
            session.cancel()
            check(!descriptor().toString().contains("test-session-token"))
            try { SessionDescriptor(UUID.randomUUID(),"test-token","http://example.test","2030-01-01"); error("Unsafe URL accepted") } catch(_: IllegalArgumentException) {}
            output.putString(REPORT_KEY_STREAMRESULT,"PASS: native HTTP contract, capture rules, authentication, timeout, interrupted body, redirects, upload metadata and credential redaction")
            finish(Activity.RESULT_OK,output)
        } catch(error: Throwable) {
            output.putString(REPORT_KEY_STREAMRESULT,"FAIL: ${error.javaClass.simpleName}: ${error.message}")
            finish(Activity.RESULT_CANCELED,output)
        }
    }
}
