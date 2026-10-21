package io.shamash.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps one value per key until its age passes [maxAge] (which can depend on the value: an empty answer is retried
 * sooner). Loads run one at a time, so a provider's shelves and featured asked together cost one fetch.
 * SPEC: HOME-14
 */
class TimedCache<K, V>(
    private val clock: () -> Long,
    private val maxAge: (V) -> Long,
) {
    private class Entry<K, V>(val key: K, val value: V, val at: Long)

    private val lock = Mutex()
    private var entry: Entry<K, V>? = null

    suspend fun get(key: K, load: suspend () -> V): V = lock.withLock {
        val now = clock()
        val hit = entry?.takeIf { it.key == key && now - it.at in 0 until maxAge(it.value) }
        if (hit != null) return@withLock hit.value
        load().also { entry = Entry(key, it, now) }
    }
}

/** One item from each list in turn ([[a1, a2], [b1]] → [a1, b1, a2]), each [identity] once, at most [max]. */
fun <T> interleave(lists: List<List<T>>, max: Int, identity: (T) -> Any? = { it }): List<T> {
    val seen = HashSet<Any?>()
    val out = ArrayList<T>()
    val longest = lists.maxOfOrNull { it.size } ?: 0
    for (round in 0 until longest) {
        for (list in lists) {
            val item = list.getOrNull(round) ?: continue
            if (out.size < max && seen.add(identity(item))) out += item
        }
    }
    return out
}

/** How often sources refresh. SPEC: HOME-14 */
object Refresh {
    const val MINUTE = 60_000L
    const val HOUR = 60 * MINUTE

    /** An empty answer (usually a failure) is asked again after this, not after the full interval. */
    const val RETRY_EMPTY = 10 * MINUTE

    fun <T> every(full: Long): (List<T>) -> Long = { if (it.isEmpty()) RETRY_EMPTY else full }
}
