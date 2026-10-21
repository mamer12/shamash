package io.shamash.data.cinemana

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A small map of answers that expire, the least recently used dropped past [max]. Loads run outside the lock, so a
 * slow request never blocks the others (two callers may load the same key once each).
 */
class MemoCache<K, V>(private val clock: () -> Long, private val max: Int) {
    private class Entry<V>(val value: V, val at: Long)

    private val lock = Mutex()
    private val entries = LinkedHashMap<K, Entry<V>>(16, 0.75f, true)

    suspend fun get(key: K, freshMs: Long, load: suspend () -> V): V {
        val now = clock()
        lock.withLock { entries[key]?.takeIf { now - it.at in 0 until freshMs }?.let { return it.value } }
        val value = load()
        lock.withLock {
            entries[key] = Entry(value, clock())
            while (entries.size > max) entries.remove(entries.keys.first())
        }
        return value
    }

    suspend fun clear() = lock.withLock { entries.clear() }
}
