package io.shamash.domain

import kotlinx.serialization.Serializable

/*
 * Layout data (stored per profile, shipped as presets). Pure data: ordering and rules are Wave 1 (domain/Layout.kt).
 * Public surface: RowPref, Layout, CardSize, PresetRow, LayoutPreset. SPEC: LAY-01, LAY-07
 */

/** One Home row's saved choices; unknown [rowId]s are kept but ignored when drawing. SPEC: LAY-01 */
@Serializable
data class RowPref(
    val rowId: String,                 // "<providerId>:<shelfId>", "matches", "moment", "continue"
    val hidden: Boolean = false,
    val pinned: Boolean = false,
    val title: String? = null,         // user text, shown as-is (LAY-06)
    val order: Int = 0,
)

@Serializable
enum class CardSize { S, M, L }

/** What a profile saved about its Home: the preset it started from and its row prefs. SPEC: LAY-03, LAY-08 */
@Serializable
data class Layout(
    val presetId: String = LayoutPreset.CINEMA,
    val rows: List<RowPref> = emptyList(),   // empty = the default HOME-04 order
    val cardSize: CardSize = CardSize.M,
    val hideRest: Boolean = false,           // rows with no pref are hidden (Kids, Minimal)
    val rules: List<MomentRule> = emptyList(),   // SPEC: LAY-10
    val mix: List<String> = emptyList(),     // carousel source order; empty = HOME-10 default (LAY-12)
    val mixOff: Set<String> = emptySet(),    // carousel sources switched off
    val look: Look = Look(),
    val autoplay: Boolean = true,            // the billboard moves on by itself; Minimal turns it off (LAY-07)
)

@Serializable
data class PresetRow(val rowId: String, val hidden: Boolean = false)

/** A look for Home as data, not code branches. SPEC: LAY-07 */
@Serializable
data class LayoutPreset(
    val id: String,
    val rows: List<PresetRow> = emptyList(),
    val carouselMix: List<String> = emptyList(),
    val cardSize: CardSize = CardSize.M,
    val tabs: List<String> = emptyList(),
    val hideRest: Boolean = false,
    val rules: List<MomentRule> = emptyList(),
    val autoplay: Boolean = true,
) {
    companion object {
        const val CINEMA = "cinema"
        const val SPORTS = "sports"
        const val KIDS = "kids"
        const val MINIMAL = "minimal"
    }
}
