package io.shamash.domain

import kotlinx.serialization.Serializable

/*
 * What the profile did, kept locally: the signal record, its caps and who may record. Everything personal is derived
 * from these (weights, taste, reasons); nothing leaves the TV. Public surface: SignalKind, Signal, Signals, ItemKeys.
 * SPEC: PER-01, PER-02, PER-16
 */

@Serializable
enum class SignalKind { OPEN, FINISH, SKIP, LIKE, DISLIKE, MUTE, LESS_LIKE_THIS }

@Serializable
data class Signal(
    val profileId: String,
    val itemKey: String,
    val providerId: String,
    val kind: SignalKind,
    val at: Long,
    val hourOfDay: Int,
    val genres: List<String> = emptyList(),
    val title: String = "",          // for "Because you watched <title>" (PER-11) and Why? (PER-12)
    val appPackage: String? = null,  // the app the title opened in (ties in "where to watch", PER-09)
)

object ItemKeys {
    /** "provider:id"; ids that already start with the provider are not doubled. */
    fun of(providerId: String, id: String): String = if (id.startsWith("$providerId:")) id else "$providerId:$id"

    fun of(item: MediaItem): String = of(item.providerId, item.id)
}

object Signals {
    const val MAX = 2000
    const val MAX_AGE_MS = 180L * 86_400_000L
    private const val MINUTE_MS = 60_000L
    const val SKIP_UNDER_MS = 2 * MINUTE_MS
    const val FINISH_DEFAULT = 0.90f
    const val FINISH_CINEMANA = 0.95f
    private const val DEDUPE_FINISH_MS = 12L * 3_600_000L

    /** Only grown-up profiles teach the TV: Guest and Kids record nothing. SPEC: PER (Guest/Kids) */
    fun mayRecord(profile: Profile?, recordingOn: Boolean): Boolean =
        recordingOn && profile != null && profile.kind == ProfileKind.ADULT

    fun finishThreshold(providerId: String): Float = if (providerId == "cinemana") FINISH_CINEMANA else FINISH_DEFAULT

    /** FINISH when [progress] reaches the provider's threshold; null otherwise. SPEC: PER-02 */
    fun kindForProgress(providerId: String, progress: Float?): SignalKind? =
        if (progress != null && progress >= finishThreshold(providerId)) SignalKind.FINISH else null

    /** SKIP: left within two minutes of opening. SPEC: PER-02 */
    fun isSkip(openedAt: Long, leftAt: Long): Boolean = leftAt - openedAt in 0 until SKIP_UNDER_MS

    fun make(profileId: String, item: MediaItem, kind: SignalKind, at: Long, hour: Int) = Signal(
        profileId, ItemKeys.of(item), item.providerId, kind, at, hour.coerceIn(0, 23), item.genres.take(3), item.title.take(80),
        appPackage = item.appPackage ?: (item.open as? OpenTarget.Lookup)?.appPackage,
    )

    /**
     * [list] plus [signal] (oldest first), minus those older than 180 days before [now] and beyond the newest 2000.
     * Signals dated in the future (the clock moved) are kept as they are. SPEC: PER-01, PER-16
     */
    fun add(list: List<Signal>, signal: Signal, now: Long): List<Signal> {
        if (signal.kind == SignalKind.FINISH && list.any {
                it.kind == SignalKind.FINISH && it.itemKey == signal.itemKey && signal.at - it.at in 0 until DEDUPE_FINISH_MS
            }
        ) return list
        return prune(list + signal, now)
    }

    fun prune(list: List<Signal>, now: Long): List<Signal> =
        list.filter { now - it.at <= MAX_AGE_MS }.takeLast(MAX)
}
