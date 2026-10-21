package io.shamash.feature.companion

import io.shamash.data.backup.BackupPayload
import io.shamash.data.backup.ImportMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/** A question the phone needs the person at the TV to answer. */
sealed interface PhonePrompt {
    /** Make a backup? (CMP-13) */
    class Export(val answer: CompletableDeferred<Boolean> = CompletableDeferred()) : PhonePrompt

    /** Merge, Replace (owner PIN on the TV) or nothing (CMP-15); null = declined. */
    class Import(
        val payload: BackupPayload,
        val answer: CompletableDeferred<ImportMode?> = CompletableDeferred(),
    ) : PhonePrompt
}

/**
 * The one open question at a time from the phone; the TV's overlay shows it and completes its answer. A second
 * request while one is open is told the TV is busy; an unanswered question times out as a "no". SPEC: CMP-13, CMP-15
 */
object PhonePrompts {
    const val ANSWER_WAIT_MS = 90_000L

    private val _current = MutableStateFlow<PhonePrompt?>(null)
    val current: StateFlow<PhonePrompt?> = _current.asStateFlow()

    sealed interface Asked<out T> {
        data class Answered<T>(val value: T?) : Asked<T>
        data object Busy : Asked<Nothing>
    }

    suspend fun <T> ask(prompt: PhonePrompt, answer: CompletableDeferred<T>, waitMs: Long = ANSWER_WAIT_MS): Asked<T> {
        if (!_current.compareAndSet(null, prompt)) return Asked.Busy
        return try {
            Asked.Answered(withTimeoutOrNull(waitMs) { answer.await() })
        } finally {
            _current.compareAndSet(prompt, null)
        }
    }
}
