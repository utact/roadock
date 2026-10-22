package dev.utact.roadock

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator

class ChecklistPane(context: Context, listTag: String) : LinearLayout(context) {
    private fun dp(value: Int) = RoadockDesign.dp(context, value)
    val list = RecyclerView(context).apply {
        id = View.generateViewId()
        tag = listTag
        layoutManager = LinearLayoutManager(context)
        (itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        clipToPadding = false
        setPadding(0, 0, 0, dp(24))
    }
    private val fade = View(context).apply {
        tag = if (listTag == "checklist") "list-fade" else "$listTag-fade"
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(RoadockDesign.background and 0x00FFFFFF, RoadockDesign.background))
        alpha = 0f
    }
    val body = FrameLayout(context).apply {
        addView(list, FrameLayout.LayoutParams(-1, -1))
        addView(fade, FrameLayout.LayoutParams(-1, dp(28), Gravity.BOTTOM))
    }

    init {
        orientation = VERTICAL
        addView(body, LayoutParams(-1, 0, 1f))
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { updateFade() }
        })
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateFade() }
    }

    fun updateFade() {
        val remaining = list.computeVerticalScrollRange() - list.computeVerticalScrollExtent() - list.computeVerticalScrollOffset()
        fade.alpha = if (list.canScrollVertically(1)) (remaining.toFloat() / dp(28)).coerceIn(0f, 1f) else 0f
    }
}
