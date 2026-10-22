package dev.utact.roadock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
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
    private lateinit var agentPane: ChecklistPane
    private lateinit var paneDivider: View
    private lateinit var agentAdapter: ChecklistAdapter
    private var splitLayout = false
    private var renderedPage: String? = null
    private lateinit var adapter: ChecklistAdapter
    private lateinit var header: ChecklistHeader
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var empty: TextView
    private lateinit var emptyState: LinearLayout
    private lateinit var registerProject: MaterialButton
    private var selected = RoadockStore.PERSONAL
    private var personal = true
    private var bindingTabs = false
    private val refreshing = mutableSetOf<String>()
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "projects" || key == "sync-state" || key == "split-projects" || key == "cache-$selected" || (key == "tasks" && personal)) {
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
        navigation.addView(RoadockDesign.iconButton(this, R.drawable.ic_settings, getString(R.string.settings), "settings") {
            AppSettingsDialog.show(this)
        }, square())
        root.addView(navigation, LinearLayout.LayoutParams(-1, dp(56)))

        header = ChecklistHeader(this)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        adapter = ChecklistAdapter(
            toggle = ::toggleTask,
            delete = { store.deleteTask(it); RoadockWidget.updateAll(this) },
            reorder = { store.reorderTasks(it); RoadockWidget.updateAll(this) },
            open = ::showTask,
        )
        agentAdapter = ChecklistAdapter(::toggleTask, {}, {}, ::showTask)
        primaryPane = ChecklistPane(this, "checklist")
        agentPane = ChecklistPane(this, "agent-checklist").apply { visibility = View.GONE }
        list = primaryPane.list.apply { adapter = this@MainActivity.adapter }
        agentPane.list.adapter = agentAdapter
        primaryPane.heading.apply { setText(R.string.human); tag = "split-human-heading" }
        agentPane.heading.apply { setText(R.string.agent); tag = "split-agent-heading" }
        paneDivider = View(this).apply { setBackgroundColor(RoadockDesign.divider); visibility = View.GONE }
        empty = RoadockDesign.text(this, "", 14, RoadockDesign.secondary).apply {
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
            setOnChildScrollUpCallback { _, _ -> list.canScrollVertically(-1) ||
                (splitLayout && agentPane.list.canScrollVertically(-1)) }
            setOnRefreshListener { currentProject()?.let { refresh(it.id) } }
            addView(row().apply {
                addView(primaryPane, LinearLayout.LayoutParams(0, -1, 1f))
                addView(paneDivider, LinearLayout.LayoutParams(dp(1), -1))
                addView(agentPane, LinearLayout.LayoutParams(0, -1, 1f))
            }, android.view.ViewGroup.LayoutParams(-1, -1))
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
        val cache = project?.let { store.cache(it.id) } ?: ChecklistCache()
        val loading = project != null && project.id in refreshing
        val saving = project != null && RefreshQueue.pending(project.id) != null
        swipeRefresh.isEnabled = project != null && !saving
        swipeRefresh.isRefreshing = loading
        val rows = if (personal) {
            store.tasks().map { ChecklistRow(it.id, it.text, it.checked) }
        } else {
            val dependencies = TaskDependencies(cache.items)
            val tasks = cache.items.mapIndexed { index, item -> ChecklistRow("${project?.id}-$index", item.text,
                item.checked, item.depth, project?.id, item, item.owner == TaskOwner.HUMAN && !saving && !loading,
                dependency = dependencies.status(item)) }
            if (tasks.none { it.source?.owner == TaskOwner.HUMAN }) tasks else buildList {
                for (owner in TaskOwner.entries) {
                    val group = tasks.filter { it.source?.owner == owner }
                    if (group.isNotEmpty()) {
                        add(ChecklistRow("section-$owner", "", false, section = owner))
                        addAll(group)
                    }
                }
            }
        }
        val split = !personal && store.splitProjects() &&
            cache.items.any { it.owner == TaskOwner.HUMAN } && cache.items.any { it.owner == TaskOwner.AGENT }
        val displayPage = if (personal) RoadockStore.PERSONAL else selected
        if (split != splitLayout || renderedPage != displayPage) {
            list.scrollToPosition(0)
            agentPane.list.scrollToPosition(0)
        }
        splitLayout = split
        renderedPage = displayPage
        primaryPane.heading.visibility = if (split) View.VISIBLE else View.GONE
        agentPane.heading.visibility = if (split) View.VISIBLE else View.GONE
        agentPane.visibility = if (split) View.VISIBLE else View.GONE
        paneDivider.visibility = if (split) View.VISIBLE else View.GONE
        adapter.submit(if (split) rows.filter { it.section == null && it.source?.owner == TaskOwner.HUMAN } else rows, personal, split)
        agentAdapter.submit(if (split) rows.filter { it.section == null && it.source?.owner == TaskOwner.AGENT } else emptyList(), false, split)
        val total = rows.count { it.section == null }
        val completed = rows.count { it.checked }
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
            else -> getString(R.string.empty_checklist)
        }
        emptyState.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        registerProject.visibility = if (!personal && projects.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (rows.isEmpty()) View.INVISIBLE else View.VISIBLE
        list.post { updateListFade() }
    }

    private fun updateListFade() {
        primaryPane.updateFade()
        agentPane.updateFade()
    }

    private fun chooseProject(anchor: View) {
        RoadockMenu.show(this, anchor, store.projects().map { project ->
            RoadockMenuItem(project.name, checked = project.id == selected) {
                selected = project.id
                store.selectPage(selected)
                showPage()
                list.scrollToPosition(0)
            }
        } + RoadockMenuItem(getString(R.string.add_project), separated = true) { projectDialog() })
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
        val detail = buildList {
            add(item.text)
            item.dependency.description(this@MainActivity).takeIf { it.isNotEmpty() }?.let { add(it) }
            if (item.dependency.unresolved.isNotEmpty()) add(getString(R.string.dependency_references,
                item.dependency.unresolved.joinToString(", ")))
        }.joinToString("\n\n")
        val content = RoadockDesign.text(this, detail).apply {
            tag = "task-full-text"
            setTextIsSelectable(true)
            setPadding(dp(24), dp(8), dp(24), dp(24))
        }
        MaterialAlertDialogBuilder(this).setTitle(when (item.source?.owner) {
            TaskOwner.HUMAN -> R.string.human
            TaskOwner.AGENT -> R.string.agent
            null -> R.string.task_label
        })
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
