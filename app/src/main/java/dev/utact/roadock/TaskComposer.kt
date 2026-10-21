package dev.utact.roadock

import android.app.Dialog
import android.os.Bundle
import android.os.Build
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.TypedValue
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class TaskComposer : BottomSheetDialogFragment() {
    private lateinit var input: TextInputEditText
    private var submitted = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        fun dp(value: Int) = RoadockDesign.dp(context, value)
        val sheet = BottomSheetDialog(context)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        root.addView(RoadockDesign.text(context, "할 일 추가", 20, weight = 600))
        val field = TextInputLayout(context).apply {
            hint = "할 일"
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
            isCounterEnabled = true
            counterMaxLength = RoadockStore.TASK_LENGTH
        }
        input = TextInputEditText(field.context).apply {
            tag = "task-input"
            setSingleLine()
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 17f)
            typeface = RoadockDesign.font(context)
            filters = arrayOf(InputFilter.LengthFilter(RoadockStore.TASK_LENGTH))
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(savedInstanceState?.getString("draft").orEmpty())
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { submit(); true } else false
            }
        }
        field.addView(input)
        root.addView(field, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val add = MaterialButton(context).apply {
            tag = "add-task"
            text = "추가"
            isAllCaps = false
            typeface = RoadockDesign.font(context, 500)
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            cornerRadius = dp(8)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { submit() }
        }
        fun update() { add.isEnabled = !input.text.isNullOrBlank() }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) { update() }
        })
        update()
        root.addView(add, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(20) })
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val types = WindowInsetsCompat.Type.systemBars() or
                (if (Build.VERSION.SDK_INT >= 30) WindowInsetsCompat.Type.ime() else 0)
            val bottom = insets.getInsets(types).bottom
            view.setPadding(dp(24), dp(24), dp(24), dp(24) + bottom)
            insets
        }
        sheet.setContentView(NestedScrollView(context).apply { addView(root) })
        return sheet
    }

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.apply { state = BottomSheetBehavior.STATE_EXPANDED; skipCollapsed = true }
        dialog?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= 30) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
            } else window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        input.requestFocus()
        input.post { context?.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("draft", input.text.toString())
        super.onSaveInstanceState(outState)
    }

    private fun submit() {
        if (submitted) return
        val text = input.text.toString().replace('\n', ' ').replace('\r', ' ').trim()
        if (text.isEmpty()) return
        submitted = true
        parentFragmentManager.setFragmentResult(RESULT, Bundle().apply { putString("text", text) })
        dismiss()
    }

    companion object {
        const val TAG = "task-composer"
        const val RESULT = "personal-task-added"
    }
}
