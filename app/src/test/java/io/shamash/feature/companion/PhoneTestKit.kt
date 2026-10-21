package io.shamash.feature.companion

import io.shamash.data.backup.BackupCodec
import io.shamash.data.backup.BackupOptions
import io.shamash.data.backup.BackupPayload
import io.shamash.data.backup.BackupProfile
import io.shamash.data.backup.BackupResult
import io.shamash.data.myfeed.UserFeed
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind
import java.net.InetAddress

/** A TV for the phone API's tests: profiles, PINs, feeds, queue and backups in memory. */
internal class FakeBackend(
    var active: Profile = Profile(id = "p-owner", name = "Sam", onboarded = true),
    var ownerProfile: Profile? = null,
) : PhoneBackend {
    val pins = HashMap<String, String>()
    val checked = ArrayList<String>()
    val saved = ArrayList<Pair<Profile, Changed>>()
    var feedList: MutableList<UserFeed>? = ArrayList()
    var dns: Map<String, List<String>> = mapOf("example.org" to listOf("93.184.216.34"))
    var tried = listOf("One", "Two", "Three", "Four")
    val queued = HashMap<String, List<QueueEntry>>()
    var confirm = Confirm.YES
    var importChoice: ImportOutcome? = null
    var exports = 0
    var importsShown = ArrayList<BackupPayload>()
    val codec = BackupCodec(clock = { 1_700_000_000_000 })
    val payload = BackupPayload(
        ownerId = "p-owner",
        profiles = listOf(BackupProfile(id = "p-owner", name = "Sam"), BackupProfile(id = "p-kid", name = "Lina", kind = ProfileKind.KIDS)),
    )

    override suspend fun active() = active
    override suspend fun owner() = ownerProfile ?: active
    override suspend fun checkPin(profileId: String, pin: String): PinAnswer {
        checked += profileId
        return if (pins[profileId] == pin) PinAnswer.OK else PinAnswer.WRONG
    }
    override suspend fun saveProfile(profile: Profile, what: Changed) { saved += profile to what; active = profile }
    override suspend fun rowChoices() = listOf(RowChoice("matches", "Matches"), RowChoice("continue", "Continue watching"))
    override suspend fun feeds() = feedList?.toList()
    override suspend fun saveFeeds(feeds: List<UserFeed>) { feedList = feeds.toMutableList() }
    override suspend fun tryFeed(feed: UserFeed) = tried
    override fun resolve(host: String): List<InetAddress> =
        dns[host]?.map { InetAddress.getByName(it) } ?: throw java.net.UnknownHostException(host)
    override suspend fun queue(profileId: String) = queued[profileId].orEmpty()
    override suspend fun addToQueue(profileId: String, entry: QueueEntry): Boolean {
        queued[profileId] = QueueOps.add(queued[profileId].orEmpty(), entry); return true
    }
    override suspend fun confirmExport() = confirm
    override suspend fun exportFile(passphrase: String, options: BackupOptions): BackupResult<ByteArray> {
        exports++
        return codec.export(payload, passphrase, options)
    }
    override suspend fun openFile(file: ByteArray, passphrase: String) = codec.open(file, passphrase)
    override suspend fun confirmImport(payload: BackupPayload): ImportOutcome {
        importsShown += payload
        return importChoice ?: ImportOutcome.Done(payload.profiles.size, 0)
    }
    override fun today() = "2026-10-21"
    override fun now() = 1_700_000_000_000L
}

internal object Kit {
    const val PIN = "123456"
    const val TV = "192.168.1.20"
    const val PORT = 8765

    class Pins(var pin: String = PIN) : PinSource {
        var rotations = 0
        override fun current() = pin
        override fun rotate() { rotations++; pin = "654321" }
    }

    fun auth(clock: () -> Long = { 0L }, pins: Pins = Pins()) = CompanionAuth(pins, PinGuard(clock), wrongDelayMs = 0)

    fun api(backend: PhoneBackend = FakeBackend(), auth: CompanionAuth = auth(), fields: PhoneFields = PhoneFields()) =
        PhoneApi(auth, backend, fields)

    fun router(api: PhoneApi, auth: CompanionAuth = auth(), sent: MutableList<String> = ArrayList()) = CompanionRouter(
        auth, api, page = { lang, nonce -> "page:$lang:$nonce" }, send = { sent += it; "sent" },
        words = { w, lang -> "$w:$lang" }, nonce = { "n0nce" },
    )

    fun call(
        method: String, path: String, body: String = "", pin: String? = PIN, profilePin: String? = null,
        extra: Map<String, String> = emptyMap(), length: Int = body.toByteArray().size,
    ): HttpRequest {
        val headers = HashMap<String, String>()
        headers["host"] = "$TV:$PORT"
        headers["x-shamash"] = "1"
        if (pin != null) headers["x-shamash-pin"] = pin
        if (profilePin != null) headers["x-shamash-profile-pin"] = profilePin
        headers.putAll(extra)
        return HttpRequest(method, path, body, headers, length)
    }

    val ENDPOINTS: List<Pair<String, String>> =
        ApiPaths.GETS.map { "GET" to it } + ApiPaths.POSTS.map { "POST" to it }

    fun HttpReply.code(): String? = Regex("\"code\":\"([^\"]+)\"").find(body)?.groupValues?.get(1)
}
