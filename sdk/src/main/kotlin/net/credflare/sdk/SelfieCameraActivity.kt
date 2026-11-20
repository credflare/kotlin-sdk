package net.credflare.sdk

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File

class SelfieCameraActivity: Activity(), SurfaceHolder.Callback {
    private var device: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private lateinit var preview: SurfaceView
    private lateinit var status: TextView
    private lateinit var take: Button
    private var sensorOrientation = 0
    private var resumed = false
    private var opening = false
    private var capturing = false
    private val handler = Handler(Looper.getMainLooper())
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32,64,32,32) }
        status = TextView(this).apply { text = "Keep your whole face visible in even lighting."; textSize = 18f }
        preview = SurfaceView(this).apply { holder.addCallback(this@SelfieCameraActivity) }
        take = Button(this).apply { text = "Take selfie"; isAllCaps = false; isEnabled = false; setOnClickListener { capture() } }
        page.addView(status); page.addView(preview,LinearLayout.LayoutParams(-1,0,1f)); page.addView(take)
        page.addView(Button(this).apply { text = "Cancel"; isAllCaps = false; setOnClickListener { finish() } })
        setContentView(page)
    }
    override fun onResume() { super.onResume(); resumed = true; if(::preview.isInitialized && preview.holder.surface.isValid) open() }
    override fun onPause() { resumed = false; closeCamera(); super.onPause() }
    override fun surfaceCreated(holder: SurfaceHolder) { open() }
    override fun surfaceChanged(holder: SurfaceHolder,format: Int,width: Int,height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { closeCamera() }
    private fun closeCamera() { take.isEnabled = false; captureSession?.close(); captureSession = null; device?.close(); device = null; reader?.close(); reader = null; opening = false }
    private fun fail(message: String) { status.text = message; closeCamera() }
    private fun open() {
        if(!resumed || opening || device != null || isFinishing) return
        if(checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { fail("Camera access is off. Return and enable it in Settings."); return }
        opening = true
        try {
            val manager = getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT }
            if(id == null) { fail("A front camera is required. Try another device."); return }
            val characteristics = manager.getCameraCharacteristics(id)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: error("Camera configuration unavailable")
            val sizes = map.getOutputSizes(ImageFormat.JPEG)
            val size = sizes.filter { it.width <= 1600 && it.height <= 1600 }.maxByOrNull { it.width.toLong()*it.height } ?: sizes.minBy { it.width.toLong()*it.height }
            val previewSize = map.getOutputSizes(SurfaceHolder::class.java).filter { it.width <= 1920 && it.height <= 1080 }.maxByOrNull { it.width.toLong()*it.height } ?: map.getOutputSizes(SurfaceHolder::class.java).first()
            preview.holder.setFixedSize(previewSize.width,previewSize.height)
            reader = ImageReader.newInstance(size.width,size.height,ImageFormat.JPEG,2).apply {
                setOnImageAvailableListener({ source ->
                    val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                    try {
                        val buffer = image.planes[0].buffer
                        val limit = CredflareVerification.active.get()?.info?.maxFileSizeBytes ?: 0
                        require(buffer.remaining().toLong() in 1..limit) { "The photo is too large. Try again." }
                        val bytes = ByteArray(buffer.remaining()); buffer.get(bytes)
                        val path = CredflareVerification.active.get()?.cameraPath ?: error("The verification was closed")
                        File(path).outputStream().use { it.write(bytes) }
                        setResult(RESULT_OK); finish()
                    } catch(_: Exception) { fail("Could not save your selfie. Return and try again.") }
                    finally { image.close() }
                },handler)
            }
            manager.openCamera(id,object: CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    opening = false
                    if(!resumed || isFinishing || !preview.holder.surface.isValid) { camera.close(); return }
                    device = camera
                    try {
                        camera.createCaptureSession(listOf(preview.holder.surface,reader!!.surface),object: CameraCaptureSession.StateCallback() {
                            override fun onConfigured(session: CameraCaptureSession) {
                                if(device !== camera) { session.close(); return }
                                captureSession = session
                                try {
                                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(preview.holder.surface); set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO) }
                                    session.setRepeatingRequest(request.build(),null,handler); take.isEnabled = true
                                } catch(_: Exception) { fail("Could not start the camera. Return and try again.") }
                            }
                            override fun onConfigureFailed(session: CameraCaptureSession) { fail("Could not start the camera. Return and try again.") }
                        },handler)
                    } catch(_: Exception) { fail("Could not start the camera. Return and try again.") }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); fail("Camera disconnected. Return and try again.") }
                override fun onError(camera: CameraDevice,error: Int) { camera.close(); fail("Camera unavailable. Return and try again.") }
            },handler)
        } catch(_: Exception) { fail("Camera unavailable. Check access in Settings and try again.") }
    }
    private fun capture() {
        if(capturing) return
        val camera = device ?: return
        val session = captureSession ?: return
        val output = reader ?: return
        capturing = true; take.isEnabled = false
        try {
            val rotation = when(windowManager.defaultDisplay.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(output.surface); set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO)
                set(CaptureRequest.JPEG_ORIENTATION,(sensorOrientation+rotation)%360)
            }
            session.capture(request.build(),object: CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(session: CameraCaptureSession,request: CaptureRequest,failure: CaptureFailure) { capturing = false; take.isEnabled = true; status.text = "Could not take your selfie. Try again." }
            },handler)
        } catch(_: Exception) { capturing = false; take.isEnabled = true; status.text = "Could not take your selfie. Try again." }
    }
}
