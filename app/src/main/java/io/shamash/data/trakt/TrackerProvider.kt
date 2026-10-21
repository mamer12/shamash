package io.shamash.data.trakt

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.Preferences
import io.shamash.data.ProfileSlot
import io.shamash.data.Say
import io.shamash.data.ServerProvider
import io.shamash.data.asSecretStore
import io.shamash.data.json
import io.shamash.data.softly
import io.shamash.domain.AddOn
import io.shamash.domain.MediaItem
import io.shamash.domain.MyListEntry
import io.shamash.domain.PersonalBridge
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import io.shamash.domain.ProfileListsOwner
import io.shamash.domain.ProviderCredentials
import io.shamash.domain.RowSource
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.Signal
import io.shamash.domain.TestResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Trakt or Simkl sync for one adult profile: sign in with a code (the user's own client, like YouTube's), then every
 * 2 h the watchlist is mirrored into the one My List and newly watched titles become FINISH signals. Writing
 * "finished" movies back is optional and off by default. Tokens are per-profile secrets; a refused refresh
 * (`invalid_grant`) signs out. Guest and Kids profiles never sync. SPEC: PEX-26, PER-02, PER-14, PRF-10, PRF-11
 */
class TrackerProvider(
    id: String,
    private val api: TrackerApi,
    private val credentials: ProviderCredentials,
    private val state: ProfileSlot<TrackerState>,
    private val activeProfile: suspend () -> Profile,
    say: Say,
    private val clock: () -> Long = System::currentTimeMillis,
) : ServerProvider(id, say, clock), PersonalBridge, ProfileListsOwner {
    /** Remove: the active profile's own list goes too. SPEC: PEX-05 */
    override suspend fun clearProfileLists() = state.clear()

    private val version = MutableStateFlow(0)
    private val finishedFlow = MutableSharedFlow<MediaItem>(extraBufferCapacity = 64)
    private val syncLock = Mutex()
    private val lastSync = ConcurrentHashMap<String, Long>()

    override val providerId: String get() = id

    override fun rowSources() = listOf(RowSource("watchlist", R.string.more_trakt_row_watchlist, defaultOn = false))

    override val salt: String get() = version.value.toString()

    /** Adults only (PRF-10, PRF-11). */
    override fun isEnabled(profile: Profile) = id in profile.addons && mayUse(profile)

    // --- PersonalBridge (PER-13, PER-14) ---

    override val listEntries: Flow<List<MyListEntry>> = version.map { state.get().list }

    override val finished: Flow<MediaItem> = finishedFlow.asSharedFlow()

    override suspend fun addToList(item: MediaItem, profile: Profile) {
        val entry = MyListEntry.of(item, clock())
        state.update { s -> if (s.list.any { it.itemKey == entry.itemKey }) s else s.copy(list = listOf(entry) + s.list, dismissed = s.dismissed - entry.itemKey) }
        version.value++
    }

    /** Removed from My List on the TV: kept out of later imports too (the tracker itself is not changed). */
    override suspend fun removeFromList(itemKey: String) {
        state.update { s -> s.copy(list = s.list.filterNot { it.itemKey == itemKey }, dismissed = (s.dismissed - itemKey + itemKey).takeLast(MAX_DISMISSED)) }
        version.value++
    }

    override suspend fun clearHistory() = Unit   // nothing of the tracker's history is kept on the TV beyond signals

    // --- sign-in ---

    suspend fun client(): Client? {
        val clientId = credentials.get(CLIENT_ID) ?: return null
        val secret = credentials.get(CLIENT_SECRET)
        if (credentials.fields.any { it.key == CLIENT_SECRET && it.required } && secret == null) return null
        return Client(clientId, secret)
    }

    suspend fun isSignedIn(): Boolean = session() != null

    /** Starts a sign-in; null for Kids and Guest, without a client, or when the tracker can't be reached. */
    suspend fun startSignIn(): DeviceCode? {
        if (!mayUse(activeProfile())) return null
        val client = client() ?: return null
        return softly("$id sign-in", null) { api.start(client) }
    }

    /** One check, at [DeviceCode.intervalSec]; on [PollAnswer.Granted] the sign-in is saved and a sync runs. */
    suspend fun poll(code: DeviceCode): PollAnswer {
        val client = client() ?: return PollAnswer.Failed
        val answer = softly("$id sign-in poll", PollAnswer.Pending as PollAnswer) { api.poll(client, code) }
        if (answer is PollAnswer.Granted) {
            saveSession(answer.session)
            state.update { it.copy(since = 0) }
            softly("$id first sync", false) { sync(activeProfile(), force = true) }
        }
        return answer
    }

    /** Forgets the sign-in and what it imported (My List entries go with the next reconcile). */
    suspend fun signOut() {
        credentials.set(SESSION, null)
        state.update { it.copy(list = emptyList(), since = 0, pushedUntil = 0) }
        version.value++
    }

    suspend fun writeBackOn(): Boolean = state.get().writeBack

    /** Turning it on pushes only what is finished from now on. */
    suspend fun setWriteBack(on: Boolean) {
        state.update { it.copy(writeBack = on, pushedUntil = if (on) clock() else it.pushedUntil) }
    }

    private suspend fun session(): Session? =
        credentials.get(SESSION)?.let { runCatching { json.decodeFromString(Session.serializer(), it) }.getOrNull() }

    private suspend fun saveSession(s: Session) = credentials.set(SESSION, json.encodeToString(Session.serializer(), s))

    private suspend fun renew(client: Client, s: Session): Session? = when (val a = api.refresh(client, s)) {
        is RefreshAnswer.Ok -> a.session.also { saveSession(it) }
        RefreshAnswer.SignedOut -> null.also { signOut() }
        RefreshAnswer.Unavailable -> null
    }

    /** Runs [block] with a current token: renewed when about to expire, and once more after a 401. */
    private suspend fun <T> authorized(block: suspend (Client, Session) -> T): T? {
        val client = client() ?: return null
        var s = session() ?: return null
        val until = s.expiresAt
        if (until != null && clock() >= until - EARLY_MS) s = renew(client, s) ?: return null
        return try {
            block(client, s)
        } catch (e: TrackerUnauthorized) {
            val renewed = renew(client, s) ?: return null
            try { block(client, renewed) } catch (again: TrackerUnauthorized) { null.also { signOut() } }
        }
    }

    // --- sync ---

    /** Imports the watchlist and new history at most every 2 h (or now with [force]); false when nothing ran. */
    suspend fun sync(profile: Profile, force: Boolean = false): Boolean = syncLock.withLock {
        if (!isEnabled(profile)) return@withLock false
        val now = clock()
        if (!force && now - (lastSync[profile.id] ?: Long.MIN_VALUE / 2) < SYNC_EVERY_MS) return@withLock false
        val before = state.get()
        val listening = finishedFlow.subscriptionCount.value > 0
        val library = authorized { c, s -> api.library(c, s, before.since) } ?: return@withLock false
        lastSync[profile.id] = now
        val plan = TrackerSync.plan(id, before, library, listening)
        state.set(plan.state)
        plan.finished.forEach { finishedFlow.tryEmit(it) }
        version.value++
        true
    }

    override suspend fun shelves(profile: Profile): List<Shelf> {
        if (isEnabled(profile)) softly("$id sync", false) { sync(profile) }
        return super.shelves(profile)
    }

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> = wanted.mapNotNull { s ->
        source(s, ShelfStyle.POSTER) { state.get().list.take(ROW_MAX).map { it.toItem() } }
    }

    override suspend fun featured(profile: Profile): List<MediaItem> = emptyList()

    /** Pushes movies the profile finished since the last push, when write-back is on. Gives how many went. */
    suspend fun writeBack(profileId: String, signals: List<Signal>): Int {
        val p = activeProfile()
        if (p.id != profileId || !isEnabled(p)) return 0
        val s = state.get()
        if (!s.writeBack) return 0
        val movies = TrackerSync.toPush(signals, s.pushedUntil)
        if (movies.isEmpty()) return 0
        val ok = authorized { c, session -> api.pushWatched(c, session, movies) } ?: false
        if (!ok) return 0
        state.update { it.copy(pushedUntil = maxOf(it.pushedUntil, movies.maxOf { m -> m.second })) }
        return movies.size
    }

    override suspend fun test(): TestResult {
        if (!mayUse(activeProfile())) return failed(R.string.more_trakt_adults_only)
        if (session() == null) return failed(R.string.more_trakt_test_sign_in)
        return try {
            val lib = authorized { c, s -> api.library(c, s, clock()) } ?: return failed(R.string.more_trakt_test_sign_in)
            TestResult(true, say(R.string.more_trakt_test_ok).format(lib.watchlist.size))
        } catch (e: Exception) {
            failed(R.string.pex_test_failed)
        }
    }

    companion object {
        const val TRAKT = "trakt"
        const val SIMKL = "simkl"
        const val CLIENT_ID = "client-id"
        const val CLIENT_SECRET = "client-secret"
        const val SESSION = "session"
        const val SYNC_EVERY_MS = 2 * 3_600_000L
        private const val EARLY_MS = 60_000L
        private const val ROW_MAX = 40
        private const val MAX_DISMISSED = 500

        fun mayUse(profile: Profile) = profile.kind == ProfileKind.ADULT

        /** The registration of one tracker (PEX-01); [api] builds its client. */
        fun addOn(id: String, nameRes: Int, descRes: Int, secretRequired: Boolean, api: (Http) -> TrackerApi) =
            AddOn(id, nameRes, descRes, TrackerFields.of(secretRequired)) { deps ->
                val http = deps.service(Http::class.java) ?: return@AddOn null
                val prefs = deps.service(Preferences::class.java) ?: return@AddOn null
                val context = deps.service(android.content.Context::class.java)
                val slot = ProfileSlot(prefs.asSecretStore(), "${id}_state", TrackerState.serializer(), TrackerState()) { prefs.current().id }
                TrackerProvider(id, api(http), deps.credentials, slot, deps::profile, say = { r -> context?.getString(r).orEmpty() })
            }

        val trakt = addOn(TRAKT, R.string.more_trakt_name, R.string.more_trakt_desc, secretRequired = true) { TraktApi(TrackerHttp(it.client)) }
        val simkl = addOn(SIMKL, R.string.more_simkl_name, R.string.more_simkl_desc, secretRequired = false) { SimklApi(TrackerHttp(it.client)) }
    }
}
