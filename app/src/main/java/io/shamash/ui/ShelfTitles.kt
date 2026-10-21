package io.shamash.ui

import android.annotation.SuppressLint
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.shamash.R

/** The shelf title keys Shamash itself uses (providers may use any key that names a string resource). */
object ShelfKeys {
    const val POPULAR_ON = "popular_on"
    const val TOP10 = "top10"
    const val YOUTUBE_RECOMMENDED = "youtube_recommended"
    const val TOD_PREFIX = "tod_"
    const val CINEMANA_PREFIX = "cinemana_"
    const val MATCHES = "matches"
    const val CONTINUE = "continue_watching"
    const val CONTINUE_YOUTUBE = "continue_youtube"
    const val RECENT_APPS = "recent_apps"
    const val ALL_APPS = "all_apps"
    const val INPUTS = "inputs"
    const val SETTINGS = "settings"
    const val MOMENT_PREFIX = "moment_"
}

private val KNOWN = mapOf(
    ShelfKeys.POPULAR_ON to R.string.popular_on,
    ShelfKeys.TOP10 to R.string.top10,
    ShelfKeys.YOUTUBE_RECOMMENDED to R.string.youtube_recommended,
    "tod_upcoming" to R.string.tod_upcoming,
    "tod_new" to R.string.tod_new,
    "cinemana_new" to R.string.cinemana_new,
    "cinemana_featured" to R.string.cinemana_featured,
    ShelfKeys.MATCHES to R.string.matches,
    ShelfKeys.CONTINUE to R.string.continue_watching,
    ShelfKeys.CONTINUE_YOUTUBE to R.string.continue_youtube,
    ShelfKeys.RECENT_APPS to R.string.recent_apps,
    ShelfKeys.ALL_APPS to R.string.all_apps,
    ShelfKeys.INPUTS to R.string.inputs,
    ShelfKeys.SETTINGS to R.string.settings,
    "moment_morning" to R.string.moment_morning,
    "moment_afternoon" to R.string.moment_afternoon,
    "moment_evening" to R.string.moment_evening,
    "moment_night" to R.string.moment_night,
)

/**
 * A shelf's title from its key (a string resource name) and argument ("Popular on %s"). Keys outside the known list
 * are looked up by name, so a provider can bring its own string; an unknown key shows its argument or nothing.
 */
@Composable
fun shelfTitle(key: String, arg: String? = null): String {
    val resources = LocalContext.current.resources
    return remember(key, arg, resources) { resolveTitle(resources, key, arg) }
}

@SuppressLint("DiscouragedApi")
private fun resolveTitle(resources: Resources, key: String, arg: String?): String {
    val id = KNOWN[key] ?: resources.getIdentifier(key, "string", "io.shamash")
    if (id == 0) return arg.orEmpty()
    return if (arg != null) resources.getString(id, arg) else resources.getString(id)
}
