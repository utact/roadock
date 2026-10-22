package dev.utact.roadock

import android.appwidget.AppWidgetHostView
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Looper
import android.view.View
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.tabs.TabLayout
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
class ProjectSplitTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val project = Project("split", "Roadock", "utact", "roadock", "main", "TODO.md")

    private fun seed(humans: Int = 33, agents: Int = 32): RoadockStore = RoadockStore(context).apply {
        saveProject(project)
        saveCache(project, ChecklistCache(List(humans) {
            ChecklistItem("Human $it", false, owner = TaskOwner.HUMAN, key = "human-$it")
        } + List(agents) { ChecklistItem("Agent $it", false) }, 123))
        selectPage(project.id)
        selectWidgetPage(10, project.id)
        setWidgetSplitProjects(true)
    }

    private fun layout(view: View, width: Int = 393, height: Int = 852) {
        repeat(2) {
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
        }
    }

    @Test fun widgetColumnsScrollIndependentlyAndKeepBothOwnersWithinTheirOwnLimit() {
        seed(125, 32)
        val root = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        layout(root, 500, 260)
        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_single).visibility)
        val human = root.findViewById<ListView>(R.id.widget_human_list)
        val agent = root.findViewById<ListView>(R.id.widget_agent_list)
        assertEquals(100, human.adapter.count)
        assertEquals(32, agent.adapter.count)
        assertEquals(human.width, agent.width)
        assertTrue(human.canScrollVertically(1))
        assertTrue(agent.canScrollVertically(1))
        human.setSelection(99)
        layout(root, 500, 260)
        assertEquals(99, human.lastVisiblePosition)
        assertEquals(0, agent.firstVisiblePosition)
        assertEquals("Agent 0", agent.getChildAt(0).findViewById<TextView>(R.id.widget_row_text).text.toString())
        for (list in listOf(human, agent)) {
            assertEquals(Color.TRANSPARENT, (list.selector as ColorDrawable).color)
            assertFalse(list.getChildAt(0).isClickable)
        }
        assertTrue(root.findViewById<View>(R.id.widget_title).isClickable)
    }

    @Test @Config(sdk = [28]) fun legacyFactoriesKeepOwnerDataSeparateOnRefresh() {
        val store = seed(2, 3)
        val controller = Robolectric.buildService(ChecklistViewsService::class.java).create()
        val service = controller.get()
        fun factory(owner: TaskOwner) = service.onGetViewFactory(Intent()
            .putExtra("page", project.id).putExtra("owner", owner.name)).apply { onCreate() }
        val human = factory(TaskOwner.HUMAN)
        val agent = factory(TaskOwner.AGENT)
        assertEquals(2, human.count)
        assertEquals(3, agent.count)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("Updated", true, owner = TaskOwner.HUMAN)), 456))
        human.onDataSetChanged()
        agent.onDataSetChanged()
        assertEquals(1, human.count)
        assertEquals(0, agent.count)
        assertEquals("Updated", human.getViewAt(0)!!.apply(service, FrameLayout(service))
            .findViewById<TextView>(R.id.widget_row_text).text.toString())
        human.onDestroy()
        agent.onDestroy()
        controller.destroy()
    }

    @Test fun appFiltersPersistIndependentlyOfWidgetLayoutAcrossRecreation() {
        val store = seed()
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var root = controller.get().window.decorView
        layout(root)
        var list = root.findViewWithTag<RecyclerView>("checklist")
        assertEquals(67, list.adapter!!.itemCount)
        assertEquals(root.width, list.width)
        assertNull(root.findViewWithTag<View>("agent-checklist"))
        assertEquals("All", root.findViewWithTag<TextView>("filter-all").text.toString())
        assertTrue(root.findViewWithTag<CheckBox>("task-split-0").isEnabled)
        root.findViewWithTag<View>("filter-agent").performClick()
        layout(root)
        assertEquals(32, list.adapter!!.itemCount)
        assertEquals(project.id, store.selectedPage())
        assertEquals(project.id, store.widgetPage(10))
        assertEquals("utact/roadock · 0/65 완료", root.findViewWithTag<TextView>("page-metadata").text.toString())
        assertFalse(root.findViewWithTag<CheckBox>("task-split-33").isEnabled)
        root.findViewWithTag<TextView>("text-split-33").performClick()
        val textDialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals("Agent 0", textDialog.window!!.decorView.findViewWithTag<TextView>("task-full-text").text.toString())
        val title = textDialog.window!!.decorView.findViewWithTag<TextView>("task-full-text")
        val owner = textDialog.window!!.decorView.findViewWithTag<TextView>("task-owner")
        assertEquals("Agent", owner.text.toString())
        assertTrue(title.textSize > owner.textSize)
        textDialog.dismiss()
        list.scrollToPosition(31)
        layout(root)
        assertTrue((list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).findFirstVisibleItemPosition() > 0)
        controller.recreate()
        root = controller.get().window.decorView
        layout(root)
        assertTrue(RoadockStore(controller.get()).widgetSplitProjects())
        assertEquals(TaskOwner.AGENT, RoadockStore(controller.get()).projectFilter())
        list = root.findViewWithTag("checklist")
        assertEquals(32, list.adapter!!.itemCount)
        WidgetSettingsDialog.show(controller.get())
        val settings = ShadowDialog.getLatestDialog() as AlertDialog
        settings.window!!.decorView.findViewWithTag<View>("widget-layout-single").performClick()
        layout(root)
        assertFalse(store.widgetSplitProjects())
        assertEquals(TaskOwner.AGENT, store.projectFilter())
        assertEquals(32, list.adapter!!.itemCount)
        var widget = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        assertEquals(View.GONE, widget.findViewById<View>(R.id.widget_split).visibility)
        settings.window!!.decorView.findViewWithTag<View>("widget-layout-split").performClick()
        layout(root)
        assertEquals(32, list.adapter!!.itemCount)
        widget = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        assertEquals(View.VISIBLE, widget.findViewById<View>(R.id.widget_split).visibility)
        assertEquals(33, widget.findViewById<ListView>(R.id.widget_human_list).adapter.count)
        assertEquals(32, widget.findViewById<ListView>(R.id.widget_agent_list).adapter.count)
        settings.dismiss()
        root.findViewWithTag<View>("filter-human").performClick()
        layout(root)
        assertEquals(33, list.adapter!!.itemCount)
        assertTrue(root.findViewWithTag<CheckBox>("task-split-0").isEnabled)
        assertEquals(0, (list.layoutManager as androidx.recyclerview.widget.LinearLayoutManager).findFirstVisibleItemPosition())
        controller.pause().stop().destroy()
    }

    @Test fun emptyOwnerFiltersKeepRefreshAvailableAndDoNotFilterPersonalTasks() {
        val store = seed(0, 3)
        store.addTask("Personal")
        store.setProjectFilter(TaskOwner.HUMAN)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val root = controller.get().window.decorView
        layout(root)
        assertNull(root.findViewWithTag<View>("agent-checklist"))
        assertEquals(View.VISIBLE, root.findViewWithTag<View>("owner-filters").visibility)
        assertEquals("Human 작업이 없습니다", root.findViewWithTag<TextView>("empty-message").text.toString())
        assertEquals(View.INVISIBLE, root.findViewWithTag<View>("checklist").visibility)
        assertTrue(root.findViewWithTag<View>("swipe-refresh").isEnabled)
        root.findViewWithTag<View>("filter-all").performClick()
        layout(root)
        assertEquals(3, root.findViewWithTag<RecyclerView>("checklist").adapter!!.itemCount)
        root.findViewWithTag<View>("filter-human").performClick()
        layout(root)
        val widget = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        assertEquals(View.GONE, widget.findViewById<View>(R.id.widget_split).visibility)
        fun findTabs(view: View): TabLayout? {
            if (view is TabLayout) return view
            if (view is android.view.ViewGroup) for (i in 0 until view.childCount)
                findTabs(view.getChildAt(i))?.let { return it }
            return null
        }
        findTabs(root)!!.getTabAt(1)!!.select()
        layout(root)
        assertEquals(1, root.findViewWithTag<RecyclerView>("checklist").adapter!!.itemCount)
        assertEquals(View.GONE, root.findViewWithTag<View>("owner-filters").visibility)
        assertEquals(TaskOwner.HUMAN, store.projectFilter())
        store.selectWidgetPage(10, RoadockStore.PERSONAL)
        val personal = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        assertEquals(View.GONE, personal.findViewById<View>(R.id.widget_split).visibility)
        assertEquals(1, personal.findViewById<ListView>(R.id.widget_list).adapter.count)
        controller.pause().stop().destroy()
    }
}
