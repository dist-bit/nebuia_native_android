package com.distbit.nebuia_plugin.activities.address

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.utils.Utils.Companion.correctOrientation
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.io.File
import java.io.FileOutputStream

class AddressActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showAddressFragment()
    }

    private fun showAddressFragment() {
        val addressFragment = AddressFragment.newInstance()
        addressFragment.show(supportFragmentManager, addressFragment.tag)
    }

    companion object {
        lateinit var addressActivity: AddressActivity

        fun close() {
            addressActivity.onBackPressed()
        }
    }
}

class AddressFragment : BottomSheetDialogFragment() {

    private var latestTmpUri: Uri? = null
    private lateinit var tmpFile: File

    private val resultDocument =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                val uri = data?.data ?: return@registerForActivityResult

                val input = requireActivity().contentResolver.openInputStream(uri)

                val file = File(requireActivity().cacheDir, "cacheFileAppeal.pdf")
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (input?.read(buffer).also { read = it ?: -1 } != -1) {
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                }

                NebuIA.task.addressFile = file
                NebuIA.task.addressBitmap = null
                finalize()
            }
        }

    private val resultCamera =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                latestTmpUri?.let { uri ->
                    val bitmap: Bitmap =
                        MediaStore.Images.Media.getBitmap(
                            requireContext().contentResolver,
                            uri
                        )
                    NebuIA.task.addressFile = null
                    NebuIA.task.addressBitmap = bitmap.correctOrientation(tmpFile.absolutePath)
                    finalize()
                }
            }
        }

    companion object {
        fun newInstance(): AddressFragment {
            return AddressFragment()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.activity_address, container, false)

        val camera = view.findViewById<LinearLayout>(R.id.camera)
        val document = view.findViewById<LinearLayout>(R.id.document)

        camera.setOnClickListener {
            pickCameraFile()
        }

        document.setOnClickListener {
            pickPDFFile()
        }

        return view
    }

    override fun onCancel(dialog: DialogInterface) {
        AddressActivity.close()
        super.onCancel(dialog)
    }

    private fun pickPDFFile() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.type = "application/pdf"
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        resultDocument.launch(intent)
    }

    private fun pickCameraFile() {
        getTmpFileUri().let { uri ->
            latestTmpUri = uri
            resultCamera.launch(uri)
        }
    }

    private fun getTmpFileUri(): Uri {
        tmpFile =
            File.createTempFile("tmp_image_file", ".jpeg", requireActivity().cacheDir).apply {
                createNewFile()
                deleteOnExit()
            }

        return FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.nebuia_plugin",
            tmpFile
        )
    }

    private fun finalize() {
        val intent = Intent(requireActivity(), AddressPreviewActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
    }
}