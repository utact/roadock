package dev.utact.roadock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GitHubWriteTest {
    private val project = Project("one", "Roadock", "utact", "roadock", "feature/human", "docs/TODO.md")
    private val markdown = "\uFEFF## Human\r\n- [ ] 키 설정 <!-- roadock:key -->\r\n## Agent\r\n- [ ] 구현\r\n"
    private val expected = MarkdownChecklist.parse(markdown).first()
    private class Connection(val status: Int, val body: String = "") : HttpURLConnection(URL("https://api.github.com")) {
        val sent = ByteArrayOutputStream()
        var closed = false
        override fun getResponseCode() = status
        override fun getInputStream() = ByteArrayInputStream(body.toByteArray(Charsets.UTF_8))
        override fun getOutputStream() = sent
        override fun connect() = Unit
        override fun disconnect() { closed = true }
        override fun usingProxy() = false
        fun payload() = JSONObject(sent.toString("UTF-8"))
    }
    private fun document(text: String = markdown, sha: String = "old-sha") = Connection(200,
        JSONObject().put("type", "file").put("encoding", "base64").put("sha", sha)
            .put("content", Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))).toString())
    private fun decoded(connection: Connection) = String(Base64.getDecoder().decode(connection.payload().getString("content")), Charsets.UTF_8)

    @Test fun commitsOnlyHumanStateOnTheConfiguredBranchWithAFixedMessage() {
        val get = document()
        val put = Connection(200)
        val pending = ArrayDeque(listOf(get, put))
        val urls = mutableListOf<String>()
        val result = GitHubClient { urls += it.toString(); pending.removeFirst() }.setHumanChecked(project, "test-token", expected, true)
        assertTrue(result.first().checked)
        assertFalse(result.last().checked)
        assertEquals(markdown.replace("[ ] 키", "[x] 키"), decoded(put))
        assertEquals("chore(todo): 사용자 작업 완료 상태 갱신", put.payload().getString("message"))
        assertEquals(project.branch, put.payload().getString("branch"))
        assertEquals("old-sha", put.payload().getString("sha"))
        assertEquals("PUT", put.requestMethod)
        assertEquals("Bearer test-token", put.getRequestProperty("Authorization"))
        assertFalse(put.instanceFollowRedirects)
        assertTrue(get.closed && put.closed)
        assertTrue(urls[0].endsWith("?ref=feature%2Fhuman"))
        assertEquals("https://api.github.com/repos/utact/roadock/contents/docs/TODO.md", urls[1])
    }

    @Test fun rebasesAfter409WithoutLosingOtherChanges() {
        val otherChanges = markdown.replace("## Human\r\n", "## Human\r\n- [x] 권한 승인 <!-- roadock:permission -->\r\n")
            .replace("[ ] 구현", "[x] 구현")
        val first = Connection(409)
        val second = Connection(200)
        val pending = ArrayDeque(listOf(document(), first, document(otherChanges, "new-sha"), second))
        GitHubClient { pending.removeFirst() }.setHumanChecked(project, "test-token", expected, true)
        assertEquals(otherChanges.replace("[ ] 키", "[x] 키"), decoded(second))
        assertEquals("new-sha", second.payload().getString("sha"))
        assertTrue(pending.isEmpty())
    }

    @Test fun doesNotWriteWhenTaskWasChangedDuplicatedDeletedOrReassigned() {
        listOf(markdown.replace("키 설정", "키 삭제"), markdown.replace("## Human", "## Agent"),
            markdown.replace("- [ ] 키 설정 <!-- roadock:key -->\r\n", ""),
            markdown + "- [ ] 중복 <!-- roadock:key -->\r\n").forEach { changed ->
            var calls = 0
            val conflict = assertThrows(HumanWriteConflict::class.java) {
                GitHubClient { calls++; document(changed) }.setHumanChecked(project, "test-token", expected, true)
            }
            assertEquals(1, calls)
            assertEquals(MarkdownChecklist.parse(changed), conflict.items)
        }
    }

    @Test fun treatsAlreadySavedStateAsSuccessWithoutAnotherCommit() {
        var calls = 0
        val client = GitHubClient { calls++; document(markdown.replace("[ ] 키", "[x] 키")) }
        assertTrue(client.setHumanChecked(project, "test-token", expected, true).first().checked)
        assertEquals(1, calls)
    }

    @Test fun unchecksOnlyTheRequestedHumanTask() {
        val checked = markdown.replace("[ ] 키", "[X] 키")
        val put = Connection(200)
        val pending = ArrayDeque(listOf(document(checked), put))
        val result = GitHubClient { pending.removeFirst() }.setHumanChecked(project, "test-token", expected.copy(checked = true), false)
        assertFalse(result.first().checked)
        assertEquals(markdown, decoded(put))
    }

    @Test fun rejectsUnsupportedOrMalformedContentBeforeAnyWrite() {
        val file = JSONObject(document().body)
        val invalidUtf8 = Base64.getEncoder().encodeToString(byteArrayOf(0xC3.toByte(), 0x28))
        listOf(JSONObject(file.toString()).put("content", invalidUtf8),
            JSONObject(file.toString()).put("encoding", "none"),
            JSONObject(file.toString()).put("type", "symlink"),
            JSONObject(file.toString()).put("sha", ""),
            JSONObject(file.toString()).put("content", "!invalid!")).forEach { json ->
            var calls = 0
            assertThrows(FetchException::class.java) {
                GitHubClient { calls++; Connection(200, json.toString()) }.setHumanChecked(project, "test-token", expected, true)
            }
            assertEquals(1, calls)
        }
    }

    @Test fun boundsConflictRetriesAndDoesNotRetryPermissionOrNetworkFailures() {
        var calls = 0
        assertThrows(HumanWriteConflict::class.java) {
            GitHubClient { calls++; if (calls % 2 == 1) document() else Connection(409) }
                .setHumanChecked(project, "test-token", expected, true)
        }
        assertEquals(6, calls)
        calls = 0
        val denied = assertThrows(FetchException::class.java) {
            GitHubClient { calls++; if (calls == 1) document() else Connection(422) }
                .setHumanChecked(project, "test-token", expected, true)
        }
        assertEquals(R.string.error_write_access, denied.text)
        assertEquals(2, calls)
        calls = 0
        assertThrows(IOException::class.java) {
            GitHubClient { calls++; if (calls == 1) document() else throw IOException("timeout") }
                .setHumanChecked(project, "test-token", expected, true)
        }
        assertEquals(2, calls)
    }

    @Test fun rejectsAgentMissingAuthenticationAndChangedProjectBeforeWriting() {
        val client = GitHubClient { error("No request allowed") }
        assertThrows(FetchException::class.java) { client.setHumanChecked(project, "", expected, true) }
        assertThrows(FetchException::class.java) { client.setHumanChecked(project, "test-token", expected.copy(owner = TaskOwner.AGENT), true) }
        assertThrows(FetchException::class.java) { client.setHumanChecked(project, "test-token", expected, true) { false } }
        var calls = 0
        assertThrows(FetchException::class.java) {
            GitHubClient { calls++; document() }.setHumanChecked(project, "test-token", expected, true) { calls == 0 }
        }
        assertEquals(1, calls)
    }
}
