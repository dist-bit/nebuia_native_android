package com.distbit.nebuia_plugin.activities

import android.annotation.SuppressLint
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
import com.distbit.nebuia_plugin.model.Documents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class UploadActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var loading: LinearLayout

    private lateinit var title: TextView

    private lateinit var errorDescription: TextView

    private lateinit var successUpload: LinearLayout
    private lateinit var continueSuccess: Button

    private lateinit var errorUpload: LinearLayout
    private lateinit var continueError: Button

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_upload)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val front: ImageView = findViewById(R.id.document_front_preview)
        val back: ImageView = findViewById(R.id.document_back_preview)

        loading = findViewById(R.id.loading)

        successUpload = findViewById(R.id.success_id)
        continueSuccess = findViewById(R.id.continue_success)

        errorUpload = findViewById(R.id.error_id)
        continueError = findViewById(R.id.continue_error)

        title = findViewById(R.id.doc_type)
        errorDescription = findViewById(R.id.error_description)

        continueSuccess.setOnClickListener {
            NebuIA.task.documents = Documents()
            NebuIA.idComplete()
            finish()
        }

        continueError.setOnClickListener {
            NebuIA.task.documents = Documents()
            ScannerID.reset()
            NebuIA.idError()
            finish()
        }

        if (NebuIA.task.documents.front != null)
            front.setImageBitmap(NebuIA.task.documents.front)

        if (NebuIA.task.documents.back != null)
            back.setImageBitmap(NebuIA.task.documents.back)

        setFonts()
        setUpColors()
        upload()
        windowFeatures()

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
    }

    /**
     * @dev set colors to components
     */
    private fun setUpColors() {
        NebuIA.theme.setUpButtonPrimaryTheme(continueSuccess, this)
        NebuIA.theme.setUpButtonPrimaryTheme(continueError, this)
        //NebuIA.theme.setUpButtonSecondaryTheme(retake, this)
    }

    /**
     * @dev upload id and call callback
     */
    private fun upload() {
        uiScope.launch {
            var error = false
            val response = NebuIA.task.uploadID(onError = {
                error = true
            })
            loading.visibility = View.INVISIBLE

            if(error) {
                errorUpload.visibility = View.VISIBLE
            }

            if(response != null) {
                when {
                    response["status"] as Boolean ->
                        successUpload.visibility = View.VISIBLE
                    else -> {
                        errorUpload.visibility = View.VISIBLE
                        errorDescription.text = "${getString(R.string.upload_id_error)}, description: ${response["payload"]}"
                    }
                }
            }
        }
    }

    /**
     * @dev override back button key
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) true
        else super.onKeyDown(keyCode, event)
    }
}