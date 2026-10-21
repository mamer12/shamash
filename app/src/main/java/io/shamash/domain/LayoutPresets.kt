package io.shamash.domain

/*
 * The shipped presets as data (LAY-07). Row ids may end in `*` (a family); ids that don't exist on this TV are skipped
 * (LAY-09). Public surface: LayoutPresets.all, byId, apply. SPEC: LAY-07, LAY-08, LAY-09
 */
object LayoutPresets {
    val CINEMA = LayoutPreset(
        id = LayoutPreset.CINEMA,
        rows = listOf(
            PresetRow("continue"), PresetRow("moment"), PresetRow("cinemana_*"), PresetRow("tmdb_top10"),
            PresetRow("tmdb_popular_*"), PresetRow("tod_new"), PresetRow("yt_recs"), PresetRow("continue:youtube"),
            PresetRow("matches"),
        ),
        carouselMix = listOf("cinemana", "youtube", "tmdb", "tod"),
    )

    val SPORTS = LayoutPreset(
        id = LayoutPreset.SPORTS,
        rows = listOf(
            PresetRow("matches"), PresetRow("moment"), PresetRow("tod_matches"), PresetRow("continue"),
            PresetRow("yt_recs"), PresetRow("continue:youtube"),
        ),
        carouselMix = listOf("tod", "youtube", "tmdb", "cinemana"),
    )

    /** Rows whose cards pass the Kids gate (PRF-12): Cinemana's kids level, made-for-kids YouTube, Kids-level feeds. */
    val KIDS = LayoutPreset(
        id = LayoutPreset.KIDS,
        rows = listOf(
            PresetRow("continue"), PresetRow("cinemana_*"), PresetRow("yt_recs"), PresetRow("myfeed:*"),
            PresetRow("matches", hidden = true),
        ),
        carouselMix = listOf("cinemana", "youtube"),
        cardSize = CardSize.L,
        hideRest = true,
    )

    val MINIMAL = LayoutPreset(
        id = LayoutPreset.MINIMAL,
        rows = listOf(PresetRow("continue"), PresetRow("moment")),
        carouselMix = listOf("tmdb"),
        hideRest = true,
        autoplay = false,
    )

    val all: List<LayoutPreset> = listOf(CINEMA, SPORTS, KIDS, MINIMAL)

    /** The preset with [id]; Cinema when unknown (LAY-19). */
    fun byId(id: String): LayoutPreset = all.firstOrNull { it.id == id } ?: CINEMA

    /** The layout a preset gives (once, at choice time); [from]'s look, rules and carousel switches are kept. SPEC: LAY-08 */
    fun apply(preset: LayoutPreset, from: Layout = Layout()): Layout = from.copy(
        presetId = preset.id,
        rows = preset.rows.mapIndexed { i, r -> RowPref(r.rowId, hidden = r.hidden, order = i) },
        cardSize = preset.cardSize,
        hideRest = preset.hideRest,
        rules = preset.rules.ifEmpty { from.rules },
        mix = preset.carouselMix,
        autoplay = preset.autoplay,
    )
}
