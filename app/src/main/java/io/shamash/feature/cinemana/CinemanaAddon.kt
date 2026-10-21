package io.shamash.feature.cinemana

import android.content.Context
import java.net.URI
import java.net.URLDecoder
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.Preferences
import io.shamash.data.cinemana.CinemaLibrary
import io.shamash.data.cinemana.CinemaStore
import io.shamash.data.cinemana.CinemanaApi
import io.shamash.data.cinemana.CinemanaProvider
import io.shamash.data.cinemana.CinemanaTmdb
import io.shamash.domain.PreviewSource
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.RefreshableProvider
import io.shamash.ui.ProviderPage

/** The single removable boundary: provider, local library and screens all share one instance. */
class CinemanaAddon private constructor(
    provider: CinemanaProvider,
    private val library: CinemaLibrary,
) : Provider by provider, PreviewSource by provider, RefreshableProvider by provider, ProviderPage,
    io.shamash.domain.PersonalBridge by io.shamash.data.cinemana.CinemanaBridge(library) {
    override val entryUri = "shamash://cinemana"
    override val titleResource = R.string.cinema_name
    override fun available(profile: Profile) = profile.cinemanaAddon
    override fun accepts(uri: String): Boolean = CinemaDestination.parse(uri) != null

    @Composable
    override fun Content(uri: String, profile: Profile, onSettings: () -> Unit, onBack: () -> Unit) {
        var stack by remember(uri) { mutableStateOf(listOfNotNull(CinemaDestination.parse(uri))) }
        val leave = { if (stack.size > 1) stack = stack.dropLast(1) else onBack() }
        val open: (String) -> Unit = { link -> CinemaDestination.parse(link)?.let { stack = stack + it } }
        BackHandler(onBack = leave)
        val state = rememberSaveableStateHolder()
        val page = stack.lastOrNull()
        state.SaveableStateProvider(page.toString()) {
            when (page) {
                is CinemaDestination.Browse -> CinemaBrowse(library, profile, open, onSettings)
                is CinemaDestination.Title -> CinemaTitlePage(library, profile, page.id, open, leave)
                is CinemaDestination.Play -> CinemaPlayer(library, profile, page.id, page.atMs, { id ->
                    stack = stack.dropLast(1) + CinemaDestination.Play(id, null)
                }, leave)
                null -> Unit
            }
        }
    }

    companion object {
        /** The registration (PEX-01): off until the profile enables it; behaviour is as before. */
        val addOn = io.shamash.domain.AddOn(
            id = CinemanaProvider.ID, nameRes = R.string.settings_cinemana, descRes = R.string.settings_cinemana_text,
        ) { deps ->
            val http = deps.service(Http::class.java)
            val prefs = deps.service(Preferences::class.java)
            if (http == null || prefs == null) null
            else create(http, prefs, deps::isInstalled, deps.service(Context::class.java))
        }

        fun create(http: Http, prefs: Preferences, installed: (String) -> Boolean, context: Context?): Provider {
            val api = CinemanaApi(http)
            val tmdb = CinemanaTmdb(http)
            val library = CinemaLibrary(api, tmdb, http, CinemaStore(context))
            return CinemanaAddon(CinemanaProvider(api, tmdb, installed, library, prefs::current), library)
        }
    }
}

internal sealed interface CinemaDestination {
    data object Browse : CinemaDestination
    data class Title(val id: String) : CinemaDestination
    data class Play(val id: String, val atMs: Long?) : CinemaDestination

    companion object {
        fun parse(link: String): CinemaDestination? {
            val uri = runCatching { URI(link) }.getOrNull() ?: return null
            if (uri.scheme != "shamash" || uri.host != "cinemana" || uri.port != -1 || uri.userInfo != null) return null
            val path = uri.path.orEmpty()
            if (path.isEmpty() || path == "/") return Browse
            val parts = path.removePrefix("/").split('/')
            if (parts.size != 2 || parts[0] !in setOf("title", "play")) return null
            val id = parts[1].takeIf { it.isNotEmpty() && it.all { ch -> ch in '0'..'9' } && it.toLongOrNull() != null } ?: return null
            val rawAt = uri.rawQuery?.split('&')?.firstOrNull { it.substringBefore('=') == "at" }?.substringAfter('=', "")
            val at = rawAt?.let { runCatching { URLDecoder.decode(it, "UTF-8").toLong() }.getOrNull() }?.takeIf { it >= 0 }
            return if (parts[0] == "play" || at != null) Play(id, at) else Title(id)
        }
    }
}
