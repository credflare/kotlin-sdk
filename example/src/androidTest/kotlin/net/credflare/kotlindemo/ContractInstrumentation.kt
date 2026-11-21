package net.credflare.kotlindemo

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import net.credflare.sdk.*
import org.json.JSONObject
import java.net.URL
import java.util.UUID

class ContractInstrumentation: Instrumentation() {
    private var base = "http://127.0.0.1:3108/api/v1"
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); base=arguments?.getString("contract_base") ?: base; probe=arguments?.getString("face_probe"); start() }
    private var probe: String? = null
    override fun onStart() {
        val output=Bundle()
        probe?.let { path -> output.putString(REPORT_KEY_STREAMRESULT,faceProbe(path)); finish(Activity.RESULT_OK,output); return }
        try {
            val fixture=JSONObject(context.assets.open("capture-snapshot-or-upload.json").bufferedReader().use { it.readText() }).getJSONObject("data")
            fun descriptor(token: String = "test-session-token") = SessionDescriptor(UUID.fromString(fixture.getString("session_id")),token,base,fixture.getString("expires_at"))
            val info=VerificationSession(descriptor()).get()
            check(info.documents[0].captureSteps().map { it.id } == listOf("camera_or_upload:front","camera_or_upload:back"))
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
            val facialInfo=VerificationSession(descriptor("selfie")).get()
            check(facialInfo.requiresFacial && !facialInfo.needsHostSteps && facialInfo.documents.isEmpty())
            val capture=VerificationSession(descriptor("capture"))
            val captureInfo=capture.get()
            check(captureInfo.requirements.map { it.type } == listOf("facial_verification","location_verification"))
            check(captureInfo.requirements[1].location!!.reference == LocationReference("within","any",listOf("Head office")))
            check(captureInfo.requirements[0].facial!!.gestureCount == 3 && captureInfo.requirements[1].location!!.maxAccuracyM == 50)
            val before=JSONObject(URL(base.substringBefore("/api/v1")+"/stats").readText())
            val challenge=capture.createLivenessChallenge()
            check(challenge.challengeId == "01a1fb8d-6c01-7160-a4fe-31853bb8dbdd" && challenge.gestures == listOf("nod","turn_right","turn_left") && challenge.recordVideo && challenge.maxVideoSeconds == 8)
            val gestureKeys=challenge.gestures.map { UUID.randomUUID() }
            val gestureIds=challenge.gestures.mapIndexed { index,gesture -> capture.uploadBiometric(bytes,"$gesture.png",BiometricUploadType.GESTURE,challenge.challengeId,gesture,gestureKeys[index]) }
            check(gestureIds == listOf(3,3,3))
            val videoKey=UUID.randomUUID()
            val videoId=capture.uploadBiometric("not-a-real-video".toByteArray(),"clip.mp4",BiometricUploadType.LIVENESS_VIDEO,challenge.challengeId,key=videoKey)
            check(videoId == 5)
            val selfieKey=UUID.randomUUID()
            val selfieId=capture.uploadBiometric(bytes,"selfie.png",BiometricUploadType.SELFIE,challenge.challengeId,key=selfieKey)
            check(selfieId == 4)
            try { capture.uploadBiometric(bytes,"frame.png",BiometricUploadType.GESTURE,challenge.challengeId,null,UUID.randomUUID()); error("Gesture frame without a gesture accepted") } catch(_: IllegalArgumentException) {}
            val results=challenge.gestures.map { GestureResult(it,true,"2026-11-20T23:04:00Z") }
            val facialKey=UUID.randomUUID()
            check(capture.submitFacial(FacialSubmission(challenge.challengeId,selfieId,gestureIds.mapIndexed { index,_ -> 10+index },videoId,"mlkit",results,FaceDetectionSummary(true,1,true,true),"2026-11-20T23:04:10Z"),facialKey) == 1)
            val fix=LocationFix(4.0511,9.7679,12.5,null,"2026-11-20T23:04:00Z",true,"gps","2026-11-20T23:04:02Z")
            val locationKey=UUID.randomUUID()
            check(capture.submitLocation(fix,locationKey) == 2)
            val captureStats=JSONObject(URL(base.substringBefore("/api/v1")+"/stats").readText())
            check(captureStats.getInt("challengeRequests") == before.getInt("challengeRequests")+1)
            val all=captureStats.getJSONArray("biometrics")
            val offset=before.getJSONArray("biometrics").length()
            check(all.length()-offset == 5) { "Expected five biometric uploads, saw ${all.length()-offset}" }
            val uploads=org.json.JSONArray((offset until all.length()).map { all.getJSONObject(it) })
            for(index in 0 until 3) {
                val upload=uploads.getJSONObject(index)
                check(upload.getString("type") == "gesture" && upload.getString("gesture") == challenge.gestures[index] && upload.getString("challenge_id") == challenge.challengeId && upload.getString("capture_source") == "camera" && upload.getString("key") == gestureKeys[index].toString())
            }
            uploads.getJSONObject(3).let { check(it.getString("type") == "liveness_video" && it.getString("content_type") == "video/mp4" && it.getString("capture_source") == "camera" && it.getString("challenge_id") == challenge.challengeId && it.isNull("gesture") && it.getString("key") == videoKey.toString()) }
            uploads.getJSONObject(4).let { check(it.getString("type") == "selfie" && it.getString("capture_source") == "camera" && it.getString("challenge_id") == challenge.challengeId && it.isNull("gesture") && !it.getJSONArray("fields").toString().contains("document_type_id") && it.getString("key") == selfieKey.toString()) }
            val submits=captureStats.getJSONArray("submits")
            check(submits.getJSONObject(submits.length()-2).getString("key") == facialKey.toString() && submits.getJSONObject(submits.length()-1).getString("key") == locationKey.toString()) { "Submit idempotency keys were not sent" }
            val facial=JSONObject(captureStats.getString("facialBody"))
            check(facial.getString("type") == "facial_verification" && facial.getString("challenge_id") == challenge.challengeId && facial.getInt("image_file_id") == selfieId && facial.getInt("video_file_id") == videoId)
            check(facial.getJSONArray("gesture_file_ids").toString() == "[10,11,12]")
            val facialMetadata=facial.getJSONObject("metadata")
            check(facialMetadata.getString("provider") == "mlkit" && facialMetadata.getJSONArray("liveness_results").length() == 3 && facialMetadata.getJSONObject("face_detection").getInt("face_count") == 1 && facialMetadata.has("captured_at"))
            check(facialMetadata.getJSONArray("liveness_results").getJSONObject(0).let { it.getString("challenge") == "nod" && it.getBoolean("completed") && it.has("timestamp") })
            val location=JSONObject(captureStats.getString("locationBody"))
            val locationMetadata=location.getJSONObject("metadata")
            check(location.getString("type") == "location_verification" && location.getDouble("latitude") == 4.0511 && location.getDouble("longitude") == 9.7679)
            check(locationMetadata.getDouble("accuracy") == 12.5 && locationMetadata.isNull("altitude") && locationMetadata.getBoolean("is_mock") && locationMetadata.getString("provider") == "gps" && locationMetadata.getString("fix_time") == "2026-11-20T23:04:00Z" && locationMetadata.has("captured_at"))
            try { VerificationSession(descriptor("reject-location")).submitLocation(fix,UUID.randomUUID()); error("Location rejection was not surfaced") }
            catch(error: CredflareException) { check(error.status == 422 && error.type == "validation" && error.code == CredflareSpec.ErrorCode.LOCATION_ACCURACY && error.isKnownCode) { "Unexpected rejection ${error.status} ${error.code}" } }
            try { VerificationSession(descriptor("reject-place")).submitLocation(fix,UUID.randomUUID()); error("Place rejection was not surfaced") }
            catch(error: CredflareException) { check(error.status == 422 && error.code == CredflareSpec.ErrorCode.LOCATION_REFERENCE_NOT_MATCHED) }
            val rejections=context.assets.list("rejections")!!.filter { it.endsWith(".json") }.sorted()
            check(rejections.map { it.removeSuffix(".json") } == CredflareSpec.ErrorCode.MEANINGS.keys.sorted()) { "Rejection fixtures do not cover every spec code" }
            for(name in rejections) {
                val code=name.removeSuffix(".json")
                val message=JSONObject(context.assets.open("rejections/$name").bufferedReader().use { it.readText() }).getString("message")
                val client=VerificationSession(descriptor("reject:$code"))
                try {
                    if(code.startsWith("location.")) client.submitLocation(fix,UUID.randomUUID())
                    else client.submitFacial(FacialSubmission(challenge.challengeId,1,listOf(2),3,"mlkit",results,FaceDetectionSummary(true,1,true,true),"2026-11-20T23:04:10Z"),UUID.randomUUID())
                    error("$code was not rejected")
                } catch(error: CredflareException) { check(error.status == 422 && error.code == code && error.isKnownCode && error.message == message) { "Unexpected rejection for $code: ${error.code}" } }
            }
            session.cancel()
            check(!descriptor().toString().contains("test-session-token"))
            try { SessionDescriptor(UUID.randomUUID(),"test-token","http://example.test","2030-01-01"); error("Unsafe URL accepted") } catch(_: IllegalArgumentException) {}
            output.putString(REPORT_KEY_STREAMRESULT,"PASS: native HTTP contract, capture rules, authentication, timeout, interrupted body, redirects, requirement settings, liveness challenge, gesture, video and selfie uploads, facial and location submissions, location rejections, every spec rejection code, upload metadata and credential redaction")
            finish(Activity.RESULT_OK,output)
        } catch(error: Throwable) {
            output.putString(REPORT_KEY_STREAMRESULT,"FAIL: ${error.javaClass.simpleName}: ${error.message}")
            finish(Activity.RESULT_CANCELED,output)
        }
    }
    private fun faceProbe(path: String): String {
        val original=BitmapFactory.decodeFile(path) ?: return "FAIL: cannot read $path"
        fun transformed(block: Matrix.() -> Unit) = Bitmap.createBitmap(original,0,0,original.width,original.height,Matrix().apply(block),true)
        val cases=listOf(
            "upright" to (original to 0),
            "mirrored" to (transformed { postScale(-1f,1f) } to 0),
            "stored rotated 90 clockwise, rotation 270" to (transformed { postRotate(90f) } to 270),
            "stored rotated 90 clockwise, rotation 90" to (transformed { postRotate(90f) } to 90),
        )
        return MlKitFaceSignalProvider().use { provider ->
            cases.joinToString("\n") { (name,input) -> "$name: ${provider.detect(input.first,input.second)}" }
        }
    }
}
