package com.distbit.nebuia_plugin.activities.face.liveness

import com.distbit.nebuia_plugin.model.LivenessColors
import com.distbit.nebuia_plugin.model.PositionGuidanceResult
import com.distbit.nebuia_plugin.model.PositionInstruction
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs

/**
 * Analyzes MediaPipe FaceLandmarker 478-landmark output to determine
 * whether the face is properly positioned for capture.
 *
 * Direct port of admin/src/lib/components/kyc/utils/position_guidance.ts
 */
object PositionGuidance {

    // MediaPipe FaceMesh landmark indices
    private const val NOSE_TIP = 1
    private const val LEFT_EYE_OUTER = 33
    private const val RIGHT_EYE_OUTER = 263
    private const val FOREHEAD = 10

    // Tolerances (mobile values — Android is always mobile)
    private const val POSITION_TOLERANCE = 0.23f
    private const val FAR_THRESHOLD = 0.18f
    private const val CLOSE_THRESHOLD = 0.60f
    private const val YAW_RATIO_THRESHOLD = 0.7f
    private const val VERTICAL_CENTER = 0.50f

    // Spanish instruction messages (matching web)
    private val MESSAGES = mapOf(
        PositionInstruction.CENTERED to "¡Perfecto! Mantente así",
        PositionInstruction.MOVE_LEFT to "Gira un poco hacia tu izquierda",
        PositionInstruction.MOVE_RIGHT to "Gira un poco hacia tu derecha",
        PositionInstruction.MOVE_UP to "Levanta tu cabeza un poco",
        PositionInstruction.MOVE_DOWN to "Baja tu cabeza un poco",
        PositionInstruction.MOVE_CLOSER to "Acércate más a la cámara",
        PositionInstruction.MOVE_AWAY to "Aléjate un poco de la cámara",
        PositionInstruction.TOO_DARK to "Busca un lugar con más luz",
        PositionInstruction.TOO_BRIGHT to "Hay mucha luz, busca menos brillo",
        PositionInstruction.BLURRY to "Mantén tu dispositivo quieto",
        PositionInstruction.REMOVE_GLASSES to "Retira tus lentes por favor",
        PositionInstruction.REMOVE_HAT to "Retira tu gorra por favor",
        PositionInstruction.NO_FACE to "Posiciona tu rostro en el área de la cámara",
        PositionInstruction.MULTIPLE_FACES to "Solo debe haber un rostro"
    )

    fun analyzeFacePosition(
        landmarks: List<NormalizedLandmark>,
        faceCount: Int
    ): PositionGuidanceResult {
        if (faceCount == 0) {
            return result(PositionInstruction.NO_FACE)
        }
        if (faceCount > 1) {
            return result(PositionInstruction.MULTIPLE_FACES)
        }

        val nose = landmarks[NOSE_TIP]
        val leftEyeOuter = landmarks[LEFT_EYE_OUTER]
        val rightEyeOuter = landmarks[RIGHT_EYE_OUTER]
        val forehead = landmarks[FOREHEAD]

        val faceWidth = abs(rightEyeOuter.x() - leftEyeOuter.x())

        // 1. Hat detection: forehead pushed to very top
        if (forehead.y() < 0.1f) {
            return result(PositionInstruction.REMOVE_HAT)
        }

        // 2. Face distance (size)
        if (faceWidth < FAR_THRESHOLD) {
            return result(PositionInstruction.MOVE_CLOSER)
        }
        if (faceWidth > CLOSE_THRESHOLD) {
            return result(PositionInstruction.MOVE_AWAY)
        }

        // 3. Vertical position
        val deltaY = nose.y() - VERTICAL_CENTER
        if (abs(deltaY) > POSITION_TOLERANCE) {
            return if (deltaY > 0) result(PositionInstruction.MOVE_UP)
            else result(PositionInstruction.MOVE_DOWN)
        }

        // 4. Horizontal position
        val deltaX = nose.x() - 0.5f
        if (abs(deltaX) > POSITION_TOLERANCE) {
            return if (deltaX > 0) result(PositionInstruction.MOVE_LEFT)
            else result(PositionInstruction.MOVE_RIGHT)
        }

        // 5. Yaw detection: compare nose-to-eye distances
        val leftDist = abs(nose.x() - leftEyeOuter.x())
        val rightDist = abs(nose.x() - rightEyeOuter.x())
        val maxDist = maxOf(leftDist, rightDist)
        if (maxDist > 0f) {
            val yawRatio = minOf(leftDist, rightDist) / maxDist
            if (yawRatio < YAW_RATIO_THRESHOLD) {
                return if (rightDist < leftDist) {
                    PositionGuidanceResult(PositionInstruction.MOVE_RIGHT, "Mira de frente a la cámara", false)
                } else {
                    PositionGuidanceResult(PositionInstruction.MOVE_LEFT, "Mira de frente a la cámara", false)
                }
            }
        }

        return result(PositionInstruction.CENTERED, ready = true)
    }

    fun computeFaceWidth(landmarks: List<NormalizedLandmark>): Float {
        val leftEyeOuter = landmarks[LEFT_EYE_OUTER]
        val rightEyeOuter = landmarks[RIGHT_EYE_OUTER]
        return abs(rightEyeOuter.x() - leftEyeOuter.x())
    }

    fun getInstructionColor(instruction: PositionInstruction): Int {
        return when (instruction) {
            PositionInstruction.CENTERED -> LivenessColors.GREEN
            PositionInstruction.NO_FACE -> LivenessColors.GRAY
            PositionInstruction.TOO_DARK,
            PositionInstruction.TOO_BRIGHT,
            PositionInstruction.BLURRY,
            PositionInstruction.REMOVE_GLASSES,
            PositionInstruction.REMOVE_HAT,
            PositionInstruction.MULTIPLE_FACES -> LivenessColors.RED
            else -> LivenessColors.YELLOW
        }
    }

    fun getMessage(instruction: PositionInstruction): String {
        return MESSAGES[instruction] ?: ""
    }

    private fun result(instruction: PositionInstruction, ready: Boolean = false): PositionGuidanceResult {
        return PositionGuidanceResult(instruction, MESSAGES[instruction] ?: "", ready)
    }
}
