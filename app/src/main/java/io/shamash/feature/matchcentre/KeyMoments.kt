package io.shamash.feature.matchcentre

import io.shamash.domain.Sport

/**
 * Did something worth a GOAL card happen between two polls? Soccer: more goals. Basketball: the lead changed hands.
 * Cricket: a wicket fell. Tennis: a set was won or the set lead changed. F1: a new leader (the score is the leader's
 * name); the finish is the final card. Never on the first look or when a score goes down. SPEC: MC-13
 */
object KeyMoments {
    fun happened(sport: Sport, before: Pair<String, String>?, now: Pair<String, String>): Boolean {
        if (before == null || before == now) return false
        return when (sport) {
            Sport.SOCCER -> soccer(before, now)
            Sport.BASKETBALL -> leadChanged(before, now) { it.toIntOrNull() }
            Sport.CRICKET -> wickets(now.first) > wickets(before.first) || wickets(now.second) > wickets(before.second)
            Sport.TENNIS -> tennis(before, now)
            Sport.F1 -> now.second.isNotBlank() && before.second.isNotBlank() && now.second != before.second
        }
    }

    private fun soccer(before: Pair<String, String>, now: Pair<String, String>): Boolean {
        val was = goals(before) ?: return true
        val goals = goals(now) ?: return true
        return goals > was
    }

    private fun goals(score: Pair<String, String>): Int? {
        val home = score.first.toIntOrNull() ?: return null
        val away = score.second.toIntOrNull() ?: return null
        return home + away
    }

    private fun leadChanged(before: Pair<String, String>, now: Pair<String, String>, n: (String) -> Int?): Boolean {
        val b = lead(before, n) ?: return false
        val a = lead(now, n) ?: return false
        return b != 0 && a != 0 && b != a
    }

    private fun lead(s: Pair<String, String>, n: (String) -> Int?): Int? {
        val h = n(s.first) ?: return null
        val a = n(s.second) ?: return null
        return (h - a).coerceIn(-1, 1)
    }

    /** "186/4 (18.2)" -> 4 */
    private fun wickets(innings: String): Int = Regex("/(\\d+)").find(innings)?.groupValues?.get(1)?.toInt() ?: 0

    /** Scores are the games of each set ("6 3 7"): a new entry is a won set; the set lead may flip with it. */
    private fun tennis(before: Pair<String, String>, now: Pair<String, String>): Boolean {
        val (bh, ba) = sets(before.first) to sets(before.second)
        val (nh, na) = sets(now.first) to sets(now.second)
        if (nh.size > bh.size && bh.isNotEmpty()) return true
        return setLead(nh, na) != setLead(bh, ba) && setLead(bh, ba) != 0
    }

    private fun sets(games: String) = games.split(' ').mapNotNull { it.toIntOrNull() }

    /** Sets won so far by each, from the finished sets (all but the last pair); sign of the difference. */
    private fun setLead(h: List<Int>, a: List<Int>): Int {
        val done = minOf(h.size, a.size) - 1
        if (done <= 0) return 0
        val diff = (0 until done).sumOf { Integer.signum(h[it] - a[it]) }
        return diff.coerceIn(-1, 1)
    }
}
