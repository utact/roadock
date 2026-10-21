package dev.utact.roadock

data class ChecklistItem(val text: String, val checked: Boolean, val depth: Int = 0)

object MarkdownChecklist {
    private val item = Regex("^(\\s*)(?:[-+*]|\\d+[.)])\\s+\\[([ xX])]\\s+(.+?)\\s*$")
    private val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")

    fun parse(markdown: String): List<ChecklistItem> {
        val result = mutableListOf<ChecklistItem>()
        var fenceChar: Char? = null
        var fenceLength = 0
        markdown.lineSequence().forEach { line ->
            val marker = fence.matchEntire(line)
            if (fenceChar != null) {
                if (marker != null && marker.groupValues[1][0] == fenceChar &&
                    marker.groupValues[1].length >= fenceLength && marker.groupValues[2].isBlank()
                ) fenceChar = null
            } else if (marker != null) {
                fenceChar = marker.groupValues[1][0]
                fenceLength = marker.groupValues[1].length
            } else {
                item.matchEntire(line)?.let {
                    result += ChecklistItem(
                        text = it.groupValues[3], checked = it.groupValues[2].equals("x", true),
                        depth = (it.groupValues[1].replace("\t", "    ").length / 2).coerceAtMost(4),
                    )
                }
            }
        }
        return result
    }
}
