package io.shamash.addons.example

import io.shamash.data.jArr
import io.shamash.data.jStr
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * The template's catalog: `{"new": [item...], "popular": [item...]}`, one list per row source. Pure and lenient: an
 * item without an id, a title or an http(s) link is dropped, never an exception; images are kept only on https; text
 * is plain and capped. SPEC: PLT-05, PEX-07, ARCH-05
 */
class ExampleCatalog(private val rows: Map<String, List<MediaItem>>) {
    /** The cards of one row source (empty when the catalog has none). */
    fun row(sourceId: String): List<MediaItem> = rows[sourceId].orEmpty()

    /** Every card, counted once (the connection test reports this). */
    val size: Int get() = rows.values.flatten().distinctBy { it.id }.size

    companion object {
        const val MAX_PER_ROW = 20
        private const val MAX_TITLE = 200
        private const val MAX_TEXT = 500

        fun parse(root: JsonElement, sourceIds: List<String>): ExampleCatalog =
            ExampleCatalog(sourceIds.associateWith { id -> root.jArr(id).mapNotNull(::item).take(MAX_PER_ROW) })

        /** One card, or null when the entry can't be shown or opened. */
        fun item(e: JsonElement): MediaItem? {
            val id = e.jStr("id")?.trim()?.take(MAX_TITLE) ?: return null
            val title = e.jStr("title")?.trim()?.take(MAX_TITLE) ?: return null
            val link = e.jStr("link")?.toHttpUrlOrNull()?.toString() ?: return null
            return MediaItem(
                id = "${ExampleAddOn.ID}:$id", providerId = ExampleAddOn.ID, title = title, kind = kind(e.jStr("kind")),
                open = OpenTarget.AppPage(link, e.jStr("app").orEmpty()),
                subtitle = e.jStr("subtitle")?.take(MAX_TITLE).orEmpty(),
                overview = e.jStr("overview")?.take(MAX_TEXT).orEmpty(),
                posterUrl = image(e.jStr("poster")), backdropUrl = image(e.jStr("backdrop")),
                rating = e.jStr("rating")?.trim(),            // the ContentGate decides; unknown hides below ALL
            )
        }

        private fun image(url: String?): String? = url?.toHttpUrlOrNull()?.takeIf { it.isHttps }?.toString()

        private fun kind(raw: String?): Kind = when (raw?.lowercase()) {
            "movie" -> Kind.MOVIE
            "series" -> Kind.SERIES
            "live" -> Kind.LIVE
            else -> Kind.VIDEO
        }
    }
}
