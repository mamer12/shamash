package io.shamash.data.backup

import io.shamash.data.backup.BackupLimits as L

/**
 * Checks a decoded payload against the limits and cleans it: text is trimmed and bounded, duplicate profile ids and
 * more than one owner are refused, forbidden secret keys are dropped. Pure. SPEC: CMP-15, CMP-12
 */
internal object BackupValidator {
    private val ID = Regex("[A-Za-z0-9._-]{1,64}")
    private val SECRET_KEY = Regex("[A-Za-z0-9._-]{1,128}")
    private val FORBIDDEN = listOf("adb", "companion", "phone_pin", "pin_hash")

    /** Keys that never travel, even when the person opts into secrets: ADB keys, the companion PIN, PIN hashes. */
    fun isForbiddenSecret(key: String): Boolean {
        val k = key.lowercase()
        return k == "pin" || FORBIDDEN.any { it in k }
    }

    /** The cleaned payload, or the reason it is refused. */
    fun check(p: BackupPayload): Result<BackupPayload> = try {
        require(p.profiles.isNotEmpty() && p.profiles.size <= L.MAX_PROFILES) { "profile count" }
        require(p.profiles.map { it.id }.toSet().size == p.profiles.size) { "duplicate profile id" }
        require(p.ownerId in p.profiles.map { it.id }) { "owner" }
        require(p.secrets.size <= L.MAX_SECRETS) { "secret count" }
        val secrets = p.secrets.filter { (k, v) ->
            require(SECRET_KEY.matches(k) && v.length <= L.MAX_SECRET_VALUE) { "secret entry" }
            !isForbiddenSecret(k)
        }
        Result.success(p.copy(profiles = p.profiles.map(::clean), secrets = secrets))
    } catch (e: IllegalArgumentException) {
        Result.failure(e)
    }

    private fun clean(b: BackupProfile): BackupProfile {
        require(ID.matches(b.id)) { "profile id" }
        require(b.layout.rows.size <= L.MAX_ROWS && b.layout.rules.size <= L.MAX_ROWS) { "layout size" }
        require(b.layout.mix.size <= L.MAX_SET && b.layout.mixOff.size <= L.MAX_SET) { "layout size" }
        require(b.feeds.size <= L.MAX_FEEDS) { "feed count" }
        require(b.extras.size <= L.MAX_EXTRAS) { "extras count" }
        listOf(b.addons, b.follows.map { it.name }, b.subscriptions, b.teams, b.competitions, b.genres, b.keepRunning)
            .forEach { require(it.size <= L.MAX_SET) { "set size" } }
        require(b.follows.size <= L.MAX_SET) { "set size" }
        require(b.latitude == null || b.latitude in -90.0..90.0) { "latitude" }
        require(b.longitude == null || b.longitude in -180.0..180.0) { "longitude" }
        require(b.extras.all { (k, v) -> k.length <= 40 && v.length <= L.MAX_EXTRA_VALUE }) { "extras" }
        return b.copy(
            name = b.name.trim().take(L.MAX_TEXT),
            city = b.city.trim().take(L.MAX_TEXT),
            region = b.region.trim().take(2).uppercase().ifEmpty { "US" },
            addons = b.addons.mapTo(HashSet()) { it.take(L.MAX_TEXT) },
            teams = b.teams.mapTo(HashSet()) { it.take(L.MAX_TEXT) },
            competitions = b.competitions.mapTo(HashSet()) { it.take(L.MAX_TEXT) },
            genres = b.genres.mapTo(HashSet()) { it.take(L.MAX_TEXT) },
            subscriptions = b.subscriptions.mapTo(HashSet()) { it.take(L.MAX_TEXT) },
            keepRunning = b.keepRunning.mapTo(HashSet()) { it.take(L.MAX_TEXT) },
            feeds = b.feeds.map { BackupFeed(it.name.take(L.MAX_TEXT), it.url.take(2_000)) },
        )
    }
}
