package io.shamash.feature.matchcentre

import android.content.Context
import io.shamash.data.Logger
import java.io.File

/** Reads the shipped `broadcast.json` and the user's own `broadcast.json` in the app's files, which wins. SPEC: MC-10 */
object BroadcastLoader {
    @Volatile private var cached: BroadcastMap? = null

    fun load(context: Context): BroadcastMap = cached ?: synchronized(this) {
        cached ?: read(context).also { cached = it }
    }

    private fun read(context: Context): BroadcastMap {
        val shipped = try {
            context.assets.open("broadcast.json").bufferedReader().use { BroadcastMap.parse(it.readText()) }
        } catch (e: java.io.IOException) {
            Logger.w("broadcast map", e)
            BroadcastMap()
        }
        val own = File(context.filesDir, "broadcast.json").takeIf { it.isFile }
            ?.let { runCatching { BroadcastMap.parse(it.readText()) }.getOrNull() } ?: BroadcastMap()
        return shipped.overriddenBy(own)
    }
}
