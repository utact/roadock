package dev.utact.roadock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProjectTitleViewTest {
    @Test fun fillsOnlyTitleGlyphsAsCompletionIncreasesWithoutMovingBaseline() {
        val context = RuntimeEnvironment.getApplication().apply { setTheme(R.style.AppTheme) }
        val title = ProjectTitleView(context).apply { text = "Roadock"; includeFontPadding = false }
        title.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(48, View.MeasureSpec.EXACTLY))
        title.layout(0, 0, 200, 48)
        val baseline = title.baseline
        fun waterPixels(completed: Int): Int {
            title.setProgress(completed, 2)
            val bitmap = Bitmap.createBitmap(200, 48, Bitmap.Config.ARGB_8888)
            title.draw(Canvas(bitmap))
            val pixels = IntArray(200 * 48)
            bitmap.getPixels(pixels, 0, 200, 0, 0, 200, 48)
            assertEquals(baseline, title.baseline)
            assertEquals(0, bitmap.getPixel(199, 47))
            bitmap.recycle()
            return pixels.count { Color.alpha(it) > 100 && Color.green(it) - Color.red(it) > 8 && Color.blue(it) - Color.green(it) > 4 }
        }
        assertEquals(0, waterPixels(0))
        val half = waterPixels(1)
        assertTrue(half > 0)
        assertTrue(waterPixels(2) > half)
    }
}
