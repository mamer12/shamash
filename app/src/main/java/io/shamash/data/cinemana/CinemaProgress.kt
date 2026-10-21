package io.shamash.data.cinemana

import kotlinx.serialization.Serializable

@Serializable
enum class CinemaRating(val genreWeight: Double, val recommendationWeight: Double) {
    LOVE(3.0, 4.0), LIKE(2.0, 2.5), DISLIKE(-3.0, 0.0),
}

@Serializable
data class RatedTitle(val title: CinemanaTitle, val rating: CinemaRating)

@Serializable
data class CinemaProgress(
    val title: CinemanaTitle,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float get() = if (durationMs <= 0) 0f else (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat()
    val started: Boolean get() = positionMs >= 3_000 && durationMs > 0
    val finished: Boolean get() = durationMs > 0 && positionMs.toDouble() / durationMs >= .95
    val resumeMs: Long get() = if (started && !finished) positionMs else 0
}

/** Pure progress decisions, shared by title pages, the player and Home. SPEC: CIN-04, CIN-07 */
object CinemaProgressRules {
    fun continuing(history: List<CinemaProgress>): List<CinemaProgress> = history
        .filter { it.started }.sortedByDescending { it.updatedAt }.distinctBy { it.title.pageId }
        .filterNot { it.finished }

    fun episode(episodes: List<CinemanaTitle>, history: List<CinemaProgress>): CinemanaTitle? {
        val ids = episodes.map { it.id }.toSet()
        val last = history.filter { it.started && it.title.id in ids }.maxByOrNull { it.updatedAt }
            ?: return episodes.firstOrNull()
        val index = episodes.indexOfFirst { it.id == last.title.id }
        return episodes.getOrNull(if (last.finished) index + 1 else index) ?: episodes.firstOrNull()
    }
}
