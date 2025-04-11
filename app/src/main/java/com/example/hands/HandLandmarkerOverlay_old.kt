package com.example.hands

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

@Composable
fun HandLandmarkerOverlayOld(
    result: HandLandmarkerResult?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        result?.let { handLandmarkerResult ->
            for (landmarks in handLandmarkerResult.landmarks()) {
                // Draw landmarks
                for (landmark in landmarks) {
                    drawCircle(
                        color = Color.Red,
                        radius = 10f,
                        center = Offset(
                            x = landmark.x() * size.width,
                            y = landmark.y() * size.height
                        )
                    )
                }

                // Draw connections
                drawHandConnections(landmarks)
            }
        }
    }
}

private fun DrawScope.drawHandConnections(landmarks: List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) {
    // Define connections between landmarks
    val connections = listOf(
        HandLandmark.WRIST to HandLandmark.THUMB_CMC,
        HandLandmark.THUMB_CMC to HandLandmark.THUMB_MCP,
        HandLandmark.THUMB_MCP to HandLandmark.THUMB_IP,
        HandLandmark.THUMB_IP to HandLandmark.THUMB_TIP,
        // Add more connections as needed for the hand skeleton
        HandLandmark.WRIST to HandLandmark.INDEX_FINGER_MCP,
        HandLandmark.INDEX_FINGER_MCP to HandLandmark.INDEX_FINGER_PIP,
        HandLandmark.INDEX_FINGER_PIP to HandLandmark.INDEX_FINGER_DIP,
        HandLandmark.INDEX_FINGER_DIP to HandLandmark.INDEX_FINGER_TIP
    )

    for ((start, end) in connections) {
        drawLine(
            color = Color.Green,
            start = Offset(
                x = landmarks[start].x() * size.width,
                y = landmarks[start].y() * size.height
            ),
            end = Offset(
                x = landmarks[end].x() * size.width,
                y = landmarks[end].y() * size.height
            ),
            strokeWidth = 4f
        )
    }
}