package com.distbit.nebuia_plugin.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Base64
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.model.Fingers
import com.distbit.nebuia_plugin.utils.Utils.Companion.hideSystemUI
import com.distbit.nebuia_plugin.utils.Utils.Companion.toBitMap
import com.distbit.nebuia_plugin.utils.progresshud.ProgressHUD
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.PictureResult
import com.otaliastudios.cameraview.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.concurrent.schedule
import kotlin.math.abs


class FingersDetector : AppCompatActivity() {
    private val uiScope = CoroutineScope(Dispatchers.Main)

    private var detect: Boolean = false

    private lateinit var skip: Button

    private lateinit var title: TextView
    private lateinit var action: TextView
    private lateinit var summary: TextView
    private lateinit var loader: ProgressBar

    private lateinit var panel: LinearLayout
    private lateinit var camera: CameraView

    private lateinit var svProgressHUD: ProgressHUD
    private lateinit var qualityBar: ProgressBar

    val timer = object: CountDownTimer(50000, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val seconds: Long = millisUntilFinished / 1000
            if(seconds == 25L) {
                summary.text = getString(R.string.fingerprints_warning_detection)
            }

            if(seconds == 0L) {
                cancel()
                qualityBar.visibility = View.GONE
                loader.visibility = View.GONE
                action.visibility = View.GONE
                skip.visibility = View.VISIBLE

                if(NebuIA.skipStep) {
                    summary.text = getString(R.string.skip_step_fingerprints)
                    skip.text = getString(R.string.skip_fingerprints)
                } else {
                    summary.text = getString(R.string.skip_fingerprints_summary)
                    skip.text = getString(R.string.retry_fingerprints)
                }
            }
        }

