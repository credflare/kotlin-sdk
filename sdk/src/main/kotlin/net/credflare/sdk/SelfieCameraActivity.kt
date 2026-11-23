package net.credflare.sdk

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

class LivenessCapture(
    val challenge: LivenessChallenge,
    val provider: String,
    val gestureFrames: List<ByteArray>,
    val selfie: ByteArray,
    val video: File?,
    val results: List<GestureResult>,
    val faceDetection: FaceDetectionSummary,
    val capturedAt: String,
) {
    internal val gestureKeys = gestureFrames.map { UUID.randomUUID() }
    internal val gestureIds = MutableList<Int?>(gestureFrames.size) { null }
    internal val videoKey = UUID.randomUUID()
    internal var videoId: Int? = null
    internal val selfieKey = UUID.randomUUID()
    internal var selfieId: Int? = null
    internal val submitKey = UUID.randomUUID()

    internal fun send(client: VerificationSession): Int {
        val id = challenge.challengeId
        gestureFrames.forEachIndexed { index, bytes ->
            if(gestureIds[index] == null) gestureIds[index] = client.uploadBiometric(bytes, "${challenge.gestures[index]}.jpg", BiometricUploadType.GESTURE, id, challenge.gestures[index], gestureKeys[index])
        }
        if(video != null && videoId == null) videoId = client.uploadBiometric(video.readBytes(), "liveness.mp4", BiometricUploadType.LIVENESS_VIDEO, id, key = videoKey)
        if(selfieId == null) selfieId = client.uploadBiometric(selfie, "selfie.jpg", BiometricUploadType.SELFIE, id, key = selfieKey)
        return client.submitFacial(FacialSubmission(id, selfieId!!, gestureIds.map { it!! }, videoId, provider, results, faceDetection, capturedAt), submitKey)
    }
}

private class AspectFrame(context: Context): FrameLayout(context) {
    var ratio = 4f / 3f
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val maxHeight = MeasureSpec.getSize(heightMeasureSpec).takeIf { MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.UNSPECIFIED } ?: Int.MAX_VALUE
        var w = width; var h = (width * ratio).toInt()
        if(h > maxHeight) { h = maxHeight; w = (h / ratio).toInt() }
        super.onMeasure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
    }
}

class SelfieCameraActivity: Activity(), SurfaceHolder.Callback {
    private enum class Phase { POSITION, GESTURE, SELFIE, DONE }

    private val ui by lazy { SpecUi(this) }
    private var challenge: LivenessChallenge? = null
    private var provider: FaceSignalProvider? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var recorder: MediaRecorder? = null
    private var videoFile: File? = null
    private var recording = false
    private var recorded = false
    private lateinit var preview: SurfaceView
    private lateinit var frame: AspectFrame
    private lateinit var progress: TextView
    private lateinit var prompt: TextView
    private lateinit var recordingLabel: TextView
    private var cameraThread: HandlerThread? = null
    private var analysisThread: HandlerThread? = null
    private var cameraHandler: Handler? = null
    private var analysisHandler: Handler? = null
    private var rotation = 0
    private var resumed = false
    private var opening = false
    private val finished = AtomicBoolean(false)

