package com.example.handtrackingdemo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandOverlayView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var results: HandLandmarkerResult? = null

    private val landmarkPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        strokeWidth = 8f
    }

    private val connectionPaint = Paint().apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    // Connections between landmarks for drawing the hand skeleton
    private val connections = listOf(
        HandLandmark.WRIST to HandLandmark.THUMB_CMC,
        HandLandmark.THUMB_CMC to HandLandmark.THUMB_MCP,
        HandLandmark.THUMB_MCP to HandLandmark.THUMB_IP,
        HandLandmark.THUMB_IP to HandLandmark.THUMB_TIP,
        // Add more connections as needed to complete the hand skeleton
        HandLandmark.WRIST to HandLandmark.INDEX_FINGER_MCP,
        HandLandmark.INDEX_FINGER_MCP to HandLandmark.INDEX_FINGER_PIP,
        HandLandmark.INDEX_FINGER_PIP to HandLandmark.INDEX_FINGER_DIP,
        HandLandmark.INDEX_FINGER_DIP to HandLandmark.INDEX_FINGER_TIP
    )

    fun setResults(handLandmarkerResult: HandLandmarkerResult) {
        results = handLandmarkerResult
        invalidate() // Request redraw
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val results = this.results ?: return

        if (results.landmarks().isEmpty()) {
            return
        }

        // Draw each detected hand
        for (landmarks in results.landmarks()) {
            // Draw landmarks
            for (landmark in landmarks) {
                canvas.drawCircle(
                    landmark.x() * width,
                    landmark.y() * height,
                    10f,
                    landmarkPaint
                )
            }

            // Draw connections (hand skeleton)
            for ((start, end) in connections) {
                canvas.drawLine(
                    landmarks[start].x() * width,
                    landmarks[start].y() * height,
                    landmarks[end].x() * width,
                    landmarks[end].y() * height,
                    connectionPaint
                )
            }
        }
    }
}