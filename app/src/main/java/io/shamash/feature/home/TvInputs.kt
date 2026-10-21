package io.shamash.feature.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A TV input (HDMI, AV…) as the TV names it. */
@Immutable
data class TvInput(val id: String, val label: String)

/** The TV's own inputs and settings, opened directly. SPEC: HOME-01, HOME-13 */
object TvInputs {
    suspend fun list(context: Context): List<TvInput> = withContext(Dispatchers.IO) {
        try {
            val manager = context.getSystemService(TvInputManager::class.java) ?: return@withContext emptyList()
            manager.tvInputList
                .filter { it.isPassthroughInput && !it.isHidden(context) }
                .map { TvInput(it.id, (it.loadCustomLabel(context) ?: it.loadLabel(context)).toString()) }
        } catch (e: Exception) {
            Log.w("Shamash", "inputs: $e")
            emptyList()
        }
    }

    fun open(context: Context, input: TvInput): Boolean =
        start(context, Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(input.id)))

    fun openTvSettings(context: Context): Boolean = start(context, Intent(Settings.ACTION_SETTINGS))

    private fun start(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Log.w("Shamash", "open: $e")
        false
    } catch (e: SecurityException) {
        Log.w("Shamash", "open: $e")
        false
    }
}
