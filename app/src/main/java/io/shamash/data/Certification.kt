package io.shamash.data

/*
 * Turns a certification as a provider states it ("12A", "TV-MA", "PG-13") into the vocabulary ContentGate reads
 * (US movie and TV ratings). Unknown text comes back as null: an unknown rating stays hidden for restricted
 * profiles. Pure. SPEC: PRF-12
 */
object Certification {
    private val US = setOf("G", "PG", "PG-13", "R", "NC-17", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14", "TV-MA")

    /** Other countries' labels with a clear US counterpart (age bands round up, never down). */
    private val OTHER = mapOf(
        "U" to "G", "0" to "G", "L" to "G", "AL" to "G", "ALL" to "G", "TP" to "G", "KIJKWIJZER AL" to "G",
        "6" to "PG", "7" to "PG", "9" to "PG", "10" to "PG", "A" to "PG",
        "12" to "PG-13", "12A" to "PG-13", "12+" to "PG-13", "13" to "PG-13", "14" to "PG-13",
        "15" to "R", "16" to "R", "18" to "R", "18+" to "R", "R18" to "R", "X" to "NC-17",
    )

    fun normalize(raw: String?): String? {
        val text = raw?.trim()?.uppercase().orEmpty()
        if (text.isEmpty()) return null
        return when {
            text in US -> text
            else -> OTHER[text]
        }
    }
}
