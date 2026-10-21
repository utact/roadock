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
        require(name.isNotBlank()) { "프로젝트 이름을 입력해 주세요" }
        require(owner.matches(Regex("[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?"))) {
            "저장소를 owner/repository 형식으로 입력해 주세요"
        }
        require(repository.matches(Regex("[A-Za-z0-9_.-]+")) && repository !in listOf(".", "..")) {
            "저장소 이름을 확인해 주세요"
        }
        require(branch.isNotBlank() && branch.none { it.isISOControl() }) { "브랜치를 입력해 주세요" }
        require(path.isNotBlank() && path.split('/').none { it.isEmpty() || it == "." || it == ".." } &&
            path.none { it.isISOControl() || it == '\\' }
        ) { "파일 경로는 저장소 기준 상대 경로로 입력해 주세요" }
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
