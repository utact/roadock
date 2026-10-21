package dev.utact.roadock

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var store: RoadockStore
    private lateinit var tabs: TabLayout
    private lateinit var list: RecyclerView
    private lateinit var listFade: View
    private lateinit var adapter: ChecklistAdapter
    private lateinit var header: ChecklistHeader
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var empty: TextView
    private lateinit var addButton: MaterialButton
    private var selected = RoadockStore.PERSONAL
    private var personal = true
    private var bindingTabs = false
    private val refreshing = mutableSetOf<String>()
    private val changes = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "projects" || key == "cache-$selected" || (key == "tasks" && personal)) {
            if (::list.isInitialized) list.post { if (!isDestroyed) showPage() }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(RoadockDesign.fixedContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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
            list.scrollToPosition(0)
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
            addTab(newTab().setText("프로젝트"))
            addTab(newTab().setText("개인"))
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
        addButton = RoadockDesign.iconButton(this, R.drawable.ic_add, "추가", "add-project") {
            if (personal) {
                if (supportFragmentManager.findFragmentByTag(TaskComposer.TAG) == null)
                    TaskComposer().show(supportFragmentManager, TaskComposer.TAG)
            } else projectDialog()
        }
        navigation.addView(addButton, square())
        val more = RoadockDesign.iconButton(this, R.drawable.ic_more, "더 보기", "more") {}
        more.setOnClickListener { showActions(more) }
        navigation.addView(more, square())
        root.addView(navigation, LinearLayout.LayoutParams(-1, dp(56)))

        header = ChecklistHeader(this)
        root.addView(header, LinearLayout.LayoutParams(-1, -2))

        adapter = ChecklistAdapter(
            toggle = { store.toggleTask(it); RoadockWidget.updateAll(this) },
            delete = { store.deleteTask(it); RoadockWidget.updateAll(this) },
            reorder = { store.reorderTasks(it); RoadockWidget.updateAll(this) },
        )
        list = RecyclerView(this).apply {
            id = View.generateViewId()
            tag = "checklist"
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
            clipToPadding = false
            setPadding(0, 0, 0, dp(24))
        }
        adapter.touchHelper.attachToRecyclerView(list)
        empty = RoadockDesign.text(this, "", 14, RoadockDesign.secondary).apply {
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), dp(48))
        }
        listFade = View(this).apply {
            tag = "list-fade"
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(RoadockDesign.background and 0x00FFFFFF, RoadockDesign.background))
            alpha = 0f
        }
        list.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { updateListFade() }
        })
        list.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateListFade() }
        swipeRefresh = SwipeRefreshLayout(this).apply {
            tag = "swipe-refresh"
            setColorSchemeColors(RoadockDesign.text)
            setProgressBackgroundColorSchemeColor(RoadockDesign.surface)
            setOnChildScrollUpCallback { _, _ -> list.canScrollVertically(-1) }
            setOnRefreshListener { currentProject()?.let { refresh(it.id) } }
            addView(FrameLayout(this@MainActivity).apply {
                addView(list, FrameLayout.LayoutParams(-1, -1))
                addView(empty, FrameLayout.LayoutParams(-1, -1))
            }, android.view.ViewGroup.LayoutParams(-1, -1))
        }
        root.addView(FrameLayout(this).apply {
            addView(swipeRefresh, FrameLayout.LayoutParams(-1, -1))
            addView(listFade, FrameLayout.LayoutParams(-1, dp(28), Gravity.BOTTOM))
        }, LinearLayout.LayoutParams(-1, 0, 1f))

    }

    private fun currentProject() = if (personal) null else store.projects().find { it.id == selected }

    private fun showPage() {
        if (!::list.isInitialized || isDestroyed || isFinishing) return
        val projects = store.projects()
        if (!personal && selected !in projects.map { it.id }) selected = projects.firstOrNull()?.id ?: RoadockStore.PERSONAL
        bindingTabs = true
        tabs.getTabAt(if (personal) 1 else 0)?.select()
        bindingTabs = false
        addButton.contentDescription = if (personal) "할 일 추가" else "프로젝트 추가"
        addButton.tag = if (personal) "add-personal-task" else "add-project"
        val project = currentProject()
        val cache = project?.let { store.cache(it.id) } ?: ChecklistCache()
        val loading = project != null && project.id in refreshing
        swipeRefresh.isEnabled = project != null
        swipeRefresh.isRefreshing = loading
        val rows = if (personal) {
            store.tasks().map { ChecklistRow(it.id, it.text, it.checked) }
        } else {
            cache.items.mapIndexed { index, item -> ChecklistRow("${project?.id}-$index", item.text, item.checked, item.depth) }
        }
        adapter.submit(rows, personal)
        val completed = rows.count { it.checked }
        val progress = if (rows.isEmpty()) "0개" else "$completed/${rows.size} 완료"
        header.bind(if (personal) "개인 할 일" else project?.name ?: "프로젝트",
            if (personal) progress else project?.let { "${it.owner}/${it.repository} · $progress" }.orEmpty(),
            completed, rows.size, !personal, loading, cache.error) { chooseProject(header) }
        empty.text = when {
            personal -> "할 일을 추가해 주세요"
            projects.isEmpty() -> "프로젝트를 추가해 주세요"
            currentProject()?.let { store.cache(it.id).fetchedAt } == 0L -> "새로고침해 체크리스트를 가져오세요"
            else -> "체크박스 항목이 없습니다"
        }
        empty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        list.visibility = if (rows.isEmpty()) View.INVISIBLE else View.VISIBLE
        list.post { updateListFade() }
    }

    private fun updateListFade() {
        if (!::listFade.isInitialized) return
        val remaining = list.computeVerticalScrollRange() - list.computeVerticalScrollExtent() - list.computeVerticalScrollOffset()
        listFade.alpha = if (list.canScrollVertically(1)) (remaining.toFloat() / dp(28)).coerceIn(0f, 1f) else 0f
    }

    private fun chooseProject(anchor: View) {
        val projects = store.projects()
        PopupMenu(this, anchor).apply {
            projects.forEachIndexed { index, project ->
                menu.add(1, index, index, project.name).apply { isCheckable = true; isChecked = project.id == selected }
            }
            menu.setGroupCheckable(1, true, true)
            menu.add(0, projects.size, projects.size, "프로젝트 추가")
            setOnMenuItemClickListener {
                projects.getOrNull(it.itemId)?.let { project ->
                    selected = project.id
                    store.selectPage(selected)
                    showPage()
                    list.scrollToPosition(0)
                } ?: projectDialog()
                true
            }
            show()
        }
    }

    private fun showActions(anchor: View) {
        val project = currentProject()
        PopupMenu(this, anchor).apply {
            if (project != null) {
                menu.add(0, 6, 0, "새로고침").isEnabled = project.id !in refreshing
                menu.add(0, 1, 0, "프로젝트 설정")
                menu.add(0, 2, 1, "동기화 정보")
                menu.add(0, 3, 2, "프로젝트 삭제")
            }
            menu.add(0, 4, 3, "위젯 추가")
            menu.add(0, 5, 4, "폰트 라이선스")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    6 -> project?.let { refresh(it.id) }
                    1 -> projectDialog(project)
                    2 -> project?.let { value ->
                        val cache = store.cache(value.id)
                        val fetched = cache.fetchedAt
                        val date = if (fetched == 0L) "아직 조회하지 않음" else DateFormat.getDateTimeInstance(
                            DateFormat.SHORT, DateFormat.SHORT, Locale.KOREA).format(Date(fetched))
                        MaterialAlertDialogBuilder(this@MainActivity).setTitle("동기화 정보")
                            .setMessage("${value.owner}/${value.repository}\n${value.branch}\n${value.path}\n\n$date" +
                                (cache.error?.let { "\n\n$it" } ?: ""))
                            .setPositiveButton("확인", null).show()
                    }
                    3 -> project?.let { value ->
                        MaterialAlertDialogBuilder(this@MainActivity).setTitle("프로젝트 삭제")
                            .setMessage("${value.name} 등록과 캐시를 삭제할까요?")
                            .setNegativeButton("취소", null).setPositiveButton("삭제") { _, _ ->
                                store.deleteProject(value.id)
                                RefreshJobService.schedulePeriodic(this@MainActivity)
                                RoadockWidget.updateAll(this@MainActivity)
                                showPage()
                            }.show()
                    }
                    4 -> pinWidget()
                    5 -> MaterialAlertDialogBuilder(this@MainActivity).setTitle("Pretendard")
                        .setMessage(resources.openRawResource(R.raw.pretendard_license).bufferedReader().use { reader -> reader.readText() })
                        .setPositiveButton("확인", null).show()
                }
                true
            }
            show()
        }
    }

    private fun refresh(projectId: String) {
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

    private fun projectDialog(project: Project? = null) {
        val form = column().apply { setPadding(dp(24), dp(8), dp(24), dp(8)) }
        fun field(caption: String, value: String, fieldTag: String, placeholder: String = ""): TextInputEditText {
            val container = TextInputLayout(this).apply {
                hint = caption
                boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
                if (placeholder.isNotEmpty()) helperText = placeholder
            }
            val edit = TextInputEditText(container.context).apply {
                tag = fieldTag
                setText(value)
                setSingleLine()
                filters = arrayOf(InputFilter.LengthFilter(500))
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            }
            container.addView(edit)
            form.addView(container, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            return edit
        }
        val name = field("이름", project?.name.orEmpty(), "project-name", "생략하면 저장소 이름 사용")
        val repository = field("GitHub 저장소", project?.let { "${it.owner}/${it.repository}" }.orEmpty(), "project-repository", "owner/repository")
        val branch = field("브랜치", project?.branch ?: "main", "project-branch")
        val path = field("Markdown 경로", project?.path ?: "TODO.md", "project-path")
        val hasToken = project != null && store.hasToken(project.id)
        val token = field("GitHub 토큰", "", "project-token", if (hasToken) "비워 두면 저장된 토큰 유지" else "공개 저장소는 생략")
        token.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        token.isSaveEnabled = false
        token.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        val clearToken = MaterialCheckBox(this).apply { text = "저장된 토큰 삭제"; visibility = if (hasToken) View.VISIBLE else View.GONE }
        form.addView(clearToken)
        val error = RoadockDesign.text(this, "", 12, RoadockDesign.error)
        form.addView(error)
        val dialog = MaterialAlertDialogBuilder(this).setTitle(if (project == null) "프로젝트 추가" else "프로젝트 설정")
            .setView(ScrollView(this).apply { addView(form) }).setNegativeButton("취소", null).setPositiveButton("저장", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                try {
                    val parts = repository.text.toString().trim().removePrefix("https://github.com/").trimEnd('/').removeSuffix(".git").split('/')
                    require(parts.size == 2) { "저장소를 owner/repository 형식으로 입력해 주세요" }
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
                    error.text = if (failure is IllegalArgumentException) failure.message else "저장에 실패했습니다. 입력 내용을 확인해 주세요"
                }
            }
        }
        dialog.show()
    }

    private fun pinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) manager.requestPinAppWidget(ComponentName(this, RoadockWidget::class.java), null, null)
        else Toast.makeText(this, "홈 화면을 길게 눌러 위젯에서 ROADOCK을 추가해 주세요", Toast.LENGTH_LONG).show()
    }

    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun square() = LinearLayout.LayoutParams(dp(48), dp(48))
    private fun dp(value: Int) = RoadockDesign.dp(this, value)
}
