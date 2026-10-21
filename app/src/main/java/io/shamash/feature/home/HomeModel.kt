package io.shamash.feature.home

import io.shamash.data.Logger
import io.shamash.app.ServiceLocator
import io.shamash.domain.Carousel
import io.shamash.domain.LayoutFetch
import io.shamash.domain.MatchState
import io.shamash.domain.MediaItem
import io.shamash.domain.Preview
import io.shamash.domain.PreviewSource
import io.shamash.domain.Profile
import io.shamash.domain.Provider
import io.shamash.domain.RefreshableProvider
import io.shamash.domain.RowSourceProvider
import io.shamash.domain.RowSources
import io.shamash.feature.matchcentre.BroadcastLoader
import io.shamash.feature.matchcentre.MatchWatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/**
 * The home screen's state: loads every source on its own schedule while the screen is visible, and combines profile,
 * providers, matches, history, apps, weather and the clock into one [HomeUi] per burst of arrivals. Lives for the
 * process, so returning Home shows the last state at once. SPEC: HOME-14, PERF-05, ARCH-03
 */
@OptIn(FlowPreview::class)
class HomeModel private constructor(private val locator: ServiceLocator) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val loaded = MutableStateFlow(Loaded())
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val lastRun = ConcurrentHashMap<String, Long>()
    private val closer = AppCloser(locator.appContext)
    private var running: Job? = null
    private var lastProfile: Profile? = null

    val state: StateFlow<HomeUi> = combine(
        locator.prefs.profile,
        locator.history.items.catch { emit(emptyList()) },
        loaded,
        clock,
        locator.personal.state,
    ) { profile, history, data, now, personal -> HomeInput(profile, data, history, now, hourOf(now), personal) }
        .debounce(COALESCE_MS)
        .map(HomeRows::build)
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, HomeUi())

    /** The screen is visible: refresh what's due, keep the clock, close the app the user came back from. */
    fun resume() {
        if (running?.isActive == true) return
        running = scope.launch {
            launch { tick() }
            launch { locator.personal.settleOpen() }   // a title left within two minutes is a SKIP (PER-02)
            launch(Dispatchers.IO) { loadApps() }
            launch(Dispatchers.IO) { closer.closeIfAway(locator.opener.lastOpened, locator.prefs.current().keepRunning) }
            locator.prefs.profile.distinctUntilChanged().collectLatest { profile -> refreshAll(profile) }
        }
    }

    fun pause() {
        running?.cancel()
        running = null
    }

    /** The preview of an item whose provider offers one (none: null). SPEC: HOME-20 */
    suspend fun preview(item: MediaItem): Preview? {
        val source = locator.providers.firstOrNull { it.id == item.providerId } as? PreviewSource ?: return null
        return soft("preview ${item.providerId}", null) { source.preview(item) }
    }

    private suspend fun refreshAll(profile: Profile) {
        if (profile != lastProfile) {
            lastRun.clear()
            lastProfile = profile
        }
        val enabled = locator.providers.filter { p -> soft("enabled ${p.id}", false) { p.isEnabled(profile) } }
        val ids = enabled.map { it.id }
        val providers = enabled.filter { p -> soft("layout ${p.id}", true) { fetchNeeded(p, profile) } }   // SPEC: LAY-04
        val fetched = providers.map { it.id }.toSet()
        loaded.update { old ->
            old.copy(
                providers = old.providers.filterKeys { it in ids },
                order = ids,
                pending = fetched.filterNot { it in old.providers }.toSet(),
                started = true,
            )
        }
        coroutineScope {
            providers.forEach { p ->
                launch { every("provider:${p.id}", { Schedule.provider(p.id) }) { loadProvider(p, profile) } }
                if (p is RefreshableProvider) launch {
                    p.changes.debounce(COALESCE_MS).collectLatest { loadProvider(p, profile) }
                }
            }
            launch { every("matches", ::matchesInterval) { loadMatches(profile) } }
            launch { every("weather", { Schedule.WEATHER_MS }) { loadWeather(profile) } }
        }
    }

    /**
     * A provider whose rows are all hidden is not fetched, unless the billboard or the Discover tab still reads it; its
     * rows are known from the last fetch or the rows it offers. SPEC: LAY-04
     */
    private fun fetchNeeded(p: Provider, profile: Profile): Boolean {
        val known = loaded.value.providers[p.id]?.shelves.orEmpty()
        val offered = (p as? RowSourceProvider)?.rowSources().orEmpty()
            .filter { RowSources.isOn(profile, p.id, it) }.map { RowSources.shelfId(p.id, it.id) }
        return LayoutFetch.needed(p.id, known.map { it.id } + offered, profile.layout, HomeRows.readsRows(known))
    }

    /** Runs [load] now if it's due, then every [interval] (read again after each run). */
    private suspend fun every(key: String, interval: () -> Long, load: suspend () -> Unit) {
        while (true) {
            val wait = (lastRun[key] ?: 0L) + interval() - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            load()
            lastRun[key] = System.currentTimeMillis()
        }
    }

    private suspend fun loadProvider(p: Provider, profile: Profile) = withContext(Dispatchers.IO) {
        val content = coroutineScope {
            val shelves = async { soft("shelves ${p.id}", emptyList()) { p.shelves(profile) } }
            val featured = async { soft("featured ${p.id}", emptyList()) { p.featured(profile) } }
            ProviderContent(shelves.await(), featured.await())
        }
        loaded.update { it.copy(providers = it.providers + (p.id to content), pending = it.pending - p.id) }
    }

    private suspend fun loadMatches(profile: Profile) = withContext(Dispatchers.IO) {
        val matches = soft("matches", emptyList()) { Carousel.markMine(locator.matches.matches(profile), profile) }
        val broadcast = soft("broadcast map", null) { BroadcastLoader.load(locator.appContext) }   // SPEC: MC-10
        loaded.update { it.copy(matches = matches, broadcast = broadcast ?: it.broadcast) }
        soft("match reminders") { MatchWatch.schedule(locator.appContext, matches) }   // SPEC: MC-04
    }

    private suspend fun loadWeather(profile: Profile) = withContext(Dispatchers.IO) {
        val weather = soft("weather", null) { locator.weather.current(profile) }
        loaded.update { it.copy(weather = weather ?: it.weather) }
    }

    private fun loadApps() {
        val installed = soft("apps") { locator.apps.installed() } ?: return
        val recent = soft("recent apps") { locator.apps.recent(RECENT_LIMIT) }.orEmpty()
        loaded.update { it.copy(installed = installed, recent = recent) }
    }

    /** Matches: every 3 h; each minute while the user's team plays; at kick-off when it's sooner. SPEC: HOME-14 */
    private fun matchesInterval(): Long {
        val mine = loaded.value.matches.filter { it.mine }
        if (mine.any { it.state == MatchState.LIVE }) return Schedule.LIVE_MS
        val now = System.currentTimeMillis()
        val kickOff = mine.filter { it.state == MatchState.PRE && it.startsAt > now }.minOfOrNull { it.startsAt - now }
        return minOf(Schedule.MATCHES_MS, kickOff?.coerceAtLeast(Schedule.LIVE_MS) ?: Schedule.MATCHES_MS)
    }

    /** Wakes on each minute: the clock decides the moment and which matches are soon. */
    private suspend fun tick() {
        while (true) {
            val now = System.currentTimeMillis()
            clock.value = now
            delay(MINUTE_MS - now % MINUTE_MS)
        }
    }

    companion object {
        private const val COALESCE_MS = 120L
        private const val MINUTE_MS = 60_000L
        private const val RECENT_LIMIT = 12

        @Volatile private var instance: HomeModel? = null

        fun get(locator: ServiceLocator): HomeModel =
            instance ?: synchronized(this) { instance ?: HomeModel(locator).also { instance = it } }

        private fun hourOf(now: Long) = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)

        /** Fails soft: an error gives [fallback] and a log line. SPEC: ARCH-05 */
        private suspend fun <T> soft(what: String, fallback: T, block: suspend () -> T): T = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(what, e)
            fallback
        }

        private fun <T> soft(what: String, block: () -> T): T? = try {
            block()
        } catch (e: Exception) {
            Logger.w(what, e)
            null
        }
    }
}

/** How often each source refreshes. SPEC: HOME-14 */
private object Schedule {
    const val HOUR_MS = 3_600_000L
    const val WEATHER_MS = 30 * 60_000L
    const val MATCHES_MS = 3 * HOUR_MS
    const val LIVE_MS = 60_000L
    private val PROVIDERS = mapOf("tod" to HOUR_MS, "cinemana" to 2 * HOUR_MS, "youtube" to 3 * HOUR_MS, "tmdb" to 6 * HOUR_MS)

    fun provider(id: String): Long = PROVIDERS[id] ?: 6 * HOUR_MS
}
