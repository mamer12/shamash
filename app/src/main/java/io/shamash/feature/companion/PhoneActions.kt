package io.shamash.feature.companion

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.shamash.R
import io.shamash.app.Opener
import io.shamash.app.ServiceLocator
import io.shamash.data.Logger
import io.shamash.domain.Kind
import io.shamash.domain.MediaItem
import io.shamash.domain.OpenTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder

/**
 * Does on the TV what came from the phone: a YouTube video in YouTube, a TOD link in TOD, another link in whatever
 * app takes it, words in Search. Answers what it did, for the phone page; the TV shows the same in a toast. SPEC: CMP-04
 */
internal class PhoneActions(private val locator: ServiceLocator) {
    private val context = locator.appContext

    /**
     * Called on a server worker; the opening itself happens on the main thread, and the phone hears whether it
     * worked; a timed-out opening is reported as a failure.
     */
    fun handle(message: PhoneMessage): String {
        val said = describe(message)
        if (message == PhoneMessage.Empty) return said
        val opened = runBlocking {
            withTimeoutOrNull(OPEN_WAIT_MS) { withContext(Dispatchers.Main) { open(message).also { if (it) toast(said) } } }
        }
        return if (opened != true) context.getString(R.string.companion_failed) else said
    }

    private fun describe(message: PhoneMessage): String = when (message) {
        PhoneMessage.Empty -> context.getString(R.string.companion_nothing)
        is PhoneMessage.YouTube -> context.getString(R.string.companion_opening_youtube)
        is PhoneMessage.Tod -> context.getString(R.string.companion_opening_tod)
        is PhoneMessage.Link -> context.getString(R.string.companion_opening_link)
        is PhoneMessage.Words -> context.getString(R.string.companion_searching, message.text)
    }

    private suspend fun open(message: PhoneMessage): Boolean = when (message) {
        PhoneMessage.Empty -> false
        is PhoneMessage.YouTube ->
            openItem(message.videoId, Kind.VIDEO, OpenTarget.Video(message.videoId), Opener.YOUTUBE)
        is PhoneMessage.Tod -> openItem(message.url, Kind.LIVE, OpenTarget.AppPage(message.url, TOD), TOD)
        is PhoneMessage.Link -> view(message.url)
        is PhoneMessage.Words -> locator.opener.internal("search?q=" + URLEncoder.encode(message.text, "UTF-8"))
            .also { if (!it) toast(context.getString(R.string.companion_failed)) }
    }

    private suspend fun openItem(id: String, kind: Kind, target: OpenTarget, app: String): Boolean {
        val item = MediaItem(id = id, providerId = PROVIDER, title = id, kind = kind, open = target, appPackage = app)
        return locator.opener.open(item).also { if (!it) toast(context.getString(R.string.companion_failed)) }
    }

    private fun view(url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        Logger.w("phone link", e); toast(context.getString(R.string.companion_failed)); false
    } catch (e: SecurityException) {
        Logger.w("phone link", e); toast(context.getString(R.string.companion_failed)); false
    }

    private fun toast(text: String) {
        Toast.makeText(context, context.getString(R.string.companion_toast, text), Toast.LENGTH_SHORT).show()
    }

    private companion object {
        const val TOD = "com.todtv.tod"
        const val PROVIDER = "phone"
        const val OPEN_WAIT_MS = 3_000L
    }
}
