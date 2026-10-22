package dev.utact.roadock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetHostView
import android.content.Intent
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.view.View
import android.os.Looper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class RoadockWidgetTest {
    private fun layout(view: View) {
        repeat(2) {
            view.measure(View.MeasureSpec.makeMeasureSpec(360, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 360, 240)
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    @Test fun disablesBothDirectionsForOnlyOneChecklist() {
        val context = RuntimeEnvironment.getApplication()
        val root = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        assertFalse(root.findViewById<View>(R.id.widget_previous).isEnabled)
        assertFalse(root.findViewById<View>(R.id.widget_next).isEnabled)
    }

    @Test fun scrollsLongWidgetListToLastTask() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        repeat(30) { store.addTask("할 일 $it") }
        val root = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        layout(root)
        val list = root.findViewById<ListView>(R.id.widget_list)
        assertTrue(list.canScrollVertically(1))
        list.setSelection(29)
        layout(root)
        assertFalse(list.canScrollVertically(1))
        assertEquals(29, list.lastVisiblePosition)
        assertEquals("할 일 29", list.getChildAt(list.childCount - 1).findViewById<TextView>(R.id.widget_row_text).text.toString())
    }
    @Test fun keepsFullLengthKoreanTaskOnOneCompactRow() {
        val context = RuntimeEnvironment.getApplication()
        val text = "가".repeat(24)
        val row = RoadockWidget.rowView(context, ChecklistItem(text, false)).apply(context, FrameLayout(context))
        val density = android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f
        row.measure(View.MeasureSpec.makeMeasureSpec((318 * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        row.layout(0, 0, row.measuredWidth, row.measuredHeight)
        val label = row.findViewById<TextView>(R.id.widget_row_text)
        assertEquals(1, label.lineCount)
        assertEquals(0, label.layout.getEllipsisCount(0))
        assertEquals((28 * density).toInt(), row.measuredHeight)
    }
    @Test fun fixesWidgetTextSizeUnderSystemFontScaling() {
        val base = RuntimeEnvironment.getApplication()
        val configuration = android.content.res.Configuration(base.resources.configuration).apply { fontScale = 2f }
        val context = base.createConfigurationContext(configuration)
        val row = RoadockWidget.rowView(context, ChecklistItem("크기 확인", false)).apply(context, FrameLayout(context))
        val text = row.findViewById<TextView>(R.id.widget_row_text)
        val density = android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f
        assertEquals(12f * density, text.textSize, 0.01f)
        assertEquals((28 * density).toInt(), text.minHeight)
    }

    @Test fun appliesSavedTextSizeWithoutIncreasingRowSpacing() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val density = android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f
        for (size in listOf(10, 16)) {
            store.setWidgetTextSize(size)
            val row = RoadockWidget.rowView(context, ChecklistItem("크기 확인", false)).apply(context, FrameLayout(context))
            val text = row.findViewById<TextView>(R.id.widget_row_text)
            assertEquals(size * density, text.textSize, 0.01f)
            assertEquals((28 * density).toInt(), text.minHeight)
        }
        store.setWidgetTextSize(99)
        assertEquals(16, store.widgetTextSize())
    }

    @Test fun modernAdapterDisplaysCachedRows() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val project = Project("one", "ROADOCK", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("캐시 항목", false), ChecklistItem("완료 항목", true)), 1234))
        store.selectWidgetPage(10, project.id)
        val root = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        val list = root.findViewById<ListView>(R.id.widget_list)
        assertEquals(2, list.adapter.count)
        val row = list.adapter.getView(0, null, list)
        assertTrue(row.findViewById<TextView>(R.id.widget_row_text).text.contains("캐시 항목"))
    }

    @Test fun navigatesWidgetsIndependently() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val project = Project("one", "ROADOCK", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        val manager = shadowOf(AppWidgetManager.getInstance(context))
        val first = manager.createWidget(RoadockWidget::class.java, R.layout.roadock_widget)
        val second = manager.createWidget(RoadockWidget::class.java, R.layout.roadock_widget)
        RoadockWidget().onReceive(context, Intent(context, RoadockWidget::class.java).apply {
            action = RoadockWidget.NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, first)
        })
        assertEquals(RoadockStore.PERSONAL, store.widgetPage(first))
        assertEquals(project.id, store.widgetPage(second))
        assertEquals("개인 할 일", manager.getViewFor(first).findViewById<TextView>(R.id.widget_title).text.toString())
        assertFalse(manager.getViewFor(first).findViewById<View>(R.id.widget_next).isEnabled)
        assertTrue(manager.getViewFor(first).findViewById<View>(R.id.widget_previous).isEnabled)
        RoadockWidget().onReceive(context, Intent(context, RoadockWidget::class.java).apply {
            action = RoadockWidget.NEXT
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, first)
        })
        assertEquals(RoadockStore.PERSONAL, store.widgetPage(first))
        RoadockWidget().onReceive(context, Intent(context, RoadockWidget::class.java).apply {
            action = RoadockWidget.PREVIOUS
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, second)
        })
        assertEquals(project.id, store.widgetPage(second))
    }

    @Test @Config(sdk = [28]) fun legacyAdapterLoadsLocalTasks() {
        val controller = Robolectric.buildService(ChecklistViewsService::class.java).create()
        val service = controller.get()
        RoadockStore(service).addTask("오프라인 복습")
        RoadockStore(service).setWidgetTextSize(14)
        val factory = service.onGetViewFactory(Intent().putExtra("page", RoadockStore.PERSONAL))
        factory.onCreate()
        assertEquals(1, factory.count)
        val view = factory.getViewAt(0)!!.apply(service, FrameLayout(service))
        assertTrue(view.findViewById<TextView>(R.id.widget_row_text).text.contains("오프라인 복습"))
        assertEquals(14f * android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f,
            view.findViewById<TextView>(R.id.widget_row_text).textSize, 0.01f)
        factory.onDestroy()
        controller.destroy()
    }
}
