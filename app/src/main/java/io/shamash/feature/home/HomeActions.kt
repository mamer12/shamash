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
        val opened = resumedAt(item, atMs)
        scope.launch { locator.opener.open(opened) }
    }

    fun openFeatured(item: MediaItem) = open(item, null)

    fun openApp(app: TvApp) {
        locator.opener.openApp(app.packageName)
    }

    /** Kids cannot reach Inputs or TV Settings. SPEC: PRF-10 */
    val canUseInputs get() = locator.session.restrictions.inputs

    fun openInput(input: TvInput) {
        if (canUseInputs) TvInputs.open(context, input)
    }

    fun tvSettings() {
        if (locator.session.restrictions.tvSettings) TvInputs.openTvSettings(context)
    }

    fun search() {
        locator.opener.internal(ROUTE_SEARCH)
    }

    fun settings() {
        if (locator.session.restrictions.settings) locator.opener.internal(ROUTE_SETTINGS)
    }

    /** The row editor, from Menu on a Home row; Kids and Guest never reach it. SPEC: LAY-02, LAY-21 */
    fun editRows() {
        if (locator.session.restrictions.settings) locator.opener.internal(ROUTE_ROW_EDITOR)
    }

    companion object {
        private const val ROUTE_SEARCH = "search"
        private const val ROUTE_SETTINGS = "settings"
        private const val OWN_SCHEME = "shamash://"
        const val ROUTE_ROW_EDITOR = "settings?section=layout"

        /**
         * [item] with [atMs] carried to where it opens: `at=` on Shamash's own routes and add-on screens, or the extra
         * an app screen names in [OpenTarget.AppScreen.resumeExtraKey] (Watchme+). Anything else opens as it is. SPEC: HOME-20
         */
        fun resumedAt(item: MediaItem, atMs: Long?): MediaItem {
            val target = item.open
            val key = (target as? OpenTarget.AppScreen)?.resumeExtraKey
            return when {
                atMs == null -> item
                target is OpenTarget.Internal -> item.copy(open = OpenTarget.Internal(withAt(target.route, atMs)))
                target is OpenTarget.AppPage && target.uri.startsWith(OWN_SCHEME) ->
                    item.copy(open = target.copy(uri = withAt(target.uri, atMs)))
                target is OpenTarget.AppScreen && !key.isNullOrBlank() ->
                    item.copy(open = target.copy(extras = target.extras + (key to atMs.toString())))
                else -> item
            }
        }

        private fun withAt(route: String, atMs: Long): String = route + (if ('?' in route) '&' else '?') + "at=$atMs"
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
