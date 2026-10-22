package dev.utact.roadock

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HumanSyncTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val project = Project("sync", "ROADOCK", "utact", "roadock", "main", "TODO.md")
    private val human = ChecklistItem("API 키 발급", false, owner = TaskOwner.HUMAN, key = "api-key")
    private val agent = ChecklistItem("인증 구현", false)

    private fun client(action: (() -> Boolean) -> List<ChecklistItem>) = object : GitHubClient() {
        override fun setHumanChecked(project: Project, token: String, expected: ChecklistItem, checked: Boolean,
            sourceIsCurrent: () -> Boolean): List<ChecklistItem> = action(sourceIsCurrent)
    }

    @Test fun waitsForServerConfirmationBlocksDuplicatesAndPreservesPendingAcrossStores() {
        val store = RoadockStore(context)
        store.saveProject(project)
        val original = ChecklistCache(listOf(human, agent), 123)
        store.saveCache(project, original)
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val fake = client { current ->
            assertTrue(current())
            started.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            listOf(human.copy(checked = true), agent)
        }
        val future = RefreshQueue.setHumanChecked(context, project, human, true, fake)!!
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS))
            assertEquals(human, RefreshQueue.pending(project.id))
            assertEquals(original, RoadockStore(context).cache(project.id))
            assertNull(RefreshQueue.setHumanChecked(context, project, human, true, fake))
        } finally { release.countDown() }
        future.get(5, TimeUnit.SECONDS)
        assertNull(RefreshQueue.pending(project.id))
        assertEquals(listOf(human.copy(checked = true), agent), store.cache(project.id).items)
        assertNull(store.cache(project.id).error)
    }

    @Test fun retainsUnconfirmedStateOnNetworkFailureAndImportsLatestConflictState() {
        val store = RoadockStore(context)
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(human, agent), 123))
        RefreshQueue.setHumanChecked(context, project, human, true, client { throw IOException() })!!
            .get(5, TimeUnit.SECONDS)
        assertEquals(listOf(human, agent), store.cache(project.id).items)
        assertEquals(context.getString(R.string.error_write_unknown), store.cache(project.id).error)
        assertNull(RefreshQueue.pending(project.id))
        val latest = listOf(agent.copy(checked = true))
        RefreshQueue.setHumanChecked(context, project, human, true,
            client { throw HumanWriteConflict(latest, R.string.error_task_conflict) })!!.get(5, TimeUnit.SECONDS)
        assertEquals(latest, store.cache(project.id).items)
        assertEquals(context.getString(R.string.error_task_conflict), store.cache(project.id).error)
        assertNull(RefreshQueue.pending(project.id))
    }

    @Test fun rejectsResultsAfterProjectSourceChanges() {
        val store = RoadockStore(context)
        store.saveProject(project)
        val changed = project.copy(branch = "next")
        RefreshQueue.setHumanChecked(context, project, human, true, client { current ->
            store.saveProject(changed)
            assertFalse(current())
            throw FetchException(R.string.error_source_changed)
        })!!.get(5, TimeUnit.SECONDS)
        assertEquals(ChecklistCache(), store.cache(project.id))
        assertEquals(changed, store.projects().single())
        assertNull(RefreshQueue.pending(project.id))
    }
}
