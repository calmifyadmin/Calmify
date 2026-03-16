package com.lifo.chat.data.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
class GeminiLiveCameraManager constructor(
    private val context: Context
) {

    // Camera configuration constants matching reference
    private val MAX_IMAGE_DIMENSION = 1024
    private val JPEG_QUALITY = 70
    private val IMAGE_SEND_INTERVAL = 3000L // 3 seconds

    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var captureRequestBuilder: CaptureRequest.Builder? = null
    private var imageReader: ImageReader? = null
    private var surfaceTexture: SurfaceTexture? = null

    private val cameraThread = HandlerThread("CameraThread").apply { start() }
    private val cameraHandler = Handler(cameraThread.looper)

    private lateinit var cameraId: String
    private lateinit var previewSize: Size

    private var lastImageSendTime: Long = 0
    private var isActive = false

    private val _isCameraActive = MutableStateFlow(false)
    val isCameraActive: StateFlow<Boolean> = _isCameraActive

    var onImageCaptured: ((String) -> Unit)? = null

    fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun startCameraPreview(surfaceTexture: SurfaceTexture) {
        println("[GeminiLiveCamera] startCameraPreview() called")
        println("[GeminiLiveCamera] Current isActive: $isActive")
        println("[GeminiLiveCamera] SurfaceTexture: $surfaceTexture")

        if (isActive) {
            println("[GeminiLiveCamera] Camera already active - skipping")
            return
        }

        if (!hasCameraPermission()) {
            println("[GeminiLiveCamera] WARNING: Camera permission not granted - cannot start")
            return
        }

        println("[GeminiLiveCamera] Setting surfaceTexture and calling openCamera()")
        this.surfaceTexture = surfaceTexture
        openCamera()
    }

    fun stopCameraPreview() {
        if (!isActive) return

        println("[GeminiLiveCamera] Stopping camera preview")
        closeCamera()
        isActive = false
        _isCameraActive.value = false
    }

    @SuppressLint("MissingPermission") // Permission checked in hasCameraPermission() before calling
    private fun openCamera() {
        println("[GeminiLiveCamera] openCamera() called")
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        try {
            println("[GeminiLiveCamera] Getting camera list...")
            val cameraList = cameraManager.cameraIdList
            println("[GeminiLiveCamera] Available cameras: ${cameraList.contentToString()}")

            // Get back camera ID
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                println("[GeminiLiveCamera] Camera $id facing: $facing")
                facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList[0]

            println("[GeminiLiveCamera] Selected camera ID: $cameraId")

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map == null) {
                println("[GeminiLiveCamera] ERROR: SCALER_STREAM_CONFIGURATION_MAP is null - returning")
                return
            }

            // Smart size selection with fallback strategy
            val outputSizes = map.getOutputSizes(SurfaceTexture::class.java)
            println("[GeminiLiveCamera] Available output sizes: ${outputSizes.contentToString()}")

            previewSize = chooseOptimalSize(outputSizes, 1280, 960)
            println("[GeminiLiveCamera] Selected preview size: ${previewSize.width}x${previewSize.height}")

            // Smart ImageReader setup with compatible formats
            println("[GeminiLiveCamera] Creating ImageReader with compatibility check...")
            val imageFormat = getCompatibleImageFormat(map)
            val imageSize = chooseOptimalSize(map.getOutputSizes(imageFormat), MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION)

            imageReader = ImageReader.newInstance(
                imageSize.width, imageSize.height,
                imageFormat, 2
            ).apply {
                setOnImageAvailableListener(imageAvailableListener, cameraHandler)
            }
            println("[GeminiLiveCamera] ImageReader created: ${imageSize.width}x${imageSize.height}, format=$imageFormat")

            println("[GeminiLiveCamera] Opening camera device...")
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)

        } catch (e: CameraAccessException) {
            println("[GeminiLiveCamera] ERROR: Error opening camera: ${e.message}")
        } catch (e: SecurityException) {
            println("[GeminiLiveCamera] ERROR: Security exception - missing camera permission: ${e.message}")
        } catch (e: Exception) {
            println("[GeminiLiveCamera] ERROR: Unexpected error opening camera: ${e.message}")
        }
    }

    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            println("[GeminiLiveCamera] Camera device opened successfully!")
            cameraDevice = camera
            println("[GeminiLiveCamera] Calling createCameraPreviewSession()...")
            createCameraPreviewSession()
        }

        override fun onDisconnected(camera: CameraDevice) {
            println("[GeminiLiveCamera] Camera disconnected")
            cameraDevice?.close()
            cameraDevice = null
            isActive = false
            _isCameraActive.value = false
        }

        override fun onError(camera: CameraDevice, error: Int) {
            println("[GeminiLiveCamera] ERROR: Camera error: $error")
            val errorMsg = when (error) {
                CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "Camera in use"
                CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "Camera disabled"
                CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "Camera device error"
                CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "Camera service error"
                else -> "Unknown error: $error"
            }
            println("[GeminiLiveCamera] ERROR: Error details: $errorMsg")
            cameraDevice?.close()
            cameraDevice = null
            isActive = false
            _isCameraActive.value = false
        }
    }

    private fun createCameraPreviewSession() {
        println("[GeminiLiveCamera] createCameraPreviewSession() called with robust fallback strategy")
        createCameraPreviewSessionWithFallback()
    }

    /**
     * Enhanced camera session creation with intelligent fallback
     */
    private fun createCameraPreviewSessionWithFallback() {
        println("[GeminiLiveCamera] createCameraPreviewSessionWithFallback() called")
        try {
            if (surfaceTexture == null) {
                println("[GeminiLiveCamera] ERROR: SurfaceTexture is null - cannot create session")
                return
            }

            // Try original configuration first
            if (tryCreateSession(previewSize)) {
                println("[GeminiLiveCamera] Session created with optimal configuration")
                return
            }

            // Fallback to smaller sizes
            val fallbackSizes = listOf(
                Size(1280, 720),   // HD
                Size(1024, 768),   // XGA
                Size(800, 600),    // SVGA
                Size(640, 480),    // VGA
                Size(320, 240)     // QVGA - last resort
            )

            for (fallbackSize in fallbackSizes) {
                println("[GeminiLiveCamera] Trying fallback size: ${fallbackSize.width}x${fallbackSize.height}")
                if (tryCreateSession(fallbackSize)) {
                    previewSize = fallbackSize
                    println("[GeminiLiveCamera] Session created with fallback: ${fallbackSize.width}x${fallbackSize.height}")
                    return
                }
            }

            println("[GeminiLiveCamera] ERROR: All fallback attempts failed")

        } catch (e: Exception) {
            println("[GeminiLiveCamera] ERROR: Exception in createCameraPreviewSessionWithFallback: ${e.message}")
        }
    }

    /**
     * Try to create camera session with specific size
     */
    private fun tryCreateSession(size: Size): Boolean {
        return try {
            println("[GeminiLiveCamera] Attempting session with ${size.width}x${size.height}")
            surfaceTexture?.setDefaultBufferSize(size.width, size.height)
            val previewSurface = Surface(surfaceTexture)

            captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(previewSurface)
                imageReader?.surface?.let { addTarget(it) }
            }

            if (captureRequestBuilder == null) {
                println("[GeminiLiveCamera] ERROR: Failed to create capture request builder for ${size.width}x${size.height}")
                return false
            }

            val surfaces = listOfNotNull(previewSurface, imageReader?.surface)
            println("[GeminiLiveCamera] Creating session with ${surfaces.size} surfaces (${size.width}x${size.height})")

            cameraDevice?.createCaptureSession(surfaces, cameraCaptureSessionCallback, cameraHandler)
            true

        } catch (e: Exception) {
            println("[GeminiLiveCamera] WARNING: Failed to create session with ${size.width}x${size.height}: ${e.message}")
            false
        }
    }

    private val cameraCaptureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            println("[GeminiLiveCamera] Camera session configured successfully!")
            // Verifica che la camera sia ancora disponibile
            if (cameraDevice == null) {
                println("[GeminiLiveCamera] WARNING: Camera was disconnected during configuration, skipping preview update")
                session.close()
                return
            }
            cameraCaptureSession = session
            println("[GeminiLiveCamera] Calling updatePreview()...")
            updatePreview()
            isActive = true
            _isCameraActive.value = true
            println("[GeminiLiveCamera] Camera is now ACTIVE and preview should be visible!")
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            println("[GeminiLiveCamera] ERROR: Camera session configuration FAILED")
            isActive = false
            _isCameraActive.value = false
        }
    }

    private fun updatePreview() {
        println("[GeminiLiveCamera] updatePreview() called")
        if (cameraDevice == null) {
            println("[GeminiLiveCamera] ERROR: cameraDevice is null - cannot update preview")
            return
        }

        try {
            println("[GeminiLiveCamera] Setting capture request control mode...")
            captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)

            val request = captureRequestBuilder?.build()
            if (request == null) {
                println("[GeminiLiveCamera] ERROR: Failed to build capture request")
                return
            }

            println("[GeminiLiveCamera] Starting repeating request...")
            cameraCaptureSession?.setRepeatingRequest(
                request,
                null, cameraHandler
            )
            println("[GeminiLiveCamera] Preview repeating request started successfully!")
        } catch (e: CameraAccessException) {
            println("[GeminiLiveCamera] ERROR: Error starting preview repeat request: ${e.message}")
        } catch (e: Exception) {
            println("[GeminiLiveCamera] ERROR: Unexpected error updating preview: ${e.message}")
        }
    }

    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastImageSendTime >= IMAGE_SEND_INTERVAL) {
            val image = reader.acquireLatestImage()
            if (image != null) {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                image.close()

                // Process image in background
                CoroutineScope(Dispatchers.IO).launch {
                    processAndSendImage(bytes)
                }

                lastImageSendTime = currentTime
                println("[GeminiLiveCamera] Image processed and sent based on time interval")
            }
        } else {
            // Just discard the image to avoid memory leaks
            reader.acquireLatestImage()?.close()
        }
    }

    private suspend fun processAndSendImage(imageBytes: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                // Step 1: Resize if necessary
                val scaledBitmap = scaleBitmap(bitmap, MAX_IMAGE_DIMENSION)

                // Step 2: Compress with reduced quality
                val byteArrayOutputStream = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, byteArrayOutputStream)

                // Step 3: Create Base64 string
                val b64Image = Base64.encodeToString(
                    byteArrayOutputStream.toByteArray(),
                    Base64.NO_WRAP
                )

                // Step 4: Notify listener
                onImageCaptured?.invoke(b64Image)

                // Clean up
                scaledBitmap.recycle()
                byteArrayOutputStream.close()

                println("[GeminiLiveCamera] Image captured and encoded: ${b64Image.take(50)}...")

            } catch (e: Exception) {
                println("[GeminiLiveCamera] ERROR: Error processing image: ${e.message}")
            }
        }
    }

    private fun scaleBitmap(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            val ratio = width.toFloat() / maxDimension
            newWidth = maxDimension
            newHeight = (height / ratio).toInt()
        } else {
            val ratio = height.toFloat() / maxDimension
            newHeight = maxDimension
            newWidth = (width / ratio).toInt()
        }

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

            println("[GeminiLiveCamera] Camera closed")
        } catch (e: Exception) {
            println("[GeminiLiveCamera] ERROR: Error closing camera: ${e.message}")
        }
    }

    /**
     * Choose optimal camera resolution with fallback strategy
     */
    private fun chooseOptimalSize(choices: Array<Size>, targetWidth: Int, targetHeight: Int): Size {
        println("[GeminiLiveCamera] Choosing optimal size from ${choices.size} options")

        // Find exact match first
        val exactMatch = choices.find { it.width == targetWidth && it.height == targetHeight }
        if (exactMatch != null) {
            println("[GeminiLiveCamera] Found exact match: ${exactMatch.width}x${exactMatch.height}")
            return exactMatch
        }

        // Find closest aspect ratio with reasonable size
        val targetAspectRatio = targetWidth.toDouble() / targetHeight
        var bestSize = choices[0]
        var bestDifference = Double.MAX_VALUE

        for (size in choices) {
            val aspectRatio = size.width.toDouble() / size.height
            val aspectDifference = kotlin.math.abs(aspectRatio - targetAspectRatio)
            val sizeDifference = kotlin.math.abs(size.width - targetWidth) + kotlin.math.abs(size.height - targetHeight)
            val totalDifference = aspectDifference * 1000 + sizeDifference

            if (totalDifference < bestDifference && size.width <= targetWidth * 2 && size.height <= targetHeight * 2) {
                bestDifference = totalDifference
                bestSize = size
            }
        }

        println("[GeminiLiveCamera] Selected optimal size: ${bestSize.width}x${bestSize.height}")
        return bestSize
    }

    /**
     * Get compatible image format with fallback
     */
    private fun getCompatibleImageFormat(map: StreamConfigurationMap): Int {
        println("[GeminiLiveCamera] Finding compatible image format...")

        // Priority order: JPEG (most compatible) -> YUV_420_888 -> others
        val preferredFormats = listOf(
            ImageFormat.JPEG,
            ImageFormat.YUV_420_888,
            ImageFormat.NV21,
            ImageFormat.YV12
        )

        for (format in preferredFormats) {
            val sizes = map.getOutputSizes(format)
            if (sizes != null && sizes.isNotEmpty()) {
                println("[GeminiLiveCamera] Selected format: $format (${sizes.size} sizes available)")
                return format
            }
        }

        // Ultimate fallback
        println("[GeminiLiveCamera] WARNING: No preferred format found, using JPEG")
        return ImageFormat.JPEG
    }

    fun release() {
        stopCameraPreview()
        cameraThread.quitSafely()
    }
}