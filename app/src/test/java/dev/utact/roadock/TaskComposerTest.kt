package dev.utact.roadock

import android.os.Looper
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskComposerTest {
    private fun layout(view: View, width: Int = 393, height: Int = 852) {
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idle()
            view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, width, height)
            view.viewTreeObserver.dispatchOnPreDraw()
        }
    }

    @Test @Config(sdk = [26]) fun submitsFromResizedKeyboardWindowOnMinimumAndroid() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity.window.decorView)
        activity.window.decorView.findViewWithTag<View>("add-personal-task").performClick()
        activity.supportFragmentManager.executePendingTransactions()
        val sheet = activity.supportFragmentManager.findFragmentByTag(TaskComposer.TAG) as TaskComposer
        val decor = sheet.requireDialog().window!!.decorView
        layout(decor, 393, 512)
        val button = decor.findViewWithTag<View>("add-task")
        val xy = IntArray(2)
        button.getLocationInWindow(xy)
        assertTrue(xy[1] + button.height <= 512)
        val input = decor.findViewWithTag<EditText>("task-input")
        input.setText("Android 8 입력 확인")
        input.onEditorAction(EditorInfo.IME_ACTION_DONE)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("Android 8 입력 확인", RoadockStore(activity).tasks().single().text)
        controller.pause().stop().destroy()
    }

    @Test fun keepsInputAndSubmitAboveKeyboardAndRestoresSpacingWhenHidden() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        layout(activity.window.decorView)
        activity.window.decorView.findViewWithTag<View>("add-personal-task").performClick()
        activity.supportFragmentManager.executePendingTransactions()
        val sheet = activity.supportFragmentManager.findFragmentByTag(TaskComposer.TAG) as TaskComposer
        val decor = sheet.requireDialog().window!!.decorView
        val input = decor.findViewWithTag<EditText>("task-input")
        val button = decor.findViewWithTag<View>("add-task")
        val content = generateSequence(input.parent as View) { it.parent as? View }
            .first { it.parent is NestedScrollView }
        val scroll = content.parent as NestedScrollView
        fun insets(ime: Int) = WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, 24, 0, 24))
            .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, ime))
            .setVisible(WindowInsetsCompat.Type.ime(), ime > 0).build()
        fun bottom(view: View): Int {
            val xy = IntArray(2)
            view.getLocationInWindow(xy)
            return xy[1] + view.height
        }
        layout(decor)
        ViewCompat.dispatchApplyWindowInsets(scroll, insets(0))
        layout(decor)
        val original = bottom(button)
        repeat(2) {
            ViewCompat.dispatchApplyWindowInsets(scroll, insets(340))
            layout(decor)
            assertTrue("Submit must remain above keyboard", bottom(button) <= 852 - 340)
            assertTrue(bottom(input) <= bottom(button))
            ViewCompat.dispatchApplyWindowInsets(scroll, insets(0))
            layout(decor)
            assertEquals(original, bottom(button))
        }
        ViewCompat.dispatchApplyWindowInsets(scroll, insets(180))
        layout(decor, 852, 393)
        assertTrue("Submit must remain above landscape keyboard: ${bottom(button)}", bottom(button) <= 393 - 180)
        input.setText("키보드 완료로 저장")
        input.onEditorAction(EditorInfo.IME_ACTION_DONE)
        input.onEditorAction(EditorInfo.IME_ACTION_DONE)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("키보드 완료로 저장", RoadockStore(activity).tasks().single().text)
        controller.pause().stop().destroy()
    }
}
