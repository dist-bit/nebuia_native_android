package com.distbit.nebuia_plugin.activities.face.liveness

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.model.LivenessColors
import com.distbit.nebuia_plugin.model.PositionInstruction
import com.distbit.nebuia_plugin.utils.Utils
import com.distbit.nebuia_plugin.utils.Utils.Companion.hideSystemUI
import com.distbit.nebuia_plugin.utils.Utils.Companion.toBitMap
import com.distbit.nebuia_plugin.utils.Utils.Companion.toArray
import com.distbit.nebuia_plugin.utils.views.FaceOvalOverlayView
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import com.otaliastudios.cameraview.CameraView
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Face liveness capture with zoom-approach (two-frame), position guidance,
 * passive liveness signals, and iris distance estimation.
 *
 * Port of admin/src/lib/components/kyc/views/liveness_capture.svelte
 */
class FaceLivenessActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FaceLiveness"

        // Zoom-approach constants (mobile values from web)
        private const val NORMAL_MIN = 0.42f
        private const val NORMAL_MAX = 0.52f
        private const val APPROACH_RETREAT = 0.20f
        private const val CAPTURE_TARGET = 0.64f

        // Smoothing
        private const val EMA_ALPHA = 0.45f
        private const val MAX_PROGRESS_DROP = 1.5f

        // Oval dynamic scale limits during approach
        private const val OVAL_SCALE_MAX = 1.45f

        // Stabilization: require N consecutive same instructions before updating UI
        private const val STABILITY_THRESHOLD = 3

        // Hold face centered for this long before starting approach
        private const val POSITIONING_HOLD_MS = 1500L

        // Default image width for iris calibration fallback
        private const val DEFAULT_IMAGE_WIDTH = 640
    }

    // ─── Views ───
    private lateinit var camera: CameraView
    private lateinit var ovalOverlay: FaceOvalOverlayView
    private lateinit var guidanceMessage: TextView
    private lateinit var statusLabel: TextView
    private lateinit var loader: ProgressBar
    private lateinit var resultOverlay: FrameLayout
    private lateinit var successLayout: LinearLayout
    private lateinit var errorLayout: LinearLayout
    private lateinit var errorMessage: TextView
    private lateinit var retryButton: Button
    private lateinit var title: TextView

    // ─── Coroutines ───
    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ─── MediaPipe ───
    private var faceLandmarker: FaceLandmarker? = null

    // ─── Utilities ───
    private val livenessCollector = PassiveLivenessCollector()
    private var irisCalibration = IrisDistance.calibrateFallback(DEFAULT_IMAGE_WIDTH)
    @Suppress("DEPRECATION")
    private val vibrator: Vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }

    // ─── Capture state ───
    private enum class CapturePhase { POSITIONING, APPROACHING, CAPTURING }

    private var capturePhase = CapturePhase.POSITIONING
    private var positioningTimer: Job? = null
    private var approachProgress = 0f
    private var approachStartWidth = 0f
    private var smoothedFaceWidth = 0f
    private var hasCaptured = false
    private var isAnalyzing = false
    @Volatile private var isProcessing = false

    // Captured frames
    private var sessionId: String? = null
    private var normalFrameBytes: ByteArray? = null
    private var latestBitmap: Bitmap? = null

    // Instruction stabilization
    private var lastInstruction: PositionInstruction? = null
    private var instructionCount = 0

    // Image dimensions (set when first frame arrives)
    private var imageWidth = DEFAULT_IMAGE_WIDTH

    // ─── Lifecycle ───

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_liveness)
        window.hideSystemUI()

        initializeViews()
        setupUI()
        calibrateCamera()
        initializeMediaPipe()
        initializeCamera()
        createCaptureSession()
    }

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
        faceLandmarker?.close()
        latestBitmap?.recycle()
    }

    // ─── Initialization ───

    private fun initializeViews() {
        camera = findViewById(R.id.camera)
        ovalOverlay = findViewById(R.id.ovalOverlay)
        guidanceMessage = findViewById(R.id.guidanceMessage)
        statusLabel = findViewById(R.id.statusLabel)
        loader = findViewById(R.id.loader)
        resultOverlay = findViewById(R.id.resultOverlay)
        successLayout = findViewById(R.id.successLayout)
        errorLayout = findViewById(R.id.errorLayout)
        errorMessage = findViewById(R.id.errorMessage)
        retryButton = findViewById(R.id.retryButton)
        title = findViewById(R.id.title)

        findViewById<Button>(R.id.back).setOnClickListener { finish() }
        retryButton.setOnClickListener { handleRetry() }
    }

    private fun setupUI() {
        NebuIA.theme.apply {
            applyBoldFont(title)
            applyNormalFont(guidanceMessage)
            applyNormalFont(statusLabel)
        }
    }

    private fun calibrateCamera() {
        try {
            val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
            val frontCameraId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
            if (frontCameraId != null) {
                val chars = cameraManager.getCameraCharacteristics(frontCameraId)
                val focalLengths = chars.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val sensorSize = chars.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                if (focalLengths != null && focalLengths.isNotEmpty() && sensorSize != null) {
                    irisCalibration = IrisDistance.calibrate(
                        focalLengths[0], sensorSize.width, imageWidth
                    )
                    Log.d(TAG, "Camera calibrated: fov=${irisCalibration.fovDegrees}°, focalPx=${irisCalibration.focalLengthPx}")
                    return
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Camera calibration failed, using fallback", e)
        }
        irisCalibration = IrisDistance.calibrateFallback(imageWidth)
    }

    private fun initializeMediaPipe() {
        // Try GPU delegate first for 2-3x faster inference, fallback to CPU
        for (delegate in listOf(Delegate.GPU, Delegate.CPU)) {
            try {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("face_landmarker.task")
                    .setDelegate(delegate)
                    .build()

                val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.LIVE_STREAM)
                    .setNumFaces(1)
                    .setMinFaceDetectionConfidence(0.5f)
                    .setMinFacePresenceConfidence(0.5f)
                    .setMinTrackingConfidence(0.5f)
                    .setResultListener(::onFaceResults)
                    .setErrorListener { error -> Log.e(TAG, "MediaPipe error", error) }
                    .build()

                faceLandmarker = FaceLandmarker.createFromOptions(this, options)
                Log.d(TAG, "MediaPipe FaceLandmarker initialized with $delegate")
                return
            } catch (e: Exception) {
                Log.w(TAG, "MediaPipe $delegate delegate failed, trying next", e)
            }
        }
        Log.e(TAG, "Failed to initialize MediaPipe with any delegate")
    }

    private fun initializeCamera() {
        with(camera) {
            frameProcessingFormat = ImageFormat.FLEX_RGBA_8888
            setLifecycleOwner(this@FaceLivenessActivity)
            playSounds = false
            exposureCorrection = 1f

            // Resolution
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            val w = displayMetrics.widthPixels
            val h = displayMetrics.heightPixels

            setPictureSize { source ->
                mutableListOf(Utils.getOptimalSize(source, w, h)!!)
            }
            setPreviewStreamSize { source ->
                mutableListOf(Utils.getOptimalSize(source, w, h)!!)
            }

            addFrameProcessor { frame ->
                if (!isProcessing && !hasCaptured && frame.dataClass == ByteArray::class.java) {
                    isProcessing = true
                    processFrame(frame.toBitMap())
                }
            }
        }
    }

    private fun createCaptureSession() {
        coroutineScope.launch {
            try {
                sessionId = NebuIA.task.createFaceCaptureSession()
                Log.d(TAG, "Capture session created: $sessionId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create capture session", e)
            }
        }
    }

    // ─── Frame processing ───

    private fun processFrame(bitmap: Bitmap) {
        // Update image dimensions on first frame
        if (imageWidth != bitmap.width) {
            imageWidth = bitmap.width
        }

        // Keep reference to latest bitmap for capture
        latestBitmap?.recycle()
        latestBitmap = bitmap

        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = SystemClock.uptimeMillis()
            faceLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe detectAsync error", e)
            isProcessing = false
        }
    }

    /**
     * MediaPipe result callback — fires on background thread.
     * This is the core state machine, mirroring the web's handleFaceResults.
     */
    private fun onFaceResults(result: FaceLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        coroutineScope.launch {
            try {
                if (hasCaptured || isAnalyzing || capturePhase == CapturePhase.CAPTURING) {
                    return@launch
                }

                val faceCount = result.faceLandmarks().size
                if (faceCount == 0) {
                    resetApproach()
                    updateScanUI(PositionInstruction.NO_FACE, LivenessColors.GRAY,
                        getString(R.string.liveness_position_face))
                    return@launch
                }

                val landmarks = result.faceLandmarks()[0]
                val rawFaceWidth = PositionGuidance.computeFaceWidth(landmarks)

                // EMA smoothing
                smoothedFaceWidth = if (smoothedFaceWidth == 0f) rawFaceWidth
                else smoothedFaceWidth * (1 - EMA_ALPHA) + rawFaceWidth * EMA_ALPHA

                val faceWidth = smoothedFaceWidth

                // Check if face fits within the oval
                // During positioning: check boundary landmarks (forehead, chin, cheeks)
                // During approach: only check nose (oval is growing with the face)
                val faceInOval = when (capturePhase) {
                    CapturePhase.POSITIONING -> {
                        val forehead = landmarks[10]   // top of face
                        val chin = landmarks[152]      // bottom of face
                        val leftCheek = landmarks[234] // left edge
                        val rightCheek = landmarks[454] // right edge
                        ovalOverlay.isPointInOval(forehead.x(), forehead.y(), 1.0f) &&
                        ovalOverlay.isPointInOval(chin.x(), chin.y(), 1.0f) &&
                        ovalOverlay.isPointInOval(leftCheek.x(), leftCheek.y(), 1.0f) &&
                        ovalOverlay.isPointInOval(rightCheek.x(), rightCheek.y(), 1.0f)
                    }
                    else -> {
                        ovalOverlay.isPointInOval(landmarks[1].x(), landmarks[1].y(), 1.1f)
                    }
                }
                if (!faceInOval) {
                    positioningTimer?.cancel(); positioningTimer = null
                    updateScanUI(PositionInstruction.NO_FACE, LivenessColors.YELLOW,
                        getString(R.string.liveness_center_in_oval))
                    return@launch
                }

                // Feed passive liveness collector
                livenessCollector.pushFrame(landmarks)

                // Position analysis
                val posResult = PositionGuidance.analyzeFacePosition(landmarks, faceCount)

                when (capturePhase) {
                    CapturePhase.POSITIONING -> handlePositioningPhase(posResult, faceWidth)
                    CapturePhase.APPROACHING -> handleApproachingPhase(posResult, faceWidth)
                    CapturePhase.CAPTURING -> { /* waiting for API */ }
                }
            } finally {
                isProcessing = false
            }
        }
    }

    // ─── State machine phases ───

    private fun handlePositioningPhase(
        posResult: com.distbit.nebuia_plugin.model.PositionGuidanceResult,
        faceWidth: Float
    ) {
        if (!posResult.ready) {
            val critical = posResult.instruction in listOf(
                PositionInstruction.NO_FACE,
                PositionInstruction.MULTIPLE_FACES,
                PositionInstruction.REMOVE_HAT,
                PositionInstruction.REMOVE_GLASSES
            )

            if (faceWidth > NORMAL_MAX) {
                positioningTimer?.cancel(); positioningTimer = null
                updateScanUI(PositionInstruction.MOVE_AWAY, LivenessColors.YELLOW,
                    getString(R.string.liveness_move_away))
                return
            }

            if (critical) {
                positioningTimer?.cancel(); positioningTimer = null
            }

            updateScanUI(posResult.instruction,
                PositionGuidance.getInstructionColor(posResult.instruction),
                posResult.message)
            return
        }

        // Face is centered
        if (faceWidth in NORMAL_MIN..NORMAL_MAX) {
            updateScanUI(PositionInstruction.CENTERED, LivenessColors.GREEN,
                getString(R.string.liveness_perfect))
            if (positioningTimer == null) {
                vibrateLight()
                positioningTimer = coroutineScope.launch {
                    delay(POSITIONING_HOLD_MS)
                    captureNormalFrame()
                    approachStartWidth = smoothedFaceWidth
                    capturePhase = CapturePhase.APPROACHING
                    vibrateLight()
                    positioningTimer = null
                    guidanceMessage.text = getString(R.string.liveness_approach)
                }
            }
        } else if (faceWidth > NORMAL_MAX) {
            positioningTimer?.cancel(); positioningTimer = null
            updateScanUI(PositionInstruction.MOVE_AWAY, LivenessColors.YELLOW,
                getString(R.string.liveness_move_away))
        } else {
            positioningTimer?.cancel(); positioningTimer = null
            updateScanUI(PositionInstruction.MOVE_CLOSER, LivenessColors.YELLOW,
                getString(R.string.liveness_move_closer))
        }
    }

    private fun handleApproachingPhase(
        posResult: com.distbit.nebuia_plugin.model.PositionGuidanceResult,
        faceWidth: Float
    ) {
        // Critical issues reset to positioning
        if (!posResult.ready) {
            val critical = posResult.instruction in listOf(
                PositionInstruction.NO_FACE,
                PositionInstruction.MULTIPLE_FACES,
                PositionInstruction.REMOVE_HAT,
                PositionInstruction.REMOVE_GLASSES
            )
            if (critical) {
                resetApproach()
                updateScanUI(posResult.instruction,
                    PositionGuidance.getInstructionColor(posResult.instruction),
                    posResult.message)
                return
            }
        }

        // Retreated too far
        if (faceWidth < APPROACH_RETREAT) {
            resetApproach()
            updateScanUI(PositionInstruction.MOVE_CLOSER, LivenessColors.YELLOW,
                getString(R.string.liveness_dont_retreat))
            return
        }

        // Calculate progress with ratchet
        val approachStart = if (approachStartWidth > 0f) approachStartWidth else NORMAL_MAX - 0.03f
        val range = CAPTURE_TARGET - approachStart
        val rawProgress = if (range > 0f)
            ((faceWidth - approachStart) / range * 100f).coerceIn(0f, 100f)
        else 0f

        approachProgress = if (rawProgress >= approachProgress) rawProgress
        else max(rawProgress, approachProgress - MAX_PROGRESS_DROP)

        // Smoothly animate oval scale and progress during approach
        val scaleRatio = if (approachStartWidth > 0f) (faceWidth / approachStartWidth) else 1.0f
        val targetScale = scaleRatio.coerceIn(1.0f, OVAL_SCALE_MAX)
        ovalOverlay.animateScaleTo(targetScale)
        ovalOverlay.animateProgressTo(approachProgress)
        val color = interpolateColor(LivenessColors.GREEN, LivenessColors.INDIGO, approachProgress / 100f)
        ovalOverlay.borderColor = color

        // Show guidance: ignore MOVE_AWAY/MOVE_CLOSER during approach (distance is handled by approach logic)
        if (!posResult.ready &&
            posResult.instruction != PositionInstruction.MOVE_AWAY &&
            posResult.instruction != PositionInstruction.MOVE_CLOSER) {
            guidanceMessage.text = posResult.message
        } else {
            guidanceMessage.text = getString(R.string.liveness_approach)
        }
        lastInstruction = PositionInstruction.CENTERED
        instructionCount = STABILITY_THRESHOLD

        // Capture at target
        if (faceWidth >= CAPTURE_TARGET) {
            capturePhase = CapturePhase.CAPTURING
            approachProgress = 100f
            ovalOverlay.cancelAnimations()
            ovalOverlay.progress = 100f
            performCapture()
        }
    }

    // ─── Capture logic ───

    private fun captureNormalFrame() {
        val bitmap = latestBitmap
        if (bitmap != null && !bitmap.isRecycled) {
            normalFrameBytes = bitmap.toArray()
            Log.d(TAG, "Normal frame captured: ${normalFrameBytes?.size} bytes")
        }
    }

    private fun performCapture() {
        if (hasCaptured) return
        hasCaptured = true
        isAnalyzing = true

        // Capture close-up frame
        val closeFrameBytes = latestBitmap?.let { if (!it.isRecycled) it.toArray() else null }

        showAnalyzingUI()

        coroutineScope.launch {
            try {
                val passiveMetadata = livenessCollector.getMetadata()

                // Fire-and-forget passive signals
                if (sessionId != null) {
                    launch(Dispatchers.IO) {
                        try {
                            NebuIA.task.sendPassiveSignals(sessionId!!, passiveMetadata)
                        } catch (e: Exception) {
                            Log.w(TAG, "sendPassiveSignals failed", e)
                        }
                    }
                }

                // Main liveness call
                val result = if (sessionId != null && normalFrameBytes != null && closeFrameBytes != null) {
                    withContext(Dispatchers.IO) {
                        NebuIA.task.analyzeLiveness(sessionId!!, normalFrameBytes!!, closeFrameBytes)
                    }
                } else {
                    // Fallback: single frame via existing saveFace
                    if (closeFrameBytes != null) {
                        val bitmap = latestBitmap
                        if (bitmap != null && !bitmap.isRecycled) {
                            val success = withContext(Dispatchers.IO) {
                                NebuIA.task.liveDetection(bitmap)
                            }
                            if (success) {
                                com.distbit.nebuia_plugin.model.LivenessResult(
                                    score = 0.0,
                                    isValidForKyc = true,
                                    accessories = null
                                )
                            } else null
                        } else null
                    } else null
                }

                if (result != null && result.isValidForKyc) {
                    showSuccessUI()
                    delay(2000)
                    NebuIA.faceLivenessComplete(true)
                    finish()
                } else if (result != null) {
                    val errorMsg = when {
                        result.accessories?.hasGlasses == true -> getString(R.string.liveness_error_glasses)
                        result.accessories?.hasMask == true -> getString(R.string.liveness_error_mask)
                        result.accessories?.hasCap == true || result.accessories?.hasHat == true ->
                            getString(R.string.liveness_error_hat)
                        else -> getString(R.string.liveness_error_generic)
                    }
                    showErrorUI(errorMsg)
                } else {
                    showErrorUI(getString(R.string.liveness_error_generic))
                }
            } catch (e: Exception) {
                Log.e(TAG, "Capture error", e)
                showErrorUI(getString(R.string.liveness_error_network))
            }
        }
    }

    // ─── State management ───

    private fun resetApproach() {
        positioningTimer?.cancel(); positioningTimer = null
        if (capturePhase == CapturePhase.APPROACHING) {
            capturePhase = CapturePhase.POSITIONING
            approachProgress = 0f
            approachStartWidth = 0f
            smoothedFaceWidth = 0f
            normalFrameBytes = null
            ovalOverlay.cancelAnimations()
            ovalOverlay.progress = 0f
            ovalOverlay.scale = 1.0f
        }
    }

    private fun handleRetry() {
        hasCaptured = false
        isAnalyzing = false
        capturePhase = CapturePhase.POSITIONING
        approachProgress = 0f
        approachStartWidth = 0f
        smoothedFaceWidth = 0f
        normalFrameBytes = null
        sessionId = null
        livenessCollector.reset()

        // Reset UI with fade transitions
        resultOverlay.fadeOut(300) {
            successLayout.visibility = View.GONE
            errorLayout.visibility = View.GONE
        }
        loader.visibility = View.GONE
        statusLabel.visibility = View.GONE
        ovalOverlay.cancelAnimations()
        ovalOverlay.progress = 0f
        ovalOverlay.scale = 1.0f
        ovalOverlay.borderColor = LivenessColors.GRAY
        guidanceMessage.text = getString(R.string.liveness_position_face)
        guidanceMessage.fadeIn(300)

        // Re-create session
        createCaptureSession()
    }

    // ─── UI updates ───

    private fun updateScanUI(instruction: PositionInstruction, color: Int, message: String) {
        // Stabilization: require consecutive same instructions
        if (instruction == lastInstruction) {
            instructionCount++
        } else {
            instructionCount = 1
            lastInstruction = instruction
        }
        if (instructionCount < STABILITY_THRESHOLD) return

        guidanceMessage.text = message
        ovalOverlay.setBorderColorAnimated(color)
    }

    private fun showAnalyzingUI() {
        guidanceMessage.fadeOut()
        statusLabel.text = getString(R.string.liveness_analyzing)
        loader.fadeIn()
        statusLabel.fadeIn()
    }

    private fun showSuccessUI() {
        vibrateSuccess()
        loader.visibility = View.GONE
        statusLabel.visibility = View.GONE
        errorLayout.visibility = View.GONE
        successLayout.visibility = View.VISIBLE
        resultOverlay.fadeIn(400)
    }

    private fun showErrorUI(message: String) {
        isAnalyzing = false
        vibrateError()
        loader.visibility = View.GONE
        statusLabel.visibility = View.GONE
        successLayout.visibility = View.GONE
        errorLayout.visibility = View.VISIBLE
        errorMessage.text = message
        resultOverlay.fadeIn(400)
    }

    // ─── Haptics ───

    private fun vibrateLight() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun vibrateSuccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 40, 80, 40), -1))
        }
    }

    private fun vibrateError() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1))
        }
    }

    // ─── Fade animations ───

    private fun View.fadeIn(durationMs: Long = 300) {
        alpha = 0f
        visibility = View.VISIBLE
        animate().alpha(1f)
            .setDuration(durationMs)
            .setInterpolator(DecelerateInterpolator(1.5f))
            .start()
    }

    private fun View.fadeOut(durationMs: Long = 300, onEnd: (() -> Unit)? = null) {
        animate().alpha(0f)
            .setDuration(durationMs)
            .setInterpolator(AccelerateInterpolator(1.5f))
            .withEndAction {
                visibility = View.GONE
                alpha = 1f
                onEnd?.invoke()
            }.start()
    }

    // ─── Color interpolation ───

    private fun interpolateColor(from: Int, to: Int, t: Float): Int {
        val clampedT = t.coerceIn(0f, 1f)
        val fromA = (from shr 24) and 0xFF
        val fromR = (from shr 16) and 0xFF
        val fromG = (from shr 8) and 0xFF
        val fromB = from and 0xFF
        val toA = (to shr 24) and 0xFF
        val toR = (to shr 16) and 0xFF
        val toG = (to shr 8) and 0xFF
        val toB = to and 0xFF
        val a = (fromA + (toA - fromA) * clampedT).toInt()
        val r = (fromR + (toR - fromR) * clampedT).toInt()
        val g = (fromG + (toG - fromG) * clampedT).toInt()
        val b = (fromB + (toB - fromB) * clampedT).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
