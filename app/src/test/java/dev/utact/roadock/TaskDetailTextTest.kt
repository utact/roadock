package dev.utact.roadock

import android.text.Spanned
import android.text.style.URLSpan
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskDetailTextTest {
    @Test fun resolvesRelativeDocumentLinksWithoutTreatingOtherSchemesAsLinks() {
        val project = Project("one", "Roadock", "utact", "roadock", "feature/details", "plans/TODO.md")
        val text = TaskDetailText.format("[기준](../docs/TESTING.md#api)\n[사이트](https://example.com/test)\n[안전하지 않은 링크](javascript:alert)", project) as Spanned
        assertEquals("기준\n사이트\n안전하지 않은 링크", text.toString())
        assertEquals(listOf("https://github.com/utact/roadock/blob/feature%2Fdetails/docs/TESTING.md#api", "https://example.com/test"),
            text.getSpans(0, text.length, URLSpan::class.java).map { it.url })
    }
}
