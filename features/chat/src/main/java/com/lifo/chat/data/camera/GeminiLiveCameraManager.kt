package com.lifo.chat.data.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLiveCameraManager @Inject constructor(
    private val context: Context
) {
    
    private val TAG = "GeminiLiveCamera"
    
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
        if (isActive) {
            Log.d(TAG, "Camera already active")
            return
        }
        
        if (!hasCameraPermission()) {
            Log.w(TAG, "Camera permission not granted")
            return
        }
        
        this.surfaceTexture = surfaceTexture
        openCamera()
    }
    
    fun stopCameraPreview() {
        if (!isActive) return
        
        Log.d(TAG, "Stopping camera preview")
        closeCamera()
        isActive = false
        _isCameraActive.value = false
    }
    
    private fun openCamera() {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            // Get back camera ID
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList[0]
            
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return
            previewSize = map.getOutputSizes(SurfaceTexture::class.java)[0]
            
            // Setup ImageReader for capturing frames
            imageReader = ImageReader.newInstance(
                MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION,
                ImageFormat.JPEG, 2
            ).apply {
                setOnImageAvailableListener(imageAvailableListener, cameraHandler)
            }
            
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error opening camera", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception - missing camera permission", e)
        }
    }
    
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera opened")
            cameraDevice = camera
            createCameraPreviewSession()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera disconnected")
            cameraDevice?.close()
            cameraDevice = null
            isActive = false
            _isCameraActive.value = false
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "Camera error: $error")
            cameraDevice?.close()
            cameraDevice = null
            isActive = false
            _isCameraActive.value = false
        }
    }
    
    private fun createCameraPreviewSession() {
        try {
            surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            val previewSurface = Surface(surfaceTexture)
            
            captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                addTarget(previewSurface)
                imageReader?.surface?.let { addTarget(it) }
            }
            
            val surfaces = listOfNotNull(previewSurface, imageReader?.surface)
            cameraDevice?.createCaptureSession(surfaces, cameraCaptureSessionCallback, cameraHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error creating preview session", e)
        }
    }
    
    private val cameraCaptureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "Camera session configured")
            cameraCaptureSession = session
            updatePreview()
            isActive = true
            _isCameraActive.value = true
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "Camera session configuration failed")
            isActive = false
            _isCameraActive.value = false
        }
    }
    
    private fun updatePreview() {
        if (cameraDevice == null) return
        
        try {
            captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            
            cameraCaptureSession?.setRepeatingRequest(
                captureRequestBuilder?.build()!!,
                null, cameraHandler
            )
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error starting preview repeat request", e)
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
                Log.d(TAG, "Image processed and sent based on time interval")
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
                
                Log.d(TAG, "Image captured and encoded: ${b64Image.take(50)}...")
                
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
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
            
            Log.d(TAG, "Camera closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }
    
    fun release() {
        stopCameraPreview()
        cameraThread.quitSafely()
    }
}