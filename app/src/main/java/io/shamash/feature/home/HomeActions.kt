package io.shamash.feature.home

import android.content.Context
import androidx.compose.runtime.Stable
import androidx.compose.ui.focus.FocusRequester
import io.shamash.app.ServiceLocator
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.TvApp
import io.shamash.ui.PreviewController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the home screen's buttons and cards do. Titles go through the Opener; apps and inputs open directly. SPEC: HOME-13 */
@Stable
class HomeActions(
    private val locator: ServiceLocator,
    private val context: Context,
    private val scope: CoroutineScope,
    private val previews: PreviewController,
) {
    /** Opens a title; [atMs] (a preview's position) goes along to Shamash's own player routes as `at=`. SPEC: HOME-20 */
    fun open(item: MediaItem, atMs: Long? = null) {
        previews.stop(null)
        val target = item.open
        val opened = when {
            atMs == null -> item
            target is OpenTarget.Internal -> item.copy(open = OpenTarget.Internal(withAt(target.route, atMs)))
            target is OpenTarget.AppPage && target.uri.startsWith(OWN_SCHEME) ->
                item.copy(open = target.copy(uri = withAt(target.uri, atMs)))   // add-ons' own screens (Cinemana)
            else -> item
        }
        scope.launch { locator.opener.open(opened) }
    }

    private fun withAt(route: String, atMs: Long): String = route + (if ('?' in route) '&' else '?') + "at=$atMs"

    fun openFeatured(item: MediaItem) = open(item, null)

    fun openApp(app: TvApp) {
        locator.opener.openApp(app.packageName)
    }

    fun openInput(input: TvInput) {
        TvInputs.open(context, input)
    }

    fun tvSettings() {
        TvInputs.openTvSettings(context)
    }

    fun search() {
        locator.opener.internal(ROUTE_SEARCH)
    }

    fun settings() {
        locator.opener.internal(ROUTE_SETTINGS)
    }

    private companion object {
        const val ROUTE_SEARCH = "search"
        const val ROUTE_SETTINGS = "settings"
        const val OWN_SCHEME = "shamash://"
    }
}

/** Where focus can be sent: the tabs, the billboard's button, the apps row. SPEC: HOME-12 */
class HomeFocus {
    val tabs = List(TAB_COUNT) { FocusRequester() }
    val button = FocusRequester()
    val apps = FocusRequester()

    companion object {
        const val TAB_COUNT = 3
    }
}

/** Requests focus; false when the target isn't on screen (yet). */
fun FocusRequester.tryFocus(): Boolean = runCatching { requestFocus() }.isSuccess
