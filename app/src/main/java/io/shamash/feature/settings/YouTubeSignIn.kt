package io.shamash.feature.settings

import io.shamash.data.youtube.DeviceCode
import io.shamash.data.youtube.YouTubeAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** YouTube's device sign-in as Settings needs it; [YouTubeAuth] in the app, a fake in tests. */
interface DeviceSignIn {
    suspend fun isSignedIn(): Boolean
    suspend fun start(): DeviceCode?
    suspend fun poll(code: DeviceCode): Boolean
    suspend fun signOut()
}

fun YouTubeAuth.asDeviceSignIn(): DeviceSignIn = object : DeviceSignIn {
    override suspend fun isSignedIn() = this@asDeviceSignIn.isSignedIn()
    override suspend fun start() = this@asDeviceSignIn.start()
    override suspend fun poll(code: DeviceCode) = this@asDeviceSignIn.poll(code)
    override suspend fun signOut() = this@asDeviceSignIn.signOut()
}

sealed interface SignInState {
    data object SignedOut : SignInState
    data object SignedIn : SignInState
    /** Waiting for the user to enter [code] at its verification URL on their phone. */
    data class Waiting(val code: DeviceCode) : SignInState
    data object Failed : SignInState
}

/**
 * Shows a code, then checks at Google's interval until the user approves on their phone or the code expires.
 * SPEC: PRV-06
 */
class YouTubeSignIn(
    private val auth: DeviceSignIn,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _state = MutableStateFlow<SignInState>(SignInState.SignedOut)
    val state: StateFlow<SignInState> = _state.asStateFlow()
    private var job: Job? = null

    fun refresh() {
        scope.launch { _state.value = if (auth.isSignedIn()) SignInState.SignedIn else SignInState.SignedOut }
    }

    fun signIn() {
        if (job?.isActive == true) return
        job = scope.launch {
            val code = auth.start() ?: run { _state.value = SignInState.Failed; return@launch }
            _state.value = SignInState.Waiting(code)
            _state.value = if (waitForApproval(code)) SignInState.SignedIn else SignInState.Failed
        }
    }

    fun signOut() {
        job?.cancel()
        scope.launch {
            auth.signOut()
            _state.value = SignInState.SignedOut
        }
    }

    private suspend fun waitForApproval(code: DeviceCode): Boolean {
        val pause = code.intervalSec.coerceAtLeast(MIN_INTERVAL_SEC) * 1_000L
        while (clock() < code.expiresAt) {
            delay(pause)
            if (auth.poll(code)) return true
        }
        return false
    }

    private companion object {
        const val MIN_INTERVAL_SEC = 5
    }
}
