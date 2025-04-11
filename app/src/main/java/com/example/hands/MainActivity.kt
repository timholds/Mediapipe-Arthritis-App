package com.example.hands

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var handLandmarker: HandLandmarker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permissions
        if (allPermissionsGranted()) {
            setupHandLandmarker()
            startCompose()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun setupHandLandmarker() {
        try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")

            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptionsBuilder.build())
                .setNumHands(2)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { handLandmarkerResult: HandLandmarkerResult, _ ->
                    // Process hand landmarks here
                    Log.d(TAG, "Hand landmarks detected: ${handLandmarkerResult.landmarks().size}")
                }
                .setErrorListener { error: RuntimeException ->
                    Log.e(TAG, "Hand landmarker error: $error")
                }

            handLandmarker = HandLandmarker.createFromOptions(this, optionsBuilder.build())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to setup hand landmarker: $e")
            Toast.makeText(
                this,
                "Failed to setup hand landmarker: $e",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun startCompose() {
        setContent {
            CameraPreview(
                handLandmarker = handLandmarker,
                cameraExecutor = cameraExecutor
            )
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                setupHandLandmarker()
                startCompose()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handLandmarker?.close()
    }

    companion object {
        private const val TAG = "HandTrackingDemo"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}

@Composable
fun CameraPreview(
    handLandmarker: HandLandmarker?,
    cameraExecutor: ExecutorService
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val (handLandmarkerResult, setHandLandmarkerResult) = remember { mutableStateOf<HandLandmarkerResult?>(null) }

    val previewView = remember { PreviewView(context) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    DisposableEffect(key1 = handLandmarker) {
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder()
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also {
                it.setAnalyzer(cameraExecutor) { imageProxy: ImageProxy ->
                    processImage(imageProxy, handLandmarker)
                }
            }

        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            // Unbind existing use cases
            cameraProvider.unbindAll()

            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
        } catch (exc: Exception) {
            Log.e("CameraPreview", "Use case binding failed", exc)
        }

        onDispose {
            cameraProvider.unbindAll()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        HandLandmarkerOverlay(
            result = handLandmarkerResult,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun processImage(imageProxy: ImageProxy, handLandmarker: HandLandmarker?) {
    val mediaImage = imageProxy.image ?: return

    try {
        val imageProcessingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(imageProxy.imageInfo.rotationDegrees)
            .build()

        // Convert mediaImage to bitmap
        val bitmap = mediaImageToBitmap(mediaImage)
        // Create MPImage from bitmap
        val mpImage = BitmapImageBuilder(bitmap).build()

        handLandmarker?.detectAsync(
            mpImage,
            imageProcessingOptions,
            System.currentTimeMillis() * 1000 // Convert to microseconds
        )
    } catch (e: Exception) {
        Log.e("HandTracking", "Error processing image: ${e.message}")
    } finally {
        imageProxy.close()
    }
}

// Helper function to convert MediaImage to Bitmap
private fun mediaImageToBitmap(mediaImage: Image): Bitmap {
    val planes = mediaImage.planes
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()

    val nv21 = ByteArray(ySize + uSize + vSize)

    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, out)
    val imageBytes = out.toByteArray()

    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}