    private var phase = Phase.POSITION
    private var run: EngineRun = PositionGate()
    private var gestureIndex = 0
    private var startedAt = 0L
    private var lastSampleT = -1L
    private val frames = mutableListOf<ByteArray>()
    private val results = mutableListOf<GestureResult>()
    private var promptHoldUntil = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val flow = CredflareVerification.active.get()
        challenge = flow?.challenge
        if(challenge == null || savedInstanceState != null) { fail(CredflareSpec.CopyKey.LIVENESS_EXPIRED); return }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(ui.color(ui.palette.surface))
            val gutter = ui.dp(CredflareSpec.Tokens.SPACING_GUTTER)
            setPadding(gutter, ui.dp(CredflareSpec.Tokens.SPACING_XL), gutter, gutter)
        }
        page.addView(ui.text(SpecCopy.text(CredflareSpec.CopyKey.LIVENESS_TITLE), CredflareSpec.Tokens.TYPE_TITLE), LinearLayout.LayoutParams(-1, -2))
        progress = ui.text(SpecCopy.text(CredflareSpec.CopyKey.SELFIE_STARTING_CAMERA), CredflareSpec.Tokens.TYPE_EYEBROW, ui.palette.muted)
        page.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(CredflareSpec.Tokens.SPACING_XS) })
        prompt = ui.text("", CredflareSpec.Tokens.TYPE_SUBTITLE).apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE; minLines = 2 }
        page.addView(prompt, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(CredflareSpec.Tokens.SPACING_MD); bottomMargin = ui.dp(CredflareSpec.Tokens.SPACING_MD) })
        frame = AspectFrame(this).apply { background = ui.rounded(ui.palette.soft, ui.palette.border); clipToOutline = true }
        preview = SurfaceView(this).apply { holder.addCallback(this@SelfieCameraActivity); contentDescription = SpecCopy.text(CredflareSpec.CopyKey.SELFIE_PREVIEW_LABEL) }
        frame.addView(preview, FrameLayout.LayoutParams(-1, -1))
        page.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f).apply { gravity = Gravity.CENTER_HORIZONTAL })
        recordingLabel = ui.text("● " + SpecCopy.text(CredflareSpec.CopyKey.VIDEO_RECORDING), CredflareSpec.Tokens.TYPE_CHIP, ui.palette.error).apply { visibility = View.INVISIBLE }
        page.addView(recordingLabel, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(CredflareSpec.Tokens.SPACING_SM) })
        page.addView(ui.button(SpecCopy.text(CredflareSpec.CopyKey.COMMON_CANCEL), primary = false) { cancel() }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ui.dp(CredflareSpec.Tokens.SPACING_MD) })
        setContentView(page)
        ui.systemBars(window)
        if(Build.VERSION.SDK_INT >= 35) page.setOnApplyWindowInsetsListener { view, insets -> val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars()); view.setPadding(view.paddingLeft, bars.top + ui.dp(CredflareSpec.Tokens.SPACING_XL), view.paddingRight, bars.bottom + ui.dp(CredflareSpec.Tokens.SPACING_GUTTER)); insets }
    }

    override fun onResume() { super.onResume(); resumed = true; if(::preview.isInitialized && preview.holder.surface.isValid) open() }
    override fun onPause() {
        resumed = false
        if(!finished.get() && device != null) fail(CredflareSpec.CopyKey.LIVENESS_EXPIRED)
        super.onPause()
    }
    override fun onDestroy() { release(); super.onDestroy() }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { cancel() }
    override fun surfaceCreated(holder: SurfaceHolder) { open() }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { release() }

    private fun cancel() {
        if(!finished.compareAndSet(false, true)) return
        release(); discardVideo(); setResult(RESULT_CANCELED); finish()
    }

    private fun fail(copyKey: String) {
        if(!finished.compareAndSet(false, true)) return
        CredflareVerification.active.get()?.livenessError = copyKey
        runOnUiThread { release(); discardVideo(); setResult(RESULT_CANCELED); finish() }
    }

    private fun discardVideo() { videoFile?.delete() }

    private fun release() {
        val current = device
        device = null
        try { session?.close() } catch(_: Exception) {}
        session = null
        if(recording) { try { recorder?.stop() } catch(_: RuntimeException) {}; recording = false }
        recorder?.release(); recorder = null
        current?.close()
        reader?.close(); reader = null
        analysisThread?.quitSafely(); analysisThread = null; analysisHandler = null
        cameraThread?.quitSafely(); cameraThread = null; cameraHandler = null
        provider?.close(); provider = null
        opening = false
    }

    private fun largest(sizes: Array<Size>?, fits: (Size) -> Boolean): Size? = sizes?.filter(fits)?.maxByOrNull { it.width.toLong() * it.height }

    private fun open() {
        val issued = challenge ?: return
        if(!resumed || opening || device != null || finished.get()) return
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { fail(CredflareSpec.CopyKey.ERRORS_CAMERA_DENIED); return }
        opening = true
        try {
            val manager = getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
                ?: return fail(CredflareSpec.CopyKey.ERRORS_FRONT_CAMERA_REQUIRED)
            val characteristics = manager.getCameraCharacteristics(id)
            val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
            @Suppress("DEPRECATION")
            val display = when(windowManager.defaultDisplay.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
            rotation = (sensorOrientation + display) % 360
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE)
            val analysis = largest(map.getOutputSizes(ImageFormat.YUV_420_888)) { it.width.toLong() * it.height <= 1280L * 720 && it.width <= 1280 }
                ?: return fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE)
            val aspect = analysis.width.toFloat() / analysis.height
            val previewSize = largest(map.getOutputSizes(SurfaceHolder::class.java)) { it.width <= 1920 && it.height <= 1080 && kotlin.math.abs(it.width.toFloat() / it.height - aspect) < 0.05f } ?: analysis
            frame.ratio = if(rotation % 180 == 0) previewSize.height.toFloat() / previewSize.width else previewSize.width.toFloat() / previewSize.height
            frame.requestLayout()
            preview.holder.setFixedSize(previewSize.width, previewSize.height)
            cameraThread = HandlerThread("credflare-camera").apply { start() }
            analysisThread = HandlerThread("credflare-analysis").apply { start() }
            cameraHandler = Handler(cameraThread!!.looper)
            analysisHandler = Handler(analysisThread!!.looper)
            provider = CredflareVerification.faceSignalProvider()
            reader = ImageReader.newInstance(analysis.width, analysis.height, ImageFormat.YUV_420_888, 3).apply {
                setOnImageAvailableListener({ source -> source.acquireLatestImage()?.let { analyze(it) } }, analysisHandler)
            }
            if(issued.recordVideo) {
                val videoSize = largest(map.getOutputSizes(MediaRecorder::class.java)) { minOf(it.width, it.height) <= CredflareSpec.Liveness.VIDEO_MAX_HEIGHT && kotlin.math.abs(it.width.toFloat() / it.height - aspect) < 0.05f }
                    ?: largest(map.getOutputSizes(MediaRecorder::class.java)) { minOf(it.width, it.height) <= CredflareSpec.Liveness.VIDEO_MAX_HEIGHT }
                    ?: return fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE)
                videoFile = File(File(cacheDir, "credflare-captures").apply { mkdirs() }, "${UUID.randomUUID()}.mp4")
                @Suppress("DEPRECATION")
                recorder = (if(Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else MediaRecorder()).apply {
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    setOutputFile(videoFile!!.absolutePath)
                    setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                    setVideoSize(videoSize.width, videoSize.height)
                    setVideoFrameRate(30)
                    setVideoEncodingBitRate(1_500_000)
                    setOrientationHint(rotation)
                    setMaxDuration(issued.maxVideoSeconds.coerceAtMost(CredflareSpec.Liveness.VIDEO_MAX_SECONDS) * 1000)
                    setOnInfoListener { _, what, _ -> if(what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) cameraHandler?.post { recordingStopped() } }
                    prepare()
                }
            }
            manager.openCamera(id, object: CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    opening = false
                    if(!resumed || finished.get() || !preview.holder.surface.isValid) { camera.close(); return }
                    device = camera
                    configure(camera)
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE) }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE) }
            }, cameraHandler)
        } catch(_: SecurityException) { fail(CredflareSpec.CopyKey.ERRORS_CAMERA_DENIED) }
        catch(_: Exception) { fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE) }
    }

    @Suppress("DEPRECATION")
    private fun configure(camera: CameraDevice) {
        val surfaces = listOfNotNull(preview.holder.surface, reader!!.surface, recorder?.surface)
        try {
            camera.createCaptureSession(surfaces, object: CameraCaptureSession.StateCallback() {
                override fun onConfigured(configured: CameraCaptureSession) {
                    if(device !== camera) { configured.close(); return }
                    session = configured
                    startedAt = SystemClock.elapsedRealtime()
                    repeat(false)
                    runOnUiThread { progress.text = ""; prompt.text = SpecCopy.text(CredflareSpec.CopyKey.LIVENESS_POSITION_NO_FACE) }
                }
                override fun onConfigureFailed(configured: CameraCaptureSession) { fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE) }
            }, cameraHandler)
        } catch(_: Exception) { fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE) }
    }

    private fun repeat(withRecorder: Boolean) {
        val camera = device ?: return
        val current = session ?: return
        try {
            val request = camera.createCaptureRequest(if(withRecorder) CameraDevice.TEMPLATE_RECORD else CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(preview.holder.surface); addTarget(reader!!.surface)
                if(withRecorder) recorder?.surface?.let { addTarget(it) }
                set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            }
            current.setRepeatingRequest(request.build(), null, cameraHandler)
        } catch(_: Exception) { fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE) }
    }

    private fun startRecording() {
        val output = recorder ?: return
        if(recording || recorded) return
        try { output.start(); recording = true; repeat(true); runOnUiThread { recordingLabel.visibility = View.VISIBLE } }
        catch(_: Exception) { fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE) }
    }

    private fun recordingStopped() {
        if(!recording) return
        recording = false
        recorded = true
        repeat(false)
        runOnUiThread { recordingLabel.visibility = View.INVISIBLE }
    }

    private fun stopRecording(): Boolean {
        if(!recording) return recorded
        repeat(false)
        recording = false
        return try { recorder?.stop(); recorded = true; runOnUiThread { recordingLabel.visibility = View.INVISIBLE }; true }
        catch(_: RuntimeException) { false }
    }

    private fun analyze(image: Image) {
        val now = SystemClock.elapsedRealtime()
        if(finished.get() || session == null || startedAt == 0L) { image.close(); return }
        val t = now - startedAt
        if(lastSampleT >= 0 && t - lastSampleT < CredflareSpec.Liveness.SAMPLE_INTERVAL_MS) { image.close(); return }
        lastSampleT = t
        val cameraFrame = try { CameraFrame(nv21(image), image.width, image.height, rotation) } finally { image.close() }
        if(t >= (CredflareSpec.Liveness.CHALLENGE_TTL_S - 20) * 1000L) { fail(CredflareSpec.CopyKey.LIVENESS_EXPIRED); return }
        val sample = try { provider?.detect(cameraFrame, t) ?: return } catch(_: Exception) { return }
        val result = run.push(sample)
        when(result.state) {
            RunState.TIMEOUT -> fail(CredflareSpec.CopyKey.LIVENESS_TIMEOUT)
            RunState.RUNNING -> show(when {
                phase == Phase.GESTURE && !result.tracked -> CredflareSpec.CopyKey.LIVENESS_FACE_LOST
                phase == Phase.GESTURE -> gestureKey()
                else -> result.status!!.copyKey
            }, now)
            RunState.PASS -> passed(cameraFrame, sample, now)
        }
    }

    private fun gestureKey() = "liveness.gesture." + challenge!!.gestures[gestureIndex]

    private fun passed(cameraFrame: CameraFrame, sample: FaceSample, now: Long) {
        val issued = challenge!!
        when(phase) {
            Phase.POSITION -> {
                phase = Phase.GESTURE; gestureIndex = 0; run = GestureRun(issued.gestures[0], provider!!.name)
                if(issued.recordVideo) cameraHandler?.post { startRecording() }
                show(gestureKey(), now, force = true)
            }
            Phase.GESTURE -> {
                frames += jpeg(cameraFrame)
                results += GestureResult(issued.gestures[gestureIndex], true, IsoTime.now())
                gestureIndex += 1
                show(CredflareSpec.CopyKey.LIVENESS_GESTURE_DONE, now, force = true)
                promptHoldUntil = now + 600
                if(gestureIndex < issued.gestures.size) run = GestureRun(issued.gestures[gestureIndex], provider!!.name)
                else {
                    phase = Phase.SELFIE; run = PositionGate()
                    cameraHandler?.post { stopRecording() }
                }
            }
            Phase.SELFIE -> {
                phase = Phase.DONE
                val selfie = jpeg(cameraFrame)
                val summary = FaceDetectionSummary(sample.faces > 0, sample.faces, true, true)
                cameraHandler?.post {
                    val videoOk = !issued.recordVideo || stopRecording()
                    if(!videoOk) { fail(CredflareSpec.CopyKey.ERRORS_CAMERA_UNAVAILABLE); return@post }
                    val flow = CredflareVerification.active.get()
                    if(flow == null || !finished.compareAndSet(false, true)) return@post
                    flow.liveness = LivenessCapture(issued, provider!!.name, frames.toList(), selfie, if(issued.recordVideo) videoFile else null, results.toList(), summary, IsoTime.now())
                    runOnUiThread { prompt.text = SpecCopy.text(CredflareSpec.CopyKey.LIVENESS_SUCCESS); release(); setResult(RESULT_OK); finish() }
                }
            }
            Phase.DONE -> {}
        }
    }

    private fun show(key: String, now: Long, force: Boolean = false) {
        if(!force && now < promptHoldUntil) return
        val total = challenge!!.gestures.size
        val step = when(phase) { Phase.GESTURE -> SpecCopy.text(CredflareSpec.CopyKey.LIVENESS_PROGRESS, "current" to (gestureIndex + 1).coerceAtMost(total), "total" to total); Phase.SELFIE, Phase.DONE -> SpecCopy.text(CredflareSpec.CopyKey.SELFIE_TITLE); else -> "" }
        val message = SpecCopy.text(key)
        runOnUiThread { if(progress.text != step) progress.text = step; if(prompt.text != message) prompt.text = message }
    }

    private fun nv21(image: Image): ByteArray {
        val width = image.width; val height = image.height
        val out = ByteArray(width * height * 3 / 2)
        val y = image.planes[0]; val u = image.planes[1]; val v = image.planes[2]
        val yBuffer = y.buffer
        var offset = 0
        for(row in 0 until height) { yBuffer.position(row * y.rowStride); yBuffer.get(out, offset, width); offset += width }
        val uBuffer = u.buffer; val vBuffer = v.buffer
        for(row in 0 until height / 2) for(col in 0 until width / 2) {
            out[offset++] = vBuffer.get(row * v.rowStride + col * v.pixelStride)
            out[offset++] = uBuffer.get(row * u.rowStride + col * u.pixelStride)
        }
        return out
    }

    private fun jpeg(frame: CameraFrame): ByteArray {
        val raw = ByteArrayOutputStream()
        YuvImage(frame.nv21, ImageFormat.NV21, frame.width, frame.height, null).compressToJpeg(Rect(0, 0, frame.width, frame.height), 92, raw)
        if(frame.rotationDegrees == 0) return raw.toByteArray()
        val bitmap = BitmapFactory.decodeByteArray(raw.toByteArray(), 0, raw.size())
        val upright = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(frame.rotationDegrees.toFloat()) }, true)
        return ByteArrayOutputStream().also { upright.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }
}
