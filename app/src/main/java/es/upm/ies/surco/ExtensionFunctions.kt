package es.upm.ies.surco

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Notifies the observers of a MutableLiveData.
 */
fun <T> MutableLiveData<T>.notifyObservers() {
    this.postValue(this.value)
}

/**
 * Creates a map with the beacon positions,
 * assigns localized keys to the unlocalized positions strings.
 * These values are written to the JSON session file.
 */
fun createPositionMap(context: Context): Map<String, String> {
    // localized values
    val positions = context.resources.getStringArray(R.array.beacon_positions)
    // unlocalized keys
    val values = context.resources.getStringArray(R.array.beacon_spinner_keys)
    return positions.zip(values).toMap()
}

/**
 * Hides the keyboard.
 */
fun Fragment.hideKeyboard() {
    view?.let { activity?.hideKeyboard(it) }
}

fun Context.hideKeyboard(view: View) {
    val inputMethodManager = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

val PATH_SAFE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss'Z'")
/**
 * Converts a ZonedDateTime to a path-safe string in UTC.
 * The format is "yyyy-MM-dd_HH-mm-ssZ".
 * @return The path-safe string.
 */
fun ZonedDateTime.formatAsPathSafeString(): String {
    return PATH_SAFE_FORMATTER.format(this)
}


/**
 * Calculate the milliseconds until some time of day (hour, minute, second).
 * If that time has already passed today, calculates the time until that time tomorrow.
 * @param now The current millis.
 * @param hour The hour of the day (0-23).
 * @param minute The minute of the hour (0-59).
 * @param second The second of the minute (0-59).
 * @return The milliseconds until that time of day.
 */
fun calculateMillisFromTo(now: Long, hour: Int, minute: Int, second: Int): Long {
    val nowZdt = ZonedDateTime.now()
    val targetZdtToday = nowZdt.withHour(hour).withMinute(minute).withSecond(second).withNano(0)
    val targetZdt = if (nowZdt.isAfter(targetZdtToday)) {
        targetZdtToday.plusDays(1)
    } else {
        targetZdtToday
    }
    return targetZdt.toInstant().toEpochMilli() - now
}