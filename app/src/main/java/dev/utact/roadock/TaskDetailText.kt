package dev.utact.roadock

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.URLSpan
import java.net.URI
import java.net.URLEncoder

object TaskDetailText {
    private val link = Regex("\\[([^]\\n]+)]\\(([^)\\s]+)\\)")

    fun format(text: String, project: Project?): CharSequence {
        val result = SpannableStringBuilder()
        var offset = 0
        link.findAll(text).forEach { match ->
            result.append(text.substring(offset, match.range.first))
            val start = result.length
            result.append(match.groupValues[1])
            resolve(match.groupValues[2], project)?.let { target ->
                result.setSpan(URLSpan(target), start, result.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            offset = match.range.last + 1
        }
        return result.append(text.substring(offset))
    }

    private fun resolve(target: String, project: Project?): String? = runCatching {
        val uri = URI(target)
        val resolved = if (uri.isAbsolute) uri else {
            if (project == null) return null
            fun encode(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
            val path = project.path.split('/').joinToString("/") { encode(it) }
            URI("https://github.com/${encode(project.owner)}/${encode(project.repository)}/blob/${encode(project.branch)}/$path").resolve(uri)
        }
        resolved.takeIf { it.scheme?.lowercase() in listOf("http", "https") && !it.host.isNullOrEmpty() }?.toASCIIString()
    }.getOrNull()
}
