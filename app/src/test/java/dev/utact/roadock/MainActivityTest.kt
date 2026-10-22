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
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.slider.Slider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowApplication
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "ko")
class MainActivityTest {
    @Test fun keepsPendingHumanWriteLockedAcrossActivityRecreation() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val project = Project("pending", "ROADOCK", "utact", "roadock", "main", "TODO.md")
        val human = ChecklistItem("기기 테스트", false, owner = TaskOwner.HUMAN, key = "device")
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(human), 123))
        val release = java.util.concurrent.CountDownLatch(1)
        val client = object : GitHubClient() {
            override fun setHumanChecked(project: Project, token: String, expected: ChecklistItem, checked: Boolean,
                sourceIsCurrent: () -> Boolean): List<ChecklistItem> {
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
                return listOf(human.copy(checked = true))
            }
        }
        val future = RefreshQueue.setHumanChecked(context, project, human, true, client)!!
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        try {
            layout(controller.get())
            var root = controller.get().window.decorView
            assertFalse(root.findViewWithTag<CheckBox>("task-pending-0").isEnabled)
            assertFalse(root.findViewWithTag<CheckBox>("task-pending-0").isChecked)
            assertEquals("저장 중…", root.findViewWithTag<TextView>("page-metadata").text.toString())
            controller.recreate()
            layout(controller.get())
            root = controller.get().window.decorView
            assertFalse(root.findViewWithTag<CheckBox>("task-pending-0").isEnabled)
            assertEquals("저장 중…", root.findViewWithTag<TextView>("page-metadata").text.toString())
            release.countDown()
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
            layout(controller.get())
            assertTrue(root.findViewWithTag<CheckBox>("task-pending-0").isChecked)
            assertTrue(root.findViewWithTag<CheckBox>("task-pending-0").isEnabled)
        } finally {
            release.countDown()
            future.get(5, java.util.concurrent.TimeUnit.SECONDS)
            controller.pause().stop().destroy()
        }
    }

    @Test fun separatesOwnersDisablesAgentAndKeepsHumanUncheckedWhenCredentialsAreMissing() {
        val context = RuntimeEnvironment.getApplication()
        val store = RoadockStore(context)
        val project = Project("roles", "ROADOCK", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("인증 구현", false),
            ChecklistItem("키 발급", false, owner = TaskOwner.HUMAN, key = "key")), 123))
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val root = activity.window.decorView
        assertEquals("Human", root.findViewWithTag<TextView>("section-HUMAN").text.toString())
        assertEquals("Agent", root.findViewWithTag<TextView>("section-AGENT").text.toString())
        val list = root.findViewWithTag<RecyclerView>("checklist")
        assertEquals(4, list.adapter!!.itemCount)
        assertEquals("키 발급", list.getChildAt(1).findViewWithTag<TextView>("text-roles-1").text.toString())
        assertEquals(View.GONE, root.findViewWithTag<View>("reorder-roles-1").visibility)
        assertFalse(root.findViewWithTag<CheckBox>("task-roles-0").isEnabled)
        assertFalse(root.findViewWithTag<SwipeTaskRow>("row-roles-1").swipeEnabled)
        assertEquals("0/2 완료", root.findViewWithTag<TextView>("page-metadata").text.toString().substringAfter(" · "))
        val human = root.findViewWithTag<CheckBox>("task-roles-1")
        assertTrue(human.isEnabled)
        human.performClick()
        assertFalse(human.isChecked)
        val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (RefreshQueue.pending(project.id) != null && System.nanoTime() < deadline) Thread.sleep(10)
        assertNull(RefreshQueue.pending(project.id))
        layout(activity)
        assertFalse(store.cache(project.id).items[1].checked)
        assertEquals(context.getString(R.string.error_write_token), store.cache(project.id).error)
        assertFalse(root.findViewWithTag<CheckBox>("task-roles-1").isChecked)
        controller.pause().stop().destroy()
    }

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

    private fun swipe(activity: MainActivity, id: String, distance: Float = -120f) {
        val root = activity.window.decorView
        val row = root.findViewWithTag<View>("row-$id")
        val position = IntArray(2)
        row.getLocationInWindow(position)
        val x = position[0] + row.width * 0.5f
        val y = position[1] + row.height / 2f
        val time = SystemClock.uptimeMillis()
        fun touch(action: Int, offset: Float) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, x + offset, y, 0)
            root.dispatchTouchEvent(event)
            event.recycle()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(32))
        }
        touch(MotionEvent.ACTION_DOWN, 0f)
        for (step in 1..4) touch(MotionEvent.ACTION_MOVE, distance * step / 4)
        touch(MotionEvent.ACTION_UP, distance)
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(200))
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
        swipe(activity, task.id)
        assertTrue(activity.window.decorView.findViewWithTag<SwipeTaskRow>("row-${task.id}").isRevealed)
        assertEquals(1, store.tasks().size)
        activity.window.decorView.findViewWithTag<View>("delete-${task.id}").performClick()
        layout(activity)
        assertTrue(store.tasks().isEmpty())
        controller.pause().stop().destroy()
    }

    @Test fun revealsOneDeleteActionAtATimeAndClosesOnReverseSwipe() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        store.addTask("첫째")
        store.addTask("둘째")
        val ids = store.tasks().map { it.id }
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val root = activity.window.decorView
        val first = root.findViewWithTag<SwipeTaskRow>("row-${ids[0]}")
        val second = root.findViewWithTag<SwipeTaskRow>("row-${ids[1]}")
        assertFalse(first.performLongClick())
        swipe(activity, ids[0])
        assertTrue(first.isRevealed)
        swipe(activity, ids[1])
        assertFalse(first.isRevealed)
        assertTrue(second.isRevealed)
        swipe(activity, ids[1], 120f)
        assertFalse(second.isRevealed)
        assertEquals(2, store.tasks().size)
        assertTrue(store.tasks().none { it.checked })
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
        assertTrue(activity.window.decorView.findViewWithTag<View>("reorder-${original[0]}")
            .performAccessibilityAction(R.id.move_task_down, null))
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

    @Test fun derivesTitleProgressForProjectAndPersonalLists() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        val project = Project("one", "Roadock", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem("완료", true), ChecklistItem("진행", false)), 1234))
        store.selectPage(project.id)
        store.addTask("개인 첫 항목")
        store.addTask("개인 둘째 항목")
        store.toggleTask(store.tasks().first().id)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val title = activity.window.decorView.findViewWithTag<TextView>("page-title")
        assertTrue(title.contentDescription.toString().startsWith("Roadock, 1/2 완료"))
        find<TabLayout>(activity.window.decorView)!!.getTabAt(1)!!.select()
        layout(activity)
        assertEquals("개인 할 일, 1/2 완료", title.contentDescription)
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
        assertEquals(2, find<ListView>(ShadowApplication.getInstance().latestPopupWindow.contentView)!!.adapter.count)
        assertNull(root.findViewWithTag<View>("add-personal-task"))
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

    @Test @Config(qualifiers = "en") fun usesEnglishLabelsAndStableFieldExamples() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val root = activity.window.decorView
        assertEquals("Personal tasks", root.findViewWithTag<TextView>("page-title").text.toString())
        find<TabLayout>(root)!!.getTabAt(0)!!.select()
        layout(activity)
        root.findViewWithTag<View>("project-actions").performClick()
        val menu = find<ListView>(ShadowApplication.getInstance().latestPopupWindow.contentView)!!
        assertEquals(listOf("Add project"), (0 until menu.adapter.count).map { menu.adapter.getItem(it).toString() })
        assertTrue(ShadowApplication.getInstance().latestPopupWindow.width < RoadockDesign.dp(activity, 196))
        selectTaskMenu(0)
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val form = dialog.window!!.decorView
        val repository = form.findViewWithTag<EditText>("project-repository")
        val container = repository.parent.parent as TextInputLayout
        val formGroup = container.parent as ViewGroup
        val nameField = form.findViewWithTag<EditText>("project-name").parent.parent as TextInputLayout
        assertTrue(formGroup.indexOfChild(container) < formGroup.indexOfChild(nameField))
        assertEquals(20f * android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f,
            form.findViewById<TextView>(androidx.appcompat.R.id.alertTitle).textSize, 0.01f)
        assertNotEquals(dialog.getButton(AlertDialog.BUTTON_POSITIVE).currentTextColor,
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).currentTextColor)
        assertEquals("GitHub repository", container.hint.toString())
        assertEquals("owner/repository", container.placeholderText.toString())
        assertFalse(container.isExpandedHintEnabled)
        for (tag in listOf("project-name", "project-token")) {
            val field = form.findViewWithTag<EditText>(tag).parent.parent as TextInputLayout
            assertNull(field.helperText)
            assertFalse(field.isHelperTextEnabled)
        }
        assertEquals("main", form.findViewWithTag<EditText>("project-branch").text.toString())
        assertEquals("TODO.md", form.findViewWithTag<EditText>("project-path").text.toString())
        dialog.dismiss()
        controller.pause().stop().destroy()
    }

    @Test fun keepsGlobalSettingsSeparateFromChecklistActionsAcrossTabs() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        val project = Project("one", "Roadock", "utact", "roadock", "main", "TODO.md")
        store.saveProject(project)
        store.selectPage(RoadockStore.PERSONAL)
        store.addTask("크기 확인")
        val widgets = shadowOf(android.appwidget.AppWidgetManager.getInstance(RuntimeEnvironment.getApplication()))
        widgets.setAlwaysRecreateViewsDuringUpdate(true)
        val widgetId = widgets.createWidget(RoadockWidget::class.java, R.layout.roadock_widget)
        store.selectWidgetPage(widgetId, RoadockStore.PERSONAL)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val root = activity.window.decorView
        val settings = root.findViewWithTag<View>("settings")
        val position = IntArray(2)
        settings.getLocationOnScreen(position)
        settings.performClick()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        val content = dialog.window!!.decorView
        assertNull(content.findViewWithTag<View>("project-name"))
        content.findViewWithTag<Slider>("widget-text-size").value = 14f
        assertEquals(14, RoadockStore(activity).widgetTextSize())
        assertEquals("14", content.findViewWithTag<TextView>("widget-text-size-value").text.toString())
        assertNull(content.findViewWithTag<View>("widget-preview"))
        val widgetList = widgets.getViewFor(widgetId).findViewById<ListView>(R.id.widget_list)
        val widgetRow = widgetList.adapter.getView(0, null, widgetList)
        assertEquals(14f * android.util.DisplayMetrics.DENSITY_DEVICE_STABLE / 160f,
            widgetRow.findViewById<TextView>(R.id.widget_row_text).textSize, 0.01f)
        content.findViewWithTag<Slider>("widget-transparency").value = 50f
        assertEquals(50, RoadockStore(activity).widgetTransparency())
        assertEquals("50%", content.findViewWithTag<TextView>("widget-transparency-value").text.toString())
        assertEquals(127, widgets.getViewFor(widgetId).findViewById<android.widget.ImageView>(R.id.widget_background).imageAlpha)
        assertEquals(1f, widgets.getViewFor(widgetId).findViewById<View>(R.id.widget_title).alpha, 0.001f)
        content.findViewWithTag<View>("font-license").performClick()
        val license = ShadowDialog.getLatestDialog() as AlertDialog
        assertNotNull(license.window!!.decorView.findViewWithTag<View>("font-license-text"))
        license.dismiss()
        dialog.dismiss()
        find<TabLayout>(root)!!.getTabAt(0)!!.select()
        layout(activity)
        val projectPosition = IntArray(2)
        settings.getLocationOnScreen(projectPosition)
        assertArrayEquals(position, projectPosition)
        assertNull(root.findViewWithTag<View>("add-personal-task"))
        assertNotNull(root.findViewWithTag<View>("project-actions"))
        settings.performClick()
        val projectSettings = ShadowDialog.getLatestDialog() as AlertDialog
        assertEquals(14f, projectSettings.window!!.decorView.findViewWithTag<Slider>("widget-text-size").value)
        assertEquals(50f, projectSettings.window!!.decorView.findViewWithTag<Slider>("widget-transparency").value)
        projectSettings.dismiss()
        controller.pause().stop().destroy()
    }

    @Test @Config(sdk = [28]) fun reflowsUnchangedLicenseTextAndSharesDialogPanelInsets() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        fun verifyPanels(dialog: AlertDialog) {
            val root = dialog.window!!.decorView
            root.measure(View.MeasureSpec.makeMeasureSpec(393, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(852, View.MeasureSpec.AT_MOST))
            root.layout(0, 0, 393, root.measuredHeight)
            val title = root.findViewById<View>(androidx.appcompat.R.id.title_template)
            val top = root.findViewById<View>(androidx.appcompat.R.id.topPanel)
            val bottom = root.findViewById<View>(androidx.appcompat.R.id.buttonPanel)
            val actions = dialog.getButton(AlertDialog.BUTTON_POSITIVE).parent as View
            assertEquals(top.width, bottom.width)
            assertEquals(RoadockDesign.dp(activity, 24), title.paddingStart)
            assertEquals(title.paddingStart, title.paddingEnd)
            assertEquals(title.paddingStart, actions.paddingStart)
            assertEquals(title.paddingEnd, actions.paddingEnd)
            assertEquals(RoadockDesign.dp(activity, 8), title.paddingBottom)
        }
        AppSettingsDialog.show(activity)
        val settings = ShadowDialog.getLatestDialog() as AlertDialog
        verifyPanels(settings)
        settings.dismiss()
        LicenseDialog.show(activity)
        val license = ShadowDialog.getLatestDialog() as AlertDialog
        verifyPanels(license)
        val content = license.window!!.decorView.findViewWithTag<TextView>("font-license-text")
        val original = activity.resources.openRawResource(R.raw.pretendard_license).bufferedReader().use { it.readText() }
        fun words(text: String) = text.replace(Regex("(?m)^-{3,}\\r?$"), "").trim().replace(Regex("\\s+"), " ")
        assertEquals(words(original), words(content.text.toString()))
        assertTrue(content.text.contains("\n\nPREAMBLE\n\n"))
        assertTrue(content.text.contains("\n\nDISCLAIMER\n\n"))
        assertFalse(content.text.contains("worldwide\ndevelopment"))
        assertEquals(android.text.Layout.JUSTIFICATION_MODE_INTER_WORD, content.justificationMode)
        assertTrue(content.isTextSelectable)
        license.dismiss()
        controller.pause().stop().destroy()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun opensUnabridgedProjectAndPersonalTextWithoutTogglingCompletion() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        val project = Project("one", "Roadock", "utact", "roadock", "main", "TODO.md")
        val projectText = "프로젝트의 전체 내용 ".repeat(30)
        val personalText = "가".repeat(24)
        store.saveProject(project)
        store.saveCache(project, ChecklistCache(listOf(ChecklistItem(projectText, false)), 1234))
        store.addTask(personalText)
        store.selectPage(project.id)
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity)
        val root = activity.window.decorView
        fun read(tag: String, expected: String) {
            val label = root.findViewWithTag<TextView>(tag)
            assertTrue(label.layout.getEllipsisCount(0) > 0)
            label.performClick()
            val dialog = ShadowDialog.getLatestDialog() as AlertDialog
            val full = dialog.window!!.decorView.findViewWithTag<TextView>("task-full-text")
            assertEquals(expected, full.text.toString())
            assertTrue(full.isTextSelectable)
            assertNull(full.ellipsize)
            dialog.dismiss()
        }
        read("text-one-0", projectText)
        find<TabLayout>(root)!!.getTabAt(1)!!.select()
        layout(activity)
        read("text-${store.tasks().single().id}", personalText)
        assertFalse(store.tasks().single().checked)
        controller.pause().stop().destroy()
    }

    @Test fun appendsNewTasksAfterTheManualOrder() {
        val store = RoadockStore(RuntimeEnvironment.getApplication())
        store.addTask("첫째")
        store.addTask("둘째")
        store.reorderTasks(store.tasks().map { it.id }.reversed())
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        layout(controller.get())
        val composer = openComposer(controller.get())
        composer.findViewWithTag<EditText>("task-input").setText("새 항목")
        composer.findViewWithTag<View>("add-task").performClick()
        layout(controller.get())
        assertEquals(listOf("둘째", "첫째", "새 항목"), store.tasks().map { it.text })
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
