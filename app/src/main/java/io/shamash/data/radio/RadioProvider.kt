package io.shamash.data.radio

import io.shamash.R
import io.shamash.data.Http
import io.shamash.data.Preferences
import io.shamash.data.ProfileSlot
import io.shamash.data.Say
import io.shamash.data.ServerHttp
import io.shamash.data.ServerProvider
import io.shamash.data.asSecretStore
import io.shamash.data.youtube.SecretStore
import io.shamash.domain.AddOn
import io.shamash.domain.CredentialField
import io.shamash.domain.CredentialKind
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import io.shamash.domain.Profile
import io.shamash.domain.ProviderCredentials
import io.shamash.domain.RowSource
import io.shamash.domain.Shelf
import io.shamash.domain.ShelfStyle
import io.shamash.domain.TestResult
import kotlinx.serialization.builtins.ListSerializer

/** The stations a profile marked as favorites (newest first), at most [MAX]. SPEC: PEX-24 */
class RadioFavorites(store: SecretStore, profileId: suspend () -> String) {
    private val slot = ProfileSlot(store, "radio_fav", ListSerializer(Station.serializer()), emptyList(), profileId)

    suspend fun list(): List<Station> = slot.get()

    /** Adds the station when absent, removes it when present; gives the new list. */
    suspend fun toggle(station: Station): List<Station> = slot.update { list ->
        if (list.any { it.id == station.id }) list.filterNot { it.id == station.id } else (listOf(station) + list).take(MAX)
    }

    suspend fun clear() = slot.clear()

    companion object {
        const val MAX = 50
    }
}

/**
 * Internet radio from Radio Browser: Favorites (per profile), the most played stations of a country, and a tag
 * ("news", "quran", "jazz"). A station opens in the user's radio app with ACTION_VIEW audio. SPEC: PEX-24, PEX-07
 */
class RadioProvider(
    private val credentials: ProviderCredentials,
    private val browser: RadioBrowser,
    private val favorites: RadioFavorites,
    say: Say,
    clock: () -> Long = System::currentTimeMillis,
) : ServerProvider(ID, say, clock) {
    @Volatile private var favSalt = ""

    override fun rowSources() = listOf(
        RowSource("fav", R.string.more_radio_row_fav),
        RowSource("top", R.string.more_radio_row_top),
        RowSource("tag", R.string.more_radio_row_tag, defaultOn = false),
    )

    override val salt: String get() = favSalt

    override suspend fun shelves(profile: Profile): List<Shelf> {
        if (isEnabled(profile)) favSalt = favorites.list().joinToString(",") { it.id }
        return super.shelves(profile)
    }

    suspend fun favorites(): List<Station> = favorites.list()

    suspend fun toggleFavorite(station: Station): List<Station> = favorites.toggle(station).also { favSalt = it.joinToString(",") { s -> s.id } }

    /** Stations whose name matches [name] in the chosen country (the panel's search). */
    suspend fun find(name: String, profile: Profile): List<Station> = browser.search(country(profile), null, name, limit = 12)

    private suspend fun country(profile: Profile) = credentials.get("country") ?: profile.region

    override suspend fun build(profile: Profile, wanted: List<RowSource>): List<Shelf> {
        val player = credentials.get("player").orEmpty()
        val tag = credentials.get("tag")
        return wanted.mapNotNull { s ->
            when (s.id) {
                "fav" -> source(s, ShelfStyle.POSTER) { favorites.list().map { card(it, player) } }
                "top" -> source(s, ShelfStyle.POSTER) { browser.search(country(profile), null).map { card(it, player) } }
                "tag" -> if (tag == null) null else source(s.copy(titleRes = null, titleText = say(R.string.more_radio_row_tag_named).format(tag)), ShelfStyle.POSTER) {
                    browser.search(country(profile), tag).map { card(it, player) }
                }
                else -> null
            }
        }
    }

    override suspend fun test(): TestResult = try {
        val found = browser.search(credentials.get("country") ?: "US", null, limit = 5)
        if (found.isEmpty()) failed(R.string.more_radio_test_none) else TestResult(true, say(R.string.more_radio_test_ok))
    } catch (e: Exception) {
        failed(R.string.pex_test_failed)
    }

    companion object {
        const val ID = "radio"

        fun card(s: Station, player: String) = MediaItem(
            id = "$ID:${s.id}", providerId = ID, title = s.name, kind = Kind.LIVE,
            open = OpenTarget.AppPage(s.url, player, "audio/*"),
            subtitle = s.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.take(2).joinToString(" · "),
            posterUrl = s.logo, appPackage = player.ifEmpty { null },
        )

        val fields = listOf(
            CredentialField("country", R.string.more_radio_country, CredentialKind.TEXT, required = false, hintRes = R.string.more_radio_country_hint),
            CredentialField("tag", R.string.more_radio_tag, CredentialKind.TEXT, required = false, hintRes = R.string.more_radio_tag_hint),
            CredentialField("player", R.string.more_radio_player, CredentialKind.TEXT, required = false, hintRes = R.string.more_radio_player_hint),
        )

        /** The registration (PEX-01). */
        val addOn = AddOn(ID, R.string.more_radio_name, R.string.more_radio_desc, fields) { deps ->
            val http = deps.service(Http::class.java) ?: return@AddOn null
            val prefs = deps.service(Preferences::class.java) ?: return@AddOn null
            val context = deps.service(android.content.Context::class.java)
            RadioProvider(
                deps.credentials, RadioBrowser(ServerHttp(http)), RadioFavorites(prefs.asSecretStore()) { prefs.current().id },
                say = { id -> context?.getString(id).orEmpty() },
            )
        }
    }
}
