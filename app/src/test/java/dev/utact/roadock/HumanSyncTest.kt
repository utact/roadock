package dev.utact.roadock

import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HumanSyncTest {
    @Test fun showsPendingFeedbackUntilConfirmationAndRestoresTheCheckboxOnFailure() {
        val store = RoadockStore(context)
        store.saveProject(project)
        store.selectPage(project.id)
        store.saveCache(project, ChecklistCache(listOf(human, agent), 123))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var root = controller.get().window.decorView
        fun layout() {
            repeat(3) {
                shadowOf(Looper.getMainLooper()).idle()
                root.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY))
                root.layout(0, 0, 393, 852)
            }
        }
        for ((initial, failure) in listOf(false to false, true to false, true to true)) {
            val target = human.copy(checked = initial)
            store.saveCache(project, ChecklistCache(listOf(target, agent), 123))
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val fake = client {
                started.countDown()
                check(release.await(30, TimeUnit.SECONDS))
                if (failure) throw IOException()
                listOf(target.copy(checked = !initial), agent)
            }
            val future = RefreshQueue.setHumanChecked(context, project, target, !initial, fake)!!
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS))
                layout()
                assertEquals(initial, store.cache(project.id).items.first().checked)
                assertEquals(View.VISIBLE, root.findViewWithTag<View>("saving-sync-0").visibility)
                assertEquals(View.INVISIBLE, root.findViewWithTag<View>("task-sync-0").visibility)
                assertEquals(context.getString(R.string.saving), root.findViewWithTag<TextView>("dependency-sync-0").text.toString())
                controller.recreate()
                root = controller.get().window.decorView
                layout()
                assertEquals(View.VISIBLE, root.findViewWithTag<View>("saving-sync-0").visibility)
                assertFalse(root.findViewWithTag<CheckBox>("task-sync-0").isEnabled)
            } finally { release.countDown() }
            future.get(5, TimeUnit.SECONDS)
            layout()
            val checkbox = root.findViewWithTag<CheckBox>("task-sync-0")
            assertEquals(View.GONE, root.findViewWithTag<View>("saving-sync-0").visibility)
            assertEquals(View.VISIBLE, checkbox.visibility)
            assertEquals(if (failure) initial else !initial, checkbox.isChecked)
            assertTrue(checkbox.isEnabled)
            if (failure) assertEquals(context.getString(R.string.error_write_unknown),
                root.findViewWithTag<TextView>("page-metadata").text.toString())
        }
        controller.pause().stop().destroy()
    }

    private val context get() = RuntimeEnvironment.getApplication()
    private val project = Project("sync", "ROADOCK", "utact", "roadock", "main", "TODO.md")
    private val human = ChecklistItem("API 키 발급", false, owner = TaskOwner.HUMAN, key = "api-key")
    private val agent = ChecklistItem("인증 구현", false, requires = listOf("api-key"))

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
            assertEquals(listOf(human.text), TaskDependencies(store.cache(project.id).items).status(agent).waiting)
            assertNull(RefreshQueue.setHumanChecked(context, project, human, true, fake))
        } finally { release.countDown() }
        future.get(5, TimeUnit.SECONDS)
        assertNull(RefreshQueue.pending(project.id))
        assertEquals(listOf(human.copy(checked = true), agent), store.cache(project.id).items)
        assertEquals(DependencyStatus(), TaskDependencies(store.cache(project.id).items).status(agent))
        assertNull(store.cache(project.id).error)
    }

    @Test fun retainsUnconfirmedStateOnNetworkFailureAndImportsLatestConflictState() {
        val store = RoadockStore(context)
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(human, agent), 123))
        RefreshQueue.setHumanChecked(context, project, human, true, client { throw IOException() })!!
            .get(5, TimeUnit.SECONDS)
        assertEquals(listOf(human, agent), store.cache(project.id).items)
        assertEquals(listOf(human.text), TaskDependencies(store.cache(project.id).items).status(agent).waiting)
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
