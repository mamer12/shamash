package io.shamash.feature.home

import io.shamash.domain.ContentLevel
import io.shamash.domain.Kind
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeGateTest {
    private fun item(id: String, rating: String?, kind: Kind = Kind.MOVIE) =
        MediaItem(id, "cinemana", id, kind, OpenTarget.App("x"), rating = rating, backdropUrl = "https://x/$id.jpg")

    private val match = Match("1", "esp.1", "LaLiga", Team("A", "A", ""), Team("B", "B", ""), 1_700_000_000_000L, MatchState.PRE)
    private val shelf = Shelf("cinemana_new", "cinemana_new", ShelfStyle.POSTER, listOf(item("ok", "PG"), item("adult", "R"), item("unknown", null)))
    private val loaded = Loaded(
        providers = mapOf("cinemana" to ProviderContent(listOf(shelf), shelf.items)),
        order = listOf("cinemana"), matches = listOf(match), started = true,
    )

    private fun build(level: ContentLevel, history: List<MediaItem> = emptyList()) = HomeRows.build(
        HomeInput(Profile(onboarded = true, contentLevel = level), loaded, history, 1_700_000_000_000L, 20),
    )

    private fun ids(ui: HomeUi) = ui.home.flatMap { s -> s.items.map { it.id } }

    @Test fun PRF_12_all_keeps_everything() {
        assertTrue(ids(build(ContentLevel.ALL)).containsAll(listOf("ok", "adult", "unknown")))
    }

    @Test fun PRF_12_home_rows_billboard_and_history_pass_the_gate() {
        val ui = build(ContentLevel.KIDS, history = listOf(item("watched-r", "R"), item("watched-g", "G")))
        val shown = ids(ui)
        assertTrue("ok" in shown && "watched-g" in shown)
        assertTrue(shown.none { it in setOf("adult", "unknown", "watched-r") })
        assertEquals(listOf("ok"), ui.featured.map { it.item.id })
        assertTrue(ui.home.none { it.id == "matches" })
    }

    @Test fun PRF_12_family_keeps_matches_and_drops_unrated_titles() {
        val ui = build(ContentLevel.FAMILY)
        assertTrue(ui.home.any { it.id == "matches" })
        assertTrue("unknown" !in ids(ui) && "adult" !in ids(ui))
    }
}