        override fun onFinish() {
        }
    }

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fingerprint_detector)
        window.hideSystemUI()

        svProgressHUD =
            ProgressHUD(this)

        camera = findViewById(R.id.camera)

        val back: Button = findViewById(R.id.back)

        title = findViewById(R.id.title)
        summary = findViewById(R.id.summary)
        //summaryOne = findViewById(R.id.summary_1)
        panel = findViewById(R.id.panel_fingerprint)
        qualityBar = findViewById(R.id.simpleProgressBar)
        loader = findViewById(R.id.loader)
        skip = findViewById(R.id.skip_step)
        action = findViewById(R.id.action_label)

        qualityBar.max = 3 // 4 fingers

        back.setOnClickListener { super.onBackPressed() }
        skip.setOnClickListener {
            NebuIA.fingerSkip()
            super.onBackPressed()
        }

        // setUp
        windowFeatures()
        setUpColors()
        setFonts()
        clearFingerprints()
        setUpCamera()
    }

    private fun windowFeatures() {
        window.navigationBarColor = resources.getColor(R.color.white_overlay)
        window.statusBarColor = resources.getColor(R.color.white_overlay)
    }

    /**
     * @dev clear fingerprints list
     */
    private fun clearFingerprints() {
        NebuIA.task.fingers.clear()
    }

    /**
     * @dev apply fonts from NebuIA theme
     */
    private fun setFonts() {
        NebuIA.theme.applyBoldFont(title)
        NebuIA.theme.applyNormalFont(summary)
        //NebuIA.theme.applyNormalFont(summaryOne)
    }

    /**
     * @dev set colors to components
     */
    private fun setUpColors() {
        NebuIA.theme.setUpButtonPrimaryTheme(skip, this)
        //NebuIA.theme.setUpButtonSecondaryTheme(retake, this)
    }

    /**
     * @dev set up camera for frame processing
     * set image format and life cycle to activity
     */
    private fun setUpCamera() {
        camera.frameProcessingFormat = ImageFormat.FLEX_RGBA_8888
        camera.setLifecycleOwner(this)
        camera.playSounds = false

        val displayMetrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(displayMetrics)
        val height = displayMetrics.heightPixels
        val width = displayMetrics.widthPixels


        camera.setPictureSize { source ->
            mutableListOf(getOptimalSize(source, width, height)!!)
        }

        camera.setPreviewStreamSize { source ->
            mutableListOf(getOptimalSize(source, width, height)!!)
        }

        timer.start()
        camera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                processFrame(result)
            }

        })

        Timer(getString(R.string.time_schedule), false)
            .schedule(2000) {
                camera.addFrameProcessor { frame ->
                    if (!detect) {
                        detect = true
                        if (frame.dataClass === ByteArray::class.java)
                            detectFingerprint(frame.toBitMap())
                        frame.release()
                    }
                    frame.release()
                }
            }
    }

    private fun processFrame(result: PictureResult) {
        svProgressHUD.show()
        result.toBitmap {

            uiScope.launch {
                val fingers: MutableList<Fingers> = mutableListOf()
                //val enhanced = NebuIA.task.imageEnhance(it!!)
                val response = NebuIA.task.extractFingerprints(it!!, onError = {
                    detect = false
                    svProgressHUD.dismiss()
                })

                // release images
                //it.recycle()

                svProgressHUD.dismiss()
                // check response1
                if (response != null) {
                    try {
                        if (response["status"] as Boolean) {
                            val data = response["payload"] as HashMap<String, Any>
                            (data["fingers"] as List<HashMap<String, Any>>).forEach { finger ->
                                fingers.add(
                                    Fingers(
                                        name = finger["name"] as String,
                                        image = (finger["image"] as String).toBitMap(),
                                        score = finger["nfiq"] as Int
                                    )
                                )
                            }

                            NebuIA.task.fingers = fingers
                            this@FingersDetector.finalize()
                            // ON FAIL PROCESSING
                        } else {
                            detect = false
                        }
                    } catch (e: Exception) {
                        detect = false
                    }
                }
            }
        }

    }

    /**
     * @dev analyze frame to search fingers in frame
     * is necessary 4 fingers
     * @param decode - frame image
     */
    private fun detectFingerprint(decode: Bitmap) {
        //val bmp2: Bitmap = decode.copy(decode.config, true)
        uiScope.launch {
            val result = NebuIA.task.fingerprintDetection(decode)
            val rects: MutableList<RectF> = mutableListOf()
            val scores: MutableList<Float> = mutableListOf()

            //order rects
            result.sortByDescending { it.y }

            result.forEach {
                rects.add(RectF(it.x, it.y, it.w, it.h))
            }

            if (rects.size == 4) {
                //detectionsCount.add(rects.size)
                result.forEach {
                    val croppedBmp: Bitmap = Bitmap.createBitmap(
                        decode,
                        it.x.toInt(),
                        it.y.toInt(),
                        it.w.toInt(),
                        it.h.toInt()
                    )

                    val rotate = croppedBmp.rotate(if (NebuIA.positionHand == 0) -90.0f else 90.0f)!!
                    // clear
                    //croppedBmp.recycle()
                    val quality = NebuIA.task.fingerprintQuality(rotate)
                    if(quality >= NebuIA.qualityValue) {
                        scores.add(quality)
                    }
                }
            } else {
                rects.clear()
            }

            val size = scores.size

            setProgressBar(size)
            // clear image
            //decode.recycle()

            if (size >= 3) {
                timer.cancel()
                camera.takePicture()
            } else {
                detect = false
            }
        }
    }

    // set score to progress bar
    private fun setProgressBar(size: Int) {
        qualityBar.setProgress(size, true)

        when (size) {
            1 -> qualityBar.progressTintList = ColorStateList.valueOf(Color.RED)
            2 -> qualityBar.progressTintList =
                ColorStateList.valueOf(Color.YELLOW)
            in 3..4 -> qualityBar.progressTintList = ColorStateList.valueOf(Color.GREEN)
        }
    }

    /**
     * @dev on 4 fingers detected, go to preview result
     */
    private fun FingersDetector.finalize() {
        val intent = Intent(this@FingersDetector, FingerprintPreview::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    /**
     * @dev convert base64 to bitmap
     * @return bitmap image
     */
    private fun String.toBitMap(): Bitmap {
        val decodedString: ByteArray = Base64.decode(this, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
    }

    fun Bitmap.rotate(angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
    }

    /**
     * Calculate the optimal size of camera preview
     * @param sizes
     * @param w
     * @param h
     * @return
     */
    private fun getOptimalSize(sizes: List<Size>?, w: Int, h: Int): Size? {
        val targetRatio = w.toDouble() / h
        if (sizes == null) return null
        var optimalSize: Size? = null
        var minDiff = Double.MAX_VALUE

        for (size in sizes) {
            val ratio: Int = size.width / size.height
            if (abs(ratio - targetRatio) > 0.2) continue
            if (abs(size.height - h) < minDiff) {
                optimalSize = size
                minDiff = abs(size.height - h).toDouble()
            }
        }
        // Cannot find the one match the aspect ratio, ignore the requirement
        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE
            for (size in sizes) {
                if (abs(size.height - h) < minDiff) {
                    optimalSize = size
                    minDiff = abs(size.height - h).toDouble()
                }
            }
        }

        return optimalSize
    }

}
