package dev.utact.roadock

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.checkbox.MaterialCheckBox

data class ChecklistRow(val id: String, val text: String, val checked: Boolean, val depth: Int = 0)

class ChecklistAdapter(
    private val toggle: (String) -> Unit,
    private val delete: (String) -> Unit,
    private val reorder: (List<String>) -> Unit,
) : RecyclerView.Adapter<ChecklistAdapter.Holder>() {
    private val rows = mutableListOf<ChecklistRow>()
    private val stableIds = mutableMapOf<String, Long>()
    private var nextId = 0L
    var editable = false
        private set
    private var dragging = false

    val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
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
                dragging = true
                holder?.itemView?.setBackgroundColor(RoadockDesign.surface)
            }
        }

        override fun clearView(recycler: RecyclerView, holder: RecyclerView.ViewHolder) {
            super.clearView(recycler, holder)
            holder.itemView.setBackgroundColor(Color.TRANSPARENT)
            if (dragging) {
                dragging = false
                val ids = rows.map { it.id }
                recycler.post { reorder(ids) }
            }
        }
    })

    init { setHasStableIds(true) }

    fun submit(items: List<ChecklistRow>, canEdit: Boolean) {
        if (dragging || (items == rows && editable == canEdit)) return
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
        holder.root.setPadding(RoadockDesign.dp(holder.root.context, RoadockDesign.screenInset - 12 + item.depth * 12),
            0, RoadockDesign.dp(holder.root.context, 12), 0)
        holder.check.setOnCheckedChangeListener(null)
        holder.check.tag = "task-${item.id}"
        holder.check.isChecked = item.checked
        holder.check.isEnabled = editable
        holder.check.contentDescription = if (editable) item.text else "${item.text}, 읽기 전용"
        holder.check.setOnCheckedChangeListener { _, _ -> if (editable) toggle(item.id) }
        holder.label.text = item.text
        holder.label.setTextColor(if (item.checked) RoadockDesign.secondary else RoadockDesign.text)
        holder.handle.visibility = if (editable) View.VISIBLE else View.GONE
        holder.handle.contentDescription = "${item.text} 순서 변경"
        holder.handle.tag = "reorder-${item.id}"
        holder.handle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) touchHelper.startDrag(holder)
            false
        }
        holder.handle.setOnClickListener { showMenu(holder, item) }
        holder.root.setOnClickListener { if (editable) holder.check.performClick() }
        holder.root.setOnLongClickListener {
            if (editable) { showMenu(holder, item); true } else false
        }
        holder.root.isClickable = editable
        holder.root.isLongClickable = editable
        holder.root.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun showMenu(holder: Holder, item: ChecklistRow) {
        val index = rows.indexOfFirst { it.id == item.id }
        PopupMenu(holder.root.context, holder.handle).apply {
            menu.add(0, 1, 0, "위로 이동").isEnabled = index > 0
            menu.add(0, 2, 1, "아래로 이동").isEnabled = index >= 0 && index < rows.lastIndex
            menu.add(0, 3, 2, "삭제")
            setOnMenuItemClickListener {
                if (it.itemId == 3) delete(item.id)
                else {
                    val destination = index + if (it.itemId == 1) -1 else 1
                    if (index in rows.indices && destination in rows.indices) {
                        rows.add(destination, rows.removeAt(index))
                        notifyItemMoved(index, destination)
                        reorder(rows.map { row -> row.id })
                    }
                }
                true
            }
            show()
        }
    }

    class Holder(parent: ViewGroup) : RecyclerView.ViewHolder(LinearLayout(parent.context)) {
        val root = itemView as LinearLayout
        val check = MaterialCheckBox(parent.context)
        val label = RoadockDesign.text(parent.context, "")
        val handle = RoadockDesign.iconButton(parent.context, R.drawable.ic_drag, "순서 변경", "drag") {}

        init {
            val context = parent.context
            handle.iconSize = RoadockDesign.dp(context, 16)
            handle.iconTint = ColorStateList.valueOf(RoadockDesign.secondary)
            root.orientation = LinearLayout.HORIZONTAL
            root.gravity = Gravity.CENTER_VERTICAL
            root.minimumHeight = RoadockDesign.dp(context, RoadockDesign.listRow)
            root.layoutParams = RecyclerView.LayoutParams(-1, -2)
            check.setUseMaterialThemeColors(false)
            check.buttonTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(RoadockDesign.text, RoadockDesign.outline),
            )
            check.setCenterIfNoTextEnabled(true)
            root.addView(check, LinearLayout.LayoutParams(RoadockDesign.dp(context, 48), RoadockDesign.dp(context, 48)))
            label.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            label.setSingleLine()
            label.ellipsize = android.text.TextUtils.TruncateAt.END
            label.setPadding(0, RoadockDesign.dp(context, 8), RoadockDesign.dp(context, 8), RoadockDesign.dp(context, 8))
            root.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
            root.addView(handle, LinearLayout.LayoutParams(RoadockDesign.dp(context, 48), RoadockDesign.dp(context, 48)))
        }
    }
}
