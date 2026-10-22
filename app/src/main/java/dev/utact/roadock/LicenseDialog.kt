package dev.utact.roadock

import android.content.Context
import android.widget.ScrollView
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object LicenseDialog {
    private val headings = Regex("(?m)^(PREAMBLE|DEFINITIONS|PERMISSION & CONDITIONS|TERMINATION|DISCLAIMER)[ \t]*$")

    internal fun paragraphs(license: String): String = license.replace("\r\n", "\n").replace('\r', '\n')
        .replace(Regex("(?m)^-{3,}[ \t]*$"), "")
        .replace(headings) { "\n\n${it.groupValues[1]}\n\n" }
        .trim().split(Regex("\n[ \t]*\n+"))
        .joinToString("\n\n") { it.lines().joinToString(" ") { line -> line.trim() } }

    // Layout constants support API 26–28; newer SDK annotations name the equivalent LineBreaker constants.
    @android.annotation.SuppressLint("WrongConstant")
    fun show(context: Context) {
        val license = context.resources.openRawResource(R.raw.pretendard_license).bufferedReader().use { it.readText() }
        val content = RoadockDesign.text(context, "", 12, RoadockDesign.secondary).apply {
            text = android.text.SpannableString(paragraphs(license)).apply {
                headings.findAll(this).forEach { heading ->
                    setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), heading.range.first,
                        heading.range.last + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
            tag = "font-license-text"
            justificationMode = android.text.Layout.JUSTIFICATION_MODE_INTER_WORD
            breakStrategy = android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY
            setTextIsSelectable(true)
            val inset = RoadockDesign.dp(context, 24)
            setPadding(inset, RoadockDesign.dp(context, 8), inset, RoadockDesign.dp(context, 8))
        }
        MaterialAlertDialogBuilder(context).setTitle("Pretendard · OFL 1.1")
            .setView(ScrollView(context).apply { addView(content) })
            .setPositiveButton(R.string.close, null).show()
    }
}
