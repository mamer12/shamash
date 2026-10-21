package io.shamash.feature.matchcentre

import io.shamash.domain.Carousel
import io.shamash.domain.Match
import io.shamash.domain.MatchState
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.Team
import io.shamash.domain.TvApp
import io.shamash.feature.home.HomeInput
import io.shamash.feature.home.HomeRows
import io.shamash.feature.home.Loaded
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhereToWatchTest {
    private val now = 1_700_000_000_000L
    private val tod = "com.todtv.tod"
    private val shahid = "net.mbc.shahidTV"
    private val map = BroadcastMap.parse(
        """{"default":{"*":["$tod"]},"competitions":{"ksa.1":{"SA":["$shahid","$tod"]}}}""",
    )
    private val match = Match(
        "m1", "ksa.1", "Saudi Pro League", Team("Al Hilal", "HIL", ""), Team("Al Nassr", "NAS", ""),
        now + 3_600_000L, MatchState.PRE, mine = true,
    )
    private val sa = Profile(onboarded = true, region = "SA", teams = setOf("Al Hilal"))
    private fun apps(vararg pkgs: String) = pkgs.map { TvApp(it, it.substringAfterLast('.'), streaming = true) }

    @Test fun MC_10_subscribed_installed_app_wins() {
        val where = WhereToWatch.of(map, sa.copy(subscriptions = setOf(tod)), apps(shahid, tod))
        assertEquals(tod, where.app(match))
        assertEquals(OpenTarget.App(tod), where.item(match).open)
    }

    @Test fun MC_10_else_the_first_installed_listed_app() {
        assertEquals(shahid, WhereToWatch.of(map, sa, apps(tod, shahid)).app(match))
    }

    @Test fun MC_10_no_listed_app_installed_gives_a_no_app_card_that_searches() {
        val item = WhereToWatch.of(map, sa, apps("com.other")).item(match)
        assertTrue(Carousel.noApp(item))
        assertNull(item.appPackage)
        val route = (item.open as OpenTarget.Internal).route
        assertTrue(route.startsWith("search?q=") && route.contains("Hilal"))
    }

    @Test fun MC_10_before_apps_load_the_maps_first_choice_stands_in() {
        assertEquals(shahid, WhereToWatch.of(map, sa, emptyList()).app(match))
    }

    @Test fun MC_10_default_match_item_still_opens_tod() {
        assertFalse(Carousel.noApp(Carousel.matchItem(match)))
    }

    @Test fun MC_10_overlay_names_the_app_by_its_tv_label() {
        assertEquals("Shahid", WhereToWatch.label(shahid, listOf(TvApp(shahid, "Shahid", true))))
        assertEquals(tod, WhereToWatch.label(tod, emptyList()))
        assertNull(WhereToWatch.label(null, apps(tod)))
    }

    @Test fun MC_10_home_billboard_and_matches_row_open_the_chosen_app() {
        val loaded = Loaded(matches = listOf(match), installed = apps(shahid), broadcast = map, started = true)
        val ui = HomeRows.build(HomeInput(sa, loaded, emptyList(), now, hour = 20))
        assertEquals(OpenTarget.App(shahid), ui.featured.first().item.open)
        val row = ui.home.first { it.id == "matches" }
        assertEquals(shahid, row.items.single().appPackage)
    }
}
