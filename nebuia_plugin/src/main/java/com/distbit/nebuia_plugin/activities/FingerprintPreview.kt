package com.distbit.nebuia_plugin.activities

import android.graphics.*
import android.opengl.Visibility
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.utils.progresshud.ProgressHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class FingerprintPreview : AppCompatActivity() {

    private lateinit var continueFinger: Button

    private lateinit var summary: TextView
    private lateinit var title: TextView
    private lateinit var titleFinger: TextView
    private lateinit var actionsPanel: LinearLayout

    private lateinit var statusIconCheck: ImageView
    private lateinit var statusIconError: ImageView

    private val fingers = NebuIA.task.fingers

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var svProgressHUD: ProgressHUD

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fingerprint_preview)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        statusIconCheck = findViewById(R.id.icon_status_check)
        statusIconError = findViewById(R.id.icon_status_error)

        actionsPanel = findViewById(R.id.actions)
        actionsPanel.visibility = View.INVISIBLE

        val indexFinger: ImageView = findViewById(R.id.index)
        val middleFinger: ImageView = findViewById(R.id.middle)
        val ringFinger: ImageView = findViewById(R.id.ring)
        val littleFinger: ImageView = findViewById(R.id.little)

        svProgressHUD = ProgressHUD(this)

        //retake = findViewById(R.id.retake_finger)
        continueFinger = findViewById(R.id.continue_fingers)

        summary = findViewById(R.id.summary)
        title = findViewById(R.id.title)
        titleFinger = findViewById(R.id.title_finger)

        setFonts()
        setUpColors()
        windowFeatures()
        //setUpRetake()

        indexFinger.setImageBitmap(fingers[0].image)
        middleFinger.setImageBitmap(fingers[1].image)
        ringFinger.setImageBitmap(fingers[2].image)
        littleFinger.setImageBitmap(fingers[3].image)

        setUpNFIQ()
    }

    private fun setUpNFIQ() {
        val indexFingerNFIQ: TextView = findViewById(R.id.index_nfiq)
        val middleFingerNFIQ: TextView = findViewById(R.id.middle_nfiq)
        val ringFingerNFIQ: TextView = findViewById(R.id.ring_nfiq)
        val littleFingerNFIQ: TextView = findViewById(R.id.little_nfiq)

        svProgressHUD.show()

        getNFIQ(fingers[0].image, onScore = {
            svProgressHUD.dismiss()
            setScoreOnNotNull(0, it)
            indexFingerNFIQ.text = getString(R.string.nfiq, it)
            runOnUiThread {
                unlockActions(it)
            }

        })
        getNFIQ(fingers[1].image, onScore = {
            setScoreOnNotNull(1, it)
            middleFingerNFIQ.text = getString(R.string.nfiq, it)
        })
        getNFIQ(fingers[2].image, onScore = {
            setScoreOnNotNull(2, it)
            ringFingerNFIQ.text = getString(R.string.nfiq, it)
        })
        getNFIQ(fingers[3].image, onScore = {
            setScoreOnNotNull(3, it)
            littleFingerNFIQ.text = getString(R.string.nfiq, it)
        })
    }

    private fun setScoreOnNotNull(position: Int, score: Int) {
        if(fingers.size - 1 >= position) {
            fingers[position].score = score
        }
    }

    private fun getNFIQ(image: Bitmap, onScore: (score: Int) -> Unit) {
        uiScope.launch {
            val response = NebuIA.task.getNFIQFingerprintImage(image, onError = {
                onScore(0)
            })

            if(response != null && response["status"] as Boolean) {
                val score = response["payload"] as Int
                onScore(score)
            } else {
                onScore(0)
            }
        }
    }

    private fun unlockActions(score: Int) {
        actionsPanel.visibility = View.VISIBLE

        val skipStep = NebuIA.skipStep
        val successSummary = resources.getString(R.string.success_fingerprint_summary)
        val skipSummary = resources.getString(R.string.skip_fingerprints_summary)
        val skipButton = resources.getString(R.string.skip_fingerprints)
        val retryButton = resources.getString(R.string.retry_fingerprints)
        val continueButton = resources.getString(R.string.continue_button)

        if (score < 40) {
            continueFinger.text = if (skipStep) skipButton else retryButton
            summary.text = skipSummary
            statusIconError.visibility = View.VISIBLE
        } else {
            continueFinger.text = continueButton
            summary.text = successSummary
            statusIconCheck.visibility = View.VISIBLE
        }

        continueFinger.setOnClickListener {
            this@FingerprintPreview.finish()
            if (score < 40) {
                NebuIA.fingerSkipWithImages(fingers[0], fingers[1], fingers[2], fingers[3])
            } else {
                NebuIA.fingerComplete(fingers[0], fingers[1], fingers[2], fingers[3])
            }
        }
    }


    private fun windowFeatures() {
        window.navigationBarColor = resources.getColor(R.color.white_overlay)
        window.statusBarColor = resources.getColor(R.color.white_overlay)
    }

    /**
     * @dev apply fonts from NebuIA theme
     */
    private fun setFonts() {
        NebuIA.theme.applyBoldFont(title)
        NebuIA.theme.applyBoldFont(titleFinger)
        NebuIA.theme.applyNormalFont(summary)
        NebuIA.theme.applyNormalFont(continueFinger)
    }

    /**
     * @dev set colors to components
     */
    private fun setUpColors() {
        NebuIA.theme.setUpButtonPrimaryTheme(continueFinger, this)
    }

    /**
     * @dev set up retake button listener
     */
    /*
    private fun setUpRetake() = retake.setOnClickListener {
        this.finish()
        val intent = Intent(this, FingersDetector::class.java)
        startActivity(intent)
    } */

    /**
     * @dev override back button key
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            true
        } else super.onKeyDown(keyCode, event)
    }
}