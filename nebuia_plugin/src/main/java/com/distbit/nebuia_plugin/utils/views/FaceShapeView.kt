package com.distbit.nebuia_plugin.utils.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.ViewGroup

class FaceShapeView : ViewGroup {
    private val ovalRect = RectF()

    private val eraserPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    )

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}

    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        ovalRect.setEmpty()

        val width = width.toFloat()
        val height = height.toFloat()

        val centerX = width / 2f
        val centerY = height / 2.45f

        val ovalWidth = width * 0.75f
        val ovalHeight = height * 0.53f

        ovalRect.set(
            centerX - ovalWidth / 2,
            centerY - ovalHeight / 2,
            centerX + ovalWidth / 2,
            centerY + ovalHeight / 2
        )

       // canvas.drawRoundRect(ovalRect, viewportCornerRadius.toFloat(), viewportCornerRadius.toFloat(), strokePaint)
        canvas.drawOval(ovalRect, eraserPaint)


    }
}
