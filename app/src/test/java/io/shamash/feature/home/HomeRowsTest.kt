package io.shamash.feature.home

import io.shamash.domain.Kind
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.MediaItem
import io.shamash.domain.Moment
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.Team
import io.shamash.domain.TvApp
import io.shamash.domain.Weather
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRowsTest {
    private val now = 1_700_000_000_000L
    private val netflix = "com.netflix.ninja"
    private val prime = "com.amazon.amazonvideo.livingroom"
    private val profile = Profile(onboarded = true, name = "Sara", subscriptions = setOf(netflix))

    private fun item(title: String, pkg: String = netflix, kind: Kind = Kind.MOVIE, progress: Float? = null) = MediaItem(
        id = title, providerId = "tmdb", title = title, kind = kind, appPackage = pkg,
        open = if (kind == Kind.VIDEO) OpenTarget.Video(title) else OpenTarget.App(pkg), progress = progress,
    )

    private fun shelf(key: String, vararg items: MediaItem, arg: String? = null, style: ShelfStyle = ShelfStyle.WIDE) =
        Shelf("id_${key}_${arg.orEmpty()}", key, style, items.toList(), arg)

    private fun rows(shelves: List<Shelf>, history: List<MediaItem> = emptyList(), matches: List<Match> = emptyList()) =
        HomeRows.homeRows(profile, shelves, matches, history, Moment.EVENING, now)

    @Test fun HOME_04_rows_follow_the_spec_order_and_skip_empty_ones() {
        val shelves = listOf(
            shelf("popular_on", item("N1"), arg = "Netflix"),
            shelf("cinemana_new", item("C1")),
            shelf("tod_upcoming", item("T1")),
            shelf("youtube_recommended", item("Y1", kind = Kind.VIDEO)),
            shelf("top10", item("R1"), style = ShelfStyle.RANKED),
            shelf("tod_new"),
        )
        val keys = rows(shelves).map { it.titleKey }
        assertEquals(listOf("moment_evening", "youtube_recommended", "tod_upcoming", "cinemana_new", "popular_on"), keys)
    }

    @Test fun HOME_04_popular_only_for_subscribed_services() {
        val shelves = listOf(shelf("popular_on", item("P1", pkg = prime), arg = "Prime Video"))
        assertFalse(rows(shelves).any { it.titleKey == "popular_on" })
    }

    @Test fun HOME_05_discover_has_top10_then_every_popular_row() {
        val shelves = listOf(
            shelf("popular_on", item("P1", pkg = prime), arg = "Prime Video"),
            shelf("top10", item("R1"), style = ShelfStyle.RANKED),
            shelf("youtube_recommended", item("Y1", kind = Kind.VIDEO)),
        )
        assertEquals(listOf("top10", "popular_on"), HomeRows.discoverRows(shelves).map { it.titleKey })
    }

    @Test fun HOME_11_continue_watching_splits_youtube_and_keeps_each_title_once() {
        val history = listOf(item("Clip", kind = Kind.VIDEO), item("Dune"), item("Show"))
        val own = shelf("continue_watching", item("Dune", progress = 0.5f))
        val home = rows(listOf(own), history)
        val watching = home.first { it.titleKey == "continue_watching" }.items
        assertEquals(listOf("Dune", "Show"), watching.map { it.title })
        assertEquals(0.5f, watching.first().progress)
        assertEquals(listOf("Clip"), home.first { it.titleKey == "continue_youtube" }.items.map { it.title })
    }

    @Test fun HOME_08_moment_row_holds_my_match_then_unfinished_title() {
        val match = Match(
            "m", "esp.1", "LaLiga", Team("Barcelona", "BAR", ""), Team("Madrid", "RMA", ""),
            startsAt = now + 3_600_000L, state = MatchState.PRE, mine = true,
        )
        val moment = rows(emptyList(), listOf(item("Show")), listOf(match)).first { it.id == "moment" }
        assertEquals(listOf("match:m", "Show"), moment.items.map { it.id })
    }

    @Test fun HOME_03_apps_row_puts_subscribed_apps_first() {
        val installed = listOf(
            TvApp(prime, "Prime Video", streaming = true),
            TvApp("com.example.game", "Game", streaming = false),
            TvApp(netflix, "Netflix", streaming = true),
        )
        val row = HomeRows.appsRow(profile, installed, recent = listOf(installed[1]))
        assertEquals(listOf(netflix, prime, "com.example.game"), row.map { it.packageName })
    }

    @Test fun PRV_12_fahrenheit_only_in_the_us() {
        val w = Weather(20.0, 0, isDay = true, sunrise = 0, sunset = 0)
        assertEquals(68, HomeRows.weatherNow(w, profile.copy(region = "US")).degrees)
        assertEquals(20, HomeRows.weatherNow(w, profile.copy(region = "IQ")).degrees)
    }

    @Test fun PERF_05_one_state_from_everything_loaded() {
        val loaded = Loaded(
            providers = mapOf("tmdb" to ProviderContent(listOf(shelf("top10", item("R1"))), emptyList())),
            order = listOf("tmdb"), started = true,
        )
        val ui = HomeRows.build(HomeInput(profile, loaded, emptyList(), now, hour = 9))
        assertTrue(ui.ready)
        assertEquals(Moment.MORNING, ui.moment)
        assertEquals("Sara", ui.greeting.name)
        assertEquals(listOf("top10"), ui.discover.map { it.titleKey })
    }
}
