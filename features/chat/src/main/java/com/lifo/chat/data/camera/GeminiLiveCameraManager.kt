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
        Log.d(TAG, "📸 startCameraPreview() called")
        Log.d(TAG, "📸 Current isActive: $isActive")
        Log.d(TAG, "📸 SurfaceTexture: $surfaceTexture")
        
        if (isActive) {
            Log.d(TAG, "📸 Camera already active - skipping")
            return
        }
        
        if (!hasCameraPermission()) {
            Log.w(TAG, "📸 Camera permission not granted - cannot start")
            return
        }
        
        Log.d(TAG, "📸 Setting surfaceTexture and calling openCamera()")
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
    
    @SuppressLint("MissingPermission") // Permission checked in hasCameraPermission() before calling
    private fun openCamera() {
        Log.d(TAG, "📸 openCamera() called")
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        try {
            Log.d(TAG, "📸 Getting camera list...")
            val cameraList = cameraManager.cameraIdList
            Log.d(TAG, "📸 Available cameras: ${cameraList.contentToString()}")
            
            // Get back camera ID
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                Log.d(TAG, "📸 Camera $id facing: $facing")
                facing == CameraCharacteristics.LENS_FACING_BACK
            } ?: cameraManager.cameraIdList[0]
            
            Log.d(TAG, "📸 Selected camera ID: $cameraId")
            
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            if (map == null) {
                Log.e(TAG, "📸 SCALER_STREAM_CONFIGURATION_MAP is null - returning")
                return
            }
            
            val outputSizes = map.getOutputSizes(SurfaceTexture::class.java)
            Log.d(TAG, "📸 Available output sizes: ${outputSizes.contentToString()}")
            previewSize = outputSizes[0]
            Log.d(TAG, "📸 Selected preview size: ${previewSize.width}x${previewSize.height}")
            
            // Setup ImageReader for capturing frames
            Log.d(TAG, "📸 Creating ImageReader...")
            imageReader = ImageReader.newInstance(
                MAX_IMAGE_DIMENSION, MAX_IMAGE_DIMENSION,
                ImageFormat.JPEG, 2
            ).apply {
                setOnImageAvailableListener(imageAvailableListener, cameraHandler)
            }
            Log.d(TAG, "📸 ImageReader created successfully")
            
            Log.d(TAG, "📸 Opening camera device...")
            cameraManager.openCamera(cameraId, cameraStateCallback, cameraHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "📸 Error opening camera", e)
        } catch (e: SecurityException) {
            Log.e(TAG, "📸 Security exception - missing camera permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "📸 Unexpected error opening camera", e)
        }
    }
    
    private val cameraStateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "📸✅ Camera device opened successfully!")
            cameraDevice = camera
            Log.d(TAG, "📸 Calling createCameraPreviewSession()...")
            createCameraPreviewSession()
        }
        
        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "📸❌ Camera disconnected")
            cameraDevice?.close()
            cameraDevice = null
            isActive = false
            _isCameraActive.value = false
        }
        
        override fun onError(camera: CameraDevice, error: Int) {
            Log.e(TAG, "📸❌ Camera error: $error")
            val errorMsg = when (error) {
                CameraDevice.StateCallback.ERROR_CAMERA_IN_USE -> "Camera in use"
                CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                CameraDevice.StateCallback.ERROR_CAMERA_DISABLED -> "Camera disabled"
                CameraDevice.StateCallback.ERROR_CAMERA_DEVICE -> "Camera device error"
                CameraDevice.StateCallback.ERROR_CAMERA_SERVICE -> "Camera service error"
                else -> "Unknown error: $error"
            }
            Log.e(TAG, "📸❌ Error details: $errorMsg")
            cameraDevice?.close()
            cameraDevice = null
            isActive = false
            _isCameraActive.value = false
        }
    }
    
    private fun createCameraPreviewSession() {
        Log.d(TAG, "📸 createCameraPreviewSession() called")
        try {
            Log.d(TAG, "📸 SurfaceTexture: $surfaceTexture")
            if (surfaceTexture == null) {
                Log.e(TAG, "📸❌ SurfaceTexture is null - cannot create session")
                return
            }
            
            Log.d(TAG, "📸 Setting buffer size: ${previewSize.width}x${previewSize.height}")
            surfaceTexture?.setDefaultBufferSize(previewSize.width, previewSize.height)
            
            val previewSurface = Surface(surfaceTexture)
            Log.d(TAG, "📸 Preview surface created: $previewSurface")
            
            Log.d(TAG, "📸 Creating capture request...")
            captureRequestBuilder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)?.apply {
                Log.d(TAG, "📸 Adding preview surface target")
                addTarget(previewSurface)
                imageReader?.surface?.let { 
                    Log.d(TAG, "📸 Adding image reader surface target")
                    addTarget(it) 
                }
            }
            
            if (captureRequestBuilder == null) {
                Log.e(TAG, "📸❌ Failed to create capture request builder")
                return
            }
            
            val surfaces = listOfNotNull(previewSurface, imageReader?.surface)
            Log.d(TAG, "📸 Creating capture session with ${surfaces.size} surfaces...")
            cameraDevice?.createCaptureSession(surfaces, cameraCaptureSessionCallback, cameraHandler)
            
        } catch (e: CameraAccessException) {
            Log.e(TAG, "📸❌ Error creating preview session", e)
        } catch (e: Exception) {
            Log.e(TAG, "📸❌ Unexpected error creating preview session", e)
        }
    }
    
    private val cameraCaptureSessionCallback = object : CameraCaptureSession.StateCallback() {
        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "📸✅ Camera session configured successfully!")
            // Verifica che la camera sia ancora disponibile
            if (cameraDevice == null) {
                Log.w(TAG, "📸⚠️ Camera was disconnected during configuration, skipping preview update")
                session.close()
                return
            }
            cameraCaptureSession = session
            Log.d(TAG, "📸 Calling updatePreview()...")
            updatePreview()
            isActive = true
            _isCameraActive.value = true
            Log.d(TAG, "📸✅ Camera is now ACTIVE and preview should be visible!")
        }
        
        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.e(TAG, "📸❌ Camera session configuration FAILED")
            isActive = false
            _isCameraActive.value = false
        }
    }
    
    private fun updatePreview() {
        Log.d(TAG, "📸 updatePreview() called")
        if (cameraDevice == null) {
            Log.e(TAG, "📸❌ cameraDevice is null - cannot update preview")
            return
        }
        
        try {
            Log.d(TAG, "📸 Setting capture request control mode...")
            captureRequestBuilder?.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
            
            val request = captureRequestBuilder?.build()
            if (request == null) {
                Log.e(TAG, "📸❌ Failed to build capture request")
                return
            }
            
            Log.d(TAG, "📸 Starting repeating request...")
            cameraCaptureSession?.setRepeatingRequest(
                request,
                null, cameraHandler
            )
            Log.d(TAG, "📸✅ Preview repeating request started successfully!")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "📸❌ Error starting preview repeat request", e)
        } catch (e: Exception) {
            Log.e(TAG, "📸❌ Unexpected error updating preview", e)
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