package io.shamash.feature.tvcontrol

import io.shamash.data.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the switch screen shows. SPEC: TVC-08 */
sealed interface SwitchState {
    data class Idle(val shamashMode: Boolean) : SwitchState
    data class Running(val step: Step) : SwitchState
    data class Finished(val result: SwitchResult, val shamashMode: Boolean) : SwitchState
}

/** The two moves the screen can make; [TvControl] in the app, a fake in tests. */
interface ModeSwitcher {
    suspend fun isShamashMode(): Boolean
    suspend fun switch(progress: (Step) -> Unit): SwitchResult
    suspend fun restore(progress: (Step) -> Unit): SwitchResult
}

fun TvControl.asModeSwitcher(): ModeSwitcher = object : ModeSwitcher {
    override suspend fun isShamashMode() = this@asModeSwitcher.isShamashMode()
    override suspend fun switch(progress: (Step) -> Unit) = this@asModeSwitcher.switch(progress)
    override suspend fun restore(progress: (Step) -> Unit) = this@asModeSwitcher.restore(progress)
}

/**
 * Runs a switch or a restore and turns its progress (called on a background thread) into one [StateFlow].
 * One move at a time: a second press while one runs is ignored. Whatever happens, the screen ends on a result
 * (an unexpected error is [Failure.UNKNOWN]), never stuck on Running.
 */
class SwitchModel(private val tv: ModeSwitcher, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<SwitchState>(SwitchState.Idle(shamashMode = false))
    val state: StateFlow<SwitchState> = _state.asStateFlow()

    fun refresh() {
        if (_state.value is SwitchState.Running) return
        scope.launch { _state.value = SwitchState.Idle(tv.isShamashMode()) }
    }

    fun switch() = run { tv.switch(it) }

    fun restore() = run { tv.restore(it) }

    private fun run(move: suspend ((Step) -> Unit) -> SwitchResult) {
        if (_state.value is SwitchState.Running) return
        _state.value = SwitchState.Running(Step(Phase.CONNECTING))
        scope.launch {
            val result = guarded(SwitchResult.Failed(Failure.UNKNOWN)) {
                move { step -> _state.value = SwitchState.Running(step) }
            }
            _state.value = SwitchState.Finished(result, guarded(false) { tv.isShamashMode() })
        }
    }

    private suspend fun <T> guarded(fallback: T, block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w("TV control screen", e)
        fallback
    }
}
