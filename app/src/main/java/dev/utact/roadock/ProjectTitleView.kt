package dev.utact.roadock

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.appcompat.widget.AppCompatTextView
import kotlin.math.PI
import kotlin.math.sin

class ProjectTitleView(context: Context) : AppCompatTextView(context) {
    private val water = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(169, 185, 194)
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
    }
    private val surface = Path()
    private val inkBounds = Rect()
    private var fraction = 0f
    private var phase = 0f
    private var animator: ValueAnimator? = null

    init { RoadockDesign.styleText(this, RoadockDesign.headline, weight = 600) }

    fun setProgress(completed: Int, total: Int) {
        fraction = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else 0f
        contentDescription = if (total > 0) context.getString(R.string.title_progress, text, completed, total) else text
        updateAnimation()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (fraction == 0f) { super.onDraw(canvas); return }
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        super.onDraw(canvas)
        paint.getTextBounds(text.toString(), 0, text.length, inkBounds)
        val top = baseline + inkBounds.top
        val bottom = baseline + inkBounds.bottom
        val level = bottom - (bottom - top) * fraction
        val amplitude = if (fraction == 1f) 0f else resources.displayMetrics.density * 1.2f
        val wavelength = resources.displayMetrics.density * 72f
        surface.reset()
        surface.moveTo(0f, height.toFloat())
        for (x in 0..width step 2) {
            val y = level + amplitude * sin(x / wavelength * 2 * PI + phase).toFloat()
            surface.lineTo(x.toFloat(), y)
        }
        surface.lineTo(width.toFloat(), level)
        surface.lineTo(width.toFloat(), height.toFloat())
        surface.close()
        val textClip = canvas.save()
        canvas.clipRect(compoundPaddingLeft.toFloat(), 0f, (width - compoundPaddingRight).toFloat(), height.toFloat())
        canvas.drawPath(surface, water)
        canvas.restoreToCount(textClip)
        canvas.restoreToCount(layer)
    }

    private fun updateAnimation() {
        val shouldAnimate = isAttachedToWindow && isShown && hasWindowFocus() &&
            fraction > 0f && fraction < 1f && ValueAnimator.areAnimatorsEnabled()
        if (!shouldAnimate) {
            animator?.cancel()
            animator = null
        } else if (animator == null) {
            animator = ValueAnimator.ofFloat(0f, (PI * 2).toFloat()).apply {
                duration = 7000
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener { phase = it.animatedValue as Float; invalidate() }
                start()
            }
        }
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateAnimation() }
    override fun onDetachedFromWindow() { animator?.cancel(); animator = null; super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); updateAnimation() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateAnimation() }
    override fun onVisibilityChanged(changedView: View, visibility: Int) { super.onVisibilityChanged(changedView, visibility); updateAnimation() }
}
