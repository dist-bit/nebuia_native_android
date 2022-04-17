package com.distbit.nebuia_plugin.activities

import android.os.Bundle
import android.view.KeyEvent
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class FingerprintPreview : AppCompatActivity() {


    // private lateinit var retake: Button
    private lateinit var continueFinger: Button

    private lateinit var summary: TextView
    private lateinit var title: TextView

    private lateinit var statusIcon: Button

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_fingerprint_preview)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        statusIcon = findViewById(R.id.icon_status)

        val indexFinger: ImageView = findViewById(R.id.index)
        val middleFinger: ImageView = findViewById(R.id.middle)
        val ringFinger: ImageView = findViewById(R.id.ring)
        val littleFinger: ImageView = findViewById(R.id.little)

        val indexFingerNFIQ: TextView = findViewById(R.id.index_nfiq)
        val middleFingerNFIQ: TextView = findViewById(R.id.middle_nfiq)
        val ringFingerNFIQ: TextView = findViewById(R.id.ring_nfiq)
        val littleFingerNFIQ: TextView = findViewById(R.id.little_nfiq)

        //retake = findViewById(R.id.retake_finger)
        continueFinger = findViewById(R.id.continue_fingers)

        summary = findViewById(R.id.summary)
        title = findViewById(R.id.title)

        setFonts()
        setUpColors()
        windowFeatures()
        //setUpRetake()

        val fingers = NebuIA.task.fingers

        indexFinger.setImageBitmap(fingers[0].image)
        middleFinger.setImageBitmap(fingers[1].image)
        ringFinger.setImageBitmap(fingers[2].image)
        littleFinger.setImageBitmap(fingers[3].image)

        indexFingerNFIQ.text = "NFIQ ${fingers[0].score}"
        middleFingerNFIQ.text = "NFIQ ${fingers[1].score}"
        ringFingerNFIQ.text = "NFIQ ${fingers[2].score}"
        littleFingerNFIQ.text = "NFIQ ${fingers[3].score}"

        if (fingers[0].score < 45) {
            if(NebuIA.skipStep) {
                continueFinger.text = getString(R.string.skip_fingerprints)
                summary.text = resources.getString(R.string.skip_step_fingerprints)
            } else {
                continueFinger.text = getString(R.string.retry_fingerprints)
                summary.text = resources.getString(R.string.skip_fingerprints_summary)
            }
            // icon
            statusIcon.background = resources.getDrawable(R.drawable.circle_red)
            statusIcon.setCompoundDrawablesWithIntrinsicBounds(R.drawable.close_icon, 0, 0, 0);

        } else {
            continueFinger.text = resources.getString(R.string.continue_button)
            summary.text = resources.getString(R.string.success_fingerprint_summary)
            //
            statusIcon.background = resources.getDrawable(R.drawable.circle_green)
            statusIcon.setCompoundDrawablesWithIntrinsicBounds(R.drawable.material_check_icon, 0, 0, 0);
        }

        // if score is > 45 this is a correct quality
        if (fingers[0].score < 45) {
            continueFinger.setOnClickListener {
                this@FingerprintPreview.finish()
                NebuIA.fingerSkipWithImages(fingers[0], fingers[1], fingers[2], fingers[3])
            }

            // set button text
            if(NebuIA.skipStep) {
                continueFinger.text = "Saltar paso"
            } else {
                continueFinger.text = "Reintentar"
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