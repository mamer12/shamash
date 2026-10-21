package io.shamash.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Orders one provider's results: the exact title first, then titles starting with the query, then the provider's own
 * order. Case- and accent-insensitive; movies and series together. SPEC: SRCH-03
 */
object SearchRanking {
    private val marks = Regex("\\p{Mn}+")
    private val spaces = Regex("\\s+")

    fun rank(items: List<MediaItem>, query: String): List<MediaItem> {
        val q = normalize(query)
        if (q.isEmpty()) return items
        return items.withIndex()
            .sortedWith(compareBy({ tier(normalize(it.value.title), q) }, { it.index }))
            .map { it.value }
    }

    /** Lower case, without accents or Arabic diacritics, spaces collapsed: "Amélie " and "amelie" are the same. */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(marks, "")
            .replace('\u0671', '\u0627')      // alef wasla -> alef (hamza forms are already split off by NFD)
            .replace('\u0649', '\u064A')      // alef maqsura -> ya
            .replace("\u0640", "")             // tatweel
            .lowercase(Locale.ROOT)
            .replace(spaces, " ")
            .trim()

    private fun tier(title: String, query: String): Int = when {
        title == query -> EXACT
        title.startsWith(query) -> STARTS_WITH
        else -> OTHER
    }

    private const val EXACT = 0
    private const val STARTS_WITH = 1
    private const val OTHER = 2
}
