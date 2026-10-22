package dev.utact.roadock

import android.appwidget.AppWidgetHostView
import android.content.Intent
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.text.Spanned
import android.text.style.URLSpan
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class DependencyUiTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val project = Project("dependencies", "Roadock", "utact", "roadock", "main", "TODO.md")
    private val markdown = """
        ## Human
        - [ ] API 키 발급 <!-- roadock:key -->
        - [x] 접근 권한 부여 <!-- roadock:access -->
        ## Agent
        - [ ] API 실호출 검증
          Requires: key, access
          실제 응답과 인증 실패 처리를 확인한다.
          [검증 기준](docs/TESTING.md#api)
        - [ ] 인증 방식 비교
        - [x] 이전 검증
          Requires: key
        - [ ] 참조 수정
          Requires: missing
    """.trimIndent()

    private fun seed(split: Boolean = false) = RoadockStore(context).apply {
        saveProject(project)
        saveCache(project, ChecklistCache(MarkdownChecklist.parse(markdown), 123))
        selectPage(project.id)
        selectWidgetPage(10, project.id)
        setWidgetSplitProjects(split)
    }

    private fun layout(view: View) {
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 393, 852)
        }
    }

    private fun centerY(view: View): Float {
        var center = view.height / 2f
        var current: View? = view
        while (current != null) {
            center += current.top
            current = current.parent as? View
        }
        return center
    }

    @Test fun appFiltersKeepHumanPrerequisitesAndRestoreWaitingLabelsOffline() {
        for (split in listOf(false, true)) {
            val store = seed(split)
            store.setProjectFilter(TaskOwner.AGENT)
            val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
            var root = controller.get().window.decorView
            layout(root)
            fun label(index: Int) = root.findViewWithTag<TextView>("dependency-dependencies-$index")
            assertEquals("API 키 발급 대기", label(2).text.toString())
            assertEquals(View.VISIBLE, label(2).visibility)
            assertEquals(View.GONE, label(3).visibility)
            assertEquals(View.GONE, label(4).visibility)
            assertEquals("선행 작업 확인 필요", label(5).text.toString())
            val checkbox = root.findViewWithTag<View>("task-dependencies-2")
            val title = root.findViewWithTag<View>("text-dependencies-2")
            assertEquals(centerY(title), centerY(checkbox), 1f)
            assertTrue(centerY(checkbox) < centerY(label(2)))
            label(2).performClick()
            val detail = ShadowDialog.getLatestDialog() as AlertDialog
            assertEquals("API 실호출 검증",
                detail.window!!.decorView.findViewWithTag<TextView>("task-full-text").text.toString())
            assertEquals("Agent", detail.window!!.decorView.findViewWithTag<TextView>("task-owner").text.toString())
            assertEquals("API 키 발급 대기", detail.window!!.decorView.findViewWithTag<TextView>("task-dependency").text.toString())
            val notes = detail.window!!.decorView.findViewWithTag<TextView>("task-details")
            assertEquals("실제 응답과 인증 실패 처리를 확인한다.\n검증 기준", notes.text.toString())
            assertTrue(notes.isTextSelectable)
            val spans = notes.text as Spanned
            assertEquals("https://github.com/utact/roadock/blob/main/docs/TESTING.md#api",
                spans.getSpans(0, spans.length, URLSpan::class.java).single().url)
            detail.dismiss()

            val original = store.cache(project.id)
            store.saveCache(project, original.copy(items = original.items.mapIndexed { index, item ->
                if (index == 0) item.copy(checked = true) else item
            }))
            layout(root)
            assertEquals(View.GONE, label(2).visibility)
            store.saveCache(project, original.copy(error = "오프라인"))
            layout(root)
            assertEquals("API 키 발급 대기", label(2).text.toString())
            controller.recreate()
            root = controller.get().window.decorView
            layout(root)
            assertEquals(View.VISIBLE, label(2).visibility)
            assertEquals("API 키 발급 대기", label(2).text.toString())
            controller.pause().stop().destroy()
        }
    }

    @Test @Config(sdk = [28, 34]) fun widgetAlignsCheckboxWithTaskTitleAtEveryTextSize() {
        val store = seed()
        val task = store.cache(project.id).items[2]
        val dependency = TaskDependencies(store.cache(project.id).items).status(task)
        for (size in 10..16) {
            store.setWidgetTextSize(size)
            val row = RoadockWidget.rowView(context, task, dependency).apply(context, FrameLayout(context))
            row.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            row.layout(0, 0, row.measuredWidth, row.measuredHeight)
            val checkbox = row.findViewById<View>(R.id.widget_check)
            val title = row.findViewById<View>(R.id.widget_row_text)
            val note = row.findViewById<View>(R.id.widget_dependency)
            assertEquals(centerY(title), centerY(checkbox), 1f)
            assertTrue(centerY(checkbox) < centerY(note))
        }
    }

    @Test fun widgetUpdatesWaitingLabelsFromCacheInBothLayouts() {
        for (split in listOf(false, true)) {
            val store = seed(split)
            fun rows(): List<View> {
                val root = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
                val list = root.findViewById<ListView>(if (split) R.id.widget_agent_list else R.id.widget_list)
                return (0 until list.adapter.count).map { list.adapter.getView(it, null, list) }
                    .filter { it.findViewById<TextView>(R.id.widget_row_text) != null }
                    .filter { it.findViewById<TextView>(R.id.widget_row_text).text == "API 실호출 검증" }
            }
            var pending = rows().single()
            assertEquals("API 키 발급 대기", pending.findViewById<TextView>(R.id.widget_dependency).text.toString())
            assertTrue(pending.contentDescription.toString().contains("API 키 발급 대기"))
            assertFalse(pending.isClickable)
            val original = store.cache(project.id)
            store.saveCache(project, original.copy(items = original.items.mapIndexed { index, item ->
                if (index == 0) item.copy(checked = true) else item
            }))
            pending = rows().single()
            assertEquals(View.GONE, pending.findViewById<View>(R.id.widget_dependency).visibility)
            store.saveCache(project, original.copy(error = "오프라인"))
            pending = rows().single()
            assertEquals(View.VISIBLE, pending.findViewById<View>(R.id.widget_dependency).visibility)
        }
    }

    @Test @Config(sdk = [28]) fun legacySplitWidgetResolvesHumansOutsideTheVisibleRowLimit() {
        val store = seed(true)
        val humans = List(101) { ChecklistItem("선행 작업 $it", false, owner = TaskOwner.HUMAN, key = "human-$it") }
        val agent = ChecklistItem("API 검증", false, requires = listOf("human-100"))
        store.saveCache(project, ChecklistCache(humans + agent, 123))
        val controller = Robolectric.buildService(ChecklistViewsService::class.java).create()
        val service = controller.get()
        val factory = service.onGetViewFactory(Intent().putExtra("page", project.id).putExtra("owner", "AGENT"))
        factory.onCreate()
        fun dependency() = factory.getViewAt(0)!!.apply(service, FrameLayout(service))
            .findViewById<TextView>(R.id.widget_dependency)
        assertEquals("선행 작업 100 대기", dependency().text.toString())
        store.saveCache(project, ChecklistCache(humans.map { it.copy(checked = true) } + agent, 456))
        factory.onDataSetChanged()
        assertEquals(View.GONE, dependency().visibility)
        factory.onDestroy()
        controller.destroy()
    }

    @Test @Config(qualifiers = "en") fun localizesWaitingAndInvalidReferences() {
        val store = seed()
        val items = store.cache(project.id).items
        val dependencies = TaskDependencies(items)
        assertEquals("Waiting for API 키 발급", dependencies.status(items[2]).description(context))
        assertEquals("Check prerequisites", dependencies.status(items[5]).description(context))
    }
}
