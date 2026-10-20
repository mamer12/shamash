package io.shamash.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences as Store
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.shamash.domain.Profile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.store by preferencesDataStore(name = "profile")

/** The profile and small secrets, on the TV only. SPEC: ONB-06, ARCH-06 */
class Preferences(private val context: Context) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val name = stringPreferencesKey("name")
        val city = stringPreferencesKey("city")
        val latitude = doublePreferencesKey("latitude")
        val longitude = doublePreferencesKey("longitude")
        val region = stringPreferencesKey("region")
        val subscriptions = stringSetPreferencesKey("subscriptions")
        val teams = stringSetPreferencesKey("teams")
        val competitions = stringSetPreferencesKey("competitions")
        val genres = stringSetPreferencesKey("genres")
        val tmdbKey = stringPreferencesKey("tmdb_key")
        val youtubeClientId = stringPreferencesKey("youtube_client_id")
        val youtubeClientSecret = stringPreferencesKey("youtube_client_secret")
        val cinemanaAddon = booleanPreferencesKey("cinemana_addon")
        val keepRunning = stringSetPreferencesKey("keep_running")
    }

    val profile: Flow<Profile> = context.store.data.map(::read)

    suspend fun current(): Profile = profile.first()

    suspend fun save(p: Profile) {
        context.store.edit { s ->
            s[Keys.onboarded] = p.onboarded
            s[Keys.name] = p.name
            s[Keys.city] = p.city
            p.latitude?.let { s[Keys.latitude] = it } ?: s.remove(Keys.latitude)
            p.longitude?.let { s[Keys.longitude] = it } ?: s.remove(Keys.longitude)
            s[Keys.region] = p.region
            s[Keys.subscriptions] = p.subscriptions
            s[Keys.teams] = p.teams
            s[Keys.competitions] = p.competitions
            s[Keys.genres] = p.genres
            s[Keys.tmdbKey] = p.tmdbKey
            s[Keys.youtubeClientId] = p.youtubeClientId
            s[Keys.youtubeClientSecret] = p.youtubeClientSecret
            s[Keys.cinemanaAddon] = p.cinemanaAddon
            s[Keys.keepRunning] = p.keepRunning
        }
    }

    /** A small secret or value outside the profile (YouTube sign-in, phone PIN…). */
    suspend fun secret(key: String): String? = context.store.data.first()[stringPreferencesKey("secret_$key")]

    suspend fun setSecret(key: String, value: String?) {
        context.store.edit { s ->
            val k = stringPreferencesKey("secret_$key")
            if (value == null) s.remove(k) else s[k] = value
        }
    }

    private fun read(s: Store): Profile {
        val defaults = Profile()
        return Profile(
            onboarded = s[Keys.onboarded] ?: false,
            name = s[Keys.name].orEmpty(),
            city = s[Keys.city].orEmpty(),
            latitude = s[Keys.latitude],
            longitude = s[Keys.longitude],
            region = s[Keys.region] ?: java.util.Locale.getDefault().country.ifEmpty { defaults.region },
            subscriptions = s[Keys.subscriptions].orEmpty(),
            teams = s[Keys.teams].orEmpty(),
            competitions = s[Keys.competitions].orEmpty(),
            genres = s[Keys.genres].orEmpty(),
            tmdbKey = s[Keys.tmdbKey].orEmpty(),
            youtubeClientId = s[Keys.youtubeClientId].orEmpty(),
            youtubeClientSecret = s[Keys.youtubeClientSecret].orEmpty(),
            cinemanaAddon = s[Keys.cinemanaAddon] ?: false,
            keepRunning = s[Keys.keepRunning] ?: defaults.keepRunning,
        )
    }
}
