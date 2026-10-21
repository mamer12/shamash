package io.shamash.domain

import java.util.Locale

/*
 * The genre profile, pure (CIN-11 generalized): picked genres +2, each My List title +1, opened +1, finished +2,
 * liked +2, disliked -3, "less like this" -2 on its genres. Public surface: TasteProfile. SPEC: PER-06
 */
object TasteProfile {
    const val SELECTED = 2
    const val MY_LIST = 1
    const val OPENED = 1
    const val FINISHED = 2
    const val LOVE = 3
    const val LIKE = 2
    const val DISLIKE = -3
    const val LESS_LIKE_THIS = -2

    fun weight(kind: SignalKind): Int = when (kind) {
        SignalKind.OPEN -> OPENED
        SignalKind.FINISH -> FINISHED
        SignalKind.LIKE -> LIKE
        SignalKind.DISLIKE -> DISLIKE
        SignalKind.LESS_LIKE_THIS -> LESS_LIKE_THIS
        SignalKind.SKIP, SignalKind.MUTE -> 0
    }

    fun genres(selected: Set<String>, myList: List<List<String>>, signals: List<Signal>, loved: List<List<String>> = emptyList()): Map<String, Int> {
        val out = HashMap<String, Int>()
        fun add(genres: Iterable<String>, w: Int) = genres.forEach { g ->
            val k = g.trim().lowercase(Locale.ROOT)
            if (k.isNotEmpty() && w != 0) out[k] = (out[k] ?: 0) + w
        }
        add(selected, SELECTED)
        myList.forEach { add(it, MY_LIST) }
        loved.forEach { add(it, LOVE) }
        signals.forEach { add(it.genres, weight(it.kind)) }
        return out
    }

    /** The [n] best-liked genres (positive weight only). */
    fun top(weights: Map<String, Int>, n: Int = 3): List<String> =
        weights.entries.filter { it.value > 0 }.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(n).map { it.key }
}
