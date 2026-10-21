package dev.utact.roadock

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoadockStoreTest {
    private lateinit var context: Application
    private lateinit var store: RoadockStore
    private val project = Project("one", "ROADOCK", "utact", "roadock", "main", "TODO.md")

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("roadock", Context.MODE_PRIVATE).edit().clear().commit()
        store = RoadockStore(context)
    }

    @Test fun persistsTasksAcrossStoreInstances() {
        store.addTask("  Kotlin 복습  ")
        val task = RoadockStore(context).tasks().single()
        assertEquals("Kotlin 복습", task.text)
        store.toggleTask(task.id)
        assertTrue(RoadockStore(context).tasks().single().checked)
        store.deleteTask(task.id)
        assertTrue(RoadockStore(context).tasks().isEmpty())
        assertThrows(IllegalArgumentException::class.java) { store.addTask(" ") }
    }

    @Test fun persistsManualOrderThroughCompletionAndWidgetReads() {
        store.addTask("첫째")
        store.addTask("둘째")
        store.addTask("셋째")
        val order = store.tasks().reversed().map { it.id }
        store.reorderTasks(order)
        store.toggleTask(order[0])
        assertEquals(order, RoadockStore(context).tasks().map { it.id })
        assertEquals(listOf("첫째", "둘째", "셋째"), RoadockWidget.rows(context, RoadockStore.PERSONAL).map { it.text })
        store.reorderTasks(listOf(order[2], "deleted", order[2]))
        assertEquals(listOf(order[2], order[0], order[1]), store.tasks().map { it.id })
    }

    @Test fun preservesCachedDataOnFailureAndRename() {
        store.saveProject(project)
        val cache = ChecklistCache(listOf(ChecklistItem("구현", true)), 1234)
        assertTrue(store.saveCache(project, cache))
        store.saveCache(project, store.cache(project.id).copy(error = "오프라인"))
        assertEquals(cache.copy(error = "오프라인"), RoadockStore(context).cache(project.id))
        store.saveProject(project.copy(name = "새 이름"))
        assertEquals(cache.items, store.cache(project.id).items)
    }

    @Test fun rejectsStaleFetchesAndClearsCacheWhenSourceChanges() {
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("이전", false)), 1234))
        store.saveProject(project.copy(branch = "next"))
        assertEquals(ChecklistCache(), store.cache(project.id))
        assertFalse(store.saveCache(project, ChecklistCache(fetchedAt = 9999)))
        store.deleteProject(project.id)
        assertFalse(store.saveCache(project, ChecklistCache(fetchedAt = 9999)))
    }

    @Test fun keepsWidgetSelectionsIndependentAndResolvesDeletedProjects() {
        val second = project.copy(id = "two", name = "다른 프로젝트")
        store.saveProject(project)
        store.saveProject(second)
        store.selectWidgetPage(10, project.id)
        store.selectWidgetPage(20, second.id)
        assertEquals(project.id, store.widgetPage(10))
        assertEquals(second.id, store.widgetPage(20))
        store.deleteProject(project.id)
        assertEquals(second.id, store.widgetPage(10))
        assertEquals(second.id, store.widgetPage(20))
        store.deleteProject(second.id)
        assertEquals(RoadockStore.PERSONAL, store.widgetPage(10))
    }
}
