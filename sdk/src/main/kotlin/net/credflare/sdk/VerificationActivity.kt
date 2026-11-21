package net.credflare.sdk

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
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
import java.text.DateFormat
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import net.credflare.sdk.CredflareSpec.CopyKey as Key
import net.credflare.sdk.CredflareSpec.Tokens as T

class VerificationActivity: Activity() {
    private var flow: CredflareVerification.Launch? = null
    private lateinit var page: LinearLayout
    private val ui by lazy { SpecUi(this) }
    private val executor = Executors.newSingleThreadExecutor()
    private var photoFile: File?
        get() = flow?.cameraPath?.let { File(it) }
        set(value) { flow?.cameraPath = value?.absolutePath }
    private var photoUri: Uri?
        get() = flow?.cameraUri?.let { Uri.parse(it) }
        set(value) { flow?.cameraUri = value?.toString() }
    private val changed: () -> Unit = { runOnUiThread { if(!isDestroyed && !isFinishing) render() } }
    private val verifier get() = SpecCopy.verifier(flow?.info?.requesterName)
    private fun copy(key: String, vararg values: Pair<String, Any?>) = SpecCopy.text(key, "verifier" to verifier, *values)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        flow = CredflareVerification.active.get()?.takeIf { it.id == intent.getStringExtra("flow_id") }
        if(Build.VERSION.SDK_INT >= 33) onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) { closeRequested() }
        flow?.changed = changed
        val current = flow
        if(current != null && current.info == null && current.client == null && !current.busy && current.error == null) start(current) else render()
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { closeRequested() }
    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        if(flow?.changed === changed) flow?.changed = null
        if(isFinishing) {
            photoUri?.let { revokeUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            photoFile?.delete()
            flow?.liveness?.video?.delete()
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
        val info = current.info
        if(info != null && !current.pendingReceipt && info.documents.isEmpty() && info.nextRequirement == null) { result(if(info.needsHostSteps) VerificationResult.HOST_ACTION_REQUIRED else VerificationResult.SUBMITTED); return }
        AlertDialog.Builder(this).setTitle(copy(Key.EXIT_TITLE)).setMessage(copy(Key.EXIT_BODY))
            .setNegativeButton(copy(Key.EXIT_STAY), null).setPositiveButton(copy(Key.EXIT_LEAVE)) { _, _ -> result(VerificationResult.CANCELLED) }.show()
    }

    private fun add(view: View, top: Int = T.SPACING_SM): View {
        page.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(top); gravity = Gravity.CENTER_HORIZONTAL })
        return view
    }
    private fun title(value: String) = add(ui.text(value, T.TYPE_TITLE), T.SPACING_LG)
    private fun body(value: String, muted: Boolean = true) = add(ui.text(value, T.TYPE_BODY, if(muted) ui.palette.muted else ui.palette.ink))
    private fun small(value: String) = add(ui.text(value, T.TYPE_SMALL, ui.palette.muted))
    private fun icon(spec: SpecIcon, size: Int = T.SIZE_ICON * 2) { page.addView(ui.icon(spec, size).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(T.SPACING_XL) }) }
    private fun button(label: String, primary: Boolean = true, enabled: Boolean = true, action: () -> Unit) = add(ui.button(label, primary, enabled, action), T.SPACING_MD)

    private fun work(message: String, failure: String = Key.ERRORS_GENERIC, task: () -> Unit, then: (() -> Unit)? = null) {
        val current = flow ?: return
        if(current.busy) return
        current.busy = true; current.error = null; current.busyMessage = message; render()
        executor.execute {
            var ok = false
            try { task(); ok = true }
            catch(error: Exception) { current.error = describe(error, failure) }
            finally { current.busy = false; current.busyMessage = null; current.changed?.invoke() }
            if(ok && then != null) runOnUiThread { if(!isFinishing) then() }
        }
    }
    private fun describe(error: Exception, failure: String): String = when {
        error is CredflareException && error.status == 401 -> copy(Key.ERRORS_SESSION_EXPIRED)
        error is CredflareException && error.status == 422 -> error.message ?: copy(failure)
        error is CredflareException && error.status == 0 -> copy(Key.ERRORS_NETWORK)
        error is LocationCaptureException -> copy(error.copyKey)
        error is IllegalArgumentException && error.message != null -> error.message!!
        else -> copy(failure)
    }
    private fun start(current: CredflareVerification.Launch) = work(copy(Key.INTRO_CHECKING), Key.ERRORS_START_FAILED, { current.client = VerificationSession(current.provider.createSession()); current.info = current.client!!.get() })
    private fun refresh(current: CredflareVerification.Launch) { current.pendingReceipt = true; current.info = current.client!!.get(); current.pendingReceipt = false }

    private fun render() {
        val scroll = ScrollView(this).apply { setBackgroundColor(ui.color(ui.palette.surface)); isFillViewport = true }
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(T.SPACING_GUTTER), ui.dp(T.SPACING_LG), ui.dp(T.SPACING_GUTTER), ui.dp(T.SPACING_GUTTER))
        }
        val container = FrameLayout(this)
        container.addView(page, FrameLayout.LayoutParams(minOf(resources.displayMetrics.widthPixels, ui.dp(T.SIZE_MAX_CONTENT_WIDTH)), -2, Gravity.CENTER_HORIZONTAL))
        scroll.addView(container); setContentView(scroll)
        if(Build.VERSION.SDK_INT >= 35) scroll.setOnApplyWindowInsetsListener { view, insets -> val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()); view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets }
        val current = flow
        if(current == null) {
            icon(CredflareSpec.Icons.ERROR); title(copy(Key.ERRORS_SESSION_EXPIRED)); button(copy(Key.COMMON_RETURN_TO_APP)) { finish() }; return
        }
        header(current)
        val info = current.info
        when {
            current.busy -> { add(ProgressBar(this), T.SPACING_XXL); body(current.busyMessage ?: copy(Key.COMMON_LOADING)) }
            info == null -> {
                icon(CredflareSpec.Icons.ID_CARD); title(copy(Key.INTRO_TITLE)); body(copy(Key.INTRO_BODY))
                button(copy(if(current.error == null) Key.INTRO_START else Key.COMMON_TRY_AGAIN)) { start(current) }
            }
            current.pendingReceipt -> {
                icon(CredflareSpec.Icons.RECEIVED); title(copy(Key.SENDING_SENT_TITLE)); body(copy(Key.SENDING_SENT_BODY))
                button(copy(Key.SENDING_CHECK_AGAIN)) { work(copy(Key.INTRO_CHECKING), Key.ERRORS_REFRESH_FAILED, { refresh(current) }) }
            }
            info.documents.isNotEmpty() && current.document != null -> capture(current)
            info.documents.isNotEmpty() -> documents(current, info)
            info.nextRequirement?.facial != null -> facial(current, info.nextRequirement!!.facial!!)
            info.nextRequirement?.location != null -> location(current, info.nextRequirement!!.location!!)
            else -> receipt(current, info)
        }
        current.error?.let { showError(it) }
        footer()
    }

    private fun header(current: CredflareVerification.Launch) {
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = ui.dp(T.SIZE_HEADER_HEIGHT) }
        bar.addView(View(this), LinearLayout.LayoutParams(ui.dp(64), 1))
        bar.addView(ui.text(current.info?.requesterName.orEmpty(), T.TYPE_LABEL, ui.palette.muted).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(Button(this).apply {
            text = copy(Key.COMMON_CLOSE); isAllCaps = false; isEnabled = !current.busy; background = null; setTextColor(ui.color(ui.palette.primary)); minWidth = ui.dp(64)
            setOnClickListener { closeRequested() }
        }, LinearLayout.LayoutParams(-2, ui.dp(T.SIZE_LINK_HEIGHT)))
        page.addView(bar, LinearLayout.LayoutParams(-1, -2))
        if(current.info?.environment == "sandbox") add(ui.text(copy(Key.COMMON_TEST_MODE), T.TYPE_CHIP, ui.palette.warningInk).apply {
            background = ui.rounded(ui.palette.warningSoft, radius = T.RADIUS_PILL); setPadding(ui.dp(T.SPACING_MD), ui.dp(T.SPACING_XS), ui.dp(T.SPACING_MD), ui.dp(T.SPACING_XS))
        }.also { it.layoutParams = LinearLayout.LayoutParams(-2, -2) }, T.SPACING_XS).let { (it.layoutParams as LinearLayout.LayoutParams).width = -2 }
    }

    private fun footer() {
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(0, ui.dp(T.SPACING_XXL), 0, 0) }
        footer.addView(ImageView(this).apply { setImageResource(R.drawable.credflare_mark); setBackgroundColor(ui.color(CredflareSpec.Tokens.LIGHT.surface)); setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2)); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO }, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)))
        footer.addView(ui.text(copy(Key.COMMON_SECURED_BY), T.TYPE_CHIP, ui.palette.muted).apply { setPadding(ui.dp(T.SPACING_SM - 2), 0, 0, 0) })
        page.addView(footer, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showError(message: String) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            background = ui.rounded(ui.palette.soft, ui.palette.border, T.RADIUS_MD)
            setPadding(ui.dp(T.SPACING_LG), ui.dp(T.SPACING_MD), ui.dp(T.SPACING_LG), ui.dp(T.SPACING_MD))
            addView(ui.icon(CredflareSpec.Icons.ERROR, T.SIZE_ICON, ui.palette.error))
            addView(ui.text(message, T.TYPE_BODY, ui.palette.error).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(T.SPACING_XS) })
        }
        add(box, T.SPACING_LG)
    }

    private fun documents(current: CredflareVerification.Launch, info: SessionInfo) {
        title(copy(Key.DOCUMENTS_TITLE)); body(copy(Key.DOCUMENTS_REQUESTED_BY))
        var lastGroup: String? = "\u0000"
        for(doc in info.documents) {
            val group = if(doc.rule == "any_of") doc.groupKey else null
            if(group != lastGroup) {
                val label = when { group == null -> copy(Key.DOCUMENTS_SECTION_REQUIRED); doc.remainingRequired <= 1 -> copy(Key.DOCUMENTS_SECTION_ONE_OF); else -> copy(Key.DOCUMENTS_SECTION_COUNT_OF, "count" to doc.remainingRequired) }
                add(ui.text(label, T.TYPE_EYEBROW, ui.palette.muted), T.SPACING_XL)
                lastGroup = group
            }
            val subtitle = if(doc.supported) doc.methodSummary else copy(Key.DOCUMENTS_UNAVAILABLE)
            add(ui.card(doc.name, subtitle, if(doc.sides.size > 1) CredflareSpec.Icons.ID_CARD else CredflareSpec.Icons.DOCUMENT, doc.supported) {
                current.selectDocument(doc); render()
            }, T.SPACING_MD)
        }
        val inApp = info.requirements.filter { it.collectedInApp }.map { it.facial != null }.distinct()
        val next = when(inApp) {
            listOf(true) -> Key.DOCUMENTS_SELFIE_NEXT
            listOf(false) -> Key.DOCUMENTS_LOCATION_NEXT
            listOf(true, false) -> Key.DOCUMENTS_SELFIE_LOCATION_NEXT
            listOf(false, true) -> Key.DOCUMENTS_LOCATION_SELFIE_NEXT
            else -> if(info.needsHostSteps) Key.DOCUMENTS_HOST_STEPS_NEXT else null
        }
        next?.let { small(copy(it)).also { view -> (view.layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(T.SPACING_LG) } }
    }

    private fun sideLabel(side: DocumentSide) = copy(if(side == DocumentSide.FRONT) Key.CAPTURE_SIDE_FRONT else Key.CAPTURE_SIDE_BACK)
    private fun sourceLabel(source: CaptureSource) = copy(if(source == CaptureSource.CAMERA) Key.CAPTURE_SOURCE_CAMERA else Key.CAPTURE_SOURCE_UPLOAD)

    private fun capture(current: CredflareVerification.Launch) {
        val doc = current.document!!; val steps = doc.captureSteps(); val info = current.info!!
        add(ui.button(copy(Key.DOCUMENTS_BACK), primary = false) {
            fun leave() { current.document = null; current.captures.clear(); current.error = null; render() }
            if(current.captures.isEmpty()) leave() else AlertDialog.Builder(this).setTitle(copy(Key.EXIT_DISCARD_TITLE)).setMessage(copy(Key.EXIT_DISCARD_BODY)).setNegativeButton(copy(Key.EXIT_DISCARD_STAY), null).setPositiveButton(copy(Key.EXIT_DISCARD_LEAVE)) { _, _ -> leave() }.show()
        }.apply { setCompoundDrawablesRelativeWithIntrinsicBounds(SpecIconDrawable(CredflareSpec.Icons.CHEVRON_LEFT, ui.color(ui.palette.primary)).apply { setBounds(0, 0, ui.dp(20), ui.dp(20)) }, null, null, null); compoundDrawablePadding = ui.dp(T.SPACING_XS) }, T.SPACING_XS)
        add(ui.text(doc.name, T.TYPE_EYEBROW, ui.palette.muted), T.SPACING_LG)
        if(current.index == steps.size) {
            title(copy(Key.REVIEW_TITLE)); body(copy(Key.REVIEW_BODY))
            steps.forEachIndexed { index, step ->
                val file = current.captures[step.id]!!
                add(ui.text(copy(Key.REVIEW_ITEM, "side" to sideLabel(step.side), "source" to sourceLabel(file.source)), T.TYPE_LABEL), T.SPACING_LG)
                preview(file, copy(Key.CAPTURE_PREVIEW_LABEL, "side" to sideLabel(step.side)))
                button(copy(Key.REVIEW_CHANGE), primary = false) { current.index = index; current.editing = true; render() }
            }
            button(copy(Key.REVIEW_SEND)) { work(copy(Key.SENDING_UPLOADING), Key.ERRORS_SEND_DOCUMENT_FAILED, {
                current.sendDocument(doc, steps) { index -> current.busyMessage = copy(Key.SENDING_UPLOADING_PROGRESS, "current" to index + 1, "total" to steps.size); current.changed?.invoke() }
                current.received += copy(Key.RECEIPT_DOCUMENT, "document" to doc.name)
                current.captures.clear(); current.document = null
                refresh(current)
            }) }
            return
        }
        val step = steps[current.index]
        title(sideLabel(step.side))
        small(copy(Key.COMMON_STEP_PROGRESS, "current" to current.index + 1, "total" to steps.size + 1))
        val file = current.captures[step.id]
        if(file != null) {
            preview(file, copy(Key.CAPTURE_PREVIEW_LABEL, "side" to sideLabel(step.side)))
            small(file.name)
            if(file.source == CaptureSource.CAMERA) body(copy(Key.CAPTURE_CHECK_PHOTO))
            button(copy(if(file.source == CaptureSource.CAMERA) Key.CAPTURE_RETAKE_PHOTO else Key.CAPTURE_CHANGE_FILE), primary = false) { current.captures.remove(step.id); render() }
            button(copy(Key.COMMON_CONTINUE)) { current.index = if(current.editing) steps.size else current.index + 1; current.editing = false; render() }
        } else {
            val side = sideLabel(step.side).lowercase(Locale.ROOT)
            body(when(step.input) {
                "camera" -> copy(Key.CAPTURE_INSTRUCTION_CAMERA, "document" to doc.name)
                "upload" -> copy(if("pdf" in info.allowedFileTypes) Key.CAPTURE_INSTRUCTION_UPLOAD_PDF else Key.CAPTURE_INSTRUCTION_UPLOAD, "side" to side)
                else -> copy(Key.CAPTURE_INSTRUCTION_EITHER, "side" to side)
            })
            if(step.input != "upload") button(copy(Key.CAPTURE_TAKE_PHOTO)) { camera() }
            if(step.input != "camera") button(copy(Key.CAPTURE_CHOOSE_FILE), primary = step.input == "upload") { chooseFile() }
            small(copy(Key.CAPTURE_FILE_LIMITS, "types" to info.allowedFileTypes.joinToString(", ") { it.uppercase() }, "size" to "${info.maxFileSizeBytes / 1048576} MB"))
        }
    }

    private fun facial(current: CredflareVerification.Launch, settings: FacialSettings) {
        val capture = current.liveness
        if(capture != null) {
            icon(CredflareSpec.Icons.VIDEO); title(copy(Key.LIVENESS_SUCCESS))
            button(copy(Key.LIVENESS_RETRY)) { sendLiveness(current) }
            return
        }
        icon(CredflareSpec.Icons.SELFIE)
        title(copy(Key.LIVENESS_TITLE))
        body(copy(Key.LIVENESS_BODY, "total" to settings.gestureCount))
        body(copy(Key.SELFIE_GUIDANCE))
        if(settings.recordVideo) add(ui.text(copy(Key.VIDEO_CONSENT), T.TYPE_BODY, ui.palette.ink).apply {
            background = ui.rounded(ui.palette.soft, radius = T.RADIUS_MD); setPadding(ui.dp(T.SPACING_LG), ui.dp(T.SPACING_MD), ui.dp(T.SPACING_LG), ui.dp(T.SPACING_MD))
        }, T.SPACING_LG)
        current.livenessError?.let { showError(copy(it)) }
        button(copy(if(current.livenessError == null && current.error == null) Key.LIVENESS_START else Key.LIVENESS_RETRY)) { startLiveness(current) }
        small(copy(Key.SELFIE_PRIVACY))
    }


    private fun startLiveness(current: CredflareVerification.Launch) {
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.CAMERA), 104); return }
        current.livenessError = null
        work(copy(Key.SELFIE_STARTING_CAMERA), Key.ERRORS_CHALLENGE_FAILED, { current.challenge = current.client!!.createLivenessChallenge() }) {
            try { startActivityForResult(Intent(this, SelfieCameraActivity::class.java), 105) }
            catch(_: Exception) { current.error = copy(Key.ERRORS_CAMERA_UNAVAILABLE); render() }
        }
    }

    private fun sendLiveness(current: CredflareVerification.Launch) {
        val capture = current.liveness ?: return
        work(copy(Key.LIVENESS_SENDING), Key.ERRORS_SEND_SELFIE_FAILED, {
            current.sendLiveness(capture, verifier)
            current.received += copy(Key.RECEIPT_SELFIE)
            refresh(current)
        })
    }

    private fun places(reference: LocationReference) = reference.placeLabels.joinToString(", ")

    private fun location(current: CredflareVerification.Launch, settings: LocationSettings) {
        val reference = settings.reference?.takeIf { it.placeLabels.isNotEmpty() }
        val fix = current.locationFix
        icon(CredflareSpec.Icons.LOCATION)
        if(fix != null) {
            title(copy(Key.LOCATION_FOUND_TITLE)); body(copy(Key.LOCATION_FOUND_BODY))
            fun detail(label: String, value: String) { add(ui.text(label, T.TYPE_EYEBROW, ui.palette.muted), T.SPACING_LG); add(ui.text(value, T.TYPE_BODY), T.SPACING_XXS) }
            detail(copy(Key.LOCATION_ACCURACY_LABEL), copy(Key.LOCATION_ACCURACY_VALUE, "meters" to Math.round(fix.accuracy)))
            detail(copy(Key.LOCATION_COORDINATES_LABEL), String.format(Locale.ROOT, "%.5f, %.5f", fix.latitude, fix.longitude))
            detail(copy(Key.LOCATION_TIME_LABEL), IsoTime.parse(fix.fixTime)?.let { DateFormat.getTimeInstance(DateFormat.MEDIUM).format(it) } ?: fix.fixTime)
            button(copy(Key.LOCATION_SEND)) { sendLocation(current, settings) }
            button(copy(Key.LOCATION_RETRY), primary = false) { current.locationFix = null; shareLocation(current, settings) }
            return
        }
        title(if(reference == null) copy(Key.LOCATION_TITLE) else copy(if(reference.rule == "outside") Key.LOCATION_PLACE_TITLE_OUTSIDE else Key.LOCATION_PLACE_TITLE_WITHIN, "place" to places(reference)))
        body(copy(Key.LOCATION_WHY))
        if(reference != null && reference.placeLabels.size > 1) body(copy(when {
            reference.rule == "outside" && reference.match == "all" -> Key.LOCATION_PLACE_NEED_OUTSIDE_ALL
            reference.rule == "outside" -> Key.LOCATION_PLACE_NEED_OUTSIDE_ANY
            reference.match == "all" -> Key.LOCATION_PLACE_NEED_WITHIN_ALL
            else -> Key.LOCATION_PLACE_NEED_WITHIN_ANY
        }, "places" to places(reference)), muted = false)
        body(copy(Key.LOCATION_WHAT_SHARED))
        small(copy(Key.LOCATION_PERMISSION_PROMPT))
        if(current.locationFailures >= CredflareSpec.Location.RETRY_ATTEMPTS && current.error != null) small(copy(Key.LOCATION_RETRIES_EXHAUSTED))
        button(copy(if(current.error == null) Key.LOCATION_SHARE else Key.LOCATION_RETRY)) { shareLocation(current, settings) }
    }

    private fun shareLocation(current: CredflareVerification.Launch, settings: LocationSettings) {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 103); return
        }
        work(copy(Key.LOCATION_WAITING), Key.LOCATION_UNAVAILABLE, {
            try {
                val location = LocationCapture.acquire(applicationContext, settings) { attempt -> if(attempt > 1) { current.busyMessage = copy(Key.LOCATION_WAITING_ACCURACY); current.changed?.invoke() } }
                current.acceptLocation(LocationCapture.toFix(location))
            } catch(error: Exception) { current.locationFailures += 1; throw error }
        })
    }

    private fun sendLocation(current: CredflareVerification.Launch, settings: LocationSettings) {
        val fix = current.locationFix ?: return
        work(copy(Key.SENDING_SENDING), Key.ERRORS_SEND_LOCATION_FAILED, {
            current.sendLocation(fix, settings.reference, verifier)
            current.received += copy(Key.RECEIPT_LOCATION)
            refresh(current)
        })
    }

    private fun receipt(current: CredflareVerification.Launch, info: SessionInfo) {
        page.addView(ui.icon(CredflareSpec.Icons.RECEIVED_ILLUSTRATION, T.SIZE_ILLUSTRATION).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = ui.dp(T.SPACING_XXL) })
        title(copy(Key.RECEIPT_TITLE))
        body(copy(Key.RECEIPT_BODY))
        for(line in current.received) {
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
            row.addView(ui.icon(CredflareSpec.Icons.RECEIVED, 20, ui.palette.success).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(20), ui.dp(20)) })
            row.addView(ui.text(line, T.TYPE_LABEL).apply { setPadding(ui.dp(T.SPACING_SM), 0, 0, 0) })
            add(row, T.SPACING_SM)
        }
        if(info.needsHostSteps) body(copy(Key.RECEIPT_HOST_STEPS), muted = false)
        button(copy(if(info.needsHostSteps) Key.COMMON_RETURN_TO_APP else Key.RECEIPT_DONE)) { result(if(info.needsHostSteps) VerificationResult.HOST_ACTION_REQUIRED else VerificationResult.SUBMITTED) }
    }

    private fun decodePreview(bytes: ByteArray): android.graphics.Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if(bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while(bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val orientation = try { android.media.ExifInterface(java.io.ByteArrayInputStream(bytes)).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL) } catch(_: Exception) { android.media.ExifInterface.ORIENTATION_NORMAL }
        val transform = android.graphics.Matrix()
        when(orientation) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> transform.postRotate(90f)
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> transform.postRotate(180f)
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> transform.postRotate(270f)
            android.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> transform.postScale(-1f, 1f)
            android.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> transform.postScale(1f, -1f)
            android.media.ExifInterface.ORIENTATION_TRANSPOSE -> { transform.postRotate(90f); transform.postScale(-1f, 1f) }
            android.media.ExifInterface.ORIENTATION_TRANSVERSE -> { transform.postRotate(270f); transform.postScale(-1f, 1f) }
        }
        return if(transform.isIdentity) bitmap else android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transform, true)
    }
    private fun preview(file: CredflareVerification.SelectedFile, label: String) {
        val bitmap = decodePreview(file.bytes)
        if(bitmap != null) page.addView(ImageView(this).apply { setImageBitmap(bitmap); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; contentDescription = label; background = ui.rounded(ui.palette.soft, radius = T.RADIUS_MD) }, LinearLayout.LayoutParams(-1, ui.dp(210)).apply { topMargin = ui.dp(T.SPACING_SM) })
        else add(ui.text(copy(if(file.name.endsWith(".pdf", true)) Key.CAPTURE_PREVIEW_PDF else Key.CAPTURE_PREVIEW_DOCUMENT), T.TYPE_LABEL, ui.palette.muted).apply { background = ui.rounded(ui.palette.soft, radius = T.RADIUS_MD); setPadding(0, ui.dp(T.SPACING_XL), 0, ui.dp(T.SPACING_XL)) })
    }
    private fun chooseFile() {
        val types = flow?.info?.allowedFileTypes.orEmpty().mapNotNull { when(it) { "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "webp" -> "image/webp"; "pdf" -> "application/pdf"; "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"; else -> null } }.toTypedArray()
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*"; putExtra(Intent.EXTRA_MIME_TYPES, types) }
        try { startActivityForResult(picker, 100) } catch(_: Exception) { flow?.error = copy(Key.ERRORS_ADD_FILE_FAILED); render() }
    }
    private fun camera() {
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions?.contains(Manifest.permission.CAMERA) == true) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 102); return
        }
        try {
            val directory = File(cacheDir, "credflare-captures").apply { mkdirs() }
            photoFile = File(directory, "${UUID.randomUUID()}.jpg")
            photoUri = FileProvider.getUriForFile(this, "$packageName.credflare.capture", photoFile!!)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply { putExtra(MediaStore.EXTRA_OUTPUT, photoUri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION); clipData = ClipData.newRawUri("Document photo", photoUri) }
            startActivityForResult(intent, 101)
        } catch(_: Exception) { flow?.error = copy(Key.ERRORS_CAMERA_UNAVAILABLE); render() }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val current = flow ?: return
        val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        when(requestCode) {
            102 -> if(granted) camera() else { current.error = copy(Key.ERRORS_CAMERA_DENIED); render() }
            104 -> if(granted) startLiveness(current) else { current.error = copy(Key.ERRORS_CAMERA_DENIED); render() }
            103 -> {
                val settings = current.info?.nextRequirement?.location ?: return
                if(granted) shareLocation(current, settings) else { current.error = copy(Key.LOCATION_PERMISSION_DENIED); render() }
            }
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val current = flow ?: return
        if(requestCode == 105) {
            current.challenge = null
            if(resultCode == RESULT_OK && current.liveness != null) sendLiveness(current) else render()
            return
        }
        if(requestCode !in listOf(100, 101)) return
        if(resultCode != RESULT_OK) { photoFile?.delete(); return }
        val uri = if(requestCode == 101) photoUri else data?.data
        if(uri == null) return
        work(copy(Key.COMMON_LOADING), Key.ERRORS_ADD_FILE_FAILED, {
            val info = current.info!!
            val name = if(requestCode == 101) "camera.jpg" else contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: throw IllegalArgumentException(copy(Key.ERRORS_FILE_UNREADABLE))
            val limit = info.maxFileSizeBytes
            require(limit in 1..Int.MAX_VALUE.toLong()) { copy(Key.ERRORS_GENERIC) }
            val size = "${limit / 1048576} MB"
            val bytes = contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(16384); var read = input.read(buffer)
                while(read != -1) { require(out.size().toLong() + read <= limit) { copy(Key.ERRORS_FILE_TOO_LARGE, "size" to size) }; out.write(buffer, 0, read); read = input.read(buffer) }
                out.toByteArray()
            } ?: throw IllegalArgumentException(copy(Key.ERRORS_FILE_UNREADABLE))
            require(bytes.isNotEmpty()) { copy(Key.ERRORS_FILE_EMPTY) }
            val ext = name.substringAfterLast('.').lowercase().let { if(it == "jpeg") "jpg" else it }
            require(ext in info.allowedFileTypes) { if(requestCode == 101) copy(Key.ERRORS_PHOTO_FORMAT) else copy(Key.ERRORS_FILE_TYPE, "types" to info.allowedFileTypes.joinToString(", ") { it.uppercase() }) }
            if(requestCode == 101) require(decodePreview(bytes) != null) { copy(Key.ERRORS_CAMERA_NO_PHOTO) }
            val step = current.document!!.captureSteps()[current.index]
            current.addCapture(step, CredflareVerification.SelectedFile(bytes, name, if(requestCode == 101) CaptureSource.CAMERA else CaptureSource.UPLOAD))
            if(requestCode == 101) { photoFile?.delete(); photoUri?.let { revokeUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }; photoUri = null; photoFile = null }
        })
    }
}
