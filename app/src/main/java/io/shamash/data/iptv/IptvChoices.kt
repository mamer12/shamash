package io.shamash.data.iptv

import io.shamash.data.youtube.SecretStore
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

/** The channels a profile marked as favorites, kept on the TV beside the other secrets. SPEC: PEX-14 */
class IptvChoices(private val store: SecretStore, private val profileId: suspend () -> String) {
    private suspend fun key() = "iptv_fav_${profileId()}"

    suspend fun favorites(): List<String> = runCatching {
        io.shamash.data.json.decodeFromString(ListSerializer(String.serializer()), store.secret(key()).orEmpty().ifEmpty { "[]" })
    }.getOrDefault(emptyList())

    suspend fun toggle(channelId: String): List<String> {
        val current = favorites()
        val next = if (channelId in current) current - channelId else current + channelId
        store.setSecret(key(), if (next.isEmpty()) null else io.shamash.data.json.encodeToString(ListSerializer(String.serializer()), next))
        return next
    }

    suspend fun clear() = store.setSecret(key(), null)
}
