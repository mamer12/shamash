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

class HomeRowsPersonalTest {
    private val now = 1_700_000_000_000L
    private val profile = Profile(onboarded = true, name = "Sara")

    private fun item(id: String, provider: String = "tmdb", title: String = id) = MediaItem(
        id = id, providerId = provider, title = title, kind = Kind.MOVIE, open = OpenTarget.App("pkg"), appPackage = "pkg",
    )

    private fun shelf(id: String, key: String, vararg items: MediaItem, arg: String? = null) =
        Shelf(id, key, ShelfStyle.WIDE, items.toList(), arg)

    private fun personal(vararg signals: io.shamash.domain.Signal, mutes: List<io.shamash.domain.Mute> = emptyList()) =
        io.shamash.domain.PersonalState("p", io.shamash.domain.PersonalData(signals = signals.toList(), mutes = mutes))

    private fun finish(i: Int, provider: String) =
        io.shamash.domain.Signal("p", "$provider:f$i", provider, io.shamash.domain.SignalKind.FINISH, now, 9)

    @Test fun PER_08_muted_title_is_gone_from_every_row_and_the_billboard() {
        val hidden = item("hidden").copy(backdropUrl = "http://x/w.jpg")
        val loaded = Loaded(
            providers = mapOf("tmdb" to ProviderContent(listOf(shelf("s", "top10", hidden, item("ok"))), listOf(hidden))),
            order = listOf("tmdb"), started = true,
        )
        val mute = io.shamash.domain.Mutes.of(hidden, now)
        val ui = HomeRows.build(HomeInput(profile, loaded, listOf(hidden), now, 9, personal(mutes = listOf(mute))))
        assertTrue(ui.featured.isEmpty())
        assertTrue((ui.home + ui.discover).flatMap { it.items }.none { it.id == "hidden" })
    }

    @Test fun PER_05_learned_order_moves_unordered_rows_but_not_a_pinned_one() {
        val shelves = listOf(
            shelf("tod_new", "tod_new", item("t1", "tod")),
            shelf("cinemana_new", "cinemana_new", item("c1", "cinemana")),
        )
        val learned = personal(*Array(6) { finish(it, "cinemana") })
        val rows = HomeRows.homeRows(profile, shelves, emptyList(), emptyList(), Moment.EVENING, now, learned)
        assertEquals(listOf("cinemana_new", "tod_new"), rows.map { it.id } - "moment")
        val pinned = profile.copy(layout = io.shamash.domain.Layout(rows = listOf(io.shamash.domain.RowPref("tod_new", pinned = true))))
        val laid = HomeLayout.lay(HomeRows.homeRows(pinned, shelves, emptyList(), emptyList(), Moment.EVENING, now, learned), pinned, null)
        assertEquals("tod_new", laid.shown.first().id)
    }

    @Test fun PER_16_few_signals_keep_the_default_order() {
        val shelves = listOf(
            shelf("tod_new", "tod_new", item("t1", "tod")),
            shelf("cinemana_new", "cinemana_new", item("c1", "cinemana")),
        )
        val rows = HomeRows.homeRows(profile, shelves, emptyList(), emptyList(), Moment.EVENING, now, personal(finish(1, "cinemana")))
        assertEquals(listOf("tod_new", "cinemana_new"), rows.map { it.id } - "moment")
    }

    @Test fun PER_11_rows_with_a_known_reason_carry_it_and_lists_do_not() {
        val shelves = listOf(
            shelf("b", "cinemana_because_watched", item("c1", "cinemana"), arg = "Dune"),
            shelf("new", "tod_new", item("t1", "tod")),
        )
        val rows = HomeRows.homeRows(profile, shelves, emptyList(), emptyList(), Moment.EVENING, now)
        assertEquals(io.shamash.domain.ReasonKind.BECAUSE_WATCHED, rows.first { it.id == "b" }.reason?.kind)
        assertEquals(null, rows.first { it.id == "new" }.reason)
    }

    @Test fun PER_13_my_list_row_shows_the_one_list_and_replaces_cinemanas() {
        val saved = io.shamash.domain.MyListEntry.of(item("tmdb:x", "tmdb", "Saved"), 5)
        val state = io.shamash.domain.PersonalState("p", io.shamash.domain.PersonalData(myList = listOf(saved)))
        val shelves = listOf(shelf("cinemana_my_list", "cinemana_my_list", item("c1", "cinemana")))
        val rows = HomeRows.homeRows(profile, shelves, emptyList(), emptyList(), Moment.EVENING, now, state)
        assertEquals(listOf("mylist"), rows.map { it.id } - "moment")
        assertEquals("Saved", rows.first { it.id == "mylist" }.items.single().title)
    }
}
