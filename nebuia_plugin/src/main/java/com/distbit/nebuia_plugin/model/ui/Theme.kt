package com.distbit.nebuia_plugin.model.ui

import android.content.Context
import android.graphics.Typeface
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import com.distbit.nebuia_plugin.R

data class Theme(
    // color configuration
    var primaryColor: Int = 0xff734FD9.toInt(),
    var secondaryColor: Int = 0xff0C1243.toInt(),
    // text color
    var primaryTextButtonColor: Int = 0xff27ae60.toInt(),
    var secondaryTextButtonColor: Int = 0xff0C1243.toInt(),
    var buttonDrawable: Int = R.drawable.button,
    // fonts
    var boldFont: Typeface? = null,
    var normalFont: Typeface? = null,
    var thinFont: Typeface? = null
) {
    fun applyBoldFont(textView: TextView) {
        textView.setTypeface(null, Typeface.BOLD);
        if (this.boldFont != null) textView.typeface = this.boldFont
    }

    fun applyNormalFont(textView: TextView) {
        if (this.normalFont != null) textView.typeface = this.normalFont
    }

    /**
     * @dev set colors to components
     */
    fun setUpButtonPrimaryTheme(view: Button, ctx: Context) {
        val drawable = getDrawable(ctx, this.buttonDrawable)
        drawable!!.setTint(this.primaryColor)
        view.background = drawable
        view.setTextColor(this.primaryTextButtonColor)
    }

    /**
     * @dev set colors to components
     */
    fun setUpButtonSecondaryTheme(view: Button, ctx: Context) {
        val drawable = getDrawable(ctx, this.buttonDrawable)
        drawable!!.setTint(this.secondaryColor)
        view.background = drawable
        view.setTextColor(this.secondaryTextButtonColor)
    }
}