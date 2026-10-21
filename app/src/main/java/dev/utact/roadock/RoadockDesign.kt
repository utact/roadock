package dev.utact.roadock

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.core.widget.TextViewCompat
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.button.MaterialButton

object RoadockDesign {
    val background = Color.rgb(16, 16, 16)
    val surface = Color.rgb(36, 36, 36)
    val text = Color.rgb(236, 236, 236)
    val secondary = Color.rgb(166, 166, 166)
    val outline = Color.rgb(112, 112, 112)
    val divider = Color.rgb(52, 52, 52)
    val error = Color.rgb(242, 184, 181)

    const val screenInset = 24
    const val touchTarget = 48
    const val listRow = 56
    const val icon = 20
    const val headline = 28
    const val body = 17
    const val supporting = 12

    fun font(context: Context, weight: Int = 400) = ResourcesCompat.getFont(context, when (weight) {
        600 -> R.font.pretendard_semibold
        500 -> R.font.pretendard_medium
        else -> R.font.pretendard_regular
    })

    fun fixedContext(base: Context): Context {
        val configuration = Configuration(base.resources.configuration).apply {
            fontScale = 1f
            densityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_YES
        }
        return base.createConfigurationContext(configuration)
    }

    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

    fun text(context: Context, value: String, size: Int = body, color: Int = text, weight: Int = 400) =
        TextView(context).apply {
            text = value
            styleText(this, size, color, weight)
        }

    fun styleText(view: TextView, size: Int = body, color: Int = text, weight: Int = 400) {
        view.apply {
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, size.toFloat())
            typeface = font(context, weight)
            letterSpacing = 0f
            includeFontPadding = false
            TextViewCompat.setLineHeight(this, dp(context, when (size) {
                headline -> 40
                body -> 24
                supporting -> 18
                else -> size + 6
            }))
        }
    }

    fun iconButton(context: Context, drawable: Int, description: String, tagValue: String, click: () -> Unit) =
        MaterialButton(context).apply {
            id = View.generateViewId()
            tag = tagValue
            contentDescription = description
            icon = AppCompatResources.getDrawable(context, drawable)
            iconSize = dp(context, RoadockDesign.icon)
            iconTint = ColorStateList.valueOf(RoadockDesign.text)
            iconPadding = 0
            iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
            setPadding(dp(context, 14), 0, dp(context, 14), 0)
            insetTop = 0
            insetBottom = 0
            minWidth = dp(context, touchTarget)
            minimumWidth = dp(context, touchTarget)
            minHeight = dp(context, touchTarget)
            backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
            cornerRadius = dp(context, 24)
            setOnClickListener { click() }
        }
}
