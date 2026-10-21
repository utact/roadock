package dev.utact.roadock

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class GitHubClientTest {
    private val project = Project("one", "Roadock", "utact", "roadock", "main", "TODO.md")

    private class Connection(private val status: Int, body: String = "- [ ] 할 일", private val type: String = "text/plain") :
        HttpURLConnection(URL("https://api.github.com")) {
        private val data = body.toByteArray()
        var disconnected = false
        override fun getResponseCode() = status
        override fun getContentType() = type
        override fun getInputStream() = ByteArrayInputStream(data)
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
    }

    @Test fun encodesBranchAndEachPathSegment() {
        val input = project.copy(branch = "feature/widget?x=1", path = "진행 상황/TODO #1.md")
        assertEquals("https://api.github.com/repos/utact/roadock/contents/%EC%A7%84%ED%96%89%20%EC%83%81%ED%99%A9/TODO%20%231.md?ref=feature%2Fwidget%3Fx%3D1", input.contentsUrl())
    }

    @Test fun rejectsInvalidSourceFields() {
        listOf(project.copy(owner = "bad/owner"), project.copy(repository = ".."),
            project.copy(branch = ""), project.copy(path = "/TODO.md"), project.copy(path = "../TODO.md"),
            project.copy(path = "docs//TODO.md"), project.copy(path = "docs\\TODO.md"),
        ).forEach { assertThrows(IllegalArgumentException::class.java) { it.validate() } }
    }

    @Test fun publicFetchDoesNotSendAuthorization() {
        val connection = Connection(200, "\uFEFF- [x] 완료")
        assertEquals(listOf(ChecklistItem("완료", true)), GitHubClient { connection }.fetch(project, ""))
        assertNull(connection.getRequestProperty("Authorization"))
        assertEquals("application/vnd.github.raw+json", connection.getRequestProperty("Accept"))
        assertFalse(connection.instanceFollowRedirects)
        assertTrue(connection.disconnected)
    }

    @Test fun privateFetchUsesHeaderInsteadOfUrl() {
        val connection = Connection(200)
        GitHubClient {
            assertFalse(it.toString().contains("test-token"))
            connection
        }.fetch(project, "test-token")
        assertEquals("Bearer test-token", connection.getRequestProperty("Authorization"))
    }

    @Test fun rejectsFailuresDirectoriesAndOversizedFiles() {
        for (status in listOf(301, 401, 403, 404, 429, 500)) {
            val connection = Connection(status)
            assertThrows(IOException::class.java) { GitHubClient { connection }.fetch(project, "") }
            assertTrue(connection.disconnected)
        }
        assertThrows(IOException::class.java) { GitHubClient { Connection(200, "[]", "application/json") }.fetch(project, "") }
        assertThrows(IOException::class.java) { GitHubClient { Connection(200, "x".repeat(1_048_577)) }.fetch(project, "") }
    }
}
