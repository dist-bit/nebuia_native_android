package com.distbit.nebuia_plugin.activities.face

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.utils.Utils
import com.distbit.nebuia_plugin.utils.Utils.Companion.hideSystemUI
import com.distbit.nebuia_plugin.utils.Utils.Companion.toBitMap
import com.otaliastudios.cameraview.CameraView
import kotlinx.coroutines.*

public class FaceDetector : AppCompatActivity() {
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var faceDetectionState = FaceDetectionState()

    private lateinit var camera: CameraView
    private lateinit var title: TextView
    private lateinit var summary: TextView
    private lateinit var status: TextView
    private lateinit var loader: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_face_detector)
        window.hideSystemUI()

        initializeViews()
        setupUI()
        initializeCamera()
    }

    private fun initializeViews() {
        camera = findViewById(R.id.camera)
        title = findViewById(R.id.title)
        summary = findViewById(R.id.summary)
        status = findViewById(R.id.summary_1)
        loader = findViewById(R.id.loader)

        findViewById<Button>(R.id.back).setOnClickListener { finish() }
    }

    private fun setupUI() {
        NebuIA.theme.apply {
            applyBoldFont(title)
            applyNormalFont(summary)
            applyNormalFont(status)
        }
        updateInitialInstructions()
    }

    private fun updateInitialInstructions() {
        title.text = getString(R.string.face_detection_title)
        summary.text = getString(R.string.face_position)
        status.text = getString(R.string.analyzing)
    }

    private fun initializeCamera() {
        with(camera) {
            frameProcessingFormat = ImageFormat.FLEX_RGBA_8888
            setLifecycleOwner(this@FaceDetector)
            playSounds = false
            exposureCorrection = 1f

            setupCameraResolution()
            setupFrameProcessor()
        }
    }

    private fun setupCameraResolution() {
        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels

        camera.setPictureSize { source ->
            mutableListOf(Utils.getOptimalSize(source, width, height)!!)
        }

        camera.setPreviewStreamSize { source ->
            mutableListOf(Utils.getOptimalSize(source, width, height)!!)
        }
    }

    private fun setupFrameProcessor() {
        camera.addFrameProcessor { frame ->
            if (!faceDetectionState.isProcessing && frame.dataClass == ByteArray::class.java) {
                faceDetectionState.isProcessing = true
                processFrame(frame.toBitMap())
            }
        }
    }

    private fun processFrame(bitmap: Bitmap) {
        coroutineScope.launch {
            try {
                when {
                    !faceDetectionState.faceComplete -> processFaceDetection(bitmap)
                    !faceDetectionState.idFrontComplete -> processIdFront(bitmap)
                    !faceDetectionState.idBackComplete -> processIdBack(bitmap)
                }
            } catch (e: Exception) {
                handleError(e)
            } finally {
                faceDetectionState.isProcessing = false
            }
        }
    }

    private suspend fun processFaceDetection(bitmap: Bitmap) {
        val detections = NebuIA.face.detect(bitmap)
        if (detections.isNotEmpty()) {
            if (NebuIA.task.liveDetection(bitmap)) {
                handleSuccessfulFaceDetection()
            }
        }
    }

    private suspend fun processIdFront(bitmap: Bitmap) {
        if (NebuIA.task.documentLabel(bitmap) == "mx_id_front") {
            faceDetectionState.idFrontComplete = true
            updateUIForBackDocument()
        }
    }

    private suspend fun processIdBack(bitmap: Bitmap) {
        if (NebuIA.task.documentLabel(bitmap) == "mx_id_back") {
            faceDetectionState.idBackComplete = true
            completeVerification()
        }
    }

    private fun handleSuccessfulFaceDetection() {
        faceDetectionState.faceComplete = true
        summary.text = getString(R.string.document_front)
        status.text = getString(R.string.document_align)
    }

    private fun updateUIForBackDocument() {
        summary.text = getString(R.string.document_back)
        status.text = getString(R.string.document_align)
    }

    private fun completeVerification() {
        coroutineScope.launch {
            summary.text = getString(R.string.verification_complete)
            status.text = ""
            loader.visibility = View.GONE
            delay(2000)
            NebuIA.faceComplete()
            finish()
        }
    }

    private fun handleError(error: Exception) {
        status.text = getString(R.string.verification_failed)
        loader.visibility = View.GONE
        Log.e("FaceDetector", "Error during detection", error)
    }

    private data class FaceDetectionState(
        var isProcessing: Boolean = false,
        var faceComplete: Boolean = false,
        var idFrontComplete: Boolean = false,
        var idBackComplete: Boolean = false
    )

    override fun onDestroy() {
        super.onDestroy()
        coroutineScope.cancel()
    }
}