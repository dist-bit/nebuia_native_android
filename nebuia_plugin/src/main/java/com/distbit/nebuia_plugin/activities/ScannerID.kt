package com.distbit.nebuia_plugin.activities

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.model.Side
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.PictureResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ScannerID : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main)

    private lateinit var camera: CameraView
    private lateinit var summarySide: TextView
    private lateinit var title: TextView
    private lateinit var capture: Button

    private val docs = NebuIA.task.documents

    private var detect: Boolean = false
    private lateinit var mxIDFront: String
    private lateinit var mxIDBack: String
    private lateinit var mxPassportFront: String

    private var onAction: Boolean = false

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_scanner)
        //window.hideSystemUI()
        window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window?.statusBarColor = this.getColor(android.R.color.transparent)
        window?.navigationBarColor = this.getColor(R.color.nebuia_bg)

        camera = findViewById(R.id.camera)
        summarySide = findViewById(R.id.summary_side)
        title = findViewById(R.id.title)

        val back: Button = findViewById(R.id.back)
        capture = findViewById(R.id.capture)

        //
        //bottomSheetFragment.dismiss()


        back.setOnClickListener {
            super.onBackPressed()
        }

        fillData()
        setUpCamera(camera)
        setFonts()
    }

    /**
     * @dev set up data for detections
     */
    fun fillData() {
        fillLabels()
        setSummarySide()
    }

    /**
     * @dev set up fill document labels
     */
    private fun fillLabels() {
        mxIDFront = getString(R.string.mx_id_front)
        mxIDBack = getString(R.string.mx_id_back)
        mxPassportFront = getString(R.string.mx_passport_front)
    }

    /**
     * @dev apply fonts from NebuIA theme
     */
    private fun setFonts() {
        NebuIA.theme.applyBoldFont(title)
        NebuIA.theme.applyNormalFont(summarySide)
    }

    /**
     * @dev set title depending of current
     * step [front/back/passport]
     */
    private fun setSummarySide() = if (docs.side == Side.FRONT) summarySide.text =
        getString(R.string.set_front_id) else summarySide.text =
        getString(R.string.set_back_id)

    /**
     * @dev set up camera for frame processing
     * set image format and life cycle to activity
     * @param camera - CameraView instance
     */
    private fun setUpCamera(camera: CameraView) {
        camera.frameProcessingFormat = ImageFormat.FLEX_RGBA_8888
        camera.setLifecycleOwner(this)
        camera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                result.toBitmap {
                    detectDocument(it!!)
                }
            }
        })
        // fast webp conversion
        //camera.setPictureSize { source ->
        //    source.removeIf { it.width > 1080 }
        //    source
        //}
        capture.setOnClickListener{
            if (!onAction) {
                camera.takePicture()
                onAction = true
            }
        }
    }

    /**
     * @dev crop id service
     */
    private fun Bitmap.cropImage() {
        uiScope.launch {
            val image = NebuIA.task.documentCrop(this@cropImage)
            when {
                image != null -> {
                    NebuIA.task.cropped = image
                    this@ScannerID.finalize()
                }
                else -> detect = false
            }
        }
    }

    /**
     * @dev real time detect document
     */
    private fun finalize() {
        PreviewDocument.newInstance().show(supportFragmentManager, PreviewDocument::class.java.canonicalName)
    }

    /**
    * @dev real time detect document
    */
     fun uploadData() {
        val intent = Intent(this, UploadActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        this.startActivity(intent)
        finish()
    }

    /**
     * @dev real time detect document
     * @param bitmap - Image frame from camera view
     */
    private fun detectDocument(bitmap: Bitmap) =
        uiScope.launch {
            val detection: String = NebuIA.task.documentRealTimeDetection(bitmap)
            if (docs.side == Side.FRONT) when (detection) {
                mxIDFront -> bitmap.cropImage()
                mxPassportFront -> bitmap.cropImage()
                else -> detect = false
            } else when (detection) {
                mxIDBack -> bitmap.cropImage()
                else -> detect = false
            }
            // re enable capture button
            onAction = false
        }

    companion object {
        /**
         * @dev reset variables to retake image id
         */
        fun reset() {
           // Side.FRONT -> NebuIA.task.documents.front = null
           // Side.BACK -> NebuIA.task.documents.back = null
        }
    }
}