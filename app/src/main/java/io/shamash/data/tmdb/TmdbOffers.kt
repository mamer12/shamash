package io.shamash.data.tmdb

import io.shamash.data.json
import io.shamash.domain.OfferType
import io.shamash.domain.WatchOffer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** `/{movie,tv}/{id}/watch/providers`: every offer of the services Shamash knows, in [region]. SPEC: PER-09 */
object TmdbOffers {
    private val TYPES = listOf(
        "flatrate" to OfferType.FLATRATE, "free" to OfferType.FREE, "ads" to OfferType.FREE,
        "rent" to OfferType.RENT, "buy" to OfferType.BUY,
    )

    fun parse(text: String, region: String): List<WatchOffer> {
        val byRegion = runCatching { json.parseToJsonElement(text).jsonObject["results"] as? JsonObject }.getOrNull() ?: return emptyList()
        val offers = byRegion[region.uppercase()] as? JsonObject ?: return emptyList()
        return TYPES.flatMap { (field, type) ->
            (offers[field] as? JsonArray).orEmpty().mapNotNull { entry ->
                val name = (entry as? JsonObject)?.get("provider_name")?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                StreamingService.fromProviderName(name)?.let { WatchOffer(it.appPackage, it.displayName, type) }
            }
        }
    }
}
