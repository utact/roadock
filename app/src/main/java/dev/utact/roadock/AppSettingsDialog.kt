package dev.utact.roadock

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

object AppSettingsDialog {
    fun show(context: Context) {
        val store = RoadockStore(context)
        fun dp(value: Int) = RoadockDesign.dp(context, value)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        content.addView(RoadockDesign.text(context, context.getString(R.string.widget), 12, RoadockDesign.secondary))
        val sizeValue = RoadockDesign.text(context, store.widgetTextSize().toString(), 14, RoadockDesign.secondary).apply {
            tag = "widget-text-size-value"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(RoadockDesign.text(context, context.getString(R.string.text_size)).apply {
                gravity = Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, dp(40), 1f))
            addView(sizeValue, LinearLayout.LayoutParams(dp(40), dp(40)))
        }, LinearLayout.LayoutParams(-1, dp(40)))
        val preview = LinearLayout(context).apply {
            tag = "widget-text-preview"
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setBackgroundResource(R.drawable.widget_background)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        fun renderPreview() {
            preview.removeAllViews()
            listOf(
                ChecklistItem(context.getString(R.string.widget_sample_short), false),
                ChecklistItem(context.getString(R.string.widget_sample_done), true),
                ChecklistItem(context.getString(R.string.widget_sample_long), false),
            ).forEach { preview.addView(RoadockWidget.rowView(context, it).apply(context.applicationContext, preview)) }
        }
        renderPreview()
        content.addView(Slider(context).apply {
            tag = "widget-text-size"
            valueFrom = 10f
            valueTo = 16f
            stepSize = 1f
            this.value = store.widgetTextSize().toFloat()
            labelBehavior = LabelFormatter.LABEL_GONE
            contentDescription = context.getString(R.string.widget_text_size)
            addOnChangeListener { _, size, _ ->
                store.setWidgetTextSize(size.toInt())
                sizeValue.text = size.toInt().toString()
                renderPreview()
                RoadockWidget.updateAll(context)
            }
        }, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(RoadockDesign.text(context, context.getString(R.string.preview), 12, RoadockDesign.secondary),
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16); bottomMargin = dp(8) })
        content.addView(preview, LinearLayout.LayoutParams(-1, -2))
        content.addView(View(context).apply { setBackgroundColor(RoadockDesign.divider) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(24); bottomMargin = dp(8) })
        @Suppress("DEPRECATION")
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        content.addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(RoadockDesign.text(context, "Roadock $version", 12, RoadockDesign.secondary),
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(RoadockDesign.text(context, context.getString(R.string.font_license), 12, RoadockDesign.secondary).apply {
                tag = "font-license"
                gravity = Gravity.CENTER_VERTICAL
                paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
                minimumHeight = dp(48)
                setPadding(dp(8), 0, 0, 0)
                val ripple = TypedValue()
                if (context.theme.resolveAttribute(android.R.attr.selectableItemBackground, ripple, true))
                    setBackgroundResource(ripple.resourceId)
                setOnClickListener { LicenseDialog.show(context) }
            }, LinearLayout.LayoutParams(-2, dp(48)))
        }, LinearLayout.LayoutParams(-1, dp(48)))
        MaterialAlertDialogBuilder(context).setTitle(R.string.settings)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.close, null).show()
    }
}
