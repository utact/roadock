package dev.utact.roadock

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.widget.ListPopupWindow

data class RoadockMenuItem(val label: String, val enabled: Boolean = true, val checked: Boolean = false,
    val separated: Boolean = false, val destructive: Boolean = false, val action: () -> Unit) {
    override fun toString() = label
}

object RoadockMenu {
    fun show(context: Context, anchor: View, items: List<RoadockMenuItem>, gravity: Int = Gravity.START,
        onDismiss: () -> Unit = {}): ListPopupWindow {
        fun dp(value: Int) = RoadockDesign.dp(context, value)
        val selection = items.any { it.checked }
        val paint = RoadockDesign.text(context, "", 16).paint
        val width = kotlin.math.ceil((items.maxOfOrNull { paint.measureText(it.label) } ?: 0f).toDouble()).toInt() + dp(if (selection) 60 else 32)
        return ListPopupWindow(context).apply {
            anchorView = anchor
            setDropDownGravity(gravity)
            isModal = true
            this.width = width.coerceIn(dp(112), context.resources.displayMetrics.widthPixels - dp(32))
            setBackgroundDrawable(GradientDrawable().apply {
                setColor(RoadockDesign.surface)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), RoadockDesign.divider)
            })
            setAdapter(object : ArrayAdapter<RoadockMenuItem>(context, 0, items) {
                override fun areAllItemsEnabled() = items.all { it.enabled }
                override fun isEnabled(position: Int) = items[position].enabled
                override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                    val item = items[position]
                    val row = LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        this.gravity = Gravity.CENTER_VERTICAL
                        minimumHeight = dp(48)
                        setPadding(dp(16), 0, dp(16), 0)
                        isSelected = item.checked
                        alpha = if (item.enabled) 1f else 0.45f
                        addView(RoadockDesign.text(context, item.label, 16,
                            if (item.destructive) RoadockDesign.error else RoadockDesign.text).apply {
                            setSingleLine()
                            ellipsize = android.text.TextUtils.TruncateAt.END
                        },
                            LinearLayout.LayoutParams(0, -2, 1f))
                        if (selection) addView(ImageView(context).apply {
                            setImageResource(R.drawable.ic_check)
                            visibility = if (item.checked) View.VISIBLE else View.INVISIBLE
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                        }, LinearLayout.LayoutParams(dp(16), dp(16)).apply { leftMargin = dp(12) })
                    }
                    return LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        if (item.separated) addView(View(context).apply { setBackgroundColor(RoadockDesign.divider) },
                            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(8) })
                        addView(row, LinearLayout.LayoutParams(-1, -2))
                    }
                }
            })
            setOnItemClickListener { _, _, position, _ -> dismiss(); items[position].action() }
            setOnDismissListener { onDismiss() }
            show()
        }
    }
}
