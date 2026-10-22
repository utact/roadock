package dev.utact.roadock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DateFormat
import java.util.Date

class MainActivity : AppCompatActivity() {
    private lateinit var store: RoadockStore
    private lateinit var tabs: TabLayout
    private lateinit var list: RecyclerView
    private lateinit var primaryPane: ChecklistPane
    private lateinit var ownerFilters: MaterialButtonToggleGroup
    private val filterButtons = linkedMapOf<TaskOwner?, Int>()
    private var bindingFilter = false
    private var renderedPage: String? = null
    private lateinit var adapter: ChecklistAdapter
    private lateinit var header: ChecklistHeader
    private var projectMenu: ListPopupWindow? = null
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var empty: TextView
    private lateinit var emptyState: LinearLayout
    private lateinit var registerProject: MaterialButton
    private var selected = RoadockStore.PERSONAL
    private var personal = true
    private var bindingTabs = false
    private val refreshing = mutableSetOf<String>()
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "projects" || key == "sync-state" || key == "project-filter" || key == "cache-$selected" || (key == "tasks" && personal)) {
            if (::list.isInitialized) list.post { if (!isDestroyed) showPage() }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(RoadockDesign.fixedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.AppTheme)
        super.onCreate(savedInstanceState)
        store = RoadockStore(this)
        selected = savedInstanceState?.getString("selected") ?: store.selectedPage()
        personal = savedInstanceState?.getBoolean("personal") ?: (selected == RoadockStore.PERSONAL)
        if (savedInstanceState == null) readPageIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        buildScreen()
        supportFragmentManager.setFragmentResultListener(TaskComposer.RESULT, this) { _, result ->
            store.addTask(result.getString("text").orEmpty())
            showPage()
            list.scrollToPosition(adapter.itemCount - 1)
            RoadockWidget.updateAll(this)
        }
        RefreshJobService.schedulePeriodic(this)
    }

    override fun onStart() {
        super.onStart()
        getSharedPreferences("roadock", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(changes)
    }

    override fun onResume() { super.onResume(); showPage() }

    override fun onStop() {
        projectMenu?.dismiss()
        getSharedPreferences("roadock", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(changes)
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected", selected)
        outState.putBoolean("personal", personal)
        super.onSaveInstanceState(outState)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        projectMenu?.dismiss()
        setIntent(intent)
        readPageIntent(intent)
        showPage()
    }

    private fun readPageIntent(intent: Intent) {
        intent.getStringExtra("page")?.let {
            store.selectPage(it)
            selected = store.selectedPage()
            personal = selected == RoadockStore.PERSONAL
        }
    }

    private fun buildScreen() {
        val root = column().apply { setBackgroundColor(RoadockDesign.background) }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        ViewCompat.requestApplyInsets(root)

        val navigation = row().apply { setPadding(dp(12), 0, dp(12), 0) }
        tabs = TabLayout(this).apply {
            tabMode = TabLayout.MODE_SCROLLABLE
            addTab(newTab().setText(R.string.projects))
            addTab(newTab().setText(R.string.personal))
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    if (bindingTabs) return
                    personal = tab.position == 1
                    if (personal) store.selectPage(RoadockStore.PERSONAL)
                    else {
                        if (selected !in store.projects().map { it.id }) selected = store.projects().firstOrNull()?.id ?: RoadockStore.PERSONAL
                        if (selected != RoadockStore.PERSONAL) store.selectPage(selected)
                    }
                    showPage()
                    list.scrollToPosition(0)
                }
                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            })
        }
        navigation.addView(tabs, LinearLayout.LayoutParams(0, dp(48), 1f))
        navigation.addView(RoadockDesign.iconButton(this, R.drawable.ic_settings, getString(R.string.widget_settings), "widget-settings") {
            WidgetSettingsDialog.show(this)
        }, square())
        root.addView(navigation, LinearLayout.LayoutParams(-1, dp(56)))

        header = ChecklistHeader(this)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        ownerFilters = MaterialButtonToggleGroup(this).apply {
            tag = "owner-filters"
            contentDescription = getString(R.string.task_owner_filter)
            isSingleSelection = true
            isSelectionRequired = true
            for ((owner, label) in listOf(null to R.string.filter_all, TaskOwner.HUMAN to R.string.human, TaskOwner.AGENT to R.string.agent)) {
                val buttonId = View.generateViewId()
                filterButtons[owner] = buttonId
                addView(MaterialButton(this@MainActivity, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    id = buttonId
                    tag = "filter-${owner?.name?.lowercase() ?: "all"}"
                    setText(label)
                    RoadockDesign.styleText(this, 13, RoadockDesign.text, 500)
                    isAllCaps = false
                    setSingleLine()
                    minWidth = 0
                    minimumWidth = 0
                    insetTop = 0
                    insetBottom = 0
                    cornerRadius = dp(8)
                }, LinearLayout.LayoutParams(0, dp(48), 1f))
            }
            addOnButtonCheckedListener { _, buttonId, checked ->
                if (checked && !bindingFilter) {
                    store.setProjectFilter(filterButtons.entries.first { it.value == buttonId }.key)
                    showPage()
                }
            }
        }
        root.addView(ownerFilters, LinearLayout.LayoutParams(-1, dp(48)).apply {
            marginStart = dp(24)
            marginEnd = dp(24)
            bottomMargin = dp(12)
        })

        adapter = ChecklistAdapter(
            toggle = ::toggleTask,
            delete = { store.deleteTask(it); RoadockWidget.updateAll(this) },
            reorder = { store.reorderTasks(it); RoadockWidget.updateAll(this) },
            open = ::showTask,
        )
        primaryPane = ChecklistPane(this, "checklist")
        list = primaryPane.list.apply { adapter = this@MainActivity.adapter }
        empty = RoadockDesign.text(this, "", 14, RoadockDesign.secondary).apply {
            tag = "empty-message"
            gravity = Gravity.CENTER
        }
        registerProject = MaterialButton(this).apply {
            tag = "add-project"
            setText(R.string.add_project)
            isAllCaps = false
            typeface = RoadockDesign.font(this@MainActivity, 500)
            insetTop = 0
            insetBottom = 0
            cornerRadius = dp(8)
            setOnClickListener { projectDialog() }
        }
        emptyState = column().apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), dp(48))
            addView(empty, LinearLayout.LayoutParams(-1, -2))
            addView(registerProject, LinearLayout.LayoutParams(-2, dp(48)).apply { topMargin = dp(16) })
        }
        primaryPane.body.addView(emptyState, FrameLayout.LayoutParams(-1, -1))
        swipeRefresh = SwipeRefreshLayout(this).apply {
            tag = "swipe-refresh"
            setColorSchemeColors(RoadockDesign.text)
            setProgressBackgroundColorSchemeColor(RoadockDesign.surface)
            setOnChildScrollUpCallback { _, _ -> list.canScrollVertically(-1) }
            setOnRefreshListener { currentProject()?.let { refresh(it.id) } }
            addView(primaryPane, android.view.ViewGroup.LayoutParams(-1, -1))
        }
        root.addView(swipeRefresh, LinearLayout.LayoutParams(-1, 0, 1f))

    }

    private fun currentProject() = if (personal) null else store.projects().find { it.id == selected }

    private fun showPage() {
        if (!::list.isInitialized || isDestroyed || isFinishing) return
        val projects = store.projects()
        if (!personal && selected !in projects.map { it.id }) selected = projects.firstOrNull()?.id ?: RoadockStore.PERSONAL
        bindingTabs = true
        tabs.getTabAt(if (personal) 1 else 0)?.select()
        bindingTabs = false
        val project = currentProject()
        if (project == null) projectMenu?.dismiss()
        val cache = project?.let { store.cache(it.id) } ?: ChecklistCache()
        val loading = project != null && project.id in refreshing
        val pending = project?.let { RefreshQueue.pending(it.id) }
        val saving = pending != null
        swipeRefresh.isEnabled = project != null && !saving
        swipeRefresh.isRefreshing = loading
        val ownerFilter = store.projectFilter()
        ownerFilters.visibility = if (project != null) View.VISIBLE else View.GONE
        bindingFilter = true
        ownerFilters.check(filterButtons.getValue(ownerFilter))
        bindingFilter = false
        val rows = if (personal) {
            store.tasks().map { ChecklistRow(it.id, it.text, it.checked) }
        } else {
            val dependencies = TaskDependencies(cache.items)
            val tasks = cache.items.mapIndexed { index, item -> ChecklistRow("${project?.id}-$index", item.text,
                item.checked, item.depth, project?.id, item, item.owner == TaskOwner.HUMAN && !saving && !loading,
                dependency = dependencies.status(item), saving = item.owner == TaskOwner.HUMAN && item == pending)
            }.filter { ownerFilter == null || it.source?.owner == ownerFilter }
            if (ownerFilter != null || tasks.none { it.source?.owner == TaskOwner.HUMAN }) tasks else buildList {
                for (owner in TaskOwner.entries) {
                    val group = tasks.filter { it.source?.owner == owner }
                    if (group.isNotEmpty()) {
                        add(ChecklistRow("section-$owner", "", false, section = owner))
                        addAll(group)
                    }
                }
            }
        }
        val displayPage = if (personal) RoadockStore.PERSONAL else "$selected:${ownerFilter?.name ?: "ALL"}"
        if (renderedPage != displayPage) {
            list.scrollToPosition(0)
        }
        renderedPage = displayPage
        adapter.submit(rows, personal)
        val total = if (personal) rows.size else cache.items.size
        val completed = if (personal) rows.count { it.checked } else cache.items.count { it.checked }
        val progress = if (total == 0) getString(R.string.empty_count) else getString(R.string.completion_count, completed, total)
        header.bind(if (personal) getString(R.string.personal_tasks) else project?.name ?: getString(R.string.projects),
            if (personal) progress else project?.let { "${it.owner}/${it.repository} · $progress" }.orEmpty(),
            completed, total, project != null, loading, cache.error, ::chooseProject, saving)
        header.setAction(if (personal) R.drawable.ic_add else R.drawable.ic_more,
            getString(if (personal) R.string.add_task else R.string.project_actions),
            if (personal) "add-personal-task" else "project-actions", visible = personal || project != null) { anchor ->
            if (personal) {
                if (supportFragmentManager.findFragmentByTag(TaskComposer.TAG) == null)
                    TaskComposer().show(supportFragmentManager, TaskComposer.TAG)
            } else showProjectActions(anchor)
        }
        empty.text = when {
            personal -> getString(R.string.empty_tasks)
            projects.isEmpty() -> getString(R.string.empty_projects)
            cache.fetchedAt == 0L -> getString(R.string.refresh_prompt)
            ownerFilter != null && cache.items.isNotEmpty() -> getString(R.string.empty_owner_tasks,
                getString(if (ownerFilter == TaskOwner.HUMAN) R.string.human else R.string.agent))
            else -> getString(R.string.empty_checklist)
        }
        emptyState.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        registerProject.visibility = if (!personal && projects.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (rows.isEmpty()) View.INVISIBLE else View.VISIBLE
        list.post { updateListFade() }
    }

    private fun updateListFade() {
        primaryPane.updateFade()
    }

    private fun chooseProject(anchor: View) {
        if (projectMenu?.isShowing == true) {
            projectMenu?.dismiss()
            return
        }
        projectMenu = RoadockMenu.show(this, anchor, store.projects().map { project ->
            RoadockMenuItem(project.name, checked = project.id == selected) {
                selected = project.id
                store.selectPage(selected)
                showPage()
                list.scrollToPosition(0)
            }
        } + RoadockMenuItem(getString(R.string.add_project), separated = true) { projectDialog() },
            onDismiss = {
                projectMenu = null
                header.setProjectMenuExpanded(false)
            })
        header.setProjectMenuExpanded(projectMenu?.isShowing == true)
    }

    private fun showProjectActions(anchor: View) {
        val project = currentProject()
        val items = mutableListOf<RoadockMenuItem>()
        if (project != null) {
            val idle = RefreshQueue.pending(project.id) == null
            items += RoadockMenuItem(getString(R.string.refresh), enabled = idle && project.id !in refreshing) { refresh(project.id) }
            items += RoadockMenuItem(getString(R.string.edit), enabled = idle) { projectDialog(project) }
            items += RoadockMenuItem(getString(R.string.delete), enabled = idle, separated = true, destructive = true) {
                MaterialAlertDialogBuilder(this).setTitle(R.string.delete_project)
                    .setMessage(getString(R.string.delete_project_message, project.name))
                    .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.delete) { _, _ ->
                        if (RefreshQueue.pending(project.id) != null) return@setPositiveButton
                        store.deleteProject(project.id)
                        RefreshJobService.schedulePeriodic(this)
                        RoadockWidget.updateAll(this)
                        showPage()
                    }.show().getButton(android.content.DialogInterface.BUTTON_POSITIVE).setTextColor(RoadockDesign.error)
            }
        }
        RoadockMenu.show(this, anchor, items, Gravity.END)
    }

    private fun showTask(item: ChecklistRow) {
        val content = column().apply {
            setPadding(dp(24), dp(24), dp(24), dp(8))
            addView(RoadockDesign.text(this@MainActivity, getString(when (item.source?.owner) {
                TaskOwner.HUMAN -> R.string.human
                TaskOwner.AGENT -> R.string.agent
                null -> R.string.task_label
            }), 12, RoadockDesign.secondary, 500).apply { tag = "task-owner" }, LinearLayout.LayoutParams(-1, -2))
            addView(RoadockDesign.text(this@MainActivity, item.text, 20, weight = 500).apply {
                tag = "task-full-text"
                setTextIsSelectable(true)
                ViewCompat.setAccessibilityHeading(this, true)
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            val supporting = if (item.saving) getString(R.string.saving) else item.dependency.description(this@MainActivity)
            supporting.takeIf { it.isNotEmpty() }?.let { description ->
                addView(RoadockDesign.text(this@MainActivity, description, 13,
                    when {
                        item.saving -> RoadockDesign.secondary
                        item.dependency.unresolved.isEmpty() -> 0xFFD4BD8A.toInt()
                        else -> RoadockDesign.error
                    }).apply {
                    tag = "task-dependency"
                    setTextIsSelectable(true)
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            }
            if (item.dependency.unresolved.isNotEmpty()) addView(RoadockDesign.text(this@MainActivity,
                getString(R.string.dependency_references, item.dependency.unresolved.joinToString(", ")),
                12, RoadockDesign.secondary).apply { setTextIsSelectable(true) },
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            item.source?.details?.takeIf { it.isNotBlank() }?.let { details ->
                val project = store.projects().find { it.id == item.projectId }
                addView(RoadockDesign.text(this@MainActivity, "", 14, RoadockDesign.secondary).apply {
                    tag = "task-details"
                    text = TaskDetailText.format(details, project)
                    setTextIsSelectable(true)
                    movementMethod = LinkMovementMethod.getInstance()
                    setLinkTextColor(RoadockDesign.text)
                    TextViewCompat.setLineHeight(this, dp(22))
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
            }
        }
        ViewCompat.setAccessibilityPaneTitle(content, item.text)
        MaterialAlertDialogBuilder(this)
            .setView(ScrollView(this).apply { addView(content) })
            .setPositiveButton(R.string.close, null).show()
    }

    private fun refresh(projectId: String) {
        if (RefreshQueue.pending(projectId) != null) return
        if (!refreshing.add(projectId)) return
        showPage()
        RefreshQueue.submit(this, projectId) {
            runOnUiThread {
                if (!isDestroyed) {
                    refreshing.remove(projectId)
                    showPage()
                }
            }
        }
    }

    private fun toggleTask(row: ChecklistRow) {
        val source = row.source
        if (source == null) {
            store.toggleTask(row.id)
            RoadockWidget.updateAll(this)
            return
        }
        if (source.owner != TaskOwner.HUMAN || !row.canToggle) return
        val project = store.projects().find { it.id == row.projectId } ?: return
        RefreshQueue.setHumanChecked(this, project, source, !source.checked) { failure ->
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                showPage()
                if (failure != null) {
                    val message = store.cache(project.id).error ?: errorMessage(failure)
                    val notice = Snackbar.make(list, message, Snackbar.LENGTH_LONG).setTextMaxLines(3)
                    if (failure is FetchException && failure.text in listOf(R.string.error_write_token, R.string.error_write_access))
                        notice.setAction(R.string.project_settings) { projectDialog(project) }
                    else notice.setAction(R.string.refresh) { refresh(project.id) }
                    notice.show()
                }
            }
        }
        showPage()
    }

    private fun projectDialog(project: Project? = null) {
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        fun field(caption: Int, value: String, fieldTag: String, example: Int): TextInputEditText {
            val container = TextInputLayout(this).apply {
                hint = getString(caption)
                isExpandedHintEnabled = false
                placeholderText = getString(example)
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
            }
            val edit = TextInputEditText(container.context).apply {
                tag = fieldTag
                setText(value)
                setSingleLine()
                filters = arrayOf(InputFilter.LengthFilter(500))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, RoadockDesign.body.toFloat())
            }
            container.addView(edit)
            form.addView(container, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            return edit
        }
        val repository = field(R.string.repository_label, project?.let { "${it.owner}/${it.repository}" }.orEmpty(), "project-repository", R.string.repository_example)
        val branch = field(R.string.branch_label, project?.branch ?: "main", "project-branch", R.string.branch_example)
        val path = field(R.string.path_label, project?.path ?: "TODO.md", "project-path", R.string.path_example)
        val name = field(R.string.project_name, project?.name.orEmpty(), "project-name", R.string.name_example)
        (name.parent.parent as TextInputLayout).let { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(24) }
        val hasToken = project != null && store.hasToken(project.id)
        val token = field(R.string.token_label, "", "project-token", R.string.token_example)
        token.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        token.isSaveEnabled = false
        token.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        val clearToken = MaterialCheckBox(this).apply { setText(R.string.remove_token); visibility = if (hasToken) View.VISIBLE else View.GONE }
        form.addView(clearToken)
        if (project != null) {
            val cache = store.cache(project.id)
            val date = if (cache.fetchedAt == 0L) getString(R.string.not_fetched) else DateFormat.getDateTimeInstance(
                DateFormat.SHORT, DateFormat.SHORT, resources.configuration.locales[0]).format(Date(cache.fetchedAt))
            form.addView(RoadockDesign.text(this, "${getString(R.string.last_refreshed)} · $date", 12, RoadockDesign.secondary).apply {
                tag = "last-refreshed"
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16); bottomMargin = dp(8) })
        }
        val error = RoadockDesign.text(this, "", 12, RoadockDesign.error)
        form.addView(error)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(if (project == null) R.string.add_project else R.string.project_settings)
            .setView(ScrollView(this).apply { addView(form) }).setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                try {
                    requireInput(project == null || RefreshQueue.pending(project.id) == null, R.string.error_write_busy)
                    val parts = repository.text.toString().trim().removePrefix("https://github.com/").trimEnd('/').removeSuffix(".git").split('/')
                    requireInput(parts.size == 2, R.string.error_repository_format)
                    val updated = Project(project?.id ?: java.util.UUID.randomUUID().toString(),
                        name.text.toString().trim().ifBlank { parts[1] }, parts[0], parts[1], branch.text.toString().trim(), path.text.toString().trim())
                    updated.validate()
                    val newToken = when { clearToken.isChecked -> ""; !token.text.isNullOrBlank() -> token.text.toString().trim(); else -> null }
                    store.saveProject(updated, newToken)
                    RefreshJobService.schedulePeriodic(this)
                    selected = updated.id
                    personal = false
                    store.selectPage(selected)
                    RoadockWidget.updateAll(this)
                    dialog.dismiss()
                    refresh(updated.id)
                } catch (failure: Exception) {
                    error.text = if (failure is ValidationException) errorMessage(failure) else getString(R.string.error_save)
                }
            }
        }
        dialog.show()
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun square() = LinearLayout.LayoutParams(dp(48), dp(48))
    private fun dp(value: Int) = RoadockDesign.dp(this, value)
}
