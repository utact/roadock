package dev.utact.roadock

import android.content.Context
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ValidationException(val text: Int, vararg val values: Any) : IllegalArgumentException()
open class FetchException(val text: Int, vararg val values: Any) : IOException()
class HumanWriteConflict(val items: List<ChecklistItem>, text: Int) : FetchException(text)

fun requireInput(condition: Boolean, text: Int, vararg values: Any) {
    if (!condition) throw ValidationException(text, *values)
}

fun Context.errorMessage(error: Exception): String = when (error) {
    is ValidationException -> getString(error.text, *error.values)
    is FetchException -> getString(error.text, *error.values)
    is UnknownHostException, is SocketTimeoutException -> getString(R.string.error_connection)
    is IllegalStateException -> error.message ?: getString(R.string.error_refresh)
    else -> getString(R.string.error_refresh)
}
