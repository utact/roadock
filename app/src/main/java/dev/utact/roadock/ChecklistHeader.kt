package dev.utact.roadock

import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

class ChecklistHeader(context: Context) : LinearLayout(context) {
    private val title = ProjectTitleView(context).apply {
        tag = "page-title"
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
        minimumWidth = RoadockDesign.dp(context, 48)
        compoundDrawablePadding = RoadockDesign.dp(context, 8)
        ViewCompat.setAccessibilityHeading(this, true)
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                if (host.isClickable) info.className = "android.widget.Button"
            }
        })
    }
    private val supporting = RoadockDesign.text(context, "", RoadockDesign.supporting, RoadockDesign.secondary).apply {
        tag = "page-metadata"
        gravity = Gravity.CENTER_VERTICAL
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.END
    }

    init {
        orientation = VERTICAL
        fun dp(value: Int) = RoadockDesign.dp(context, value)
        setPadding(dp(24), dp(16), dp(24), dp(8))
        addView(FrameLayout(context).apply {
            addView(title, FrameLayout.LayoutParams(-2, -1))
        }, LayoutParams(-1, dp(48)))
        addView(supporting, LayoutParams(-1, dp(20)))
    }

    fun bind(name: String, detail: String, completed: Int, total: Int, project: Boolean,
        loading: Boolean, error: String?, select: (View) -> Unit) {
        title.text = name
        title.setProgress(completed, total)
        title.setOnClickListener(if (project) View.OnClickListener { select(it) } else null)
        title.isClickable = project
        title.isFocusable = project
        val chevron = if (project) AppCompatResources.getDrawable(context, R.drawable.ic_expand)?.apply {
            val size = RoadockDesign.dp(context, 16)
            setBounds(0, 0, size, size)
            setTint(RoadockDesign.secondary)
        } else null
        title.setCompoundDrawablesRelative(null, null, chevron, null)
        if (project) title.contentDescription = context.getString(R.string.switch_project, title.contentDescription)
        val background = TypedValue()
        title.background = if (project && context.theme.resolveAttribute(android.R.attr.selectableItemBackground, background, true))
            AppCompatResources.getDrawable(context, background.resourceId) else null
        supporting.text = when {
            loading -> context.getString(R.string.refreshing)
            error != null -> error
            else -> detail
        }
        supporting.setTextColor(if (!loading && error != null) RoadockDesign.error else RoadockDesign.secondary)
    }
}
