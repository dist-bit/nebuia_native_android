package com.distbit.nebuia_plugin.activities.face.liveness

import com.distbit.nebuia_plugin.model.CameraCalibration
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.roundToInt
import kotlin.math.tan

/**
 * Real-world distance estimation using iris diameter and the pinhole camera model.
 * The human iris has a nearly constant diameter of ~11.7mm across adults.
 *
 * On Android, calibration uses Camera2 CameraCharacteristics (focal length + sensor size)
 * which is more accurate than the web's FOV table heuristics.
 *
 * Direct port of admin/src/lib/components/kyc/utils/iris_distance.ts
 */
object IrisDistance {

    // MediaPipe FaceLandmarker iris indices (478-landmark model)
    private const val LEFT_IRIS_RIGHT = 469
    private const val LEFT_IRIS_LEFT = 471
    private const val RIGHT_IRIS_RIGHT = 474
    private const val RIGHT_IRIS_LEFT = 476

    // Average human iris diameter in mm (biological constant)
    private const val IRIS_DIAMETER_MM = 11.7f

    // Default mobile front camera FOV (fallback)
    private const val FALLBACK_FOV_DEGREES = 78f

    /**
     * Calibrate using Android Camera2 metadata.
     * @param focalLengthMm from CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS[0]
     * @param sensorWidthMm from CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE.width
     * @param imageWidthPx preview resolution width in pixels
     */
    fun calibrate(
        focalLengthMm: Float,
        sensorWidthMm: Float,
        imageWidthPx: Int
    ): CameraCalibration {
        // FOV = 2 * atan(sensorWidth / (2 * focalLength))
        val fovRadians = 2.0 * atan((sensorWidthMm / (2.0 * focalLengthMm)).toDouble())
        val fovDegrees = (fovRadians * 180.0 / PI).toFloat().coerceIn(40f, 120f)
        val focalLengthPx = fovToFocalPx(imageWidthPx, fovDegrees)
        return CameraCalibration(focalLengthPx, fovDegrees)
    }

    /**
     * Fallback calibration when Camera2 metadata is unavailable.
     * Uses 78° horizontal FOV (typical mobile front camera).
     */
    fun calibrateFallback(imageWidthPx: Int): CameraCalibration {
        val focalLengthPx = fovToFocalPx(imageWidthPx, FALLBACK_FOV_DEGREES)
        return CameraCalibration(focalLengthPx, FALLBACK_FOV_DEGREES)
    }

    /**
     * Compute real-world distance from camera to face in centimeters.
     * Returns null if iris landmarks are unavailable or too small.
     */
    fun computeDistance(
        landmarks: List<NormalizedLandmark>,
        imageWidthPx: Int,
        focalLengthPx: Float
    ): Float? {
        if (landmarks.size < 478) return null

        val ll = landmarks[LEFT_IRIS_LEFT]
        val lr = landmarks[LEFT_IRIS_RIGHT]
        val rl = landmarks[RIGHT_IRIS_LEFT]
        val rr = landmarks[RIGHT_IRIS_RIGHT]

        // Iris width in pixels (average both irises for robustness)
        val leftIrisPx = abs(lr.x() - ll.x()) * imageWidthPx
        val rightIrisPx = abs(rr.x() - rl.x()) * imageWidthPx
        val irisPx = (leftIrisPx + rightIrisPx) / 2f

        if (irisPx < 1f) return null

        // Pinhole model: Z = (f * realSize) / pixelSize
        val distanceCm = (focalLengthPx * IRIS_DIAMETER_MM) / irisPx / 10f

        return (distanceCm * 100f).roundToInt() / 100f
    }

    private fun fovToFocalPx(imageWidthPx: Int, fovDegrees: Float): Float {
        val fovRadians = (fovDegrees * PI / 180.0)
        return (imageWidthPx / 2f) / tan(fovRadians / 2.0).toFloat()
    }
}
