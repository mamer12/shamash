package io.shamash.data

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.shamash.domain.ContentLevel
import io.shamash.domain.Follow
import io.shamash.domain.KeyMode
import io.shamash.domain.Layout
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import kotlinx.serialization.builtins.SetSerializer
import kotlinx.serialization.json.Json
import java.util.Locale

/**
 * A [Profile] as preference keys, the same keys the v1 single-profile store used plus the new ones, so one codec reads
 * the legacy store and a `profile_<id>` store. Layout and follows are JSON; a corrupt value falls back to its default
 * (LAY-19) and logs without content. Pure: the tests run it on the JVM. SPEC: PRF-02, PRF-17, PRF-18, LAY-19
 */
internal object ProfileCodec {
    private val json = Json { ignoreUnknownKeys = true }
    private val followsSerializer = SetSerializer(Follow.serializer())

    private val onboarded = booleanPreferencesKey("onboarded")
    private val name = stringPreferencesKey("name")
    private val city = stringPreferencesKey("city")
    private val latitude = doublePreferencesKey("latitude")
    private val longitude = doublePreferencesKey("longitude")
    private val region = stringPreferencesKey("region")
    private val subscriptions = stringSetPreferencesKey("subscriptions")
    private val teams = stringSetPreferencesKey("teams")
    private val competitions = stringSetPreferencesKey("competitions")
    private val genres = stringSetPreferencesKey("genres")
    private val tmdbKey = stringPreferencesKey("tmdb_key")
    private val youtubeClientId = stringPreferencesKey("youtube_client_id")
    private val youtubeClientSecret = stringPreferencesKey("youtube_client_secret")
    private val legacyCinemana = booleanPreferencesKey("cinemana_addon")
    private val contentLevel = stringPreferencesKey("content_level")
    private val keepRunning = stringSetPreferencesKey("keep_running")
    private val kind = stringPreferencesKey("kind")
    private val avatarColor = intPreferencesKey("avatar_color")
    private val pinHash = stringPreferencesKey("pin_hash")
    private val keyMode = stringPreferencesKey("key_mode")
    private val addons = stringSetPreferencesKey("addons")
    private val follows = stringPreferencesKey("follows")
    private val layout = stringPreferencesKey("layout")

    /** Every key a v1 store held for the profile (the legacy store keeps only its `secret_` keys after migration). */
    val legacyKeys: List<Preferences.Key<*>> = listOf(
        onboarded, name, city, latitude, longitude, region, subscriptions, teams, competitions, genres, tmdbKey,
        youtubeClientId, youtubeClientSecret, legacyCinemana, contentLevel, keepRunning,
    )

    fun hasLegacyKeys(s: Preferences): Boolean = legacyKeys.any { it in s }

    fun read(s: Preferences, id: String = ""): Profile {
        val defaults = Profile()
        return Profile(
            id = id,
            kind = enumOf(s[kind], defaults.kind),
            avatarColor = s[avatarColor] ?: defaults.avatarColor,
            pinHash = s[pinHash],
            keyMode = enumOf(s[keyMode], defaults.keyMode),
            addons = s[addons] ?: if (s[legacyCinemana] == true) setOf("cinemana") else emptySet(),
            follows = decode(s[follows], followsSerializer, emptySet()),
            layout = decode(s[layout], Layout.serializer(), Layout()),
            onboarded = s[onboarded] ?: false,
            name = s[name].orEmpty(),
            city = s[city].orEmpty(),
            latitude = s[latitude],
            longitude = s[longitude],
            region = s[region] ?: Locale.getDefault().country.ifEmpty { defaults.region },
            subscriptions = s[subscriptions].orEmpty(),
            teams = s[teams].orEmpty(),
            competitions = s[competitions].orEmpty(),
            genres = s[genres].orEmpty(),
            tmdbKey = s[tmdbKey].orEmpty(),
            youtubeClientId = s[youtubeClientId].orEmpty(),
            youtubeClientSecret = s[youtubeClientSecret].orEmpty(),
            contentLevel = enumOf(s[contentLevel], defaults.contentLevel),
            keepRunning = s[keepRunning] ?: defaults.keepRunning,
        )
    }

    fun write(s: MutablePreferences, p: Profile) {
        s[kind] = p.kind.name
        s[avatarColor] = p.avatarColor
        p.pinHash?.let { s[pinHash] = it } ?: s.remove(pinHash)
        s[keyMode] = p.keyMode.name
        s[addons] = p.addons
        s[follows] = json.encodeToString(followsSerializer, p.follows)
        s[layout] = json.encodeToString(Layout.serializer(), p.layout)
        s[onboarded] = p.onboarded
        s[name] = p.name
        s[city] = p.city
        p.latitude?.let { s[latitude] = it } ?: s.remove(latitude)
        p.longitude?.let { s[longitude] = it } ?: s.remove(longitude)
        s[region] = p.region
        s[subscriptions] = p.subscriptions
        s[teams] = p.teams
        s[competitions] = p.competitions
        s[genres] = p.genres
        s[tmdbKey] = p.tmdbKey
        s[youtubeClientId] = p.youtubeClientId
        s[youtubeClientSecret] = p.youtubeClientSecret
        s[contentLevel] = p.contentLevel.name
        s[keepRunning] = p.keepRunning
        s.remove(legacyCinemana)
    }

    fun clearLegacy(s: MutablePreferences) = legacyKeys.forEach { s.remove(it) }

    private inline fun <reified E : Enum<E>> enumOf(value: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == value } ?: default

    private fun <T> decode(text: String?, serializer: kotlinx.serialization.KSerializer<T>, default: T): T {
        if (text == null) return default
        return try {
            json.decodeFromString(serializer, text)
        } catch (e: Exception) {
            Logger.w("profile layout", e)
            default
        }
    }
}
