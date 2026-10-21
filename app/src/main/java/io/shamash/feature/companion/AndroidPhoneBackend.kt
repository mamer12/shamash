package io.shamash.feature.companion

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.shamash.MainActivity
import io.shamash.R
import io.shamash.app.ServiceLocator
import io.shamash.data.Logger
import io.shamash.data.Sources
import io.shamash.data.backup.BackupOptions
import io.shamash.data.backup.BackupPayload
import io.shamash.data.backup.BackupResult
import io.shamash.data.backup.LocalBackups
import io.shamash.data.myfeed.MyFeedProvider
import io.shamash.data.myfeed.UserFeed
import io.shamash.domain.Profile
import io.shamash.feature.backup.ApplyResult
import io.shamash.feature.backup.BackupActions
import io.shamash.feature.home.HomeModel
import io.shamash.feature.profiles.PinCheck
import kotlinx.coroutines.flow.first
import java.io.File
import java.net.InetAddress
import java.time.LocalDate

/**
 * The phone API wired to this TV: the active profile from the profile store, PINs through the profile session
 * (its five-tries lockout), rows from Home's own model, feeds through My feed, the queue store, and backups through
 * the same [BackupActions] the TV's Settings use, after the person at the TV said yes. SPEC: CMP-06..15
 */
internal class AndroidPhoneBackend(private val locator: ServiceLocator) : PhoneBackend {
    private val context = locator.appContext
    private val session get() = locator.session
    private val queueStore get() = QueueStore.get(context)
    private val feedProvider get() = locator.providers.firstOrNull { it.id == MyFeedProvider.ID } as? MyFeedProvider
    private val backup by lazy {
        BackupActions(locator.prefs, session.security, Sources.addOns, LocalBackups(File(context.noBackupFilesDir, "local-backups")))
    }

    override suspend fun active(): Profile = locator.prefs.current()

    override suspend fun owner(): Profile? {
        val id = locator.profiles.ownerId()
        val all = locator.profiles.profiles.first()
        return all.firstOrNull { it.id == id } ?: all.firstOrNull()
    }

    override suspend fun checkPin(profileId: String, pin: String): PinAnswer = when (session.checkPin(profileId, pin)) {
        PinCheck.Ok -> PinAnswer.OK
        is PinCheck.Wrong -> PinAnswer.WRONG
        is PinCheck.Locked -> PinAnswer.LOCKED
    }

    override suspend fun saveProfile(profile: Profile, what: Changed) {
        locator.profiles.save(profile)
        toast(
            when (what) {
                Changed.ROWS -> R.string.cmp_toast_rows
                Changed.TEAMS -> R.string.cmp_toast_teams
                Changed.FEEDS -> R.string.cmp_toast_feeds
                Changed.QUEUE -> R.string.cmp_toast_queue
            },
        )
    }

    override suspend fun rowChoices(): List<RowChoice> {
        val res = context.resources
        return HomeModel.get(locator).state.value.editableRows.map { s ->
            RowChoice(s.id, title(res, s.titleKey, s.titleArg).ifEmpty { s.title.orEmpty() })
        }
    }

    override suspend fun feeds(): List<UserFeed>? = feedProvider?.feeds()

    override suspend fun saveFeeds(feeds: List<UserFeed>) {
        val provider = feedProvider ?: return
        provider.save(feeds)
        toast(R.string.cmp_toast_feeds)
    }

    override suspend fun tryFeed(feed: UserFeed): List<String> =
        feedProvider?.preview(feed)?.map { it.title } ?: emptyList()

    override fun resolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()

    override suspend fun queue(profileId: String): List<QueueEntry> = queueStore.list(profileId)

    override suspend fun addToQueue(profileId: String, entry: QueueEntry): Boolean =
        queueStore.add(profileId, entry).also { if (it) toast(R.string.cmp_toast_queue) }

    override suspend fun confirmExport(): Confirm {
        val prompt = PhonePrompt.Export()
        bringToFront()
        return when (val asked = PhonePrompts.ask(prompt, prompt.answer)) {
            PhonePrompts.Asked.Busy -> Confirm.BUSY
            is PhonePrompts.Asked.Answered -> if (asked.value == true) Confirm.YES else Confirm.NO
        }
    }

    override suspend fun exportFile(passphrase: String, options: BackupOptions): BackupResult<ByteArray> =
        backup.export(passphrase, options)

    override suspend fun openFile(file: ByteArray, passphrase: String): BackupResult<BackupPayload> = backup.open(file, passphrase)

    override suspend fun confirmImport(payload: BackupPayload): ImportOutcome {
        val prompt = PhonePrompt.Import(payload)
        bringToFront()
        val mode = when (val asked = PhonePrompts.ask(prompt, prompt.answer)) {
            PhonePrompts.Asked.Busy -> return ImportOutcome.Busy
            is PhonePrompts.Asked.Answered -> asked.value ?: return ImportOutcome.Declined
        }
        return when (val r = backup.apply(payload, mode)) {
            is ApplyResult.Done -> ImportOutcome.Done(r.added, r.replaced).also { toast(R.string.cmp_import_done) }
            ApplyResult.Failed -> ImportOutcome.Failed.also { toast(R.string.backup_failed) }
        }
    }

    override fun today(): String = LocalDate.now().toString()

    override fun now(): Long = System.currentTimeMillis()

    /** A backup question must be seen: Shamash comes forward when another app is in front. */
    private fun bringToFront() {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        try {
            context.startActivity(intent)
        } catch (e: RuntimeException) {
            Logger.w("phone prompt: bring to front", e)
        }
    }

    private fun toast(text: Int) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(context, text, Toast.LENGTH_SHORT).show() }
    }

    /** A shelf's default title from its key (a string resource name) and argument, as Home draws it. */
    @SuppressLint("DiscouragedApi")
    private fun title(res: android.content.res.Resources, key: String, arg: String?): String {
        val id = res.getIdentifier(key, "string", context.packageName)
        if (id == 0) return arg.orEmpty()
        return runCatching { if (arg != null) res.getString(id, arg) else res.getString(id) }.getOrDefault(arg.orEmpty())
    }
}
