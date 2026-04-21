package com.distbit.nebuia_plugin.utils.views

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.distbit.nebuia_plugin.model.LivenessColors
import kotlin.math.abs

/**
 * Full-screen overlay with an oval cutout, progress arc border,
 * pulse animation during positioning, and glow during approach.
 */
class FaceOvalOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val overlayColor = 0x99000000.toInt()

    var borderColor: Int = LivenessColors.GRAY
        set(value) {
            if (field != value) {
                field = value
                postInvalidateOnAnimation()
            }
        }

    var progress: Float = 0f
        set(value) {
            val newVal = value.coerceIn(0f, 100f)
            field = newVal
            if (newVal > 0f) stopPulse() else startPulse()
            postInvalidateOnAnimation()
        }

    var scale: Float = 1.0f
        set(value) {
            field = value.coerceIn(0.5f, 2.0f)
            postInvalidateOnAnimation()
        }

    // Base oval proportions
    private val baseOvalWidthFraction = 0.75f
    private val baseOvalHeightFraction = 0.53f
    private val ovalCenterYDivisor = 2.45f

    private val dp = resources.displayMetrics.density
    private val borderWidth = 5f * dp
    private val glowRadius = 10f * dp

    // Paints
    private val overlayPaint = Paint().apply { color = overlayColor }
    private val borderBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        color = 0x40FFFFFF
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = borderWidth
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    // Path-based cutout (avoids saveLayer + PorterDuff.CLEAR)
    private val cutoutPath = Path()
    private val ovalRect = RectF()

    // Pulse animation (positioning phase)
    private var pulseAnimator: ValueAnimator? = null
    private var pulseFraction = 0f

    // Color transition
    private var colorAnimator: ValueAnimator? = null

    // Smooth scale/progress animators
    private var scaleAnimator: ValueAnimator? = null
    private var progressAnimator: ValueAnimator? = null

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // Required for BlurMaskFilter glow
        startPulse()
    }

    private fun startPulse() {
        if (pulseAnimator?.isRunning == true) return
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2200 // slow breathing
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                pulseFraction = it.animatedValue as Float
                if (progress == 0f) postInvalidateOnAnimation()
            }
            start()
        }
    }

    private fun stopPulse() {
        pulseAnimator?.cancel()
        pulseFraction = 1f
    }

    fun setBorderColorAnimated(targetColor: Int, durationMs: Long = 400) {
        colorAnimator?.cancel()
        colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), borderColor, targetColor).apply {
            duration = durationMs
            addUpdateListener { borderColor = it.animatedValue as Int }
            start()
        }
    }

    /**
     * Smoothly animate scale to target value.
     * Uses DecelerateInterpolator for natural deceleration.
     */
    fun animateScaleTo(target: Float, durationMs: Long = 180) {
        val clamped = target.coerceIn(0.5f, 2.0f)
        if (abs(scale - clamped) < 0.002f) return
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(scale, clamped).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { scale = it.animatedValue as Float }
            start()
        }
    }

    /**
     * Smoothly animate progress arc fill.
     * Uses DecelerateInterpolator for smooth arc growth.
     */
    fun animateProgressTo(target: Float, durationMs: Long = 180) {
        val clamped = target.coerceIn(0f, 100f)
        if (abs(progress - clamped) < 0.1f) return
        progressAnimator?.cancel()
        progressAnimator = ValueAnimator.ofFloat(progress, clamped).apply {
            duration = durationMs
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener { progress = it.animatedValue as Float }
            start()
        }
    }

    /** Cancel scale and progress animations (call on reset). */
    fun cancelAnimations() {
        scaleAnimator?.cancel()
        progressAnimator?.cancel()
    }

    fun isPointInOval(x: Float, y: Float, margin: Float = 1.0f): Boolean {
        if (width == 0 || height == 0) return true
        val cx = 0.5f
        val cy = 1f / ovalCenterYDivisor
        val semiAxisX = baseOvalWidthFraction * scale / 2f * margin
        val semiAxisY = baseOvalHeightFraction * scale / 2f * margin
        val dx = (x - cx) / semiAxisX
        val dy = (y - cy) / semiAxisY
        return dx * dx + dy * dy <= 1.0f
    }

    private fun computeOvalRect(w: Float, h: Float) {
        val ovalWidth = w * baseOvalWidthFraction * scale
        val ovalHeight = h * baseOvalHeightFraction * scale
        val cx = w / 2f
        val cy = h / ovalCenterYDivisor
        ovalRect.set(cx - ovalWidth / 2f, cy - ovalHeight / 2f, cx + ovalWidth / 2f, cy + ovalHeight / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        computeOvalRect(w, h)

        // 1. Overlay with oval cutout using Path EVEN_ODD (no saveLayer needed)
        cutoutPath.reset()
        cutoutPath.addRect(0f, 0f, w, h, Path.Direction.CW)
        cutoutPath.addOval(ovalRect, Path.Direction.CCW)
        cutoutPath.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(cutoutPath, overlayPaint)

        // 2. Background ring (faint white)
        canvas.drawOval(ovalRect, borderBgPaint)

        // 3. Border with effects
        if (progress > 0f) {
            val sweepAngle = 360f * (progress / 100f)

            // Glow behind the progress arc
            glowPaint.color = (borderColor and 0x00FFFFFF) or 0x40000000
            glowPaint.strokeWidth = borderWidth + glowRadius
            glowPaint.maskFilter = BlurMaskFilter(glowRadius, BlurMaskFilter.Blur.NORMAL)
            canvas.drawArc(ovalRect, -90f, sweepAngle, false, glowPaint)

            // Solid progress arc
            borderPaint.color = borderColor
            borderPaint.strokeWidth = borderWidth
            borderPaint.alpha = 255
            borderPaint.maskFilter = null
            canvas.drawArc(ovalRect, -90f, sweepAngle, false, borderPaint)
        } else {
            // Positioning: pulsing border (breathing effect)
            borderPaint.color = borderColor
            borderPaint.strokeWidth = borderWidth * (0.85f + 0.15f * pulseFraction)
            borderPaint.alpha = (255 * (0.5f + 0.5f * pulseFraction)).toInt()
            borderPaint.maskFilter = null
            canvas.drawOval(ovalRect, borderPaint)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
        colorAnimator?.cancel()
        scaleAnimator?.cancel()
        progressAnimator?.cancel()
    }
}
