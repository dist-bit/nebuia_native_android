package com.distbit.nebuia_plugin.activities.address

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.ImageView
import com.distbit.nebuia_plugin.R
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
import com.distbit.nebuia_plugin.NebuIA
import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import android.graphics.Matrix
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import com.distbit.nebuia_plugin.utils.progresshud.ProgressHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AddressPreviewActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(Dispatchers.Main)

    private lateinit var preview: ImageView
    private lateinit var bitmap: Bitmap

    private lateinit var close: Button
    private lateinit var retake: Button
    private lateinit var upload: ImageView

    private lateinit var svProgressHUD: ProgressHUD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_address_preview)

        windowFeatures()

        preview = findViewById(R.id.preview)
        upload = findViewById(R.id.continue_upload)
        retake = findViewById(R.id.retake)
        close = findViewById(R.id.back)

        close.setOnClickListener { this@AddressPreviewActivity.finish() }
        upload.setOnClickListener { uploadAddress() }
        retake.setOnClickListener {
            this.finish()
        }

        svProgressHUD = ProgressHUD(this)

        if (NebuIA.task.addressBitmap != null)
            parseBitmap()
        else parsePDF()
    }

    private fun parsePDF() {
        val file: ParcelFileDescriptor =
            ParcelFileDescriptor.open(NebuIA.task.addressFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(file)

        val page: PdfRenderer.Page = renderer.openPage(0)
        bitmap = Bitmap.createBitmap(
            page.width, page.height,
            Bitmap.Config.ARGB_8888
        )
        page.render(bitmap, null, null, RENDER_MODE_FOR_DISPLAY)
        page.close()
        renderer.close()
        preview.setImageBitmap(bitmap)
    }

    private fun windowFeatures() {
        window.navigationBarColor = getColor(R.color.nebuia_bg)
        window.statusBarColor = getColor(R.color.nebuia_bg)
    }

    private fun parseBitmap() {
        preview.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        preview.setImageBitmap(NebuIA.task.addressBitmap)
        preview.viewTreeObserver.addOnGlobalLayoutListener { layoutImageView(NebuIA.task.addressBitmap!!) }
    }

    private fun layoutImageView(bitmap: Bitmap) {
        val matrix = FloatArray(9)
        preview.imageMatrix.getValues(matrix)
        val w = (matrix[Matrix.MSCALE_X] * bitmap.width).toInt()
        val h = (matrix[Matrix.MSCALE_Y] * bitmap.height).toInt()
        preview.maxHeight = h
        preview.maxWidth = w
    }

    private fun uploadAddress() {
        svProgressHUD.show()
        uiScope.launch {
            val result = NebuIA.task.uploadAddress(onError = {
                NebuIA.addressCapture(null)
            })

            if(result != null) {
                NebuIA.addressCapture(result)
            }

            svProgressHUD.dismiss()
            NebuIA.task.addressBitmap = null
            NebuIA.task.addressFile = null
            //AddressFragment.closeInstance()
            AddressActivity.close()
            this@AddressPreviewActivity.finish()

        }
    }

    /**
     * @dev override back button key
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return if (keyCode == KeyEvent.KEYCODE_BACK) {
            true
        } else super.onKeyDown(keyCode, event)
    }
}