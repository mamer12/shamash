package io.shamash.data.weather

import io.shamash.data.Fixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoTest {
    @Test
    fun PRV_12_forecast_gives_temperature_code_day_and_sun_times() {
        val weather = OpenMeteoJson.forecast(Fixtures.read("weather/forecast.json"))

        assertEquals(23.6, weather.temperatureC, 0.001)
        assertEquals(3, weather.code)
        assertFalse(weather.isDay)
        assertEquals(1_792_552_297_000L, weather.sunrise)
        assertEquals(1_792_592_534_000L, weather.sunset)
    }

    @Test
    fun ONB_02_geocoding_takes_the_first_match() {
        val place = OpenMeteoJson.place(Fixtures.read("weather/geocode.json"))
        assertEquals(Place("Baghdad", "IQ", 33.34058, 44.40088), place)
    }

    @Test
    fun ONB_02_unknown_city_has_no_place() {
        assertNull(OpenMeteoJson.place(Fixtures.read("weather/geocode_empty.json")))
    }
}
