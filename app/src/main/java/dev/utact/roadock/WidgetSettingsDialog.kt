package dev.utact.roadock

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider

object WidgetSettingsDialog {
    fun show(context: Context) {
        val store = RoadockStore(context)
        fun dp(value: Int) = RoadockDesign.dp(context, value)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        content.addView(RoadockDesign.text(context, context.getString(R.string.widget_project_layout), 14,
            RoadockDesign.secondary, 500).apply {
            tag = "widget-layout-label"
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(-1, dp(24)))
        content.addView(MaterialButtonToggleGroup(context).apply {
            tag = "widget-project-layout"
            isSingleSelection = true
            isSelectionRequired = true
            val singleId = View.generateViewId()
            val splitId = View.generateViewId()
            for ((buttonId, label) in listOf(singleId to R.string.layout_single, splitId to R.string.layout_split)) {
                addView(MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = buttonId
                    tag = if (buttonId == singleId) "widget-layout-single" else "widget-layout-split"
                    setText(label)
                    RoadockDesign.styleText(this, 14, RoadockDesign.text, 500)
                    isAllCaps = false
                    setSingleLine()
                    minWidth = 0
                    minimumWidth = 0
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(8)
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            check(if (store.widgetSplitProjects()) splitId else singleId)
            addOnButtonCheckedListener { _, buttonId, checked ->
                if (checked) {
                    store.setWidgetSplitProjects(buttonId == splitId)
                    RoadockWidget.updateAll(context)
                }
            }
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8); bottomMargin = dp(24) })
        fun control(label: Int, tagValue: String, initial: Int, from: Int, to: Int, step: Int,
            format: (Int) -> String = { it.toString() }, save: (Int) -> Unit) {
            val value = RoadockDesign.text(context, format(initial), 14).apply {
                tag = "$tagValue-value"
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }
            content.addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(RoadockDesign.text(context, context.getString(label), 14, RoadockDesign.secondary, 500).apply {
                    gravity = Gravity.CENTER_VERTICAL
                }, LinearLayout.LayoutParams(0, dp(24), 1f))
                addView(value, LinearLayout.LayoutParams(dp(48), dp(24)))
            }, LinearLayout.LayoutParams(-1, dp(24)))
            content.addView(Slider(context).apply {
                tag = tagValue
                valueFrom = from.toFloat()
                valueTo = to.toFloat()
                stepSize = step.toFloat()
                this.value = initial.toFloat()
                labelBehavior = LabelFormatter.LABEL_GONE
                contentDescription = context.getString(label)
                addOnChangeListener { _, selected, _ ->
                    save(selected.toInt())
                    value.text = format(selected.toInt())
                    RoadockWidget.updateAll(context)
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(16) })
        }
        control(R.string.widget_text_size, "widget-text-size", store.widgetTextSize(), 10, 16, 1, save = store::setWidgetTextSize)
        control(R.string.widget_transparency, "widget-transparency", store.widgetTransparency(), 0, 100, 5,
            format = { "$it%" }, save = store::setWidgetTransparency)
        content.addView(View(context).apply { setBackgroundColor(RoadockDesign.divider) },
            LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(8); bottomMargin = dp(8) })
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
        MaterialAlertDialogBuilder(context).setTitle(R.string.widget_settings)
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.close, null).show()
    }
}
