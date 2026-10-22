package dev.utact.roadock

enum class TaskOwner { HUMAN, AGENT }

data class ChecklistItem(
    val text: String, val checked: Boolean, val depth: Int = 0,
    val owner: TaskOwner = TaskOwner.AGENT, val key: String? = null,
)

data class SourceTask(val item: ChecklistItem, val checkboxOffset: Int)

object MarkdownChecklist {
    private val item = Regex("^(\\s*)(?:[-+*]|\\d+[.)])\\s+\\[([ xX])]\\s+(.+?)\\s*$")
    private val fence = Regex("^ {0,3}(`{3,}|~{3,})(.*)$")
    private val heading = Regex("^ {0,3}(#{1,6})\\s+(.+?)(?:\\s+#+)?\\s*$")
    private val identity = Regex("\\s*<!--\\s*roadock:([a-z][a-z0-9-]{0,63})\\s*-->$")

    fun parse(markdown: String): List<ChecklistItem> = scan(markdown).map { it.item }

    fun scan(markdown: String): List<SourceTask> {
        val result = mutableListOf<SourceTask>()
        val owners = mutableListOf<Pair<Int, TaskOwner>>()
        var fenceChar: Char? = null
        var fenceLength = 0
        Regex("([^\\r\\n]*)(\\r\\n|\\r|\\n|$)").findAll(markdown).forEach { source ->
            val raw = source.groupValues[1]
            val line = if (source.range.first == 0) raw.removePrefix("\uFEFF") else raw
            val marker = fence.matchEntire(line)
            if (fenceChar != null) {
                if (marker != null && marker.groupValues[1][0] == fenceChar &&
                    marker.groupValues[1].length >= fenceLength && marker.groupValues[2].isBlank()
                ) fenceChar = null
            } else if (marker != null) {
                fenceChar = marker.groupValues[1][0]
                fenceLength = marker.groupValues[1].length
            } else {
                heading.matchEntire(line)?.let { title ->
                    val level = title.groupValues[1].length
                    owners.removeAll { it.first >= level }
                    val owner = when (title.groupValues[2].lowercase()) {
                        "human" -> TaskOwner.HUMAN
                        "agent" -> TaskOwner.AGENT
                        else -> null
                    }
                    if (owner != null) owners += level to owner
                }
                item.matchEntire(line)?.let {
                    val key = identity.find(it.groupValues[3])
                    val text = if (key == null) it.groupValues[3] else it.groupValues[3].substring(0, key.range.first).trimEnd()
                    if (text.isEmpty()) return@let
                    result += SourceTask(ChecklistItem(
                        text = text, checked = it.groupValues[2].equals("x", true),
                        depth = (it.groupValues[1].replace("\t", "    ").length / 2).coerceAtMost(4),
                        owner = owners.lastOrNull()?.second ?: TaskOwner.AGENT,
                        key = key?.groupValues?.get(1),
                    ), source.range.first + raw.length - line.length + it.groups[2]!!.range.first)
                }
            }
        }
        return result
    }

    fun setHumanChecked(markdown: String, expected: ChecklistItem, checked: Boolean): String {
        if (expected.owner != TaskOwner.HUMAN) throw FetchException(R.string.error_agent_read_only)
        val matches = scan(markdown).filter {
            if (expected.key != null) it.item.key == expected.key
            else it.item.key == null && it.item.owner == TaskOwner.HUMAN && it.item.text == expected.text
        }
        val target = matches.singleOrNull() ?: throw FetchException(R.string.error_task_conflict)
        if (target.item.owner != TaskOwner.HUMAN || target.item.text != expected.text)
            throw FetchException(R.string.error_task_conflict)
        if (target.item.checked == checked) return markdown
        return markdown.replaceRange(target.checkboxOffset, target.checkboxOffset + 1, if (checked) "x" else " ")
    }
}
