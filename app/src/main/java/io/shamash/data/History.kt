package io.shamash.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.WatchHistory
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private val Context.historyStore by preferencesDataStore(name = "history")

/** Items opened from Shamash, newest first, ≤ 20, 30 days. SPEC: HOME-11 */
class History(private val context: Context, private val clock: () -> Long = System::currentTimeMillis) : WatchHistory {
    private val key = stringPreferencesKey("items")

    override val items: Flow<List<MediaItem>> = context.historyStore.data.map { s ->
        decode(s[key]).filter { clock() - it.at < KEEP_MS }.map { it.toItem() }
    }

    override suspend fun record(item: MediaItem) {
        context.historyStore.edit { s ->
            s[key] = json.encodeToString(remember(decode(s[key]), Saved.of(item, clock())))
        }
    }

    /** Forgets everything opened from Shamash (Settings -> clear my history). SPEC: PER-04 */
    suspend fun clear() {
        context.historyStore.edit { it.remove(key) }
    }

    private fun decode(raw: String?): List<Saved> =
        raw?.let { runCatching { json.decodeFromString<List<Saved>>(it) }.getOrNull() }.orEmpty()

    /** The item first, its older copy gone, at most [MAX]. */
    internal fun remember(old: List<Saved>, item: Saved): List<Saved> =
        (listOf(item) + old.filterNot { it.title == item.title && it.appPackage == item.appPackage }).take(MAX)

    @Serializable
    internal data class Saved(
        val id: String, val providerId: String, val title: String, val kind: Kind, val subtitle: String,
        val image: String?, val appPackage: String?, val youtubeId: String?, val page: String?, val at: Long,
    ) {
        fun toItem() = MediaItem(
            id = id, providerId = providerId, title = title, kind = kind, subtitle = subtitle,
            posterUrl = image, backdropUrl = image, appPackage = appPackage,
            open = when {
                youtubeId != null -> OpenTarget.Video(youtubeId)
                page != null && appPackage != null -> OpenTarget.AppPage(page, appPackage)
                appPackage != null -> OpenTarget.App(appPackage)
                else -> OpenTarget.Internal("home")
            },
        )

        companion object {
            fun of(item: MediaItem, at: Long) = Saved(
                id = item.id, providerId = item.providerId, title = item.title, kind = item.kind,
                subtitle = item.subtitle, image = item.backdropUrl ?: item.posterUrl, appPackage = item.appPackage,
                youtubeId = (item.open as? OpenTarget.Video)?.youtubeId,
                page = (item.open as? OpenTarget.AppPage)?.uri, at = at,
            )
        }
    }

    private companion object {
        const val MAX = 20
        const val KEEP_MS = 30L * 86_400_000L
    }
}
