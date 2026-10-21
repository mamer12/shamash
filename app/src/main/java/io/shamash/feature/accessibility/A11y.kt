package io.shamash.feature.accessibility

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.shamash.data.Logger

/** Text size steps (PLT-11): multiplied with the system font scale. */
enum class TextScale(val factor: Float) { NORMAL(1f), LARGE(1.15f), LARGER(1.3f) }

/**
 * The accessibility choices. A null [highContrast] or [reduceMotion] means "not chosen": follow the TV's own setting
 * (PLT-12, PLT-14). SPEC: PLT-11, PLT-12, PLT-14
 */
data class A11ySettings(
    val textScale: TextScale = TextScale.NORMAL,
    val highContrast: Boolean? = null,
    val reduceMotion: Boolean? = null,
) {
    /** The font scale to draw with, given the system's. */
    fun fontScale(system: Float): Float = system * textScale.factor
}

/** What the TV itself says (read once at load); injectable so the rules stay testable. */
data class SystemA11y(val highContrast: Boolean = false, val animationsOff: Boolean = false)

/** The settings after following the system where the user has not chosen. */
data class EffectiveA11y(val textScale: TextScale, val highContrast: Boolean, val reduceMotion: Boolean) {
    companion object {
        fun of(s: A11ySettings, system: SystemA11y) = EffectiveA11y(
            textScale = s.textScale,
            highContrast = s.highContrast ?: system.highContrast,
            reduceMotion = s.reduceMotion ?: system.animationsOff,
        )
    }
}

private inline fun <T> safely(what: String, fallback: T, block: () -> T): T =
    try { block() } catch (e: Exception) { Logger.w(what, e); fallback }

/** Reads and writes the choices (device-wide, not per profile: they describe the person at the screen). */
internal class A11yStore(private val context: Context) {
    private val prefs get() = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun read(): A11ySettings = safely("a11y read", A11ySettings()) {
        val p = prefs
        A11ySettings(
            textScale = TextScale.entries.firstOrNull { it.name == p.getString(SCALE, null) } ?: TextScale.NORMAL,
            highContrast = p.optBool(CONTRAST),
            reduceMotion = p.optBool(MOTION),
        )
    }

    fun write(s: A11ySettings) {
        safely("a11y write", Unit) {
            prefs.edit().putString(SCALE, s.textScale.name).putOpt(CONTRAST, s.highContrast).putOpt(MOTION, s.reduceMotion).apply()
        }
    }

    fun system(): SystemA11y = safely("a11y system", SystemA11y()) {
        val r = context.contentResolver
        SystemA11y(
            highContrast = Settings.Secure.getInt(r, "high_text_contrast_enabled", 0) == 1,
            animationsOff = Settings.Global.getFloat(r, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f,
        )
    }

    private fun android.content.SharedPreferences.optBool(key: String): Boolean? =
        if (contains(key)) getBoolean(key, false) else null

    private fun android.content.SharedPreferences.Editor.putOpt(key: String, v: Boolean?) =
        if (v == null) remove(key) else putBoolean(key, v)

    private companion object {
        const val FILE = "a11y"
        const val SCALE = "text_scale"
        const val CONTRAST = "high_contrast"
        const val MOTION = "reduce_motion"
    }
}

/** The live accessibility state the UI reads (Compose state, so a change redraws at once). SPEC: PLT-11..14 */
object A11y {
    private var chosen by mutableStateOf(A11ySettings())
    private var system by mutableStateOf(SystemA11y())
    private var store: A11yStore? = null

    /** The choices as saved, for the Settings panel. */
    val settings: A11ySettings get() = chosen
    val effective: EffectiveA11y get() = EffectiveA11y.of(chosen, system)
    val highContrast: Boolean get() = effective.highContrast
    val reduceMotion: Boolean get() = effective.reduceMotion

    /** Animation length: none when motion is reduced. */
    fun ms(normal: Int): Int = if (reduceMotion) 0 else normal

    fun load(context: Context) {
        if (store != null) return
        val s = A11yStore(context.applicationContext)
        store = s
        chosen = s.read()
        system = s.system()
    }

    fun update(change: (A11ySettings) -> A11ySettings) {
        chosen = change(chosen)
        store?.write(chosen)
    }
}
