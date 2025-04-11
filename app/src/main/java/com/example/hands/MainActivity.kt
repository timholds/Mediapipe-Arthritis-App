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
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private var handLandmarker: HandLandmarker? = null
    private val handLandmarkerResult = mutableStateOf<HandLandmarkerResult?>(null)


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
                .setResultListener { result: HandLandmarkerResult, _ ->
                    // Process hand landmarks here
                    Log.d(TAG, "Hand landmarks detected: ${result.landmarks().size}")

                    // Update the state with the new result
                    runOnUiThread {
                        handLandmarkerResult.value = result
                    }
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
                handLandmarkerResultState = handLandmarkerResult,
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
    handLandmarkerResultState: State<HandLandmarkerResult?>,
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
            result = handLandmarkerResultState.value,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun HandLandmarkerOverlay(
    result: HandLandmarkerResult?,
    modifier: Modifier = Modifier
) {
    if (result == null) return

    Canvas(modifier = modifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        // For each detected hand
        result.landmarks().forEach { landmarks ->
            // For each landmark in the hand
            landmarks.forEach { landmark ->
                // Scale the landmark coordinates to the canvas size
                val x = landmark.x() * canvasWidth
                val y = landmark.y() * canvasHeight

                // Draw a small circle at each landmark
                drawCircle(
                    color = Color.Green,
                    radius = 8f,
                    center = Offset(x, y)
                )
            }

            // Optional: Draw connections between landmarks to form hand skeleton
            // Define connections based on MediaPipe hand landmark indices
            val connections = listOf(
                0 to 1, 1 to 2, 2 to 3, 3 to 4,  // thumb
                0 to 5, 5 to 6, 6 to 7, 7 to 8,  // index finger
                0 to 9, 9 to 10, 10 to 11, 11 to 12,  // middle finger
                0 to 13, 13 to 14, 14 to 15, 15 to 16,  // ring finger
                0 to 17, 17 to 18, 18 to 19, 19 to 20,  // pinky
                // Optional: Add wrist connections
                0 to 5, 5 to 9, 9 to 13, 13 to 17  // palm
            )

            connections.forEach { (start, end) ->
                if (start < landmarks.size && end < landmarks.size) {
                    val startX = landmarks[start].x() * canvasWidth
                    val startY = landmarks[start].y() * canvasHeight
                    val endX = landmarks[end].x() * canvasWidth
                    val endY = landmarks[end].y() * canvasHeight

                    drawLine(
                        color = Color.Yellow,
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 3f
                    )
                }
            }
        }
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