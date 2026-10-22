package dev.utact.roadock

import org.junit.Assert.*
import org.junit.Test

class ChecklistTest {
    @Test fun parsesCompletedNestedAndOrderedItems() {
        val markdown = "# Tasks\r\n- [ ] 첫 작업\r\n  - [x] 하위 작업\r\n1. [X] 완료\r\n+ [ ] 추가\r\n* [ ] 항목\r\n"
        assertEquals(listOf(
            ChecklistItem("첫 작업", false), ChecklistItem("하위 작업", true, 1),
            ChecklistItem("완료", true), ChecklistItem("추가", false), ChecklistItem("항목", false),
        ), MarkdownChecklist.parse(markdown))
    }

    @Test fun ignoresFencedExamplesAndNonTasks() {
        val markdown = """
            ```markdown
            - [ ] example
            ```
            ~~~~
            - [x] example
            ~~~
            - [ ] still fenced
            ~~~~
            - ordinary list
            prose [ ] ignored
            - [a] ignored
            - [ ]
            - [ ] 실제 작업
        """.trimIndent()
        assertEquals(listOf(ChecklistItem("실제 작업", false)), MarkdownChecklist.parse(markdown))
    }

    @Test fun preservesTaskTextAndLimitsIndentation() {
        val parsed = MarkdownChecklist.parse("                    - [x] `TODO.md` 갱신  ")
        assertEquals(listOf(ChecklistItem("`TODO.md` 갱신", true, 4)), parsed)
        assertTrue(MarkdownChecklist.parse("").isEmpty())
    }

    @Test fun tracksOwnerThroughSubsectionsAndIgnoresFencedHeadings() {
        val markdown = "\uFEFF## Human\r\n### Setup\r\n- [ ] 키 설정 <!-- roadock:api-key -->\r\n" +
            "```md\n## Agent\n- [ ] 예시\n```\n- [x] 권한 승인\n## Agent\n- [ ] API 연동\n## Other\n- [ ] 문서 정리\n"
        val tasks = MarkdownChecklist.parse(markdown)
        assertEquals(listOf(TaskOwner.HUMAN, TaskOwner.HUMAN, TaskOwner.AGENT, TaskOwner.AGENT), tasks.map { it.owner })
        assertEquals("키 설정", tasks[0].text)
        assertEquals("api-key", tasks[0].key)
    }

    @Test fun changesOnlyTheCheckboxAndPreservesBomNewlinesAndComments() {
        val original = "\uFEFF## Human\r\n  1. [ ] 키 설정 <!-- roadock:api-key -->\r\n\r\n## Agent\r\n- [X] 구현\r\n"
        val expected = MarkdownChecklist.parse(original).first()
        assertEquals(original.replace("1. [ ]", "1. [x]"), MarkdownChecklist.setHumanChecked(original, expected, true))
        assertEquals(original, MarkdownChecklist.setHumanChecked(original, expected, false))
    }

    @Test fun rejectsAmbiguousRenamedReassignedAndDeletedTasks() {
        val original = "## Human\n- [ ] 키 설정 <!-- roadock:key -->\n"
        val expected = MarkdownChecklist.parse(original).single()
        listOf(original + "- [ ] 다른 작업 <!-- roadock:key -->\n", original.replace("키 설정", "키 삭제"),
            original.replace("Human", "Agent"), "## Human\n").forEach { changed ->
            assertThrows(FetchException::class.java) { MarkdownChecklist.setHumanChecked(changed, expected, true) }
        }
        val plain = MarkdownChecklist.parse("## Human\n- [ ] 키 설정").single()
        assertEquals("## Human\n- [x] 키 설정", MarkdownChecklist.setHumanChecked("## Human\n- [ ] 키 설정", plain, true))
        assertThrows(FetchException::class.java) { MarkdownChecklist.setHumanChecked("## Human\n- [ ] 키 설정\n- [ ] 키 설정", plain, true) }
        assertThrows(FetchException::class.java) { MarkdownChecklist.setHumanChecked("## Agent\n- [ ] 구현", ChecklistItem("구현", false), true) }
    }
}
