package com.distbit.nebuia_plugin.activities

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.distbit.nebuia_plugin.NebuIA
import com.distbit.nebuia_plugin.R
import com.distbit.nebuia_plugin.model.Documents
import com.distbit.nebuia_plugin.model.Side
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment


class PreviewDocument : BottomSheetDialogFragment() {

    private lateinit var docType: TextView
    private lateinit var summary: TextView
    private lateinit var preview: ImageView
    private lateinit var retake: Button
    private lateinit var continueID: Button
    private lateinit var close: Button

    private var documents: Documents = Documents

    companion object {
        fun newInstance() = PreviewDocument()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view: View = inflater.inflate(R.layout.activity_preview_document, container, false)

        preview = view.findViewById(R.id.document_preview)
        docType = view.findViewById(R.id.doc_type)
        summary = view.findViewById(R.id.summary)
        retake = view.findViewById(R.id.retake)
        continueID = view.findViewById(R.id.continue_id)
        close = view.findViewById(R.id.back)

        close.setOnClickListener { dismiss() }

        setUpRetake()
        setUpContinue()

        setType()
        setFonts()
        return view
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext(), theme)
        dialog.setOnShowListener {
            val d = it as BottomSheetDialog

            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet) as? FrameLayout

            val behavior = BottomSheetBehavior.from(bottomSheet!!)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                    //if(!slideOffset.isNaN()) dialog.window?.setDimAmount(0.5f - ((slideOffset * -1)/2))
                }

                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    when (newState) {
                        BottomSheetBehavior.STATE_EXPANDED -> {
                            setImage()
                        }
                        BottomSheetBehavior.STATE_HIDDEN -> dismiss()
                        BottomSheetBehavior.STATE_COLLAPSED -> {

                        }
                        BottomSheetBehavior.STATE_DRAGGING -> {}
                        BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                            setImage()
                        }
                        BottomSheetBehavior.STATE_SETTLING -> {}
                    }
                }
            })
        }
        return dialog
    }

    /**
     * @dev apply fonts from NebuIA theme
     */
    private fun setFonts() {
        NebuIA.theme.applyBoldFont(docType)
        NebuIA.theme.applyNormalFont(summary)
        NebuIA.theme.applyNormalFont(continueID)
        NebuIA.theme.applyNormalFont(retake)
    }

    /**
     * @dev set up retake button listener
     */
    private fun setUpRetake() = retake.setOnClickListener {
        Documents.reset()
        dismiss()
    }

    /**
     * @dev set up continue button listener
     */
    private fun setUpContinue() = continueID.setOnClickListener {
        when {
            documents.isComplete() -> {
                dismiss()
                (activity as ScannerID).uploadData()
            }
            else -> {
                dismissAllowingStateLoss()
                documents.setSide(Side.BACK)
                (activity as ScannerID).fillData()
            }
        }
    }

    fun setImage() {
        preview.setImageBitmap(documents.getPreview())
    }

    /**
     * @dev set label for document type
     */
    private fun setType() {
        docType.text = documents.getLabel()
    }
}