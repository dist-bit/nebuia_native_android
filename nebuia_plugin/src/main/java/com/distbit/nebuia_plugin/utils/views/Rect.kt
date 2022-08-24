package com.distbit.nebuia_plugin.utils.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.ViewGroup


class Rect : ViewGroup {
    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle
    ) {
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(widthMeasureSpec, heightMeasureSpec)
    }

    public override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        val metrics = resources.displayMetrics
        val density = metrics.densityDpi

        Log.i("VALUE", density.toString())

        val viewportMargin = if(density <= 320) {
            45
        } else 80

        val viewportCornerRadius = 8
        val eraser = Paint()
        eraser.isAntiAlias = true
        eraser.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)

        val width = width.toFloat() - viewportMargin
        val height = width * 0.65.toFloat()


        val scale: Float = resources.displayMetrics.density

        val marginTop = if(density <= 240) {
            250 * scale
        } else if(density in 241..320) {
            155 * scale
        } else 180 * scale


        val rect = RectF(
            viewportMargin.toFloat(),
            viewportMargin.toFloat() + marginTop,
            width,
            height + marginTop
        )

        val frame =
            RectF(
                viewportMargin.toFloat() - 2,
                viewportMargin.toFloat() - 2 + marginTop,
                width + 4,
                height + 4 + marginTop
            )

        val path = Path()
        val stroke = Paint()
        stroke.isAntiAlias = true
        stroke.strokeWidth = 4F
        stroke.color = Color.WHITE
        stroke.style = Paint.Style.STROKE

        path.addRoundRect(
            frame,
            viewportCornerRadius.toFloat(), viewportCornerRadius.toFloat(), Path.Direction.CW
        )

        canvas.drawPath(path, stroke)
        canvas.drawRoundRect(
            rect,
            viewportCornerRadius.toFloat(), viewportCornerRadius.toFloat(), eraser
        )
    }
}