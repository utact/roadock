package dev.utact.roadock

import android.content.Context

data class DependencyStatus(val waiting: List<String> = emptyList(), val unresolved: List<String> = emptyList()) {
    fun description(context: Context): String = buildList {
        if (waiting.isNotEmpty()) add(context.getString(R.string.waiting_for, waiting.joinToString(", ")))
        if (unresolved.isNotEmpty()) add(context.getString(R.string.dependency_unresolved))
    }.joinToString(" · ")
}

class TaskDependencies(items: List<ChecklistItem>) {
    private val byKey = items.filter { it.key != null }.groupBy { it.key }

    fun status(item: ChecklistItem): DependencyStatus {
        if (item.owner != TaskOwner.AGENT || item.checked) return DependencyStatus()
        val waiting = mutableListOf<String>()
        val unresolved = mutableListOf<String>()
        item.requires.distinct().forEach { key ->
            val target = byKey[key]?.singleOrNull()
            if (target == null || target.owner != TaskOwner.HUMAN) unresolved += key
            else if (!target.checked) waiting += target.text
        }
        return DependencyStatus(waiting, unresolved)
    }
}
