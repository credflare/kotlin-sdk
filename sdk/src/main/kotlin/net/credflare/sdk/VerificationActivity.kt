package net.credflare.sdk

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import android.window.OnBackInvokedDispatcher
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

class VerificationActivity: Activity() {
    private var flow: CredflareVerification.Launch? = null
    private lateinit var page: LinearLayout
    private val executor = Executors.newSingleThreadExecutor()
    private var photoFile: File?
        get() = flow?.cameraPath?.let { File(it) }
        set(value) { flow?.cameraPath = value?.absolutePath }
    private var photoUri: Uri?
        get() = flow?.cameraUri?.let { Uri.parse(it) }
        set(value) { flow?.cameraUri = value?.toString() }
    private val changed: () -> Unit = { runOnUiThread { if(!isDestroyed && !isFinishing) render() } }
    private val dark get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val ink get() = if(dark) Color.rgb(244,247,254) else Color.rgb(24,35,60)
    private val muted get() = if(dark) Color.rgb(183,196,218) else Color.rgb(82,97,122)
    private val primary get() = if(dark) Color.rgb(185,205,255) else Color.rgb(32,49,95)
    private val surface get() = if(dark) Color.rgb(17,28,49) else Color.WHITE
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flow = CredflareVerification.active.get()?.takeIf { it.id == intent.getStringExtra("flow_id") }
        if(Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { closeRequested() }
        flow?.changed = changed
        val current = flow
        if(current != null && current.info == null && current.client == null && !current.busy && current.error == null) {
            work { current.client = VerificationSession(current.provider.createSession()); current.info = current.client!!.get() }
        } else render()
    }
    override fun onBackPressed() { closeRequested() }
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        if(flow?.changed === changed) flow?.changed = null
        if(isFinishing) {
            photoUri?.let { revokeUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            photoFile?.delete()
            flow?.let { current -> if(CredflareVerification.active.compareAndSet(current, null)) current.result(VerificationResult.CANCELLED) }
        }
    }
    private fun result(value: VerificationResult) {
        val current = flow
        if(current != null && CredflareVerification.active.compareAndSet(current, null)) current.result(value)
        finish()
    }
    private fun closeRequested() {
        val current = flow ?: return finish()
        if(current.busy) return
        if(current.info != null && !current.pendingReceipt && current.info!!.documents.isEmpty() && !current.info!!.needsHostSteps && !current.info!!.requiresSelfie) { result(VerificationResult.SUBMITTED); return }
        val body = if(current.captures.isNotEmpty()) "Your unsent photos and files will be discarded. Documents you already sent are kept." else "Documents you already sent are kept. You can return through the app that requested them."
        AlertDialog.Builder(this).setTitle("Leave verification?").setMessage(body).setNegativeButton("Continue", null).setPositiveButton("Leave for now") { _,_ -> result(VerificationResult.CANCELLED) }.show()
    }
    private fun text(value: String, size: Float = 16f, secondary: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size; gravity=Gravity.CENTER; setTextColor(if(secondary) muted else ink)
        setPadding(0,dp(7),0,dp(7)); page.addView(this)
    }
    private fun button(label: String, enabled: Boolean = true, action: () -> Unit) {
        val view = Button(this).apply { text = label; isAllCaps = false; textSize = 16f; isEnabled = enabled; minHeight = dp(54); setTextColor(if(dark) Color.rgb(22,41,78) else Color.WHITE); backgroundTintList = android.content.res.ColorStateList.valueOf(primary); setOnClickListener { action() } }
        page.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin=dp(12) })
    }
    private fun work(task: () -> Unit) {
        val current = flow ?: return
        if(current.busy) return
        current.busy = true; current.error = null; render()
        executor.execute {
            try { task() }
            catch(error: Exception) { current.error = if(error is CredflareException && error.status == 401) "Your session has expired. Close this screen and start again." else if(error is CredflareException || error is IllegalArgumentException) error.message else "We could not complete that step. Please try again." }
            finally { current.busy = false; current.changed?.invoke() }
        }
    }
    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(surface); isFillViewport = true }
        page = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(24),dp(28),dp(24),dp(24)) }
        scroll.addView(page); setContentView(scroll)
        val current = flow
        if(current == null) { text("Start again from your app",24f); text("This verification session is no longer available on this device.",secondary=true); button("Return to app") { finish() }; return }
        val header = LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply { text=current.info?.requesterName ?: ""; setTextColor(ink); textSize=15f }, LinearLayout.LayoutParams(0,-2,1f))
        header.addView(Button(this).apply { text="Close"; isAllCaps=false; isEnabled=!current.busy; setOnClickListener { closeRequested() } })
        page.addView(header)
        if(current.info?.environment == "sandbox") text("Test mode",12f,true)
        if(current.busy) { page.addView(ProgressBar(this)); text("Getting or sending your evidence…",secondary=true) }
        else if(current.info == null) {
            text("Verify your identity",24f); text("Follow the steps requested by your verifier. Review each item before sending.",secondary=true)
            button(if(current.error == null) "Start" else "Try again") { work { current.client = VerificationSession(current.provider.createSession()); current.info = current.client!!.get() } }
        } else if(current.pendingReceipt) {
            text("Your evidence was sent",24f); text("Check your progress to see what is still needed.",secondary=true)
            button("Check progress") { work { current.info = current.client!!.get(); current.pendingReceipt=false } }
        } else if(current.info!!.documents.isEmpty() && current.info!!.requiresSelfie) {
            selfie(current)
        } else if(current.info!!.documents.isEmpty()) {
            text("Evidence received",24f)
            text(if(current.info!!.needsHostSteps) "Return to the app for its remaining checks." else "Your verifier will review what you sent.",secondary=true)
            button(if(current.info!!.needsHostSteps) "Return to app" else "Done") { result(if(current.info!!.needsHostSteps) VerificationResult.HOST_ACTION_REQUIRED else VerificationResult.SUBMITTED) }
        } else if(current.document != null) capture(current)
        else {
            text("Documents requested",24f)
            if(current.info!!.requiresSelfie) text(if(current.info!!.needsHostSteps) "After your ID: take a selfie, then continue the remaining steps in the requesting app." else "After your ID: take a selfie.",secondary=true)
            var lastGroup: String? = null
            for(doc in current.info!!.documents) {
                if(doc.rule == "any_of" && doc.groupKey != lastGroup) text("Choose ${doc.remainingRequired} from this group",14f,true)
                lastGroup=doc.groupKey
                button(doc.name + "\n" + doc.methodSummary, doc.supported) { current.document=doc; current.index=0; current.captures.clear(); current.submitKey=UUID.randomUUID(); render() }
            }
        }
        current.error?.let { text(it,16f).apply { setTextColor(if(dark) Color.rgb(255,180,171) else Color.rgb(170,37,37)); accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE } }
        val footer=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(0,dp(32),0,0) }
        footer.addView(ImageView(this).apply { setImageResource(R.drawable.credflare_mark); setBackgroundColor(Color.WHITE); setPadding(dp(2),dp(2),dp(2),dp(2)); importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO },LinearLayout.LayoutParams(dp(18),dp(18)))
        footer.addView(TextView(this).apply { text="Secured by Credflare"; textSize=12f; setTextColor(muted); setPadding(dp(6),0,0,0) })
        page.addView(footer,LinearLayout.LayoutParams(-1,-2))
        if(Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener { view,insets -> val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars()); view.setPadding(bars.left,bars.top,bars.right,bars.bottom); insets }
    }
    private fun selfie(current: CredflareVerification.Launch) {
        val photo = current.captures["selfie"]
        text(if(photo == null) "Take a selfie" else "Review your selfie",24f)
        text("Face the camera in even lighting. Keep your whole face visible and remove anything covering it.",secondary=true)
        if(photo != null) {
            preview(photo)
            button("Retake selfie") { camera() }
            button("Send selfie") { work {
                if(photo.fileId == null) photo.fileId = current.client!!.uploadSelfie(photo.bytes,photo.name,photo.key)
                current.client!!.submitSelfie(photo.fileId!!,current.submitKey)
                current.captures.clear(); current.pendingReceipt=true
                current.info=current.client!!.get(); current.pendingReceipt=false
            } }
        } else button("Open front camera") { camera() }
        text("Your selfie will be sent to your verifier for review.",13f,true)
    }
    private fun isSelfie() = flow?.info?.let { it.documents.isEmpty() && it.requiresSelfie } ?: false
    private fun capture(current: CredflareVerification.Launch) {
        val doc=current.document!!; val steps=doc.captureSteps()
        button("‹ Documents") {
            fun leave() { current.document=null; current.captures.clear(); render() }
            if(current.captures.isEmpty()) leave() else AlertDialog.Builder(this).setTitle("Discard unsent evidence?").setMessage("Your unsent photos and files will be discarded.").setNegativeButton("Keep editing",null).setPositiveButton("Discard") { _,_ -> leave() }.show()
        }
        text(doc.name,14f,true)
        if(current.index == steps.size) {
            text("Review before sending",24f)
            steps.forEachIndexed { index,step ->
                val file=current.captures[step.id]!!
                text("${step.side.value.replaceFirstChar { it.uppercase() }}: ${if(file.source == CaptureSource.CAMERA) "Camera photo" else "Saved file"}")
                preview(file)
                button("Change") { current.index=index; current.editing=true; render() }
            }
            button("Send document") { work {
                val evidence=steps.map { step ->
                    val file=current.captures[step.id]!!
                    if(file.fileId == null) file.fileId=current.client!!.upload(file.bytes,file.name,file.source,step.side,doc.id,file.key)
                    Evidence(file.fileId!!,file.source,step.side)
                }
                current.client!!.submit(doc.id,evidence,current.submitKey)
                current.captures.clear(); current.document=null; current.pendingReceipt=true
                current.info=current.client!!.get(); current.pendingReceipt=false
            } }
            return
        }
        val step=steps[current.index]
        text(step.side.value.replaceFirstChar { it.uppercase() },24f)
        text(when(step.input) { "camera" -> "Camera photo"; "upload" -> "Saved file"; else -> "Photo or file" },16f,true)
        text("Step ${current.index+1} of ${steps.size+1}",12f,true)
        val file=current.captures[step.id]
        if(file != null) {
            preview(file); text(file.name,13f,true)
            button("Choose again") { current.captures.remove(step.id); render() }
            button(if(current.editing) "Return to review" else if(current.index == steps.lastIndex) "Review document" else "Continue") { current.index=if(current.editing) steps.size else current.index+1; current.editing=false; render() }
        } else {
            text(if(step.input == "camera") "Keep all four corners visible and avoid reflections." else "Add a clear photo, scan or PDF of this side.",secondary=true)
            if(step.input != "upload") button("Take photo") { camera() }
            if(step.input != "camera") button("Choose a file") { chooseFile() }
            text("${current.info!!.allowedFileTypes.joinToString(", ").uppercase()}, up to ${current.info!!.maxFileSizeBytes/1048576} MB",12f,true)
        }
    }
    private fun decodePreview(bytes: ByteArray): android.graphics.Bitmap? {
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        if(bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample=1
        while(bounds.outWidth/sample > 1600 || bounds.outHeight/sample > 1600) sample*=2
        val bitmap = BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply { inSampleSize=sample }) ?: return null
        val orientation = try { android.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,android.media.ExifInterface.ORIENTATION_NORMAL) } catch(_: Exception) { android.media.ExifInterface.ORIENTATION_NORMAL }
        val transform = android.graphics.Matrix()
        when(orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> transform.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> transform.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> transform.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.postScale(-1f,1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.postScale(1f,-1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { transform.postRotate(90f); transform.postScale(-1f,1f) }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { transform.postRotate(270f); transform.postScale(-1f,1f) }
        }
        return if(transform.isIdentity) bitmap else android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,transform,true)
    }
    private fun preview(file: CredflareVerification.SelectedFile) {
        val bitmap=decodePreview(file.bytes)
        if(bitmap != null) page.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds=true; maxHeight=dp(210); scaleType=ImageView.ScaleType.FIT_CENTER; contentDescription=if(isSelfie()) "Your selfie preview" else "Selected document" },LinearLayout.LayoutParams(-1,dp(210))) else text(file.name,16f,true)
    }
    private fun chooseFile() {
        val types=flow?.info?.allowedFileTypes.orEmpty().mapNotNull { when(it) { "jpg","jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; else -> null } }.toTypedArray()
        val picker=Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type="*/*"; putExtra(Intent.EXTRA_MIME_TYPES,types) }
        try { startActivityForResult(picker,100) } catch(_: Exception) { flow?.error="No document picker is available on this device."; render() }
    }
    private fun camera() {
        if(checkSelfPermission(android.Manifest.permission.CAMERA) != android.content.pm.PackageManager.PERMISSION_GRANTED && packageManager.getPackageInfo(packageName,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions?.contains(android.Manifest.permission.CAMERA) == true) {
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA),102); return
        }
        try {
            val directory=File(cacheDir,"credflare-captures").apply { mkdirs() }
            photoFile=File(directory,"${UUID.randomUUID()}.jpg")
            photoUri=FileProvider.getUriForFile(this,"$packageName.credflare.capture",photoFile!!)
            val intent=(if(isSelfie()) Intent(this,SelfieCameraActivity::class.java) else Intent(MediaStore.ACTION_IMAGE_CAPTURE)).apply { putExtra(MediaStore.EXTRA_OUTPUT,photoUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); clipData=ClipData.newRawUri("Document photo",photoUri) }
            startActivityForResult(intent,101)
        } catch(_: Exception) { flow?.error="The camera is unavailable. Check camera access and try again."; render() }
    }
    override fun onRequestPermissionsResult(requestCode: Int,permissions: Array<out String>,grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode == 102) {
            if(grantResults.firstOrNull() == android.content.pm.PackageManager.PERMISSION_GRANTED) camera()
            else { flow?.error="Camera access is off. Enable it in Settings, then try again."; render() }
        }
    }
    override fun onActivityResult(requestCode: Int,resultCode: Int,data: Intent?) {
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode !in listOf(100,101)) return
        if(resultCode != RESULT_OK) { photoFile?.delete(); return }
        val uri=if(requestCode == 101) photoUri else data?.data
        if(uri == null) return
        val current=flow ?: return
        work {
            var name=if(requestCode == 101) "camera.jpg" else contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: throw IllegalArgumentException("This file has no readable name.")
            val limit=current.info!!.maxFileSizeBytes
            require(limit in 1..Int.MAX_VALUE.toLong()) { "File limits are unavailable." }
            val bytes=contentResolver.openInputStream(uri)?.use { input ->
                val out=ByteArrayOutputStream(); val buffer=ByteArray(16384); var read=input.read(buffer)
                while(read != -1) { require(out.size().toLong()+read <= limit) { "This file is too large." }; out.write(buffer,0,read); read=input.read(buffer) }
                out.toByteArray()
            } ?: throw IllegalArgumentException("This file could not be read.")
            require(bytes.isNotEmpty()) { "This file is empty." }
            val ext=name.substringAfterLast('.').lowercase().let { if(it == "jpeg") "jpg" else it }
            require(ext in current.info!!.allowedFileTypes) { "This file type is not allowed by your verifier." }
            if(requestCode == 101) require(decodePreview(bytes) != null) { "The camera did not return a readable photo." }
            val step=if(isSelfie()) null else current.document!!.captureSteps()[current.index]
            current.captures[step?.id ?: "selfie"]=CredflareVerification.SelectedFile(bytes,name,if(requestCode == 101) CaptureSource.CAMERA else CaptureSource.UPLOAD)
            current.submitKey=UUID.randomUUID()
            if(requestCode == 101) { photoFile?.delete(); photoUri?.let { revokeUriPermission(it,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }; photoUri=null; photoFile=null }
        }
    }
}
