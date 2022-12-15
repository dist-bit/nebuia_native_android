package com.distbit.nebuia_plugin.activities

import android.graphics.Bitmap
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
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
    private lateinit var actionsPanel: LinearLayout

    private lateinit var statusIcon: Button
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

        statusIcon = findViewById(R.id.icon_status)
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
            fingers[0].score = it
            indexFingerNFIQ.text = getString(R.string.nfiq, it)
            unlockActions(it)
        })
        getNFIQ(fingers[1].image, onScore = {
            fingers[1].score = it
            middleFingerNFIQ.text = getString(R.string.nfiq, it)
        })
        getNFIQ(fingers[2].image, onScore = {
            fingers[2].score = it
            ringFingerNFIQ.text = getString(R.string.nfiq, it)
        })
        getNFIQ(fingers[3].image, onScore = {
            fingers[3].score = it
            littleFingerNFIQ.text = getString(R.string.nfiq, it)
        })

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
        if (score < 40) {
            if(NebuIA.skipStep) {
                continueFinger.text = getString(R.string.skip_fingerprints)
                summary.text = resources.getString(R.string.skip_step_fingerprints)
            } else {
                continueFinger.text = getString(R.string.retry_fingerprints)
                summary.text = resources.getString(R.string.skip_fingerprints_summary)
            }
            // icon
            statusIcon.background = ResourcesCompat.getDrawable(resources, R.drawable.circle_red, null)
            statusIcon.setCompoundDrawablesWithIntrinsicBounds(R.drawable.close_icon, 0, 0, 0);

        } else {
            continueFinger.text = resources.getString(R.string.continue_button)
            summary.text = resources.getString(R.string.success_fingerprint_summary)
            //
            statusIcon.background = ResourcesCompat.getDrawable(resources, R.drawable.circle_green, null)
            statusIcon.setCompoundDrawablesWithIntrinsicBounds(R.drawable.material_check_icon, 0, 0, 0);
        }

        // if score is > 45 this is a correct quality
        if (score < 40) {
            continueFinger.setOnClickListener {
                this@FingerprintPreview.finish()
                NebuIA.fingerSkipWithImages(fingers[0], fingers[1], fingers[2], fingers[3])
            }

            // set button text
            if(NebuIA.skipStep) {
                continueFinger.text = getString(R.string.skip_step_fingerprint)
            } else {
                continueFinger.text = getString(R.string.retyr_step_fingerprint)
            }
        } else {
            continueFinger.setOnClickListener {
                this@FingerprintPreview.finish()
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
        NebuIA.theme.applyBoldFont(summary)
        NebuIA.theme.applyNormalFont(continueFinger)
        //NebuIA.theme.applyNormalFont(retake)
    }

    /**
     * @dev set colors to components
     */
    private fun setUpColors() {
        NebuIA.theme.setUpButtonPrimaryTheme(continueFinger, this)
        //NebuIA.theme.setUpButtonSecondaryTheme(retake, this)
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