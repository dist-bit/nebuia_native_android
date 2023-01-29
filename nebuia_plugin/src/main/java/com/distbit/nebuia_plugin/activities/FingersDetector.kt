package com.distbit.nebuia_plugin.activities

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.model.Fingers
import com.distbit.nebuia_plugin.utils.Utils.Companion.hideSystemUI
import com.distbit.nebuia_plugin.utils.Utils.Companion.toBitMap
import com.otaliastudios.cameraview.CameraView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.util.*
import kotlin.concurrent.schedule


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
        //camera.frameProcessingFormat = ImageFormat.FLEX_RGBA_8888
        camera.setLifecycleOwner(this)
        camera.playSounds = false

        timer.start()

        Timer(getString(R.string.time_schedule), false)
            .schedule(2000) {
                camera.addFrameProcessor { frame ->
                    if (!detect) {
                        detect = true
                        detectFingerprint(frame.toBitMap())
                        frame.release()
                    }
                    frame.release()
                }
            }
    }

    val img = mutableListOf<Bitmap>()

    /**
     * @dev analyze frame to search fingers in frame
     * is necessary 4 fingers
     * @param decode - frame image
     */
    private fun detectFingerprint(decode: Bitmap) {
        uiScope.launch {
            val result = NebuIA.task.fingerprintDetection(decode)
            val rectangles: MutableList<RectF> = mutableListOf()
            val scores: MutableList<Float> = mutableListOf()

            //order rectangles
            result.sortBy { it.y }

            result.forEach {
                rectangles.add(RectF(it.x, it.y, it.w, it.h))
            }

            img.clear()

            if (rectangles.size == 4) {
                for (it in result) {
                    val croppedBmp: Bitmap = Bitmap.createBitmap(
                        decode,
                        it.x.toInt(),
                        it.y.toInt(),
                        it.w.toInt(),
                        it.h.toInt()
                    )

                    val rotate = croppedBmp.rotate(if (NebuIA.positionHand == 0) -90.0f else 90.0f)!!
                    val quality = NebuIA.task.fingerprintQuality(rotate)
                    //Log.i("NEEEEBU", quality.toString())
                    if(quality >= NebuIA.qualityValue) {
                        scores.add(quality)
                    }
                    img.add(rotate)
                }
            } else {
                rectangles.clear()
            }

            val size = scores.size
            setProgressBar(size)

            if (size >= 3) {
                timer.cancel()
                processFingerprints()
            } else {
                detect = false
            }
        }
    }

    private fun processFingerprints() {
        val fingers: MutableList<Fingers> = mutableListOf()

        uiScope.launch {
            val transformed = NebuIA.task.processFingerprint(img)
            for (image in transformed) {
                fingers.add(
                    Fingers(
                        name = "fingerprint",
                        image = image,
                        score = 0
                    )
                )
            }

            NebuIA.task.fingers = fingers
            this@FingersDetector.finalize()
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
    private fun finalize() {
        val intent = Intent(this, FingerprintPreview::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        finish()
    }

    fun Bitmap.rotate(angle: Float): Bitmap? {
        val matrix = Matrix()
        matrix.postRotate(angle)
        return Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
    }

}
