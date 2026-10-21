package io.shamash.feature.personalization

import io.shamash.data.Logger
import io.shamash.data.signals.PersonalStore
import io.shamash.domain.MediaItem
import io.shamash.domain.Moment
import io.shamash.domain.Mute
import io.shamash.domain.Mutes
import io.shamash.domain.MyListEntry
import io.shamash.domain.MyListOps
import io.shamash.domain.MyListSync
import io.shamash.domain.PersonalBridge
import io.shamash.domain.PersonalState
import io.shamash.domain.Profile
import io.shamash.domain.Signal
import io.shamash.domain.SignalKind
import io.shamash.domain.Signals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Calendar

/*
 * The personalization facade the app talks to: records signals for the active profile (adults only), keeps mutes and
 * the one My List, and mirrors add-on lists. Fails soft: any error is logged and ignored. Public surface: state,
 * record, recordFinish, noteOpened, settleOpen, mute, unmute, toggleMyList, removeMyList, setLearnOrder, clear, start.
 * SPEC: PER-01..08, PER-13..15
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Personalization(
    private val store: PersonalStore,
    private val profile: Flow<Profile>,
    private val bridges: () -> List<PersonalBridge>,
    private val scope: CoroutineScope,
    private val recordingOn: () -> Boolean = { true },
    private val clock: () -> Long = System::currentTimeMillis,
    private val hourOf: (Long) -> Int = { Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.HOUR_OF_DAY) },
) {
    private val syncLock = Mutex()
    private var pendingOpen: Pair<MediaItem, Long>? = null

    /** The active profile's data, following profile switches. */
    val state: StateFlow<PersonalState> = profile.map { it.id }.distinctUntilChanged()
        .flatMapLatest { id -> store.flow(id).also { scope.launch { soft("load") { store.read(id) } } }.filterNotNull().map { PersonalState(id, it) } }
        .stateIn(scope, SharingStarted.Eagerly, PersonalState.EMPTY)

    /** Starts mirroring add-on lists and finished titles; call once the providers exist. */
    fun start() {
        bridges().forEach { bridge ->
            scope.launch { soft("finished ${bridge.providerId}") { bridge.finished.collect { recordFinish(it) } } }
            scope.launch {
                soft("list ${bridge.providerId}") { bridge.listEntries.distinctUntilChanged().collect { sync(bridge) } }
            }
        }
        scope.launch { soft("resync") { profile.map { it.id }.distinctUntilChanged().collect { bridges().forEach { b -> sync(b) } } } }
    }

    private suspend fun active(): Profile = profile.first()

    // --- signals ---

    /** Records [kind] for [item] when the active profile may teach the TV. SPEC: PER-01, PER-02 */
    suspend fun record(item: MediaItem, kind: SignalKind) = soft("record") {
        val p = active()
        if (!Signals.mayRecord(p, recordingOn())) return@soft
        val now = clock()
        val signal = Signals.make(p.id, item, kind, now, hourOf(now))
        store.update(p.id) { it.copy(signals = Signals.add(it.signals, signal, now)) }
    }

    suspend fun recordFinish(item: MediaItem) = record(item, SignalKind.FINISH)

    /** A title was opened now: OPEN is recorded and remembered to tell a SKIP later. SPEC: PER-02 */
    suspend fun noteOpened(item: MediaItem) {
        record(item, SignalKind.OPEN)
        pendingOpen = item to clock()
    }

    /** Back on Home: a title left within two minutes is a SKIP unless it was finished meanwhile. SPEC: PER-02 */
    suspend fun settleOpen() {
        val (item, at) = pendingOpen ?: return
        pendingOpen = null
        val now = clock()
        val finished = state.value.data.signals.any { it.kind == SignalKind.FINISH && it.at >= at && it.itemKey == io.shamash.domain.ItemKeys.of(item) }
        if (Signals.isSkip(at, now) && !finished && item.open !is io.shamash.domain.OpenTarget.Internal) record(item, SignalKind.SKIP)
    }

    // --- Not for you ---

    suspend fun mute(item: MediaItem, lessLikeThis: Boolean) = soft("mute") {
        val p = active()
        val now = clock()
        val mute = Mutes.of(item, now, lessLikeThis)
        store.update(p.id) { it.copy(mutes = Mutes.add(it.mutes, mute)) }
        record(item, if (lessLikeThis) SignalKind.LESS_LIKE_THIS else SignalKind.MUTE)
    }

    suspend fun unmute(mute: Mute) = soft("unmute") {
        store.update(active().id) { it.copy(mutes = Mutes.remove(it.mutes, mute)) }
    }

    // --- My List ---

    /** Adds [item] to the profile's My List, or removes it; an add-on's own list follows. SPEC: PER-13, PER-14 */
    suspend fun toggleMyList(item: MediaItem) = soft("my list") {
        val p = active()
        store.update(p.id) { it.copy(myList = MyListOps.toggle(it.myList, item, clock())) }
        bridges().filter { it.providerId == item.providerId }.forEach { sync(it) }
    }

    suspend fun removeMyList(itemKey: String) = soft("my list remove") {
        val p = active()
        store.update(p.id) { it.copy(myList = MyListOps.remove(it.myList, itemKey)) }
        bridges().forEach { sync(it) }
    }

    fun inMyList(item: MediaItem): Boolean = MyListOps.contains(state.value.data.myList, item)

    private suspend fun sync(bridge: PersonalBridge) = syncLock.withLock {
        soft("sync ${bridge.providerId}") {
            val p = active()
            if (!p.cinemanaAddon && bridge.providerId == "cinemana") return@soft
            val mirror = bridge.listEntries.first()
            var plan: io.shamash.domain.SyncPlan? = null
            store.update(p.id) { data ->
                val first = bridge.providerId !in data.mirrorSeen
                val made = MyListSync.reconcile(
                    bridge.providerId, data.myList, mirror, data.mirrorSeen[bridge.providerId].orEmpty().toSet(), first,
                )
                plan = made
                data.copy(myList = made.list, mirrorSeen = data.mirrorSeen + (bridge.providerId to made.seen))
            }
            plan?.let { made ->
                made.addToMirror.forEach { bridge.addToList(it.toItem(), p) }
                made.removeFromMirror.forEach { bridge.removeFromList(it) }
            }
        }
    }

    // --- settings ---

    suspend fun setLearnOrder(on: Boolean) = soft("learn order") { store.update(active().id) { it.copy(learnOrder = on) } }

    /** Deletes the profile's signals, mutes and ratings, and the add-ons' history. SPEC: PER-04 */
    suspend fun clear() = soft("clear") {
        store.update(active().id) { it.copy(signals = emptyList(), mutes = emptyList()) }
        bridges().forEach { b -> soft("clear ${b.providerId}") { b.clearHistory() } }
    }

    val moment: Moment get() = Moment.at(hourOf(clock()))

    private suspend fun soft(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w("personalization $what", e)
        }
    }
}
