package com.distbit.nebuia_plugin.activities

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.graphics.*
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.otaliastudios.cameraview.CameraListener
import com.otaliastudios.cameraview.CameraView
import com.otaliastudios.cameraview.PictureResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers


class CaptureActivity : AppCompatActivity() {

    private lateinit var camera: CameraView
    private lateinit var summarySide: TextView
    private lateinit var title: TextView
    private lateinit var capture: Button

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_capturer)
        //window.hideSystemUI()
        window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window?.statusBarColor = this.getColor(android.R.color.transparent)
        window?.navigationBarColor = this.getColor(R.color.nebuia_bg)

        camera = findViewById(R.id.camera)

        val back: Button = findViewById(R.id.back)
        capture = findViewById(R.id.capture)

        back.setOnClickListener {
            super.onBackPressed()
        }

        setUpCamera(camera)
    }

    /**
     * @dev set up camera for frame processing
     * set image format and life cycle to activity
     * @param camera - CameraView instance
     */
    private fun setUpCamera(camera: CameraView) {
        camera.frameProcessingFormat = ImageFormat.FLEX_RGBA_8888
        camera.setLifecycleOwner(this)
        camera.startAutoFocus(camera.width / 2F, camera.height / 2F);

        camera.addCameraListener(object : CameraListener() {
            override fun onPictureTaken(result: PictureResult) {
                NebuIA.documentCapture(result.data)
                this@CaptureActivity.finish()
            }
        })

        capture.setOnClickListener{
            camera.takePictureSnapshot()
        }
    }
}