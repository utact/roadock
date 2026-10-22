package dev.utact.roadock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService

class RoadockWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        RefreshJobService.schedulePeriodic(context)
        ids.forEach { update(context, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action !in listOf(PREVIOUS, NEXT)) return
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return
        val store = RoadockStore(context)
        val page = store.widgetPage(id)
        val pages = store.pageIds()
        val index = pages.indexOf(page) + if (intent.action == NEXT) 1 else -1
        if (index !in pages.indices) return
        store.selectWidgetPage(id, pages[index])
        update(context, id)
    }

    override fun onDeleted(context: Context, ids: IntArray) {
        val store = RoadockStore(context)
        ids.forEach {
            store.deleteWidget(it)
        }
    }

    companion object {
        const val PREVIOUS = "dev.utact.roadock.PREVIOUS"
        const val NEXT = "dev.utact.roadock.NEXT"
        const val MAX_ROWS = 100
        private val checkboxes = mutableMapOf<Boolean, Bitmap>()
        private fun pixels(value: Int) = value * DisplayMetrics.DENSITY_DEVICE_STABLE / 160f

        private fun checkbox(checked: Boolean): Bitmap = synchronized(checkboxes) {
            checkboxes.getOrPut(checked) {
                Bitmap.createBitmap(pixels(16).toInt(), pixels(16).toInt(), Bitmap.Config.ARGB_8888).apply {
                    density = Bitmap.DENSITY_NONE
                    val canvas = Canvas(this)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (checked) 0xFF808080.toInt() else 0xFFB8B8B8.toInt()
                        style = if (checked) Paint.Style.FILL else Paint.Style.STROKE
                        strokeWidth = pixels(1)
                    }
                    canvas.drawRoundRect(RectF(pixels(1), pixels(1), pixels(15), pixels(15)), pixels(2), pixels(2), paint)
                    if (checked) {
                        paint.color = Color.WHITE
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = pixels(2)
                        paint.strokeCap = Paint.Cap.ROUND
                        paint.strokeJoin = Paint.Join.ROUND
                        canvas.drawPath(Path().apply { moveTo(pixels(4), pixels(8)); lineTo(pixels(7), pixels(11)); lineTo(pixels(12), pixels(5)) }, paint)
                    }
                }
            }
        }

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            manager.getAppWidgetIds(ComponentName(context, RoadockWidget::class.java)).forEach { update(context, it) }
        }

        fun rows(context: Context, page: String): List<ChecklistItem> {
            val store = RoadockStore(context)
            return if (page == RoadockStore.PERSONAL) {
                store.tasks().map { ChecklistItem(it.text, it.checked) }
            } else store.cache(page).items
        }

        fun rowView(context: Context, item: ChecklistItem): RemoteViews =
            RemoteViews(context.packageName, R.layout.widget_row).apply {
                setTextViewText(R.id.widget_row_text, item.text.take(300))
                setTextViewTextSize(R.id.widget_row_text, TypedValue.COMPLEX_UNIT_PX, pixels(12))
                setInt(R.id.widget_row_text, "setHeight", pixels(28).toInt())
                setTextColor(R.id.widget_row_text, if (item.checked) 0xFF909090.toInt() else 0xFFC8C8C8.toInt())
                setImageViewBitmap(R.id.widget_check, checkbox(item.checked))
                setViewPadding(R.id.widget_row, pixels(item.depth.coerceIn(0, 4) * 8).toInt(), 0, 0, 0)
                setViewPadding(R.id.widget_row_text, pixels(10).toInt(), 0, 0, 0)
            }

        fun update(context: Context, widgetId: Int) {
            val manager = AppWidgetManager.getInstance(context)
            manager.updateAppWidget(widgetId, views(context, widgetId))
            if (Build.VERSION.SDK_INT < 31) {
                @Suppress("DEPRECATION")
                manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_list)
            }
        }

        fun views(context: Context, widgetId: Int): RemoteViews {
            val store = RoadockStore(context)
            val page = store.widgetPage(widgetId)
            val pages = store.pageIds()
            val pageIndex = pages.indexOf(page)
            val project = store.projects().find { it.id == page }
            val cache = store.cache(page)
            val allRows = rows(context, page)
            val views = RemoteViews(context.packageName, R.layout.roadock_widget)
            val title = project?.name ?: context.getString(R.string.personal_tasks)
            views.setTextViewText(R.id.widget_title, if (cache.error == null) title else context.getString(R.string.refresh_failed_title, title))
            val status = when {
                cache.error != null -> cache.error
                allRows.size > MAX_ROWS -> context.getString(R.string.widget_limit, MAX_ROWS)
                else -> ""
            }
            views.setTextViewText(R.id.widget_status, status)
            views.setViewVisibility(R.id.widget_status, View.GONE)
            views.setContentDescription(R.id.widget_title, context.getString(R.string.widget_open, title) + if (status.isBlank()) "" else ", $status")
            views.setTextViewTextSize(R.id.widget_title, TypedValue.COMPLEX_UNIT_PX, pixels(18))
            views.setTextViewTextSize(R.id.widget_status, TypedValue.COMPLEX_UNIT_PX, pixels(12))
            views.setTextViewTextSize(R.id.widget_empty, TypedValue.COMPLEX_UNIT_PX, pixels(13))
            views.setViewPadding(R.id.widget_root, pixels(16).toInt(), pixels(4).toInt(), pixels(16).toInt(), pixels(12).toInt())
            views.setInt(R.id.widget_title, "setHeight", pixels(48).toInt())
            listOf(R.id.widget_previous, R.id.widget_next).forEach {
                views.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_PX, pixels(24))
                views.setInt(it, "setWidth", pixels(48).toInt())
                views.setInt(it, "setHeight", pixels(48).toInt())
            }
            views.setTextViewText(R.id.widget_empty, when {
                page == RoadockStore.PERSONAL -> context.getString(R.string.widget_empty_tasks)
                cache.fetchedAt == 0L -> context.getString(R.string.refresh_prompt)
                else -> context.getString(R.string.empty_checklist)
            })
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)
            if (Build.VERSION.SDK_INT >= 31) {
                val items = RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(false).setViewTypeCount(1)
                allRows.take(MAX_ROWS).forEachIndexed { index, item -> items.addItem(index.toLong(), rowView(context, item)) }
                views.setRemoteAdapter(R.id.widget_list, items.build())
            } else {
                val adapter = Intent(context, ChecklistViewsService::class.java).apply {
                    putExtra("page", page)
                    data = Uri.parse("roadock://list/$widgetId/$page")
                }
                @Suppress("DEPRECATION")
                views.setRemoteAdapter(R.id.widget_list, adapter)
            }
            fun activityIntent(): PendingIntent {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra("page", page)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    data = Uri.parse("roadock://open/$widgetId/$page")
                }
                return PendingIntent.getActivity(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            views.setOnClickPendingIntent(R.id.widget_title, activityIntent())
            listOf(R.id.widget_previous to PREVIOUS, R.id.widget_next to NEXT).forEach { (viewId, action) ->
                val enabled = if (action == PREVIOUS) pageIndex > 0 else pageIndex < pages.lastIndex
                views.setBoolean(viewId, "setEnabled", enabled)
                views.setTextColor(viewId, if (enabled) 0xFFB8B8B8.toInt() else 0xFF505050.toInt())
                val intent = Intent(context, RoadockWidget::class.java).apply {
                    this.action = action
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    data = Uri.parse("roadock://action/$widgetId/$action")
                }
                views.setOnClickPendingIntent(viewId, if (enabled) PendingIntent.getBroadcast(context, widgetId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) else null)
            }
            return views
        }
    }
}

class ChecklistViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = object : RemoteViewsFactory {
        private val page = intent.getStringExtra("page") ?: RoadockStore.PERSONAL
        private var items = emptyList<ChecklistItem>()
        override fun onCreate() { onDataSetChanged() }
        override fun onDataSetChanged() { items = RoadockWidget.rows(applicationContext, page).take(RoadockWidget.MAX_ROWS) }
        override fun onDestroy() { items = emptyList() }
        override fun getCount() = items.size
        override fun getViewAt(position: Int) = items.getOrNull(position)?.let { RoadockWidget.rowView(applicationContext, it) }
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
    }
}
