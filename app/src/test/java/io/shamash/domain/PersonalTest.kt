package io.shamash.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalTest {
    private val day = 86_400_000L
    private val now = 1_700_000_000_000L

    private fun item(id: String, title: String = id, provider: String = "tmdb", genres: List<String> = emptyList(), sub: String = "", kind: Kind = Kind.MOVIE) =
        MediaItem(id, provider, title, kind, OpenTarget.App("com.netflix.ninja"), subtitle = sub, genres = genres, appPackage = "com.netflix.ninja")

    private fun sig(kind: SignalKind, at: Long = now, key: String = "tmdb:a", provider: String = "tmdb", hour: Int = 9, genres: List<String> = emptyList(), title: String = "T") =
        Signal("p", key, provider, kind, at, hour, genres, title)

    @Test fun PER_01_signals_are_capped_at_2000_and_180_days() {
        var list = emptyList<Signal>()
        repeat(2105) { i -> list = Signals.add(list, sig(SignalKind.OPEN, now + i, key = "tmdb:$i"), now + i) }
        assertEquals(2000, list.size)
        assertEquals("tmdb:2104", list.last().itemKey)
        val old = sig(SignalKind.OPEN, now - 181 * day)
        assertTrue(Signals.add(listOf(old), sig(SignalKind.OPEN), now).none { it === old })
    }

    @Test fun PER_01_signal_keeps_hour_genres_and_title() {
        val s = Signals.make("p", item("m1", "Movie", genres = listOf("Drama", "War", "Epic", "Extra")), SignalKind.OPEN, now, 21)
        assertEquals(21, s.hourOfDay)
        assertEquals(3, s.genres.size)
        assertEquals("tmdb:m1", s.itemKey)
        assertEquals("Movie", s.title)
    }

    @Test fun PER_02_finish_thresholds_and_skip() {
        assertEquals(SignalKind.FINISH, Signals.kindForProgress("tmdb", 0.9f))
        assertNull(Signals.kindForProgress("tmdb", 0.89f))
        assertNull(Signals.kindForProgress("cinemana", 0.94f))
        assertEquals(SignalKind.FINISH, Signals.kindForProgress("cinemana", 0.95f))
        assertNull(Signals.kindForProgress("tmdb", null))
        assertTrue(Signals.isSkip(now, now + 119_000))
        assertFalse(Signals.isSkip(now, now + 120_000))
    }

    @Test fun PER_02_finish_is_not_recorded_twice_in_a_row() {
        val once = Signals.add(emptyList(), sig(SignalKind.FINISH), now)
        assertEquals(1, Signals.add(once, sig(SignalKind.FINISH, now + 5_000), now + 5_000).size)
    }

    @Test fun PER_01_only_adults_record() {
        assertTrue(Signals.mayRecord(Profile(kind = ProfileKind.ADULT), true))
        assertFalse(Signals.mayRecord(Profile(kind = ProfileKind.KIDS), true))
        assertFalse(Signals.mayRecord(Profile(kind = ProfileKind.GUEST), true))
        assertFalse(Signals.mayRecord(Profile(kind = ProfileKind.ADULT), false))
    }

    @Test fun PER_03_moment_picks_genres_finished_at_the_same_time() {
        val signals = listOf(
            sig(SignalKind.FINISH, hour = 9, genres = listOf("comedy")),
            sig(SignalKind.FINISH, hour = 21, genres = listOf("horror"), key = "tmdb:b"),
        )
        assertEquals(listOf("comedy"), Weights.momentGenres(signals, Moment.MORNING))
        assertEquals(listOf("horror"), Weights.momentGenres(signals, Moment.EVENING))
        assertEquals(setOf("comedy", "horror"), Weights.momentGenres(signals, Moment.AFTERNOON).toSet())   // falls back to all
    }

    @Test fun PER_05_weight_function() {
        assertEquals(1.0, Weights.weight(0, 0), 1e-9)
        assertEquals(1.3, Weights.weight(2, 0), 1e-9)
        assertEquals(0.8, Weights.weight(0, 2), 1e-9)
        assertEquals(2.0, Weights.weight(100, 0), 1e-9)
        assertEquals(0.5, Weights.weight(0, 100), 1e-9)
    }

    @Test fun PER_05_weights_use_the_last_30_days_only() {
        val old = List(10) { sig(SignalKind.FINISH, now - 40 * day, key = "tmdb:$it") }
        assertEquals(1.0, Weights.providerWeight(old, "tmdb", now), 1e-9)
    }

    @Test fun PER_05_learned_order_moves_only_the_rows_it_is_given() {
        val a = Shelf("a", "a", ShelfStyle.WIDE, listOf(item("a1", provider = "tod")))
        val b = Shelf("b", "b", ShelfStyle.WIDE, listOf(item("b1", provider = "cinemana")))
        val signals = List(6) { sig(SignalKind.FINISH, key = "cinemana:b$it", provider = "cinemana") }
        assertEquals(listOf("b", "a"), Weights.orderShelves(listOf(a, b), signals, now).map { it.id })
        assertEquals(listOf("a", "b"), Weights.orderShelves(listOf(a, b), signals, now, enabled = false).map { it.id })
        assertEquals(listOf("cinemana", "tmdb"), Weights.orderProviders(listOf("tmdb", "cinemana"), signals, now))
    }

    @Test fun PER_16_fewer_than_five_signals_change_nothing() {
        val a = Shelf("a", "a", ShelfStyle.WIDE, listOf(item("a1")))
        val b = Shelf("b", "b", ShelfStyle.WIDE, listOf(item("b1", provider = "cinemana")))
        val few = List(4) { sig(SignalKind.FINISH, key = "cinemana:$it", provider = "cinemana") }
        assertEquals(listOf("a", "b"), Weights.orderShelves(listOf(a, b), few, now).map { it.id })
        assertEquals(listOf("tmdb", "cinemana"), Weights.orderProviders(listOf("tmdb", "cinemana"), few, now))
    }

    @Test fun PER_16_old_signals_are_not_rewritten_when_the_clock_goes_back() {
        val future = sig(SignalKind.OPEN, now + 10 * day)
        assertEquals(listOf(future), Signals.prune(listOf(future), now))
    }

    @Test fun PER_06_taste_weights() {
        val signals = listOf(
            sig(SignalKind.OPEN, genres = listOf("drama")), sig(SignalKind.FINISH, genres = listOf("drama", "war")),
            sig(SignalKind.DISLIKE, genres = listOf("horror")), sig(SignalKind.LESS_LIKE_THIS, genres = listOf("war")),
            sig(SignalKind.LIKE, genres = listOf("comedy")),
        )
        val w = TasteProfile.genres(setOf("Drama"), listOf(listOf("comedy")), signals)
        assertEquals(2 + 1 + 2, w["drama"])
        assertEquals(2 - 2, w["war"])
        assertEquals(-3, w["horror"])
        assertEquals(1 + 2, w["comedy"])
        assertEquals(listOf("drama", "comedy"), TasteProfile.top(w))
    }

    @Test fun PER_08_mute_matches_by_id_or_by_title_year_kind() {
        val muted = item("m1", "The Movie!", sub = "2020 · PG")
        val index = MuteIndex(listOf(Mutes.of(muted, now)))
        assertTrue(index.isMuted(muted))
        assertTrue(index.isMuted(item("other-key", "the movie", provider = "cinemana", sub = "2020")))   // re-keyed
        assertFalse(index.isMuted(item("x", "The Movie", sub = "2019")))
        assertFalse(index.isMuted(item("y", "The Movie", sub = "2020", kind = Kind.SERIES)))
    }

    @Test fun PER_08_filter_applies_to_items_and_shelves() {
        val muted = item("m1", "Hidden")
        val index = MuteIndex(listOf(Mutes.of(muted, now)))
        val shelf = Shelf("s", "s", ShelfStyle.WIDE, listOf(muted, item("ok")))
        assertEquals(listOf("ok"), Mutes.filter(shelf, index).items.map { it.id })
        assertEquals(shelf, Mutes.filter(shelf, MuteIndex(emptyList())))
    }

    @Test fun PER_07_muting_twice_keeps_one_entry() {
        val m = item("m1")
        val list = Mutes.add(Mutes.add(emptyList(), Mutes.of(m, 1)), Mutes.of(m, 2, lessLikeThis = true))
        assertEquals(1, list.size)
        assertTrue(list.single().lessLikeThis)
    }

    private val netflix = "com.netflix.ninja"
    private val prime = "com.amazon.amazonvideo.livingroom"
    private val disney = "com.disney.disneyplus"

    @Test fun PER_09_subscribed_first_then_flatrate_free_rent_buy() {
        val offers = listOf(
            WatchOffer(prime, "Prime", OfferType.BUY), WatchOffer(netflix, "Netflix", OfferType.RENT),
            WatchOffer(disney, "Disney+", OfferType.FLATRATE),
        )
        val ranked = WhereToWatch.rank(offers, setOf(netflix, prime), { true })
        assertEquals(listOf(netflix, prime, disney), ranked.map { it.offer.appPackage })   // rent before buy, unsubscribed last
        assertEquals(listOf(true, true, false), ranked.map { it.inYourApps })
    }

    @Test fun PER_09_ties_go_to_the_heavier_provider_and_uninstalled_apps_are_skipped() {
        val offers = listOf(WatchOffer(netflix, "Netflix", OfferType.FLATRATE), WatchOffer(prime, "Prime", OfferType.FLATRATE))
        val byWeight = WhereToWatch.rank(offers, setOf(netflix, prime), { true }) { if (it == prime) 1.5 else 1.0 }
        assertEquals(prime, byWeight.first().offer.appPackage)
        val notInstalled = WhereToWatch.rank(offers, setOf(netflix, prime), { it == netflix })
        assertEquals(netflix, WhereToWatch.best(notInstalled)!!.offer.appPackage)
        assertFalse(notInstalled.last().inYourApps)
    }

    @Test fun PER_09_nothing_of_the_users_labels_the_best_other() {
        val ranked = WhereToWatch.rank(listOf(WatchOffer(disney, "Disney+", OfferType.FLATRATE)), setOf(netflix), { true })
        assertFalse(WhereToWatch.best(ranked)!!.inYourApps)
    }

    @Test fun PER_10_at_most_six_and_each_service_once() {
        val pkgs = List(9) { "app.$it" }
        val offers = pkgs.map { WatchOffer(it, it, OfferType.FLATRATE) } + WatchOffer("app.0", "app.0", OfferType.BUY)
        val ranked = WhereToWatch.rank(offers, emptySet(), { true })
        assertEquals(6, ranked.size)
        assertEquals(ranked.size, ranked.map { it.offer.appPackage }.distinct().size)
    }

    @Test fun PER_11_no_reason_means_no_line() {
        val plain = Shelf("top", "top10", ShelfStyle.RANKED, listOf(item("a")))
        assertNull(Reasons.forShelf(plain, listOf("drama")))
        assertNull(Reasons.forShelf(Shelf("x", "popular_on", ShelfStyle.WIDE, emptyList()), emptyList()))   // no service named
        assertNull(Reasons.forShelf(Shelf("y", "cinemana_picks", ShelfStyle.WIDE, emptyList()), emptyList()))   // no taste yet
    }

    @Test fun PER_11_known_rows_get_their_reason() {
        val watched = Shelf("b", "cinemana_because_watched", ShelfStyle.POSTER, emptyList(), "Dune")
        assertEquals(Reason(ReasonKind.BECAUSE_WATCHED, "Dune"), Reasons.forShelf(watched, emptyList()))
        assertEquals(Reason(ReasonKind.POPULAR_WITH_SERVICE, "Netflix"), Reasons.forShelf(Shelf("p", "popular_on", ShelfStyle.WIDE, emptyList(), "Netflix"), emptyList()))
        assertEquals(Reason(ReasonKind.BECAUSE_GENRE, "Drama"), Reasons.forShelf(Shelf("c", "cinemana_picks", ShelfStyle.WIDE, emptyList()), listOf("drama")))
        val own = Reason(ReasonKind.FROM_CHANNEL)
        assertEquals(own, Reasons.forShelf(Shelf("f", "feed", ShelfStyle.WIDE, emptyList(), reason = own), emptyList()))
    }

    @Test fun PER_11_line_is_at_most_60_characters() {
        assertTrue(Reasons.clip("x".repeat(200)).length <= 60)
        assertEquals("short", Reasons.clip("short"))
    }

    @Test fun PER_12_why_shows_at_most_three_signals_with_shared_genres() {
        val signals = List(5) { sig(SignalKind.FINISH, key = "tmdb:$it", genres = listOf("Drama"), title = "T$it") } +
            sig(SignalKind.FINISH, key = "tmdb:z", genres = listOf("Comedy"), title = "Other")
        val used = Reasons.evidence(item("new", genres = listOf("drama")), signals)
        assertEquals(3, used.size)
        assertTrue(used.none { it.title == "Other" })
        assertTrue(Reasons.evidence(item("new"), signals).isEmpty())
    }

    private fun entry(key: String, provider: String = "cinemana", title: String = key, at: Long = 1) =
        MyListEntry(key, provider, title, Kind.MOVIE, null, OpenTarget.App("x"), at)

    @Test fun PER_14_my_list_dedupes_newest_first_max_40() {
        var list = emptyList<MyListEntry>()
        repeat(50) { i -> list = MyListOps.add(list, entry("k$i", at = i.toLong())) }
        list = MyListOps.add(list, entry("other", "tmdb", title = "k3", at = 99))   // same title: one copy
        val row = MyListOps.row(list, setOf("cinemana", "tmdb"), MuteIndex(emptyList()))
        assertEquals(40, row.size)
        assertEquals("other", row.first().id)
        assertEquals(1, list.count { it.title == "k3" })
    }

    @Test fun PER_14_toggle_adds_then_removes() {
        val m = item("m1")
        val added = MyListOps.toggle(emptyList(), m, 5)
        assertTrue(MyListOps.contains(added, m))
        assertTrue(MyListOps.toggle(added, m, 6).isEmpty())
    }

    @Test fun PER_08_muted_titles_leave_my_list() {
        val list = listOf(entry("tmdb:a", "tmdb"))
        val index = MuteIndex(listOf(Mutes.of(item("a"), 1)))
        assertTrue(MyListOps.row(list, setOf("tmdb"), index).isEmpty())
    }

    @Test fun PER_15_removed_provider_entries_are_kept_but_unavailable() {
        val list = listOf(entry("cinemana:1"), entry("tmdb:2", "tmdb"))
        assertEquals(listOf("cinemana:1"), MyListOps.unavailable(list, setOf("tmdb")).map { it.itemKey })
        assertEquals(listOf("tmdb:2"), MyListOps.row(list, setOf("tmdb"), MuteIndex(emptyList())).map { it.id })
        assertEquals(2, list.size)
    }

    @Test fun PER_13_first_sync_merges_both_sides_and_loses_nothing() {
        val core = listOf(entry("cinemana:core"), entry("tmdb:t", "tmdb"))
        val mirror = listOf(entry("cinemana:m1"), entry("cinemana:m2"))
        val plan = MyListSync.reconcile("cinemana", core, mirror, emptySet(), first = true)
        assertEquals(setOf("cinemana:core", "tmdb:t", "cinemana:m1", "cinemana:m2"), plan.list.map { it.itemKey }.toSet())
        assertEquals(listOf("cinemana:core"), plan.addToMirror.map { it.itemKey })
        assertTrue(plan.removeFromMirror.isEmpty())
    }

    @Test fun PER_13_add_and_remove_flow_both_ways() {
        val seen = setOf("cinemana:a", "cinemana:b")
        // add-on removed a and added c; core removed b
        val core = listOf(entry("cinemana:a"))
        val mirror = listOf(entry("cinemana:b"), entry("cinemana:c"))
        val plan = MyListSync.reconcile("cinemana", core, mirror, seen, first = false)
        assertEquals(setOf("cinemana:c"), plan.list.map { it.itemKey }.toSet())
        assertEquals(listOf("cinemana:b"), plan.removeFromMirror)
        assertEquals(setOf("cinemana:c"), plan.seen.toSet())
    }

    @Test fun PER_13_core_add_reaches_the_add_on() {
        val plan = MyListSync.reconcile("cinemana", listOf(entry("cinemana:n")), emptyList(), setOf("cinemana:x"), first = false)
        assertEquals(listOf("cinemana:n"), plan.addToMirror.map { it.itemKey })
        assertTrue(plan.seen.contains("cinemana:n"))
    }

    @Test fun PER_13_a_second_sync_with_nothing_new_is_quiet() {
        val core = listOf(entry("cinemana:a"))
        val first = MyListSync.reconcile("cinemana", core, core, emptySet(), first = true)
        val again = MyListSync.reconcile("cinemana", first.list, core, first.seen.toSet(), first = false)
        assertEquals(first.list, again.list)
        assertTrue(again.addToMirror.isEmpty() && again.removeFromMirror.isEmpty())
    }
}
