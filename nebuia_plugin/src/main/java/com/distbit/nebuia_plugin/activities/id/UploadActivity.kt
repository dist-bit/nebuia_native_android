package com.distbit.nebuia_plugin.activities.id

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
import com.distbit.nebuia_plugin.model.Side
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class UploadActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var loading: LinearLayout

    private lateinit var titleSummaryOk: TextView
    private lateinit var titleSummaryError: TextView
    private lateinit var errorDescription: TextView

    private lateinit var successUpload: LinearLayout
    private lateinit var continueSuccess: Button

    private lateinit var errorUpload: LinearLayout
    private lateinit var continueError: Button

    private var documents: Documents = Documents

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_upload)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        //val front: ImageView = findViewById(R.id.document_front_preview)
        //val back: ImageView = findViewById(R.id.document_back_preview)

        loading = findViewById(R.id.loading)

        successUpload = findViewById(R.id.success_id)
        continueSuccess = findViewById(R.id.continue_success)

        errorUpload = findViewById(R.id.error_id)
        continueError = findViewById(R.id.continue_error)

        titleSummaryOk = findViewById(R.id.title_summary_ok)
        titleSummaryError = findViewById(R.id.title_summary_error)
        errorDescription = findViewById(R.id.error_description)

        continueSuccess.setOnClickListener {
            documents.setSide(Side.FRONT)
            documents.reset()
            NebuIA.idComplete()
            finish()
        }

        continueError.setOnClickListener {
            documents.reset()
            NebuIA.idError()
            finish()
        }

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
        NebuIA.theme.applyBoldFont(titleSummaryOk)
        NebuIA.theme.applyBoldFont(titleSummaryError)
    }

    /**
     * @dev set colors to components
     */
    private fun setUpColors() {
        NebuIA.theme.setUpButtonPrimaryTheme(continueSuccess, this)
        NebuIA.theme.setUpButtonPrimaryTheme(continueError, this)
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
                        errorDescription.text = "${getString(R.string.upload_id_error)}, descripción: ${response["payload"]}"
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