package com.distbit.nebuia_plugin.activities

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.utils.Utils.Companion.hideSystemUI
import com.distbit.nebuia_plugin.utils.Utils.Companion.toBitMap
import com.distbit.nebuia_plugin.utils.Utils.Companion.warning
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.PictureResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.timerTask

class FaceDetector : AppCompatActivity() {
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private val timer = Timer()
    private var detect: Boolean = false

    private lateinit var title: TextView
    private lateinit var summary: TextView
    private lateinit var summaryOne: TextView

    // detection helpers
    private var faceComplete: Boolean = false
    private var ineFront: Boolean = false
    private var ineBack: Boolean = false

    private lateinit var camera: CameraView

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_face_detector)
        window.hideSystemUI()

        camera = findViewById(R.id.camera)
        val back: Button = findViewById(R.id.back)

        title = findViewById(R.id.title)
        summary = findViewById(R.id.summary)
        summaryOne = findViewById(R.id.summary_1)

        back.setOnClickListener { back() }

        setFonts()
        setUpCamera()
    }

    /**
     * @dev apply fonts from NebuIA theme
     */
    private fun setFonts() {
        NebuIA.theme.applyBoldFont(title)
        NebuIA.theme.applyNormalFont(summary)
        NebuIA.theme.applyNormalFont(summaryOne)
        //NebuIA.theme.applyNormalFont(summaryTwo)
    }

    /**
     * @dev return to previous activity
     */
    private fun back() = this.finish()

    /**
     * @dev set up camera for frame processing
     * set image format and life cycle to activity
     */
    private fun setUpCamera() {
        camera.frameProcessingFormat = ImageFormat.FLEX_RGBA_8888
        camera.setLifecycleOwner(this)
        camera.playSounds = false
        camera.setPreviewStreamSize { source ->
            source.removeIf { it.width > 720 }
            source
        }
        camera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                result.toBitmap {
                    uiScope.launch {
                        if (NebuIA.task.liveDetection(it!!)) {
                            faceComplete = true
                            summary.text = getString(R.string.document_instruction)
                            summaryOne.text = getString(R.string.waiting_document)
                            detect = false
                        } else detect = false
                    }
                }
            }
        })
        camera.addFrameProcessor { frame ->
            if (!detect) {
                detect = true
                if (frame.dataClass === ByteArray::class.java)
                    detect(frame.toBitMap())
            }
        }
    }

    private fun done() {
        Timer(getString(R.string.time_schedule), false)
            .schedule(2000) {
                NebuIA.faceComplete()
                this@FaceDetector.finish()
            }
    }

    /**
     * @dev on every frame detect if face exist
     * if exist it will be analyzed for anti spoofing protection
     * @param bitmap - frame from camera preview
     */
    private fun detect(bitmap: Bitmap) {
        uiScope.launch {

            if(!faceComplete) {
                val detections = NebuIA.face.detect(bitmap)
                if (detections.isNotEmpty()) {
                    // get face quality
                    val qua = NebuIA.task.qualityFace(bitmap)
                    if(qua > 60) {
                        // detect face live
                        if (NebuIA.task.liveDetection(bitmap)) {
                            faceComplete = true
                            summary.text = getString(R.string.document_instruction)
                            summaryOne.text = getString(R.string.waiting_document)
                            detect = false
                        } else detect = false
                    } else {
                        // play warning sound
                        warning(this@FaceDetector)
                        summaryOne.text = getString(R.string.face_quality_warn)
                        detect = false
                    }
                } else detect = false
            }

            if(faceComplete && !ineFront) {
                val detection: String = NebuIA.task.documentRealTimeDetection(bitmap)

                if(detection == "mx_id_front") {
                    timer.schedule(timerTask {
                        // execute on main thread
                        uiScope.launch {
                            ineFront = true
                            summary.text = getString(R.string.back_document_instruction)
                            detect = false
                        }
                    }, 3000)
                } else {
                    detect = false
                }
            }

            if(ineFront && !ineBack) {
                val detection: String = NebuIA.task.documentRealTimeDetection(bitmap)
                if(detection == "mx_id_back") {
                    timer.schedule(timerTask {
                        // execute on main thread
                        uiScope.launch {
                            ineBack = true
                            detect = false
                            done()
                        }
                    }, 3000)
                } else {
                    detect = false
                }
            }
        }
    }
}