package dev.utact.roadock

import android.content.res.Configuration
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
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
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {
    private fun layout(activity: MainActivity) {
        shadowOf(Looper.getMainLooper()).idle()
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 393, 852)
        shadowOf(Looper.getMainLooper()).idle()
        root.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 393, 852)
    }

    private inline fun <reified T : View> find(root: View): T? {
        if (root is T) return root
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            findType(root.getChildAt(i), T::class.java)?.let { return it }
        }
        return null
    }

    private fun <T : View> findType(root: View, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            findType(root.getChildAt(i), type)?.let { return it }
        }
        return null
    }

    private fun selectTaskMenu(position: Int) {
        val popup = ShadowApplication.getInstance().latestPopupWindow
        val list = find<ListView>(popup.contentView)!!
        list.performItemClick(null, position, list.adapter.getItemId(position))
    }

    private fun openComposer(activity: MainActivity): View {
        activity.window.decorView.findViewWithTag<View>("add-personal-task").performClick()
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        return (activity.supportFragmentManager.findFragmentByTag(TaskComposer.TAG) as TaskComposer).requireDialog().window!!.decorView
    }

    @Test fun addsCompletesAndDeletesPersonalTasks() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val store = RoadockStore(activity)
        val composer = openComposer(activity)
        composer.findViewWithTag<EditText>("task-input").setText("위젯 테스트")
        composer.findViewWithTag<View>("add-task").performClick()
        layout(activity)
        val task = store.tasks().single()
        assertEquals("위젯 테스트", task.text)
        activity.window.decorView.findViewWithTag<CheckBox>("task-${task.id}").performClick()
        layout(activity)
        assertTrue(store.tasks().single().checked)
        activity.window.decorView.findViewWithTag<View>("row-${task.id}").performLongClick()
        selectTaskMenu(2)
        layout(activity)
        assertTrue(store.tasks().isEmpty())
        controller.pause().stop().destroy()
    }

    @Test fun reordersPersonalTasksAndPreservesOrderOnCompletion() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        store.addTask("첫째")
        store.addTask("둘째")
        val original = store.tasks().map { it.id }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        activity.window.decorView.findViewWithTag<View>("reorder-${original[0]}").performClick()
        selectTaskMenu(1)
        layout(activity)
        assertEquals(original.reversed(), store.tasks().map { it.id })
        activity.window.decorView.findViewWithTag<CheckBox>("task-${original[1]}").performClick()
        layout(activity)
        assertEquals(original.reversed(), store.tasks().map { it.id })
        assertEquals(store.tasks().map { it.text }, RoadockWidget.rows(activity, RoadockStore.PERSONAL).map { it.text })
        controller.pause().stop().destroy()
    }

    @Test fun observesBackgroundCacheUpdates() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val project = Project("one", "ROADOCK", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.selectPage(project.id)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("갱신된 항목", false)), 1234))
        layout(controller.get())
        val recycler = controller.get().window.decorView.findViewWithTag<RecyclerView>("checklist")
        assertEquals(1, recycler.adapter!!.itemCount)
        val matches = arrayListOf<View>()
        recycler.findViewsWithText(matches, "갱신된 항목", View.FIND_VIEWS_WITH_TEXT)
        assertFalse(matches.isEmpty())
        controller.pause().stop().destroy()
    }

    @Test fun rejectsMalformedProjectWithoutDismissingDialog() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        find<TabLayout>(activity.window.decorView)!!.getTabAt(0)!!.select()
        activity.window.decorView.findViewWithTag<View>("add-project").performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        dialog.window!!.decorView.findViewWithTag<EditText>("project-repository").setText("invalid")
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        assertTrue(dialog.isShowing)
        assertTrue(RoadockStore(activity).projects().isEmpty())
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun fixesTypographyDensityAndDarkTheme() {
        val base = RuntimeEnvironment.getApplication()
        val altered = Configuration(base.resources.configuration).apply { fontScale = 2f; densityDpi = 480 }
        val fixed = RoadockDesign.fixedContext(base.createConfigurationContext(altered))
        assertEquals(1f, fixed.resources.configuration.fontScale)
        assertEquals(android.util.DisplayMetrics.DENSITY_DEVICE_STABLE, fixed.resources.configuration.densityDpi)
        assertEquals(Configuration.UI_MODE_NIGHT_YES, fixed.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
        assertEquals(17f * android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f, RoadockDesign.text(fixed, "본문").textSize, 0.01f)
    }

    @Test fun preservesTitleBaselineAndFirstRowAcrossTabsAndRefreshStates() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val project = Project("one", "ROADOCK", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("프로젝트 항목", false)), 1234))
        store.addTask("개인 항목")
        store.selectPage(project.id)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        fun coordinates(): List<Int> {
            layout(activity)
            val root = activity.window.decorView
            val title = root.findViewWithTag<TextView>("page-title")
            val list = root.findViewWithTag<RecyclerView>("checklist")
            val xy = IntArray(2)
            title.getLocationOnScreen(xy)
            val baseline = xy[1] + title.baseline
            list.getLocationOnScreen(xy)
            return listOf(baseline, xy[1], list.getChildAt(0).height)
        }
        val projectCoordinates = coordinates()
        store.saveCache(project, store.cache(project.id).copy(error = "조회에 실패했습니다. 이전 목록을 표시합니다"))
        assertEquals(projectCoordinates, coordinates())
        val tabs = find<TabLayout>(activity.window.decorView)!!
        repeat(3) {
            tabs.getTabAt(1)!!.select()
            assertEquals(projectCoordinates, coordinates())
            tabs.getTabAt(0)!!.select()
            assertEquals(projectCoordinates, coordinates())
        }
        controller.pause().stop().destroy()
    }

    @Test fun limitsPersonalTaskInputToShortSingleLine() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val composer = openComposer(activity)
        val input = composer.findViewWithTag<EditText>("task-input")
        input.setText("가".repeat(30))
        composer.findViewWithTag<View>("add-task").performClick()
        assertEquals("가".repeat(24), RoadockStore(activity).tasks().single().text)
        assertThrows(IllegalArgumentException::class.java) { RoadockStore(activity).addTask("첫째\n둘째") }
        controller.pause().stop().destroy()
    }

    @Test fun fadesOnlyScrollableBottomAndRestoresLastTaskVisibility() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        repeat(30) { store.addTask("할 일 $it") }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val root = activity.window.decorView
        val list = root.findViewWithTag<RecyclerView>("checklist")
        val fade = root.findViewWithTag<View>("list-fade")
        assertTrue(list.canScrollVertically(1))
        assertEquals(1f, fade.alpha)
        assertFalse(fade.isClickable)
        list.scrollToPosition(29)
        layout(activity)
        assertFalse(list.canScrollVertically(1))
        assertEquals(0f, fade.alpha)
        assertNotNull(root.findViewWithTag<View>("task-${store.tasks().last().id}"))
        controller.pause().stop().destroy()
    }

    @Test fun derivesTitleProgressFromCurrentProjectAndClearsItOnPersonalTab() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        val project = Project("one", "Roadock", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("완료", true), ChecklistItem("진행", false)), 1234))
        store.selectPage(project.id)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val title = activity.window.decorView.findViewWithTag<TextView>("page-title")
        assertTrue(title.contentDescription.toString().startsWith("Roadock, 1/2 완료"))
        find<TabLayout>(activity.window.decorView)!!.getTabAt(1)!!.select()
        layout(activity)
        assertEquals("개인 할 일", title.contentDescription)
        controller.pause().stop().destroy()
    }

    @Test fun switchesProjectsFromTitleAndOpensPersonalInputOnlyWhenRequested() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        val first = Project("one", "Roadock", "utact", "roadock", "main", "TODO.md")
        val second = first.copy(id = "two", name = "다른 프로젝트")
        store.saveProject(first)
        store.saveProject(second)
        store.selectPage(first.id)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val root = activity.window.decorView
        val title = root.findViewWithTag<TextView>("page-title")
        assertTrue(title.isClickable)
        title.performClick()
        selectTaskMenu(1)
        layout(activity)
        assertEquals(second.id, store.selectedPage())
        assertEquals(second.name, title.text.toString())
        assertNull(root.findViewWithTag<View>("task-input"))
        val projectListHeight = root.findViewWithTag<RecyclerView>("checklist").height
        find<TabLayout>(root)!!.getTabAt(1)!!.select()
        layout(activity)
        assertFalse(title.isClickable)
        assertEquals(projectListHeight, root.findViewWithTag<RecyclerView>("checklist").height)
        val composer = openComposer(activity)
        val input = composer.findViewWithTag<EditText>("task-input")
        val add = composer.findViewWithTag<View>("add-task")
        assertFalse(add.isEnabled)
        input.setText(" ")
        assertFalse(add.isEnabled)
        input.setText("작업")
        assertTrue(add.isEnabled)
        (activity.supportFragmentManager.findFragmentByTag(TaskComposer.TAG) as TaskComposer).dismissNow()
        controller.pause().stop().destroy()
    }

    @Test fun preservesUnsubmittedTaskWhenActivityIsRecreated() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        layout(controller.get())
        openComposer(controller.get()).findViewWithTag<EditText>("task-input").setText("작성 중인 할 일")
        controller.recreate()
        layout(controller.get())
        val sheet = controller.get().supportFragmentManager.findFragmentByTag(TaskComposer.TAG) as TaskComposer
        assertEquals("작성 중인 할 일", sheet.requireDialog().window!!.decorView.findViewWithTag<EditText>("task-input").text.toString())
        assertTrue(RoadockStore(controller.get()).tasks().isEmpty())
        sheet.dismissNow()
        controller.pause().stop().destroy()
    }

    @Test fun dragHandlePersistsMovedTask() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        repeat(4) { store.addTask("항목 $it") }
        val original = store.tasks().map { it.id }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val root = activity.window.decorView
        val handle = root.findViewWithTag<View>("reorder-${original[0]}")
        val xy = IntArray(2)
        handle.getLocationInWindow(xy)
        val x = xy[0] + handle.width / 2f
        val y = xy[1] + handle.height / 2f
        val down = SystemClock.uptimeMillis()
        fun touch(action: Int, offset: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y + offset, 0)
            root.dispatchTouchEvent(event)
            event.recycle()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        }
        touch(MotionEvent.ACTION_DOWN, 0f)
        touch(MotionEvent.ACTION_MOVE, 30f)
        touch(MotionEvent.ACTION_MOVE, 65f)
        touch(MotionEvent.ACTION_MOVE, 90f)
        touch(MotionEvent.ACTION_UP, 90f)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(1))
        layout(activity)
        assertNotEquals(original, store.tasks().map { it.id })
        assertEquals(original.toSet(), store.tasks().map { it.id }.toSet())
        controller.pause().stop().destroy()
    }

    @Test @Config(sdk = [26]) fun opensAtMinimumAndroidVersion() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        layout(controller.get())
        assertNotNull(controller.get().window.decorView.findViewWithTag<View>("add-personal-task"))
        controller.pause().stop().destroy()
    }
}
