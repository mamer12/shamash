package io.shamash.data.cinemana

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.shamash.data.json
import io.shamash.data.softly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

private val Context.cinemaData by preferencesDataStore("cinemana")

@Serializable
data class CinemaSaved(
    val history: List<CinemaProgress> = emptyList(),
    val myList: List<CinemanaTitle> = emptyList(),
    val ratings: List<RatedTitle> = emptyList(),
    val subtitles: String = SubtitleLanguage.ARABIC,
)

/** One instance per provider; all mutations are atomic, including the five-second progress writes. SPEC: CIN-04,09 */
class CinemaStore(private val context: Context? = null) {
    private val lock = Mutex()
    private val mutable = MutableStateFlow(CinemaSaved())
    val state = mutable.asStateFlow()
    private val finishedTitles = MutableSharedFlow<CinemanaTitle>(extraBufferCapacity = 8)

    /** A title played to its end (95% or more). SPEC: PER-02, CIN-04 */
    val finished: SharedFlow<CinemanaTitle> = finishedTitles.asSharedFlow()
    private var loaded = false
    private val key = stringPreferencesKey("library")

    suspend fun snapshot(): CinemaSaved = withContext(Dispatchers.IO) {
        lock.withLock { read(); mutable.value }
    }

    suspend fun toggleList(title: CinemanaTitle) = update { old ->
        val others = old.myList.filterNot { it.pageId == title.pageId }
        old.copy(myList = if (others.size == old.myList.size) (listOf(title) + others).take(500) else others)
    }

    suspend fun rate(title: CinemanaTitle, rating: CinemaRating) = update { old ->
        val prior = old.ratings.find { it.title.pageId == title.pageId }
        val others = old.ratings.filterNot { it.title.pageId == title.pageId }
        old.copy(ratings = if (prior?.rating == rating) others else (listOf(RatedTitle(title, rating)) + others).take(500))
    }

    suspend fun progress(title: CinemanaTitle, position: Long, duration: Long) {
        if (duration <= 0 || position < 3_000) return
        update { old ->
            val entry = CinemaProgress(title, position.coerceAtMost(duration), duration, System.currentTimeMillis())
            old.copy(history = (listOf(entry) + old.history.filterNot { it.title.id == title.id }).take(300))
        }
        if (position >= duration * FINISHED && io.shamash.data.Recording.enabled) finishedTitles.tryEmit(title)
    }

    /** Forgets ratings and watch progress (Settings -> clear my history). SPEC: PER-04 */
    suspend fun clearPersonal() = update { it.copy(history = emptyList(), ratings = emptyList()) }

    suspend fun subtitles(language: String) {
        if (language in SubtitleLanguage.ALL) update { it.copy(subtitles = language) }
    }

    private suspend fun read() {
        if (loaded) return
        mutable.value = softly("Cinemana library", CinemaSaved()) {
            context?.cinemaData?.data?.first()?.get(key)?.let { json.decodeFromString<CinemaSaved>(it) } ?: CinemaSaved()
        }
        loaded = true
    }

    private suspend fun update(change: (CinemaSaved) -> CinemaSaved) = withContext(Dispatchers.IO) {
        if (!io.shamash.data.Recording.enabled) return@withContext   // Guest saves nothing (PRF-11)
        lock.withLock {
            read()
            val next = change(mutable.value)
            softly("Cinemana save", Unit) { context?.cinemaData?.edit { it[key] = json.encodeToString(next) }; Unit }
            mutable.value = next
        }
    }

    private companion object {
        const val FINISHED = 0.95
    }
}
