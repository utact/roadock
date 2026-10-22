package dev.utact.roadock

import java.net.URLEncoder
import java.util.UUID

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val owner: String,
    val repository: String,
    val branch: String,
    val path: String,
) {
    fun validate() {
        requireInput(name.isNotBlank(), R.string.error_project_name)
        requireInput(owner.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?")), R.string.error_repository_format)
        requireInput(repository.matches(Regex("[A-Za-z0-9_.-]+")) && repository !in listOf(".", ".."), R.string.error_repository_name)
        requireInput(branch.isNotBlank() && branch.none { it.isISOControl() }, R.string.error_branch)
        requireInput(path.isNotBlank() && path.split('/').none { it.isEmpty() || it == "." || it == ".." } &&
            path.none { it.isISOControl() || it == '\\' }, R.string.error_path)
    }

    fun contentsUrl(): String {
        validate()
        fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        return "https://api.github.com/repos/${encode(owner)}/${encode(repository)}/contents/" +
            path.split('/').joinToString("/") { encode(it) } + "?ref=${encode(branch)}"
    }
}

data class PersonalTask(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val checked: Boolean = false,
)

data class ChecklistCache(
    val items: List<ChecklistItem> = emptyList(),
    val fetchedAt: Long = 0,
    val error: String? = null,
)
