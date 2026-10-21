package io.shamash.feature.tvcontrol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchModelTest {
    private class FakeTv : ModeSwitcher {
        var shamash = false
        var switches = 0
        val gate = CompletableDeferred<Unit>()
        override suspend fun isShamashMode() = shamash
        override suspend fun switch(progress: (Step) -> Unit): SwitchResult {
            switches++
            progress(Step(Phase.TURNING_OFF_APPS, 1, 3))
            gate.await()
            shamash = true
            return SwitchResult.Switched(listOf("a", "b", "c"), emptyList(), emptyList(), remoteButtons = true)
        }
        override suspend fun restore(progress: (Step) -> Unit): SwitchResult {
            shamash = false
            return SwitchResult.Restored(listOf("a"), emptyList(), homeRestored = true)
        }
    }

    @Test
    fun `TVC_08 progress shows while switching, then the result and the new mode`() = runTest {
        val tv = FakeTv()
        val model = SwitchModel(tv, this)
        model.switch()
        advanceUntilIdle()
        assertEquals(SwitchState.Running(Step(Phase.TURNING_OFF_APPS, 1, 3)), model.state.value)
        tv.gate.complete(Unit)
        advanceUntilIdle()
        val end = model.state.value as SwitchState.Finished
        assertTrue(end.shamashMode)
        assertTrue(end.result is SwitchResult.Switched)
    }

    @Test
    fun `TVC_08 a second press while a switch runs is ignored`() = runTest {
        val tv = FakeTv()
        val model = SwitchModel(tv, this)
        model.switch()
        model.switch()
        model.restore()
        advanceUntilIdle()
        assertEquals(1, tv.switches)
        tv.gate.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `TVC_05 Original goes back and reports the mode`() = runTest {
        val tv = FakeTv().apply { shamash = true }
        val model = SwitchModel(tv, this)
        model.restore()
        advanceUntilIdle()
        assertEquals(false, (model.state.value as SwitchState.Finished).shamashMode)
    }

    @Test
    fun `TVC_08 refresh reads the current mode`() = runTest {
        val model = SwitchModel(FakeTv().apply { shamash = true }, this)
        model.refresh()
        advanceUntilIdle()
        assertEquals(SwitchState.Idle(shamashMode = true), model.state.value)
    }

    @Test
    fun `TVC_08 every phase and failure has words`() {
        Phase.entries.forEach { assertTrue(SwitchText.phase(it) != 0) }
        Failure.entries.forEach { assertTrue(SwitchText.failure(it) != 0) }
    }
}
