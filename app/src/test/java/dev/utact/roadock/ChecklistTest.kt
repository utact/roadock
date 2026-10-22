package dev.utact.roadock

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ChecklistTest {
    @Test fun parsesIndentedNotesSeparatelyFromRequirementsAndPreservesParagraphs() {
        val markdown = "\uFEFF## Human\r\n- [ ] 실기기 확인 <!-- roadock:device -->\r\n" +
            "  실제 회전과 오류 전환을 확인한다.\r\n  [검증 기준](docs/TESTING.md#device)\r\n\r\n" +
            "## Agent\r\n- [ ] 편집 구현\r\n  Requires: device\r\n  초안 저장과 복구를 포함한다.\r\n\r\n" +
            "  설계는 독립적으로 진행한다.\r\n  <!-- hidden\r\n  Requires: hidden\r\n  -->\r\n" +
            "  - [ ] 하위 작업\r\n    하위 설명\r\n### Next\r\n  관계없는 설명\r\n"
        val tasks = MarkdownChecklist.parse(markdown)
        assertEquals("실제 회전과 오류 전환을 확인한다.\n[검증 기준](docs/TESTING.md#device)", tasks[0].details)
        assertEquals(listOf("device"), tasks[1].requires)
        assertEquals("초안 저장과 복구를 포함한다.\n\n설계는 독립적으로 진행한다.", tasks[1].details)
        assertEquals("하위 설명", tasks[2].details)
        assertEquals(markdown.replace("- [ ] 실기기 확인", "- [x] 실기기 확인"),
            MarkdownChecklist.setHumanChecked(markdown, tasks[0], true))
    }

    @Test fun templateExamplesMatchTheActualParserContract() {
        val template = listOf(File("templates/roadock-agent.md"), File("../templates/roadock-agent.md"))
            .first { it.isFile }.readText()
        val example = Regex("```markdown\\s*\\n([\\s\\S]*?)\\n```").find(template)!!.groupValues[1]
        val tasks = MarkdownChecklist.parse(example)
        assertEquals(3, tasks.size)
        assertTrue(tasks.all { it.details.isNotBlank() })
        val human = tasks.single { it.owner == TaskOwner.HUMAN }
        assertEquals("api-key", human.key)
        val dependent = tasks.single { it.requires.isNotEmpty() }
        assertEquals(DependencyStatus(waiting = listOf(human.text)), TaskDependencies(tasks).status(dependent))
        assertEquals(tasks.size, tasks.map { it.text }.distinct().size)
    }

    @Test fun parsesIndentedHumanPrerequisitesWithoutLeakingAcrossTasksHeadingsOrFences() {
        val markdown = "\uFEFF## Human\r\n- [ ] 키 발급 <!-- roadock:key -->\r\n  Requires: ignored\r\n" +
            "## Agent\r\n- [ ] API 호출\r\n  설명\r\n  Requires: key, access\r\n  Requires: key\r\n" +
            "- [ ] 독립 작업\r\nRequires: ignored\r\n  Requires: ignored\r\n" +
            "- [ ] 다른 작업\r\n### Next\r\n  Requires: ignored\r\n" +
            "- [ ] 예시 작업\r\n```\r\n  Requires: ignored\r\n```\r\n  Requires: ignored\r\n" +
            "  - [ ] 하위 작업\r\n  Requires: ignored\r\n- [ ] 마지막 작업\r\n\tRequires: key\r\n"
        val tasks = MarkdownChecklist.parse(markdown)
        assertEquals(listOf(emptyList(), listOf("key", "access"), emptyList(), emptyList(),
            emptyList(), emptyList(), listOf("key")), tasks.map { it.requires })
    }

    @Test fun resolvesOnlyUniqueHumanIdsAndRecomputesWaitingAfterCompletionAndReopening() {
        val human = ChecklistItem("키 발급", false, owner = TaskOwner.HUMAN, key = "key")
        val agent = ChecklistItem("API 호출", false, requires = listOf("key", "key"))
        assertEquals(DependencyStatus(waiting = listOf("키 발급")), TaskDependencies(listOf(human, agent)).status(agent))
        assertEquals(DependencyStatus(), TaskDependencies(listOf(human.copy(checked = true), agent)).status(agent))
        assertEquals(DependencyStatus(waiting = listOf("키 발급")), TaskDependencies(listOf(human, agent)).status(agent))
        assertEquals(DependencyStatus(), TaskDependencies(listOf(human)).status(agent.copy(checked = true)))
        assertEquals(DependencyStatus(), TaskDependencies(listOf(human)).status(human.copy(requires = listOf("key"))))
        for (targets in listOf(emptyList(), listOf(human, human.copy(checked = true)), listOf(human.copy(owner = TaskOwner.AGENT)))) {
            assertEquals(DependencyStatus(unresolved = listOf("key")), TaskDependencies(targets).status(agent))
        }
    }

    @Test fun humanCheckboxWritesPreservePrerequisitesAndRecomputeTheirState() {
        val markdown = "## Human\r\n- [ ] 키 발급 <!-- roadock:key -->\r\n\r\n## Agent\r\n- [ ] API 호출\r\n  Requires: key\r\n"
        val human = MarkdownChecklist.parse(markdown).first()
        val updated = MarkdownChecklist.setHumanChecked(markdown, human, true)
        assertEquals(markdown.replace("- [ ] 키 발급", "- [x] 키 발급"), updated)
        val tasks = MarkdownChecklist.parse(updated)
        assertEquals(listOf("key"), tasks.last().requires)
        assertEquals(DependencyStatus(), TaskDependencies(tasks).status(tasks.last()))
    }

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
