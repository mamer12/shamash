package io.shamash.data.tmdb

import io.shamash.data.KNOWN_STREAMING

/**
 * The streaming services TMDB's "where to watch" can point at, recognised by the provider name TMDB and JustWatch use.
 * Add-on channels ("Starz Apple TV channel") and stores ("Apple TV Store") are other offers, not the service. SPEC: PRV-01
 */
enum class StreamingService(val appPackage: String, private val names: Regex) {
    NETFLIX("com.netflix.ninja", Regex("^netflix\\b")),
    APPLE_TV("com.apple.atve.androidtv.appletv", Regex("^apple tv( ?\\+| plus)?$")),
    PRIME_VIDEO("com.amazon.amazonvideo.livingroom", Regex("^(amazon )?prime video\\b")),
    DISNEY_PLUS("com.disney.disneyplus", Regex("^disney( ?\\+| plus)")),
    MAX("com.wbd.stream", Regex("^(hbo )?max$")),
    OSN_PLUS("com.osn.go", Regex("^osn\\b")),
    SHAHID("net.mbc.shahidTV", Regex("^shahid\\b")),
    TOD("com.todtv.tod", Regex("^tod$"));

    val displayName: String get() = KNOWN_STREAMING[appPackage] ?: name

    /** Titles whose page link JustWatch can find, so they open on the title itself. SPEC: PRV-05 */
    val hasTitleLinks: Boolean get() = this == APPLE_TV || this == NETFLIX

    companion object {
        private val OTHER_OFFER = Regex("\\b(channel|store)\\b")

        fun fromProviderName(providerName: String): StreamingService? {
            val name = providerName.trim().lowercase()
            if (OTHER_OFFER.containsMatchIn(name)) return null
            return entries.firstOrNull { it.names.containsMatchIn(name) }
        }

        fun fromPackage(appPackage: String): StreamingService? = entries.firstOrNull { it.appPackage == appPackage }
    }
}
