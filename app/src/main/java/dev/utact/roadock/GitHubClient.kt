package dev.utact.roadock

import java.io.ByteArrayOutputStream
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
                401 -> throw FetchException(R.string.error_token)
                403, 429 -> throw FetchException(R.string.error_access)
                404 -> throw FetchException(R.string.error_source)
                301, 302, 307, 308 -> throw FetchException(R.string.error_moved)
                else -> throw FetchException(R.string.error_http, connection.responseCode)
            }
            if (connection.contentType?.substringBefore(';') == "application/json") {
                throw FetchException(R.string.error_directory)
            }
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    if (Thread.currentThread().isInterrupted) throw FetchException(R.string.error_cancelled)
                    val count = input.read(buffer)
                    if (count == -1) break
                    if (bytes.size() + count > 1_048_576) throw FetchException(R.string.error_file_size)
                    bytes.write(buffer, 0, count)
                }
            }
            return MarkdownChecklist.parse(bytes.toString("UTF-8").removePrefix("\uFEFF"))
        } finally {
            connection.disconnect()
        }
    }
}
