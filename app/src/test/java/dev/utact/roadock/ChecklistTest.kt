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
}
