package io.shamash.domain

/*
 * The one place a title is checked against a content level (defense in depth after each provider's own filter).
 * Public surface: ContentGate (allows, filter for items and shelves; `ContentGate` itself is the default gate).
 * Rules: ALL shows everything; FAMILY and KIDS show only items whose [MediaItem.rating] is on the level's list, so an
 * unknown rating is hidden; matches carry no rating and show for FAMILY but not KIDS. SPEC: PRF-12, CIN-08
 */
interface ContentGate {
    fun allows(item: MediaItem, level: ContentLevel): Boolean

    fun filter(items: List<MediaItem>, level: ContentLevel): List<MediaItem> =
        if (level == ContentLevel.ALL) items else items.filter { allows(it, level) }

    fun filter(shelf: Shelf, level: ContentLevel): Shelf =
        if (level == ContentLevel.ALL) shelf else shelf.copy(items = filter(shelf.items, level))

    fun filter(items: List<MediaItem>, profile: Profile): List<MediaItem> = filter(items, profile.contentLevel)

    /** The standard gate by certification. */
    companion object : ContentGate {
        private val FAMILY = setOf("G", "PG", "PG-13", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14")
        private val KIDS = setOf("G", "PG", "TV-Y", "TV-Y7", "TV-G")

        /** True when a title with this certification (null or blank = unknown) may show at [level]. */
        fun allowsRating(rating: String?, level: ContentLevel): Boolean {
            val known = rating?.trim()?.uppercase().orEmpty()
            return when (level) {
                ContentLevel.ALL -> true
                ContentLevel.FAMILY -> known in FAMILY
                ContentLevel.KIDS -> known in KIDS
            }
        }

        override fun allows(item: MediaItem, level: ContentLevel): Boolean =
            if (item.kind == Kind.MATCH) level != ContentLevel.KIDS else allowsRating(item.rating, level)
    }
}
