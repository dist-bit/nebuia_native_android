package com.distbit.nebuia_plugin.activities

import android.content.DialogInterface
import android.content.Intent
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.model.Documents
import com.distbit.nebuia_plugin.model.Side
import com.distbit.nebuia_plugin.utils.SpanFormatter
import com.distbit.nebuia_plugin.utils.Utils.Companion.correctOrientation
import com.distbit.nebuia_plugin.utils.progresshud.ProgressHUD
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File


class IDCaptureActivity : AppCompatActivity() {

    private lateinit var title: TextView
    private lateinit var summary: TextView
    private lateinit var capture: Button
    private lateinit var back: Button
    private lateinit var icon: ImageView

    private lateinit var mxIDFront: String
    private lateinit var mxIDBack: String
    private lateinit var mxPassportFront: String
    private var documents: Documents = Documents

    private lateinit var frontIcon: Bitmap
    private lateinit var backIcon: Bitmap

    /**
     * @dev onCreate default android life cycle
     * init listeners for camera frames
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window?.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window?.statusBarColor = this.getColor(android.R.color.transparent)
        window?.navigationBarColor = this.getColor(android.R.color.transparent)

        setContentView(R.layout.activity_scanner)

        title = findViewById(R.id.instruction_id)
        summary = findViewById(R.id.subtitle_id)
        capture = findViewById(R.id.capture)
        icon = findViewById(R.id.id_icon)
        back = findViewById(R.id.back)

        frontIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_id_front)
        backIcon = BitmapFactory.decodeResource(resources, R.drawable.ic_id_back)

        setFonts()
        fillData()
        setUpColors()

        IDCaptureActivity.act = this

        capture.setOnClickListener {
            openDialog()
        }

        back.setOnClickListener {
            this.finish()
        }
    }

    /**
     * @dev set colors to components
     */
    private fun setUpColors() {
        NebuIA.theme.setUpButtonPrimaryTheme(capture, this)
    }

    private fun openDialog() {
        idFragment = IDFragment.newInstance()
        idFragment.show(supportFragmentManager, idFragment.tag)
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
        NebuIA.theme.applyNormalFont(summary)
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

        icon.setImageBitmap(
            if (documents.side() == Side.FRONT) {
                frontIcon
            } else {
                backIcon
            }
        )

       // spanned2.setSpan(BackgroundColorSpan(Color.parseColor("#1a8ed0")), 0, spanned2.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanned2.setSpan(StyleSpan(Typeface.BOLD), 0, spanned2.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        summary.text = SpanFormatter.format( spanned1, spanned2)
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

    companion object {
        private lateinit var act: IDCaptureActivity
        lateinit var idFragment: IDFragment

        fun close() = act.onBackPressed()

        /**
         * @dev preview id document
         */
        fun finalize() = PreviewDocument.newInstance()
            .show(act.supportFragmentManager, PreviewDocument::class.java.canonicalName)

        fun hideDialog() = idFragment.dismiss()
    }
}

class IDFragment : BottomSheetDialogFragment() {

    private val uiScope = CoroutineScope(Dispatchers.Main)
    private lateinit var svProgressHUD: ProgressHUD
    private lateinit var tmpFile: File
    private var latestTmpUri: Uri? = null

    companion object {
        fun newInstance(): IDFragment {
            return IDFragment()
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
    }

    private var resultCamera =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                latestTmpUri?.let { uri ->
                    IDCaptureActivity.hideDialog()
                    val correctOrientation = MediaStore.Images.Media.getBitmap(
                        requireContext().contentResolver,
                        uri
                    ).correctOrientation(tmpFile.absolutePath)

                    uiScope.launch {
                        svProgressHUD.show()
                        val cropped = NebuIA.task.documentRealTimeDetection(correctOrientation)
                        svProgressHUD.dismiss()

                        if (cropped != null) {
                            IDCaptureActivity.finalize()
                        }
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.menu_capture_camera, container, false)
        val camera = view.findViewById<LinearLayout>(R.id.camera)
        svProgressHUD = ProgressHUD(activity)
        camera.setOnClickListener { pickCameraFile() }
        return view
    }

    private fun pickCameraFile() = getTmpFileUri().let { uri ->
        latestTmpUri = uri
        resultCamera.launch(uri)
    }

    private fun getTmpFileUri(): Uri {
        tmpFile =
            File.createTempFile(
                getString(R.string.tmp_image_file),
                getString(R.string.jpeg),
                requireActivity().cacheDir
            ).apply {
                createNewFile()
                deleteOnExit()
            }

        return FileProvider.getUriForFile(
            this.requireContext(),
            getString(R.string.com_distbit_nebuia_plugin),
            tmpFile
        )
    }
}