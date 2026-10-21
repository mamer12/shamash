package io.shamash.feature.onboarding

import io.shamash.data.weather.Place
import io.shamash.domain.Profile
import io.shamash.feature.settings.Keys

/** What setup has been told so far; becomes the [Profile] on Done. SPEC: ONB-06 */
data class Answers(
    val name: String,
    val city: String,
    val latitude: Double?,
    val longitude: Double?,
    val region: String,
    val subscriptions: Set<String>,
    val teams: String,                 // as typed: comma separated
    val competitions: Set<String>,
    val genres: Set<String>,
    val keys: Keys,
) {
    /** The geocoder's answer: its coordinates and country (the region for "where to watch"). */
    fun located(place: Place?): Answers =
        if (place == null) copy(latitude = null, longitude = null)
        else copy(latitude = place.latitude, longitude = place.longitude, region = place.countryCode.ifBlank { region })

    fun into(start: Profile): Profile = keys.into(start).copy(
        onboarded = true,
        name = name.trim(),
        city = city.trim(),
        latitude = latitude,
        longitude = longitude,
        region = region,
        subscriptions = subscriptions,
        teams = teams.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet(),
        competitions = competitions,
        genres = genres,
    )

    companion object {
        fun of(p: Profile) = Answers(
            name = p.name, city = p.city, latitude = p.latitude, longitude = p.longitude, region = p.region,
            subscriptions = p.subscriptions, teams = p.teams.joinToString(", "), competitions = p.competitions,
            genres = p.genres, keys = Keys.of(p),
        )
    }
}
