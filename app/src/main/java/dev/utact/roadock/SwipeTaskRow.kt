package dev.utact.roadock

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import kotlin.math.abs

class SwipeTaskRow(context: Context) : FrameLayout(context) {
    val content = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(RoadockDesign.background)
    }
    val deleteButton = MaterialButton(context).apply {
        setText(R.string.delete)
        isAllCaps = false
        typeface = RoadockDesign.font(context, 500)
        setTextColor(RoadockDesign.error)
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, 14f)
        setSingleLine()
        setPadding(RoadockDesign.dp(context, 12), 0, RoadockDesign.dp(context, 12), 0)
        backgroundTintList = ColorStateList.valueOf(0xFF482426.toInt())
        insetTop = 0
        insetBottom = 0
        cornerRadius = RoadockDesign.dp(context, 8)
        visibility = View.INVISIBLE
    }
    var swipeEnabled = false
    var dragHandle: View? = null
    var onReveal: ((SwipeTaskRow) -> Unit)? = null
    var isRevealed = false
        private set
    private val actionWidth = RoadockDesign.dp(context, 88).toFloat()
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var startX = 0f
    private var swiping = false
    private var handleTouch = false

    init {
        addView(deleteButton, LayoutParams(RoadockDesign.dp(context, 76), RoadockDesign.dp(context, 48), Gravity.END or Gravity.CENTER_VERTICAL).apply {
            rightMargin = RoadockDesign.dp(context, 12)
        })
        addView(content, LayoutParams(-1, -1))
    }

    fun close(animate: Boolean = true) { settle(false, animate) }

    override fun performClick(): Boolean = super.performClick()

    private fun settle(reveal: Boolean, animate: Boolean = true) {
        isRevealed = reveal
        content.animate().cancel()
        val target = if (reveal) -actionWidth else 0f
        if (animate) content.animate().translationX(target).setDuration(160).withEndAction {
            deleteButton.visibility = if (reveal) View.VISIBLE else View.INVISIBLE
        }.start() else {
            content.translationX = target
            deleteButton.visibility = if (reveal) View.VISIBLE else View.INVISIBLE
        }
    }

    private fun startSwipe(event: MotionEvent): Boolean {
        val dx = event.x - downX
        val dy = event.y - downY
        if (!swipeEnabled || handleTouch || abs(dx) <= slop || abs(dx) <= abs(dy) || (startX == 0f && dx > 0f)) return false
        swiping = true
        content.animate().cancel()
        deleteButton.visibility = View.VISIBLE
        onReveal?.invoke(this)
        parent.requestDisallowInterceptTouchEvent(true)
        return true
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!swipeEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                startX = content.translationX
                swiping = false
                handleTouch = dragHandle?.let {
                    event.x >= it.left + startX && event.x <= it.right + startX && event.y >= it.top && event.y <= it.bottom
                } ?: false
            }
            MotionEvent.ACTION_MOVE -> if (swiping || startSwipe(event)) return true
            MotionEvent.ACTION_UP -> if (isRevealed && event.x < width - actionWidth) return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_MOVE && (swiping || startSwipe(event))) {
            content.translationX = (startX + event.x - downX).coerceIn(-actionWidth, 0f)
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            if (swiping) {
                swiping = false
                settle(event.actionMasked == MotionEvent.ACTION_UP && content.translationX <= -actionWidth / 2)
                parent.requestDisallowInterceptTouchEvent(false)
                return true
            }
            if (isRevealed && event.x < width - actionWidth) { close(); return true }
        }
        return super.onTouchEvent(event)
    }
}
