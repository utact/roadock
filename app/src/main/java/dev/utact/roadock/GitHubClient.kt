package dev.utact.roadock

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GitHubClient(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    fun fetch(project: Project, token: String): List<ChecklistItem> {
        val connection = openConnection(URL(project.contentsUrl()))
        try {
            connection.apply {
                connectTimeout = 8000
                readTimeout = 8000
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/vnd.github.raw+json")
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "ROADOCK/0.1")
                if (token.isNotBlank()) setRequestProperty("Authorization", "Bearer $token")
            }
            when (connection.responseCode) {
                200 -> Unit
                401 -> throw IOException("GitHub 토큰을 확인해 주세요")
                403, 429 -> throw IOException("접근 권한 또는 GitHub 요청 한도를 확인해 주세요")
                404 -> throw IOException("저장소·브랜치·파일 경로와 토큰 권한을 확인해 주세요")
                301, 302, 307, 308 -> throw IOException("저장소가 이동했습니다. 등록 정보를 수정해 주세요")
                else -> throw IOException("GitHub 조회에 실패했습니다 (${connection.responseCode})")
            }
            if (connection.contentType?.substringBefore(';') == "application/json") {
                throw IOException("폴더 대신 Markdown 파일 경로를 입력해 주세요")
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw IOException("새로고침이 취소되었습니다")
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (bytes.size() + count > 1_048_576) throw IOException("1MB 이하의 체크리스트를 사용해 주세요")
                    bytes.write(buffer, 0, count)
                }
            }
            return MarkdownChecklist.parse(bytes.toString("UTF-8").removePrefix("\uFEFF"))
        } finally {
            connection.disconnect()
        }
    }
}
