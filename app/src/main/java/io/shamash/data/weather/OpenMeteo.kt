package io.shamash.data.weather

import io.shamash.data.Http
import io.shamash.data.Refresh
import io.shamash.data.TimedCache
import io.shamash.data.json
import io.shamash.data.softly
import io.shamash.domain.Profile
import io.shamash.domain.Weather
import io.shamash.domain.WeatherSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * The weather at the profile's place from Open-Meteo (no key), in Celsius (the UI converts for the US), every 30 min.
 * SPEC: PRV-12, HOME-14
 */
class OpenMeteo(private val http: Http, clock: () -> Long = System::currentTimeMillis) : WeatherSource {
    private val cache = TimedCache<Pair<Double, Double>, Weather?>(clock) { if (it == null) Refresh.RETRY_EMPTY else EVERY }

    override suspend fun current(profile: Profile): Weather? {
        val place = (profile.latitude ?: return null) to (profile.longitude ?: return null)
        return cache.get(place) { softly("weather", null) { OpenMeteoJson.forecast(http.text(url(place))) } }
    }

    private fun url(place: Pair<Double, Double>): String =
        "https://api.open-meteo.com/v1/forecast".toHttpUrl().newBuilder()
            .addQueryParameter("latitude", place.first.toString())
            .addQueryParameter("longitude", place.second.toString())
            .addQueryParameter("current", "temperature_2m,weather_code,is_day")
            .addQueryParameter("daily", "sunrise,sunset")
            .addQueryParameter("timezone", "auto")
            .addQueryParameter("timeformat", "unixtime")
            .addQueryParameter("forecast_days", "1")
            .build().toString()

    private companion object {
        const val EVERY = 30 * Refresh.MINUTE
    }
}

/** A place found by name, for onboarding's city step. SPEC: ONB-02 */
data class Place(val name: String, val countryCode: String, val latitude: Double, val longitude: Double)

/** Finds a city's coordinates with Open-Meteo's geocoding (the first match). SPEC: ONB-02 */
class Geocoder(private val http: Http) {
    suspend fun find(city: String): Place? {
        if (city.isBlank()) return null
        val url = "https://geocoding-api.open-meteo.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", city.trim())
            .addQueryParameter("count", "1")
            .build().toString()
        return softly("geocoding", null) { OpenMeteoJson.place(http.text(url)) }
    }
}

/** Pure parsers of Open-Meteo's answers. */
object OpenMeteoJson {
    @Serializable private data class Forecast(val current: Current, val daily: Daily)
    @Serializable private data class Daily(val sunrise: List<Long> = emptyList(), val sunset: List<Long> = emptyList())

    @Serializable
    private data class Current(
        @SerialName("temperature_2m") val temperature: Double,
        @SerialName("weather_code") val code: Int,
        @SerialName("is_day") val isDay: Int = 1,
    )

    @Serializable private data class Places(val results: List<Found> = emptyList())

    @Serializable
    private data class Found(
        val name: String,
        @SerialName("country_code") val countryCode: String = "",
        val latitude: Double,
        val longitude: Double,
    )

    /** Asked with `timeformat=unixtime`: sunrise and sunset come as epoch seconds. */
    fun forecast(text: String): Weather {
        val f = json.decodeFromString<Forecast>(text)
        return Weather(
            temperatureC = f.current.temperature,
            code = f.current.code,
            isDay = f.current.isDay == 1,
            sunrise = f.daily.sunrise.firstOrNull()?.times(1000) ?: 0,
            sunset = f.daily.sunset.firstOrNull()?.times(1000) ?: 0,
        )
    }

    fun place(text: String): Place? =
        json.decodeFromString<Places>(text).results.firstOrNull()
            ?.let { Place(it.name, it.countryCode, it.latitude, it.longitude) }
}
