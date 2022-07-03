package com.distbit.nebuia_plugin.utils.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.ViewGroup


class Rect : ViewGroup {
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

    public override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {}
    override fun shouldDelayChildPressedState(): Boolean {
        return false
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)

        val metrics = resources.displayMetrics
        val viewportMargin = if(metrics.densityDpi <= 320) {
            65
        } else {
            130
        }

        val viewportCornerRadius = 8
        val eraser = Paint()
        eraser.isAntiAlias = true
        eraser.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)


        val scale: Float = resources.displayMetrics.density

        val marginBottom = if(metrics.densityDpi <= 320) {
            275 * scale
        } else 290 * scale

        val width = width.toFloat() - viewportMargin
        val height = height - marginBottom


        val marginTop = if(metrics.densityDpi <= 320) {
            50 * scale
        } else 40 * scale

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
        stroke.strokeWidth = 2F
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

        val paint = Paint(Paint.DITHER_FLAG)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 10F
        paint.color = Color.parseColor("#9775f5")

        canvas.drawPath(createCornersPath(frame.left, frame.top, frame.right, frame.bottom, 80), paint)

    }

    private fun createCornersPath(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        cornerWidth: Int
    ): Path {
        val path = Path()
        path.moveTo(left, (top + cornerWidth))
        path.lineTo(left, top)
        path.lineTo((left + cornerWidth), top)
        path.moveTo((right - cornerWidth), top)
        path.lineTo(right, top)
        path.lineTo(right, (top + cornerWidth))
        path.moveTo(left, (bottom - cornerWidth))
        path.lineTo(left, bottom)
        path.lineTo((left + cornerWidth), bottom)
        path.moveTo((right - cornerWidth), bottom)
        path.lineTo(right, bottom)
        path.lineTo(right, (bottom - cornerWidth))
        return path
    }

}