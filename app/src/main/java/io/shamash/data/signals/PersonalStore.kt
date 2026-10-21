package io.shamash.data.signals

import io.shamash.data.Logger
import io.shamash.data.json
import io.shamash.domain.PersonalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/*
 * One small JSON file per profile under [dir] (`<id>.json`, <= 300 KB), written to a temp file and moved into place,
 * so a crash never leaves half a file. A corrupt or unreadable file reads as empty. Public surface: flow, read,
 * update, delete. SPEC: PER-17
 */
class PersonalStore(private val dir: File, private val writeOk: () -> Boolean = { true }) {
    private val states = ConcurrentHashMap<String, MutableStateFlow<PersonalData?>>()
    private val lock = Mutex()

    /** The profile's data; loads on first use and follows every update. */
    fun flow(profileId: String): Flow<PersonalData?> = state(profileId).asStateFlow()

    suspend fun read(profileId: String): PersonalData = lock.withLock { load(profileId) }

    /** Changes the profile's data atomically. Nothing is written when [writeOk] is false (Guest). */
    suspend fun update(profileId: String, change: (PersonalData) -> PersonalData): PersonalData = lock.withLock {
        val old = load(profileId)
        if (!writeOk()) return@withLock old
        val next = fit(change(old))
        withContext(Dispatchers.IO) { write(profileId, next) }
        state(profileId).value = next
        next
    }

    suspend fun delete(profileId: String) = lock.withLock {
        withContext(Dispatchers.IO) { runCatching { file(profileId).delete() } }
        state(profileId).value = PersonalData()
    }

    private fun state(id: String) = states.getOrPut(id) { MutableStateFlow(null) }

    private suspend fun load(id: String): PersonalData {
        state(id).value?.let { return it }
        val data = withContext(Dispatchers.IO) { readFile(id) }
        state(id).value = data
        return data
    }

    private fun readFile(id: String): PersonalData {
        val f = file(id)
        if (!f.exists()) return PersonalData()
        return try {
            json.decodeFromString<PersonalData>(f.readText())
        } catch (e: Exception) {
            Logger.w("personal data unreadable", e)
            PersonalData()
        }
    }

    /** Keeps the encoded size under [MAX_BYTES] by letting the oldest signals go first. SPEC: PER-17 */
    internal fun fit(data: PersonalData): PersonalData {
        var d = data
        while (encode(d).length > MAX_BYTES && d.signals.isNotEmpty()) d = d.copy(signals = d.signals.drop(maxOf(1, d.signals.size / 10)))
        if (encode(d).length > MAX_BYTES) d = d.copy(myList = d.myList.take(MAX_LIST_FALLBACK))
        return d
    }

    private fun encode(d: PersonalData) = json.encodeToString(d)

    private fun write(id: String, data: PersonalData) {
        try {
            dir.mkdirs()
            val target = file(id)
            val temp = File(dir, target.name + ".tmp")
            temp.writeText(encode(data))
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            Logger.w("personal data not saved", e)
        }
    }

    private fun file(id: String) = File(dir, id.replace(Regex("[^A-Za-z0-9_-]"), "_").ifEmpty { "_" } + ".json")

    companion object {
        const val MAX_BYTES = 300_000
        private const val MAX_LIST_FALLBACK = 200
    }
}
