package io.shamash.feature.search

import io.shamash.data.Logger
import io.shamash.domain.ContentGate
import io.shamash.domain.MediaItem
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.SearchRanking
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One row of results: a provider's titles in one app (TMDB gives a row per subscribed service). SPEC: SRCH-01 */
data class SearchRow(val key: String, val providerId: String, val appPackage: String?, val items: List<MediaItem>)

data class SearchUiState(
    val query: String = "",
    val rows: List<SearchRow> = emptyList(),
    val searching: Boolean = false,
    val searched: Boolean = false,
    /** Set by Enter or a query from the phone: the first result takes the focus once it arrives. SPEC: SRCH-04 */
    val focusResults: Boolean = false,
)

/**
 * The search screen's state: every enabled provider searched in parallel, 900 ms after typing stops or at once on
 * Enter, an older search cancelled by a newer one. Rows appear as their provider answers. SPEC: SRCH-01..04
 */
class SearchModel(
    private val providers: List<Provider>,
    private val profile: suspend () -> Profile,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = mutableState.asStateFlow()
    private var job: Job? = null

    fun type(text: String) {
        if (text == mutableState.value.query) return
        mutableState.update { it.copy(query = text) }
        start(DEBOUNCE_MS, focusResults = false)
    }

    /** Enter, or a query that arrives with the screen. */
    fun submit(text: String = mutableState.value.query) {
        mutableState.update { it.copy(query = text) }
        start(0, focusResults = true)
    }

    fun focusTaken() = mutableState.update { it.copy(focusResults = false) }

    private fun start(wait: Long, focusResults: Boolean) {
        job?.cancel()
        val query = mutableState.value.query.trim()
        if (query.length < MIN_QUERY) {
            mutableState.update { it.copy(rows = emptyList(), searching = false, searched = false) }
            return
        }
        job = scope.launch {
            delay(wait)
            mutableState.update { it.copy(rows = emptyList(), searching = true, searched = false, focusResults = focusResults) }
            searchAll(query)
            mutableState.update { it.copy(searching = false, searched = true) }
        }
    }

    private suspend fun searchAll(query: String) = coroutineScope {
        val p = profile()
        val enabled = providers.filter { it.isEnabled(p) }
        val found = HashMap<Int, List<MediaItem>>()
        enabled.forEachIndexed { index, provider ->
            launch {
                val items = searchOne(provider, query, p)
                found[index] = items
                mutableState.update { it.copy(rows = SearchRows.build(enabled, found, query)) }
            }
        }
    }

    private suspend fun searchOne(provider: Provider, query: String, p: Profile): List<MediaItem> =
        try {
            ContentGate.filter(provider.search(query, p), p)   // SPEC: PRF-12
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("search ${provider.id}", e)
            emptyList()
        }

    companion object {
        const val DEBOUNCE_MS = 900L
        const val MIN_QUERY = 2
    }
}

/** Turns each provider's answer into rows, in the providers' order. SPEC: SRCH-01, SRCH-03 */
object SearchRows {
    fun build(providers: List<Provider>, found: Map<Int, List<MediaItem>>, query: String): List<SearchRow> =
        providers.indices.flatMap { index ->
            val provider = providers[index]
            found[index].orEmpty()
                .groupBy { it.appPackage }
                .map { (app, items) ->
                    SearchRow("${provider.id}:${app.orEmpty()}", provider.id, app, SearchRanking.rank(items, query))
                }
        }
}
