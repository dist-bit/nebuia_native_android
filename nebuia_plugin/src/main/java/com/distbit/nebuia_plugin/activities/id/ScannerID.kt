package com.distbit.nebuia_plugin.activities.id

import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.model.Documents
import com.distbit.nebuia_plugin.model.Side
import com.distbit.nebuia_plugin.utils.SpanFormatter
import com.distbit.nebuia_plugin.utils.progresshud.ProgressHUD
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

    private var documents: Documents = Documents

    private lateinit var mxIDFront: String
    private lateinit var mxIDBack: String
    private lateinit var mxPassportFront: String

    private lateinit var svProgressHUD: ProgressHUD

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
        capture = findViewById(R.id.capture)

        findViewById<Button>(R.id.back).setOnClickListener {
            super.onBackPressed()
        }

        svProgressHUD = ProgressHUD(this)

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
    private fun setSummarySide() {
        val spanned1 = SpannableString(getString(R.string.set_front_id))

        val spanned2: SpannableString = if (documents.side() == Side.FRONT) {
            SpannableString(getString(R.string.side_front_id))
        } else {
            SpannableString(getString(R.string.side_back_id))
        }

        spanned2.setSpan(BackgroundColorSpan(Color.parseColor("#1a8ed0")), 0, spanned2.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned2.setSpan(StyleSpan(Typeface.BOLD), 0, spanned2.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned2.setSpan(ForegroundColorSpan(Color.WHITE), 0, spanned2.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        summarySide.text = SpanFormatter.format( spanned1, spanned2)
    }

    /**
     * @dev set up camera for frame processing
     * set image format and life cycle to activity
     * @param camera - CameraView instance
     */
    private fun setUpCamera(camera: CameraView) {
        camera.frameProcessingFormat = ImageFormat.FLEX_RGBA_8888
        camera.setLifecycleOwner(this)
        camera.exposureCorrection = 1F
        camera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                result.toBitmap {
                    detectDocument(it!!)
                }
            }
        })

        capture.setOnClickListener{
            capture.isEnabled = false
            svProgressHUD.show()
            camera.takePicture()
        }
    }

    /**
     * @dev real time detect document
     */
    private fun finalize() =
        PreviewDocument.newInstance().show(supportFragmentManager, PreviewDocument::class.java.canonicalName)

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
            val cropped = NebuIA.task.documentRealTimeDetection(bitmap)
            svProgressHUD.dismiss()
            capture.isEnabled = true
            if(cropped != null) {
                this@ScannerID.finalize()
            }
        }
}