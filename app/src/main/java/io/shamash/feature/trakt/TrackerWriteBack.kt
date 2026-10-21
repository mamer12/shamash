package io.shamash.feature.trakt

import io.shamash.data.softly
import io.shamash.data.trakt.TrackerProvider
import io.shamash.domain.PersonalState
import io.shamash.domain.Provider
import io.shamash.domain.SignalKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Optional write-back of "finished" (PEX-26): whenever the active profile records a new FINISH, each tracker that is
 * on, signed in and has write-back switched on pushes the finished movies it hasn't pushed yet. Fails soft.
 */
object TrackerWriteBack {
    private const val SETTLE_MS = 5_000L

    @OptIn(FlowPreview::class)
    fun start(state: StateFlow<PersonalState>, providers: List<Provider>, scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)) {
        val trackers = providers.filterIsInstance<TrackerProvider>()
        if (trackers.isEmpty()) return
        scope.launch {
            state.map { s -> s.profileId to s.data.signals.lastOrNull { it.kind == SignalKind.FINISH }?.at }
                .distinctUntilChanged().debounce(SETTLE_MS)
                .collect { (profileId, last) ->
                    if (last == null || profileId.isEmpty()) return@collect
                    val signals = state.value.data.signals
                    trackers.forEach { t -> softly("${t.id} write-back", 0) { t.writeBack(profileId, signals) } }
                }
        }
    }
}
