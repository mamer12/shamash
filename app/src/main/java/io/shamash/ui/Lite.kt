package io.shamash.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import io.shamash.data.Logger

/** The Settings choice for lite drawing: follow the TV, or force it. SPEC: PERF-06 */
enum class LiteChoice { AUTO, ON, OFF }

/** What decides lite drawing; pure, so the rule is testable. SPEC: PERF-06 */
object LiteRule {
    const val MAX_RAM_BYTES = 2_684_354_560L   // 2.5 GB
    const val MAX_SDK = 30                      // Android 11

    fun weak(lowRam: Boolean, totalMem: Long, sdk: Int): Boolean =
        lowRam || (totalMem in 1..MAX_RAM_BYTES) || sdk <= MAX_SDK

    fun on(choice: LiteChoice, weak: Boolean): Boolean = when (choice) {
        LiteChoice.AUTO -> weak
        LiteChoice.ON -> true
        LiteChoice.OFF -> false
    }
}

/**
 * Lite drawing for weak TVs (small GPUs choke on full-screen gradients and offscreen layers): read while composing
 * and drawing, a Compose state so a change in Settings applies at once. SPEC: PERF-06
 */
object Lite {
    private var chosen by mutableStateOf(LiteChoice.AUTO)
    private var weak by mutableStateOf(false)
    private var appContext: Context? = null

    val choice: LiteChoice get() = chosen
    val on: Boolean get() = LiteRule.on(chosen, weak)

    fun load(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        weak = deviceIsWeak(app)
        chosen = try {
            val saved = app.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
            LiteChoice.entries.firstOrNull { it.name == saved } ?: LiteChoice.AUTO
        } catch (e: Exception) {
            Logger.w("lite read", e)
            LiteChoice.AUTO
        }
    }

    fun choose(choice: LiteChoice) {
        chosen = choice
        try {
            appContext?.getSharedPreferences(FILE, Context.MODE_PRIVATE)?.edit()?.putString(KEY, choice.name)?.apply()
        } catch (e: Exception) {
            Logger.w("lite write", e)
        }
    }

    private fun deviceIsWeak(context: Context): Boolean = try {
        val am = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        LiteRule.weak(am.isLowRamDevice, info.totalMem, Build.VERSION.SDK_INT)
    } catch (e: Exception) {
        Logger.w("lite probe", e)
        false
    }

    private const val FILE = "render"
    private const val KEY = "lite"
}

/**
 * A fade read while drawing. Lite multiplies the alpha into each draw instead of painting the content offscreen
 * first (the same picture for what doesn't overlap: a photo, a line of text). SPEC: PERF-06
 */
fun Modifier.fade(alpha: () -> Float): Modifier = graphicsLayer {
    this.alpha = alpha()
    if (Lite.on) compositingStrategy = CompositingStrategy.ModulateAlpha
}
