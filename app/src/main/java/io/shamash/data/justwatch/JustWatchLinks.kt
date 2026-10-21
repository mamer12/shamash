package io.shamash.data.justwatch

import io.shamash.data.Http
import io.shamash.data.json
import io.shamash.data.softly
import io.shamash.data.tmdb.StreamingService
import io.shamash.domain.Kind
import io.shamash.domain.LinkResolver
import io.shamash.domain.OpenTarget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A TMDB title's own page in Apple TV or Netflix, which those apps open on the title. TMDB has no such links; JustWatch's
 * catalog does. Looked up when a title is opened and kept for the session; no link means the app opens. SPEC: PRV-05
 */
class JustWatchLinks(private val http: Http) : LinkResolver {
    private val found = HashMap<String, String?>()
    private val lock = Mutex()

    override suspend fun resolve(target: OpenTarget.Lookup, region: String): String? {
        val service = StreamingService.fromPackage(target.appPackage)?.takeIf { it.hasTitleLinks } ?: return null
        val key = "${target.appPackage}|${target.kind}|${target.tmdbId}|$region"
        lock.withLock { if (key in found) return found[key] }
        val link = softly("JustWatch link ${target.tmdbId}", Lookup.Failed) {
            Lookup.Done(JustWatchJson.link(fetch(target.title, region), target.tmdbId, target.kind, service))
        }
        if (link !is Lookup.Done) return null
        lock.withLock { found[key] = link.url }
        return link.url
    }

    private suspend fun fetch(title: String, region: String): String {
        val body = buildJsonObject {
            put("query", QUERY)
            put("variables", buildJsonObject {
                put("country", region.uppercase())
                put("language", "en")
                put("q", title)
            })
        }
        return http.postJson(API, body.toString())
    }

    /** A failed request isn't remembered (it's tried again next time); "no link" is. */
    private sealed interface Lookup {
        data object Failed : Lookup
        data class Done(val url: String?) : Lookup
    }

    private companion object {
        const val API = "https://apis.justwatch.com/graphql"
        const val QUERY = "query Find(\$country: Country!, \$language: Language!, \$q: String!) {" +
            " popularTitles(country: \$country, first: 5, filter: {searchQuery: \$q}) { edges { node {" +
            " objectType content(country: \$country, language: \$language) { externalIds { tmdbId } }" +
            " offers(country: \$country, platform: WEB) { monetizationType standardWebURL package { clearName } } } } } }"
    }
}

/** Reads JustWatch's `popularTitles` answer. */
object JustWatchJson {
    @Serializable private data class Answer(val data: Data? = null)
    @Serializable private data class Data(val popularTitles: Titles? = null)
    @Serializable private data class Titles(val edges: List<Edge> = emptyList())
    @Serializable private data class Edge(val node: Node)
    @Serializable
    private data class Node(val objectType: String? = null, val content: Content? = null, val offers: List<Offer> = emptyList())
    @Serializable private data class Content(val externalIds: Ids? = null)
    @Serializable private data class Ids(val tmdbId: String? = null)
    @Serializable
    private data class Offer(
        val monetizationType: String? = null,
        val standardWebURL: String? = null,
        val `package`: Package? = null,
    )
    @Serializable private data class Package(val clearName: String = "")

    /** The subscription offer's page on [service] for the title with this TMDB id and kind, or null. */
    fun link(text: String, tmdbId: Int, kind: Kind, service: StreamingService): String? {
        val objectType = if (kind == Kind.SERIES) "SHOW" else "MOVIE"
        val nodes = json.decodeFromString<Answer>(text).data?.popularTitles?.edges.orEmpty().map { it.node }
        return nodes
            .filter { it.content?.externalIds?.tmdbId == tmdbId.toString() && (it.objectType ?: objectType) == objectType }
            .flatMap { it.offers }
            .firstOrNull { offer ->
                offer.monetizationType == "FLATRATE" &&
                    StreamingService.fromProviderName(offer.`package`?.clearName.orEmpty()) == service &&
                    !offer.standardWebURL.isNullOrBlank()
            }
            ?.standardWebURL
    }
}
