package com.distbit.nebuia_plugin.model

import android.graphics.Bitmap

data class Documents(
    var frontCrop: Bitmap? = null,
    var backCrop: Bitmap? = null,
    var side: Side = Side.FRONT,
    var isPassport: Boolean = false
) {
    fun isComplete(): Boolean {
        return if (this.isPassport) {
            this.frontCrop != null
        } else {
            this.frontCrop != null && this.backCrop != null
        }
    }
}