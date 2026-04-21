package com.distbit.nebuia_plugin.model

import android.graphics.Color

// ─── Position Guidance ───

enum class PositionInstruction {
    CENTERED, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN,
    MOVE_CLOSER, MOVE_AWAY, NO_FACE, MULTIPLE_FACES,
    REMOVE_HAT, REMOVE_GLASSES,
    TOO_DARK, TOO_BRIGHT, BLURRY
}

data class PositionGuidanceResult(
    val instruction: PositionInstruction,
    val message: String,
    val ready: Boolean
)

// ─── Camera Calibration ───

data class CameraCalibration(
    val focalLengthPx: Float,
    val fovDegrees: Float
)

// ─── Passive Liveness ───

data class PassiveLivenessMetadata(
    val blinkCount: Int,
    val blinkTimestamps: List<Long>,
    val avgBlinkDurationMs: Long,
    val mouthMovements: Int,
    val headVarianceX: Double,
    val headVarianceY: Double,
    val irisVarianceX: Double,
    val irisVarianceY: Double,
    val sessionDurationMs: Long,
    val frameCount: Int,
    val avgEAR: Double
)

// ─── API Results ───

data class LivenessResult(
    val score: Double,
    val isValidForKyc: Boolean,
    val accessories: Accessories?
)

data class Accessories(
    val hasGlasses: Boolean = false,
    val hasMask: Boolean = false,
    val hasCap: Boolean = false,
    val hasHat: Boolean = false,
    val rejectReason: String? = null
)

// ─── Colors ───

object LivenessColors {
    val GREEN = Color.parseColor("#22c55e")
    val YELLOW = Color.parseColor("#eab308")
    val RED = Color.parseColor("#ef4444")
    val GRAY = Color.parseColor("#9ca3af")
    val INDIGO = Color.parseColor("#6366f1")
}
