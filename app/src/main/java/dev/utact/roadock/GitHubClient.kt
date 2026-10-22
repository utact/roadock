package dev.utact.roadock

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64
import org.json.JSONObject

open class GitHubClient(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) {
    companion object {
        const val HUMAN_COMMIT_MESSAGE = "chore(todo): 사용자 작업 완료 상태 갱신"
    }

    private data class Document(val markdown: String, val sha: String)

    open fun setHumanChecked(project: Project, token: String, expected: ChecklistItem, checked: Boolean,
        sourceIsCurrent: () -> Boolean = { true }): List<ChecklistItem> {
        if (expected.owner != TaskOwner.HUMAN) throw FetchException(R.string.error_agent_read_only)
        if (token.isBlank()) throw FetchException(R.string.error_write_token)
        var latest = emptyList<ChecklistItem>()
        repeat(3) {
            if (!sourceIsCurrent()) throw FetchException(R.string.error_source_changed)
            val document = document(project, token)
            latest = MarkdownChecklist.parse(document.markdown)
            val updated = try { MarkdownChecklist.setHumanChecked(document.markdown, expected, checked) }
                catch (error: FetchException) { throw HumanWriteConflict(latest, error.text) }
            if (updated == document.markdown) return MarkdownChecklist.parse(updated)
            if (!sourceIsCurrent()) throw FetchException(R.string.error_source_changed)
            if (write(project, token, document.sha, updated)) return MarkdownChecklist.parse(updated)
        }
        throw HumanWriteConflict(latest, R.string.error_write_conflict)
    }

    private fun document(project: Project, token: String): Document {
        val connection = openConnection(URL(project.contentsUrl()))
        try {
            configure(connection, token, "application/vnd.github+json")
            checkReadStatus(connection.responseCode)
            val json = try { JSONObject(read(connection, 2_097_152).toString(Charsets.UTF_8)) }
                catch (error: FetchException) { throw error }
                catch (_: Exception) { throw FetchException(R.string.error_document) }
            if (json.optString("type") != "file" || json.has("target")) throw FetchException(R.string.error_directory)
            if (json.optString("encoding") != "base64" || json.optString("sha").isBlank()) throw FetchException(R.string.error_document)
            val bytes = try { Base64.getDecoder().decode(json.getString("content").replace("\n", "").replace("\r", "")) }
                catch (_: Exception) { throw FetchException(R.string.error_document) }
            if (bytes.size > 1_048_576) throw FetchException(R.string.error_file_size)
            val markdown = try {
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) { throw FetchException(R.string.error_document) }
            return Document(markdown, json.getString("sha"))
        } finally { connection.disconnect() }
    }

    private fun write(project: Project, token: String, sha: String, markdown: String): Boolean {
        val body = JSONObject().put("message", HUMAN_COMMIT_MESSAGE).put("branch", project.branch).put("sha", sha)
            .put("content", Base64.getEncoder().encodeToString(markdown.toByteArray(Charsets.UTF_8))).toString().toByteArray(Charsets.UTF_8)
        val connection = openConnection(URL(project.contentsUrl().substringBefore('?')))
        try {
            configure(connection, token, "application/vnd.github+json")
            connection.requestMethod = "PUT"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }
            return when (connection.responseCode) {
                200 -> true
                409 -> false
                401 -> throw FetchException(R.string.error_token)
                403, 404, 422 -> throw FetchException(R.string.error_write_access)
                else -> throw FetchException(R.string.error_http, connection.responseCode)
            }
        } finally { connection.disconnect() }
    }

    private fun configure(connection: HttpURLConnection, token: String, accept: String) {
        connection.connectTimeout = 8000
        connection.readTimeout = 8000
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.setRequestProperty("Accept", accept)
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "ROADOCK/0.2")
        if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
    }

    private fun checkReadStatus(status: Int) {
        when (status) {
            200 -> Unit
            401 -> throw FetchException(R.string.error_token)
            403, 429 -> throw FetchException(R.string.error_access)
            404 -> throw FetchException(R.string.error_source)
            301, 302, 307, 308 -> throw FetchException(R.string.error_moved)
            else -> throw FetchException(R.string.error_http, status)
        }
    }

    private fun read(connection: HttpURLConnection, limit: Int): ByteArray {
        val bytes = ByteArrayOutputStream()
        connection.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                if (Thread.currentThread().isInterrupted) throw FetchException(R.string.error_cancelled)
                val count = input.read(buffer)
                if (count == -1) break
                if (bytes.size() + count > limit) throw FetchException(R.string.error_file_size)
                bytes.write(buffer, 0, count)
            }
        }
        return bytes.toByteArray()
    }

    fun fetch(project: Project, token: String): List<ChecklistItem> {
        val connection = openConnection(URL(project.contentsUrl()))
        try {
            configure(connection, token, "application/vnd.github.raw+json")
            checkReadStatus(connection.responseCode)
            if (connection.contentType?.substringBefore(';') == "application/json") {
                throw FetchException(R.string.error_directory)
            }
            return MarkdownChecklist.parse(read(connection, 1_048_576).toString(Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }
}
