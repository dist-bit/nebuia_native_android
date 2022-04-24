package com.distbit.nebuia_plugin.activities

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.utils.Utils.Companion.hideSystemUI
import com.distbit.nebuia_plugin.utils.Utils.Companion.toBitMap
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.VideoResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.HashMap
import kotlin.concurrent.schedule
import kotlin.concurrent.timerTask


class RecordActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main)

    private var doneLayout: LinearLayout? = null
    private lateinit var zone: RelativeLayout

    private var title: TextView? = null
    private lateinit var summary: TextView
    private var readText: TextView? = null

    private lateinit var record: Button
    private lateinit var recordDone: Button

    private var camera: CameraView? = null

    private var lastName: String? = null
    private var names: String? = null

    private var currentTextPosition: Int = 0
    private lateinit var suffixText: ArrayList<String>

    private var faceComplete: Boolean = false
    private var lectureComplete: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_evidence)
        window.hideSystemUI()

        camera = findViewById(R.id.camera)
        val back: Button = findViewById(R.id.back)

        doneLayout = findViewById(R.id.done)

        title = findViewById(R.id.title)
        summary = findViewById(R.id.summary)

        readText = findViewById(R.id.read_text)
        record = findViewById(R.id.record)
        recordDone = findViewById(R.id.record_done)

        zone = findViewById(R.id.zone)

        suffixText = intent!!.getStringArrayListExtra("text_to_load")!!
        permission()

        back.setOnClickListener { back() }
        record.setOnClickListener {
            parseText()
        }

        recordDone.setOnClickListener {
            parseText()
        }

        if(NebuIA.getNameFromId) getDataReport() else readText!!.text = suffixText[0]

        setFonts()
        setUpCamera(camera!!)
    }

    private fun parseText() {

        if(suffixText.size == currentTextPosition + 2) {
            record.visibility = View.INVISIBLE
            recordDone.visibility = View.VISIBLE
        }

        if(suffixText.size != currentTextPosition + 1) {
            currentTextPosition++
            readText!!.text = suffixText[currentTextPosition]
        } else {
            lectureComplete = true
            zone.visibility = View.INVISIBLE
            record.visibility = View.INVISIBLE
            recordDone.visibility = View.INVISIBLE
            //summary.text =
            //    "Por favor coloca la parte frontal de tu documento de identidad frente a la cámara"
            timer.schedule(timerTask {
                //ineBack = true
                camera!!.stopVideo()
            }, 1000)
        }
    }

    private fun permission() {
        ActivityCompat.requestPermissions(this,
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE).toTypedArray(),
            100)
    }

    /**
     * @dev if report exist, get names from ID and put to read text
     * if not exist, fill with const text
     */
    private fun getDataReport() {
        val payload = StringBuilder()
        uiScope.launch {
            val report = NebuIA.task.getReportSummary()
            if (report.containsKey("document")) {
                val document = report["document"] as HashMap<*, *>
                if (document.containsKey("names")) {
                    val namesMap: HashMap<*, *> = document["names"] as HashMap<*, *>

                    if (namesMap.containsKey("last_name"))
                        lastName = namesMap["last_name"] as String

                    if (namesMap.containsKey("names")) {
                        val namesList: List<String> = namesMap["names"] as List<String>
                        if (namesList.isNotEmpty()) names = namesList.joinToString(" ")
                    }

                    if (!names.isNullOrBlank()) payload.append("Yo $names")
                    if (!lastName.isNullOrBlank()) payload.append(" $lastName ")
                }
            }

            if (payload.isBlank())
                payload.append("Yo (indique su nombre) ")
            // set text
            payload.append(suffixText[0])
            readText!!.text = payload.toString()
        }
    }

    /**
     * @dev apply fonts from NebuIA theme
     */
    private fun setFonts() {
        NebuIA.theme.applyBoldFont(title!!)
        NebuIA.theme.applyNormalFont(summary)
        NebuIA.theme.applyNormalFont(readText!!)
    }

    /**
     * @dev return to previous activity
     */
    private fun back() = this.finish()

    /**
     * @dev set up camera for frame processing
     * set image format and life cycle to activity
     * @param camera - CameraView instance
     */
    @DelicateCoroutinesApi
    fun setUpCamera(camera: CameraView) {
        camera.frameProcessingFormat = ImageFormat.NV21
        camera.setLifecycleOwner(this)

        Timer(getString(R.string.time_schedule), false)
            .schedule(3000) {
                camera.addFrameProcessor { frame ->
                    if (frame.dataClass === ByteArray::class.java)
                        detect(frame.toBitMap())
                }
            }

        camera.addCameraListener(object : CameraListener() {
            override fun onVideoTaken(result: VideoResult) {
                NebuIA.recordComplete(result.file)
                this@RecordActivity.finish()
            }

            override fun onVideoRecordingStart() {
                zone.visibility = View.VISIBLE
                record.visibility = View.VISIBLE
            }

            override fun onVideoRecordingEnd() {

            }
        })
    }

    /**
     * @dev on every frame detect if face exist
     * if exist it will be analyzed for anti spoofing protection
     * @param bitmap - frame from camera preview
     */
    private val timer = Timer()
    private fun detect(bitmap: Bitmap) {
        uiScope.launch {

            if(!lectureComplete && !faceComplete) {
                val detections = NebuIA.face.detect(bitmap)
                if (detections.isNotEmpty()) {
                    faceComplete = true
                    summary.text =
                        "Por favor lee el texto en voz alta, una vez finalices presiona el boton verde para terminar"
                    camera!!.takeVideoSnapshot(File(getExternalFilesDir(Environment.DIRECTORY_MOVIES), "video.mp4"))
                }
            }
        }
    }
}