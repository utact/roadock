package dev.utact.roadock

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox

data class ChecklistRow(val id: String, val text: String, val checked: Boolean, val depth: Int = 0)

class ChecklistAdapter(
    private val toggle: (String) -> Unit,
    private val delete: (String) -> Unit,
    private val reorder: (List<String>) -> Unit,
    private val open: (ChecklistRow) -> Unit,
) : RecyclerView.Adapter<ChecklistAdapter.Holder>() {
    private val rows = mutableListOf<ChecklistRow>()
    private val stableIds = mutableMapOf<String, Long>()
    private var nextId = 0L
    var editable = false
        private set
    private var dragging = false
    private var revealed: SwipeTaskRow? = null
    private val outsideTouch = object : RecyclerView.SimpleOnItemTouchListener() {
        override fun onInterceptTouchEvent(recycler: RecyclerView, event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) revealed?.let { row ->
                val bounds = android.graphics.Rect()
                row.getHitRect(bounds)
                if (!bounds.contains(event.x.toInt(), event.y.toInt())) closeRevealed()
            }
            return false
        }
    }
    private val scroll = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recycler: RecyclerView, state: Int) {
            if (state != RecyclerView.SCROLL_STATE_IDLE) closeRevealed()
        }
    }

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
        override fun isLongPressDragEnabled() = false
        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int =
            if (editable) super.getMovementFlags(recyclerView, viewHolder) else 0

        override fun onMove(recycler: RecyclerView, source: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = source.bindingAdapterPosition
            val to = target.bindingAdapterPosition
            if (from !in rows.indices || to !in rows.indices) return false
            rows.add(to, rows.removeAt(from))
            notifyItemMoved(from, to)
            return true
        }

        override fun onSwiped(holder: RecyclerView.ViewHolder, direction: Int) = Unit

        override fun onSelectedChanged(holder: RecyclerView.ViewHolder?, state: Int) {
            super.onSelectedChanged(holder, state)
            if (state == ItemTouchHelper.ACTION_STATE_DRAG) {
                closeRevealed()
                dragging = true
                (holder as? Holder)?.root?.content?.setBackgroundColor(RoadockDesign.surface)
            }
        }

        override fun clearView(recycler: RecyclerView, holder: RecyclerView.ViewHolder) {
            super.clearView(recycler, holder)
            (holder as? Holder)?.root?.content?.setBackgroundColor(RoadockDesign.background)
            if (dragging) {
                dragging = false
                val ids = rows.map { it.id }
                recycler.post { notifyItemRangeChanged(0, itemCount); reorder(ids) }
            }
        }
    })

    init { setHasStableIds(true) }

    override fun onAttachedToRecyclerView(recycler: RecyclerView) {
        super.onAttachedToRecyclerView(recycler)
        touchHelper.attachToRecyclerView(recycler)
        recycler.addOnItemTouchListener(outsideTouch)
        recycler.addOnScrollListener(scroll)
    }

    override fun onDetachedFromRecyclerView(recycler: RecyclerView) {
        closeRevealed()
        touchHelper.attachToRecyclerView(null)
        recycler.removeOnItemTouchListener(outsideTouch)
        recycler.removeOnScrollListener(scroll)
        super.onDetachedFromRecyclerView(recycler)
    }

    private fun closeRevealed() { revealed?.close(); revealed = null }

    fun submit(items: List<ChecklistRow>, canEdit: Boolean) {
        if (dragging || (items == rows && editable == canEdit)) return
        closeRevealed()
        editable = canEdit
        rows.clear()
        rows.addAll(items)
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size
    override fun getItemId(position: Int): Long = stableIds.getOrPut(rows[position].id) { nextId++ }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder = Holder(parent)

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = rows[position]
        holder.root.tag = "row-${item.id}"
        holder.root.close(false)
        holder.root.swipeEnabled = editable
        holder.root.onReveal = { row -> if (revealed !== row) revealed?.close(); revealed = row }
        holder.root.deleteButton.tag = "delete-${item.id}"
        holder.root.deleteButton.contentDescription = holder.root.context.getString(R.string.delete_task, item.text)
        holder.root.deleteButton.setOnClickListener { delete(item.id) }
        holder.root.content.setPadding(RoadockDesign.dp(holder.root.context, RoadockDesign.screenInset - 12 + item.depth * 12),
            0, RoadockDesign.dp(holder.root.context, 12), 0)
        holder.check.setOnCheckedChangeListener(null)
        holder.check.tag = "task-${item.id}"
        holder.check.isChecked = item.checked
        holder.check.isEnabled = editable
        holder.check.contentDescription = if (editable) item.text else holder.root.context.getString(R.string.read_only_task, item.text)
        holder.check.setOnCheckedChangeListener { _, _ -> if (editable) toggle(item.id) }
        holder.label.text = item.text
        holder.label.setTextColor(if (item.checked) RoadockDesign.secondary else RoadockDesign.text)
        holder.label.tag = "text-${item.id}"
        holder.label.setOnClickListener { open(item) }
        ViewCompat.replaceAccessibilityAction(holder.label, AccessibilityActionCompat.ACTION_CLICK,
            holder.root.context.getString(R.string.view_full_text)) { _, _ -> open(item); true }
        holder.handle.visibility = if (editable) View.VISIBLE else View.GONE
        holder.handle.contentDescription = holder.root.context.getString(R.string.reorder_task, item.text)
        holder.handle.tag = "reorder-${item.id}"
        holder.handle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
            true
        }
        fun moveAction(id: Int, label: Int, enabled: Boolean, delta: Int) {
            ViewCompat.removeAccessibilityAction(holder.handle, id)
            if (editable && enabled) ViewCompat.replaceAccessibilityAction(holder.handle,
                AccessibilityActionCompat(id, null), holder.root.context.getString(label)) { _, _ -> move(item.id, delta); true }
        }
        moveAction(R.id.move_task_up, R.string.move_up, position > 0, -1)
        moveAction(R.id.move_task_down, R.string.move_down, position < rows.lastIndex, 1)
        ViewCompat.removeAccessibilityAction(holder.check, AccessibilityActionCompat.ACTION_DISMISS.id)
        if (editable) ViewCompat.replaceAccessibilityAction(holder.check, AccessibilityActionCompat.ACTION_DISMISS,
            holder.root.context.getString(R.string.delete)) { _, _ -> delete(item.id); true }
        holder.root.setOnClickListener { open(item) }
        holder.root.isClickable = true
        holder.root.isLongClickable = false
        holder.root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun move(id: String, delta: Int) {
        val index = rows.indexOfFirst { it.id == id }
        val destination = index + delta
        if (index !in rows.indices || destination !in rows.indices) return
        rows.add(destination, rows.removeAt(index))
        notifyItemMoved(index, destination)
        notifyItemRangeChanged(minOf(index, destination), 2)
        reorder(rows.map { it.id })
    }

    class Holder(parent: ViewGroup) : RecyclerView.ViewHolder(SwipeTaskRow(parent.context)) {
        val root = itemView as SwipeTaskRow
        val check = MaterialCheckBox(parent.context)
        val label = RoadockDesign.text(parent.context, "")
        val handle = ImageView(parent.context).apply {
            setImageResource(R.drawable.ic_drag)
            scaleType = ImageView.ScaleType.CENTER
            setColorFilter(RoadockDesign.secondary)
            isFocusable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }

        init {
            val context = parent.context
            root.layoutParams = RecyclerView.LayoutParams(-1, RoadockDesign.dp(context, RoadockDesign.listRow))
            root.dragHandle = handle
            check.setUseMaterialThemeColors(false)
            check.buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(RoadockDesign.text, RoadockDesign.outline),
            )
            check.setCenterIfNoTextEnabled(true)
            root.content.addView(check, LinearLayout.LayoutParams(RoadockDesign.dp(context, 48), RoadockDesign.dp(context, 48)))
            label.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            label.setSingleLine()
            label.ellipsize = android.text.TextUtils.TruncateAt.END
            label.setPadding(0, RoadockDesign.dp(context, 8), RoadockDesign.dp(context, 8), RoadockDesign.dp(context, 8))
            root.content.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            root.content.addView(handle, LinearLayout.LayoutParams(RoadockDesign.dp(context, 48), RoadockDesign.dp(context, 48)))
        }
    }
}
