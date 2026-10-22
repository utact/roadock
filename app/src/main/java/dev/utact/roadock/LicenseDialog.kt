package dev.utact.roadock

import android.content.Context
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object LicenseDialog {
    fun show(context: Context) {
        val license = context.resources.openRawResource(R.raw.pretendard_license).bufferedReader().use { it.readText() }
        val content = RoadockDesign.text(context,
            license.replace(Regex("(?m)^-{3,}\\r?$"), ""),
            12, RoadockDesign.secondary).apply {
            tag = "font-license-text"
            setTextIsSelectable(true)
            val inset = RoadockDesign.dp(context, 24)
            setPadding(inset, 0, inset, inset)
        }
        MaterialAlertDialogBuilder(context).setTitle("Pretendard · OFL 1.1")
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.close, null).show()
    }
}
