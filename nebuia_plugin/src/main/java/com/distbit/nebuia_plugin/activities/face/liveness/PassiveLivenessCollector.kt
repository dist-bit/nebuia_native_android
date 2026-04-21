package com.distbit.nebuia_plugin.activities.face.liveness

import android.os.SystemClock
import com.distbit.nebuia_plugin.model.PassiveLivenessMetadata
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.sqrt

/**
 * Collects passive liveness signals per frame: blink detection (EAR),
 * mouth movement (MAR), head micro-movements, and iris variance.
 *
 * Direct port of admin/src/lib/components/kyc/utils/passive_liveness.ts
 */
class PassiveLivenessCollector {

    // ─── Eye Aspect Ratio (EAR) landmarks ───
    // Right eye
    private companion object {
        const val R_P1 = 33;  const val R_P2 = 160; const val R_P3 = 158
        const val R_P4 = 133; const val R_P5 = 153; const val R_P6 = 144
        // Left eye
        const val L_P1 = 362; const val L_P2 = 385; const val L_P3 = 387
        const val L_P4 = 263; const val L_P5 = 373; const val L_P6 = 380
        // Mouth (MAR)
        const val UPPER_LIP = 13;  const val LOWER_LIP = 14
        const val MOUTH_LEFT = 61; const val MOUTH_RIGHT = 291
        // Other
        const val NOSE_TIP = 1
        const val LEFT_IRIS_CENTER = 468
        const val RIGHT_IRIS_CENTER = 473
        // Thresholds
        const val EAR_BLINK_THRESHOLD = 0.21f
        const val EAR_OPEN_THRESHOLD = 0.25f
        const val MAR_MOVEMENT_THRESHOLD = 0.35f
        const val MAR_CLOSED_THRESHOLD = 0.20f
        const val HISTORY_MAX = 120
    }

    // State
    private var startTime = 0L
    private var frameCount = 0
    private var blinkCount = 0
    private val blinkTimestamps = mutableListOf<Long>()
    private var eyesClosed = false
    private var blinkStartMs = 0L
    private var totalBlinkDurationMs = 0L
    private var mouthMovements = 0
    private var mouthOpen = false
    private val noseXHistory = ArrayDeque<Float>(HISTORY_MAX + 1)
    private val noseYHistory = ArrayDeque<Float>(HISTORY_MAX + 1)
    private val irisXHistory = ArrayDeque<Float>(HISTORY_MAX + 1)
    private val irisYHistory = ArrayDeque<Float>(HISTORY_MAX + 1)
    private var earSum = 0.0
    private var earCount = 0

    fun pushFrame(landmarks: List<NormalizedLandmark>) {
        if (startTime == 0L) {
            startTime = SystemClock.elapsedRealtime()
        }
        frameCount++
        val now = SystemClock.elapsedRealtime()

        // ─── EAR (blink detection) ───
        val rightEAR = computeEAR(landmarks, R_P1, R_P2, R_P3, R_P4, R_P5, R_P6)
        val leftEAR = computeEAR(landmarks, L_P1, L_P2, L_P3, L_P4, L_P5, L_P6)
        val avgEAR = (rightEAR + leftEAR) / 2f

        earSum += avgEAR
        earCount++

        if (avgEAR < EAR_BLINK_THRESHOLD && !eyesClosed) {
            eyesClosed = true
            blinkStartMs = now
        } else if (avgEAR > EAR_OPEN_THRESHOLD && eyesClosed) {
            eyesClosed = false
            blinkCount++
            blinkTimestamps.add(now - startTime)
            totalBlinkDurationMs += (now - blinkStartMs)
        }

        // ─── MAR (mouth movement) ───
        val mar = computeMAR(landmarks)
        if (mar > MAR_MOVEMENT_THRESHOLD && !mouthOpen) {
            mouthOpen = true
            mouthMovements++
        } else if (mar < MAR_CLOSED_THRESHOLD && mouthOpen) {
            mouthOpen = false
        }

        // ─── Head micro-movements (nose position history) ───
        val nose = landmarks[NOSE_TIP]
        addToHistory(noseXHistory, nose.x())
        addToHistory(noseYHistory, nose.y())

        // ─── Iris variance (if 478+ landmarks) ───
        if (landmarks.size > RIGHT_IRIS_CENTER) {
            val leftIris = landmarks[LEFT_IRIS_CENTER]
            val rightIris = landmarks[RIGHT_IRIS_CENTER]
            val irisX = (leftIris.x() + rightIris.x()) / 2f
            val irisY = (leftIris.y() + rightIris.y()) / 2f
            addToHistory(irisXHistory, irisX)
            addToHistory(irisYHistory, irisY)
        }
    }

    fun getMetadata(): PassiveLivenessMetadata {
        val now = SystemClock.elapsedRealtime()
        return PassiveLivenessMetadata(
            blinkCount = blinkCount,
            blinkTimestamps = blinkTimestamps.toList(),
            avgBlinkDurationMs = if (blinkCount > 0) totalBlinkDurationMs / blinkCount else 0L,
            mouthMovements = mouthMovements,
            headVarianceX = variance(noseXHistory),
            headVarianceY = variance(noseYHistory),
            irisVarianceX = variance(irisXHistory),
            irisVarianceY = variance(irisYHistory),
            sessionDurationMs = if (startTime > 0) now - startTime else 0L,
            frameCount = frameCount,
            avgEAR = if (earCount > 0) earSum / earCount else 0.0
        )
    }

    fun reset() {
        startTime = 0L
        frameCount = 0
        blinkCount = 0
        blinkTimestamps.clear()
        eyesClosed = false
        blinkStartMs = 0L
        totalBlinkDurationMs = 0L
        mouthMovements = 0
        mouthOpen = false
        noseXHistory.clear()
        noseYHistory.clear()
        irisXHistory.clear()
        irisYHistory.clear()
        earSum = 0.0
        earCount = 0
    }

    // ─── Private helpers ───

    private fun computeEAR(
        lm: List<NormalizedLandmark>,
        p1: Int, p2: Int, p3: Int, p4: Int, p5: Int, p6: Int
    ): Float {
        val vertical1 = dist(lm[p2], lm[p6])
        val vertical2 = dist(lm[p3], lm[p5])
        val horizontal = dist(lm[p1], lm[p4])
        return if (horizontal > 0f) (vertical1 + vertical2) / (2f * horizontal) else 0f
    }

    private fun computeMAR(lm: List<NormalizedLandmark>): Float {
        val vertical = dist(lm[UPPER_LIP], lm[LOWER_LIP])
        val horizontal = dist(lm[MOUTH_LEFT], lm[MOUTH_RIGHT])
        return if (horizontal > 0f) vertical / horizontal else 0f
    }

    private fun dist(a: NormalizedLandmark, b: NormalizedLandmark): Float {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt(dx * dx + dy * dy)
    }

    private fun addToHistory(history: ArrayDeque<Float>, value: Float) {
        if (history.size >= HISTORY_MAX) {
            history.removeFirst()
        }
        history.addLast(value)
    }

    private fun variance(values: ArrayDeque<Float>): Double {
        if (values.size < 2) return 0.0
        val mean = values.sumOf { it.toDouble() } / values.size
        return values.sumOf { (it - mean) * (it - mean) } / values.size
    }
}
