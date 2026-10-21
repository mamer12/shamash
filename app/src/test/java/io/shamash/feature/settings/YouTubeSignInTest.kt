package io.shamash.feature.settings

import io.shamash.data.youtube.DeviceCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class YouTubeSignInTest {
    private class FakeAuth(private val approveAfter: Int, private val noCode: Boolean = false) : DeviceSignIn {
        var polls = 0
        var signedIn = false
        override suspend fun isSignedIn() = signedIn
        override suspend fun start() =
            if (noCode) null else DeviceCode("ABCD-EFGH", "google.com/device", "dev", intervalSec = 5, expiresAt = 60_000)
        override suspend fun poll(code: DeviceCode): Boolean {
            polls++
            signedIn = polls >= approveAfter
            return signedIn
        }
        override suspend fun signOut() { signedIn = false }
    }

    @Test
    fun `PRV_06 shows the code, then signs in once the phone approves`() = runTest {
        val auth = FakeAuth(approveAfter = 3)
        val signIn = YouTubeSignIn(auth, this, clock = { testScheduler.currentTime })
        signIn.signIn()
        advanceTimeBy(1)
        assertTrue(signIn.state.value is SignInState.Waiting)
        advanceUntilIdle()
        assertEquals(SignInState.SignedIn, signIn.state.value)
        assertEquals(3, auth.polls)
    }

    @Test
    fun `PRV_06 an expired code fails`() = runTest {
        val auth = FakeAuth(approveAfter = Int.MAX_VALUE)
        val signIn = YouTubeSignIn(auth, this, clock = { testScheduler.currentTime })
        signIn.signIn()
        advanceUntilIdle()
        assertEquals(SignInState.Failed, signIn.state.value)
        assertEquals(12, auth.polls)   // every 5 s for 60 s
    }

    @Test
    fun `PRV_06 no code from Google fails at once`() = runTest {
        val signIn = YouTubeSignIn(FakeAuth(1, noCode = true), this)
        signIn.signIn()
        advanceUntilIdle()
        assertEquals(SignInState.Failed, signIn.state.value)
    }

    @Test
    fun `PRV_06 sign out`() = runTest {
        val auth = FakeAuth(1).apply { signedIn = true }
        val signIn = YouTubeSignIn(auth, this)
        signIn.refresh()
        advanceUntilIdle()
        assertEquals(SignInState.SignedIn, signIn.state.value)
        signIn.signOut()
        advanceUntilIdle()
        assertEquals(SignInState.SignedOut, signIn.state.value)
    }
}
