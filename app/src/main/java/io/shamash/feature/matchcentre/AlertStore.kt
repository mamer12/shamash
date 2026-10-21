package io.shamash.feature.matchcentre

import io.shamash.data.Logger
import io.shamash.data.json
import io.shamash.data.Preferences

/** The profile's [AlertSettings], kept on the TV next to its other small settings; never sent anywhere. SPEC: MC-11, MC-16 */
class AlertStore(private val prefs: Preferences) {
    suspend fun load(): AlertSettings {
        val id = prefs.current().id
        val text = prefs.secret(key(id)) ?: return AlertSettings()
        return try {
            json.decodeFromString(AlertSettings.serializer(), text)
        } catch (e: kotlinx.serialization.SerializationException) {
            Logger.w("match alerts", e)
            AlertSettings()
        }
    }

    suspend fun save(settings: AlertSettings) {
        prefs.setSecret(key(prefs.current().id), json.encodeToString(AlertSettings.serializer(), settings))
    }

    private fun key(profileId: String) = "match_alerts_$profileId"
}
