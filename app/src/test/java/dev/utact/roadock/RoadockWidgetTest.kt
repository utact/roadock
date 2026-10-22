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
    @Test fun pickerIncludesRowsAndCheckboxesWithoutLoadingAnyData() {
        val context = RuntimeEnvironment.getApplication()
        val preview = android.widget.RemoteViews(context.packageName, R.layout.widget_preview).apply(context, FrameLayout(context))
        val rows = preview.findViewById<android.view.ViewGroup>(R.id.widget_preview_rows)
        assertEquals(3, rows.childCount)
        val labels = (0 until rows.childCount).map { index ->
            val row = rows.getChildAt(index) as android.view.ViewGroup
            assertNotNull((row.getChildAt(0) as android.widget.ImageView).drawable)
            (row.getChildAt(1) as TextView).text.toString()
        }
        assertEquals(listOf(R.string.widget_sample_short, R.string.widget_sample_done, R.string.widget_sample_long)
            .map { context.getString(it) }, labels)
    }

    @Test @Config(sdk = [28]) fun changesOnlyBackgroundTransparencyAndKeepsPreviewInSync() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        for (transparency in listOf(0, 15, 50, 100)) {
            store.setWidgetTransparency(transparency)
            val actual = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
            val preview = RoadockWidget.previewViews(context).apply(context, FrameLayout(context))
            for (view in listOf(actual, preview)) {
                assertEquals(((100 - transparency) * 255f / 100).toInt(),
                    view.findViewById<android.widget.ImageView>(R.id.widget_background).imageAlpha)
                assertEquals(1f, view.findViewById<View>(R.id.widget_title).alpha, 0.001f)
                assertEquals(1f, view.findViewById<View>(R.id.widget_content).alpha, 0.001f)
            }
        }
        store.setWidgetTransparency(-10)
        assertEquals(0, RoadockStore(context).widgetTransparency())
        store.setWidgetTransparency(120)
        assertEquals(100, RoadockStore(context).widgetTransparency())
    }

    @Test fun scalesCheckboxWithTypeWithoutChangingRowHeightOrSampleText() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val density = android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f
        for (size in listOf(10, 12, 16)) {
            store.setWidgetTextSize(size)
            val preview = RoadockWidget.previewViews(context).apply(context, FrameLayout(context))
            val rows = preview.findViewById<android.view.ViewGroup>(R.id.widget_preview_rows)
            val row = rows.getChildAt(0)
            val checkbox = row.findViewById<android.widget.ImageView>(R.id.widget_check)
            val bitmap = (checkbox.drawable as android.graphics.drawable.BitmapDrawable).bitmap
            assertEquals((size * density * 4f / 3f).toInt(), bitmap.width)
            assertEquals((28 * density).toInt(), row.findViewById<TextView>(R.id.widget_row_text).minHeight)
            assertEquals(context.getString(R.string.widget_sample_long), rows.getChildAt(2).findViewById<TextView>(R.id.widget_row_text).text.toString())
        }
    }

    @Test fun showsHumanFirstWithCompactSectionsAndNoWriteAction() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val project = Project("roles", "ROADOCK", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("구현", true),
            ChecklistItem("키 발급", false, owner = TaskOwner.HUMAN, key = "key")), 123))
        store.selectWidgetPage(10, project.id)
        val root = RoadockWidget.views(context, 10).apply(context, AppWidgetHostView(context))
        val list = root.findViewById<ListView>(R.id.widget_list)
        assertEquals(4, list.adapter.count)
        assertEquals("내 작업", list.adapter.getView(0, null, list).findViewById<TextView>(R.id.widget_section).text.toString())
        val human = list.adapter.getView(1, null, list)
        assertEquals("키 발급", human.findViewById<TextView>(R.id.widget_row_text).text.toString())
        assertFalse(human.isClickable)
        assertEquals("에이전트", list.adapter.getView(2, null, list).findViewById<TextView>(R.id.widget_section).text.toString())
        assertEquals("구현", list.adapter.getView(3, null, list).findViewById<TextView>(R.id.widget_row_text).text.toString())
    }

    @Test @Config(sdk = [28]) fun legacyAdapterUsesTheSameOwnerSections() {
        val controller = Robolectric.buildService(ChecklistViewsService::class.java).create()
        val service = controller.get()
        val store = RoadockStore(service)
        val project = Project("roles", "ROADOCK", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("구현", false),
            ChecklistItem("기기 테스트", false, owner = TaskOwner.HUMAN)), 123))
        val factory = service.onGetViewFactory(Intent().putExtra("page", project.id))
        factory.onCreate()
        assertEquals(4, factory.count)
        assertEquals(2, factory.viewTypeCount)
        assertEquals("내 작업", factory.getViewAt(0)!!.apply(service, FrameLayout(service))
            .findViewById<TextView>(R.id.widget_section).text.toString())
        assertEquals("기기 테스트", factory.getViewAt(1)!!.apply(service, FrameLayout(service))
            .findViewById<TextView>(R.id.widget_row_text).text.toString())
        factory.onDestroy()
        controller.destroy()
    }

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
