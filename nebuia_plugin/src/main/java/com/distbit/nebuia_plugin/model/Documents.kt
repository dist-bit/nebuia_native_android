package com.distbit.nebuia_plugin.model

import android.graphics.Bitmap

data class Documents(
    var front: Bitmap? = null,
    var back: Bitmap? = null,
    var frontCrop: Bitmap? = null,
    var backCrop: Bitmap? = null,
    var side: Side = Side.FRONT,
    var isPassport: Boolean = false
) {
    fun isComplete(): Boolean {
        return if (this.isPassport) {
            this.front != null
        } else {
            this.front != null && this.back != null
        }
    }
}