package dev.utact.roadock

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class RoadockStore(context: Context) {
    private val preferences = context.getSharedPreferences("roadock", Context.MODE_PRIVATE)
    private val vault = CredentialVault(context)

    fun projects(): List<Project> = synchronized(lock) {
        val array = JSONArray(preferences.getString("projects", "[]"))
        (0 until array.length()).map {
            array.getJSONObject(it).run {
                Project(getString("id"), getString("name"), getString("owner"),
                    getString("repository"), getString("branch"), getString("path"))
            }
        }
    }

    fun saveProject(project: Project, token: String? = null) = synchronized(lock) {
        project.validate()
        if (token != null) vault.write(project.id, token)
        val projects = projects().toMutableList()
        val index = projects.indexOfFirst { it.id == project.id }
        val previous = projects.getOrNull(index)
        if (index < 0) projects.add(project) else projects[index] = project
        val array = JSONArray()
        projects.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("owner", it.owner)
                .put("repository", it.repository).put("branch", it.branch).put("path", it.path))
        }
        val edit = preferences.edit().putString("projects", array.toString())
        if (previous?.copy(name = project.name) != project) edit.remove("cache-${project.id}")
        edit.apply()
    }

    fun deleteProject(id: String) = synchronized(lock) {
        val remaining = projects().filterNot { it.id == id }
        val array = JSONArray()
        remaining.forEach {
            array.put(JSONObject().put("id", it.id).put("name", it.name).put("owner", it.owner)
                .put("repository", it.repository).put("branch", it.branch).put("path", it.path))
        }
        vault.write(id, "")
        preferences.edit().putString("projects", array.toString()).remove("cache-$id").apply()
    }

    fun hasToken(id: String): Boolean = synchronized(lock) { vault.contains(id) }
    fun token(id: String): String = synchronized(lock) { vault.read(id) }

    fun cache(id: String): ChecklistCache = synchronized(lock) {
        val value = preferences.getString("cache-$id", null) ?: return@synchronized ChecklistCache()
        val json = JSONObject(value)
        val array = json.getJSONArray("items")
        ChecklistCache(
            items = (0 until array.length()).map {
                array.getJSONObject(it).run {
                    ChecklistItem(getString("text"), getBoolean("checked"), optInt("depth"),
                        if (optString("owner") == "HUMAN") TaskOwner.HUMAN else TaskOwner.AGENT,
                        optString("key").takeIf { value -> value.isNotEmpty() },
                        optJSONArray("requires")?.let { keys ->
                            (0 until keys.length()).map { index -> keys.getString(index) }
                        }.orEmpty(), optString("details"))
                }
            },
            fetchedAt = json.getLong("fetchedAt"),
            error = json.optString("error").takeIf { it.isNotEmpty() },
        )
    }

    fun saveCache(project: Project, cache: ChecklistCache): Boolean = synchronized(lock) {
        if (projects().find { it.id == project.id } != project) return@synchronized false
        val items = JSONArray()
        cache.items.forEach {
            items.put(JSONObject().put("text", it.text).put("checked", it.checked).put("depth", it.depth)
                .put("owner", it.owner.name).put("requires", JSONArray(it.requires)).put("details", it.details)
                .apply { it.key?.let { key -> put("key", key) } })
        }
        val json = JSONObject().put("items", items).put("fetchedAt", cache.fetchedAt)
        cache.error?.let { json.put("error", it) }
        preferences.edit().putString("cache-${project.id}", json.toString()).apply()
        true
    }

    fun tasks(): List<PersonalTask> = synchronized(lock) {
        val array = JSONArray(preferences.getString("tasks", "[]"))
        (0 until array.length()).map {
            array.getJSONObject(it).run { PersonalTask(getString("id"), getString("text"), getBoolean("checked")) }
        }
    }

    fun addTask(text: String) = synchronized(lock) {
        requireInput(text.isNotBlank(), R.string.error_task_empty)
        requireInput(text.trim().length <= TASK_LENGTH && !text.trim().contains('\n'), R.string.error_task_length, TASK_LENGTH)
        saveTasks(tasks() + PersonalTask(text = text.trim()))
    }

    fun toggleTask(id: String) = synchronized(lock) {
        saveTasks(tasks().map { if (it.id == id) it.copy(checked = !it.checked) else it })
    }

    fun deleteTask(id: String) = synchronized(lock) { saveTasks(tasks().filterNot { it.id == id }) }

    fun reorderTasks(ids: List<String>) = synchronized(lock) {
        val current = tasks()
        val byId = current.associateBy { it.id }
        val ordered = ids.distinct().mapNotNull { byId[it] }
        saveTasks(ordered + current.filterNot { it.id in ids })
    }

    private fun saveTasks(tasks: List<PersonalTask>) {
        val array = JSONArray()
        tasks.forEach { array.put(JSONObject().put("id", it.id).put("text", it.text).put("checked", it.checked)) }
        preferences.edit().putString("tasks", array.toString()).apply()
    }

    fun widgetTextSize(): Int = preferences.getInt("widget-text-size", 12).coerceIn(10, 16)
    // Retain the existing key so installed widgets keep their layout.
    fun widgetSplitProjects(): Boolean = preferences.getBoolean("split-projects", false)
    fun setWidgetSplitProjects(split: Boolean) { preferences.edit().putBoolean("split-projects", split).apply() }
    fun projectFilter(): TaskOwner? = TaskOwner.entries.find { it.name == preferences.getString("project-filter", null) }
    fun setProjectFilter(owner: TaskOwner?) { preferences.edit().putString("project-filter", owner?.name ?: "ALL").apply() }
    fun setWidgetTextSize(size: Int) { preferences.edit().putInt("widget-text-size", size.coerceIn(10, 16)).apply() }
    fun widgetTransparency(): Int = preferences.getInt("widget-transparency", 15).coerceIn(0, 100)
    fun setWidgetTransparency(value: Int) { preferences.edit().putInt("widget-transparency", value.coerceIn(0, 100)).apply() }

    fun pageIds(): List<String> = projects().map { it.id } + PERSONAL

    fun selectedPage(): String = resolvePage(preferences.getString("selected", null))
    fun selectPage(id: String) { preferences.edit().putString("selected", resolvePage(id)).apply() }
    fun widgetPage(widgetId: Int): String = resolvePage(preferences.getString("widget-$widgetId", null))
    fun selectWidgetPage(widgetId: Int, id: String) {
        preferences.edit().putString("widget-$widgetId", resolvePage(id)).apply()
    }
    fun deleteWidget(widgetId: Int) { preferences.edit().remove("widget-$widgetId").apply() }
    private fun resolvePage(id: String?): String = pageIds().let { if (id in it) id!! else it.first() }

    companion object {
        const val PERSONAL = "personal"
        const val TASK_LENGTH = 24
        private val lock = Any()
    }
}
