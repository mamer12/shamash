package io.shamash.feature.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.shamash.R
import io.shamash.data.Logger
import io.shamash.domain.SearchRanking

/**
 * Voice text to a search query, and the recognizer intent. No audio is stored or sent by Shamash: the TV's own
 * recognizer does the listening. SPEC: PLT-08, PLT-09, PLT-10
 */
object VoiceQuery {
    /** The first non-blank hypothesis from the recognizer's results, or null. */
    fun best(results: List<String>?): String? = results?.firstOrNull { it.isNotBlank() }

    /** The query to search: Arabic diacritics and alef forms folded by the ranking's own normalizer (PLT-10). */
    fun toQuery(spoken: String?): String? = spoken?.let(SearchRanking::normalize)?.takeIf { it.isNotEmpty() }

    /** The recognizer's language for the app language: Arabic or English, nothing else is promised. */
    fun language(appLanguage: String): String = if (appLanguage.startsWith("ar")) "ar" else "en-US"

    /** The internal route for a query ("search?q=…"), as Home and the phone use it. */
    fun route(query: String): String = "search?q=" + java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")

    /** The keys a remote's voice or search button sends. */
    fun isVoiceKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_VOICE_ASSIST || keyCode == KeyEvent.KEYCODE_ASSIST

    fun intent(appLanguage: String, prompt: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, language(appLanguage))
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
}

/** Starts the TV's voice recognizer; [onQuery] gets the text. A missing recognizer gives a toast and the keyboard. */
class VoiceLauncher internal constructor(
    private val context: Context,
    private val prompt: String,
    private val launcher: ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult>,
) {
    fun start() {
        val language = context.resources.configuration.locales[0].language
        try {
            launcher.launch(VoiceQuery.intent(language, prompt))
        } catch (e: ActivityNotFoundException) {
            Logger.w("voice search: no recognizer", e)
            Toast.makeText(context, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            Logger.w("voice search: recognizer refused", e)
            Toast.makeText(context, R.string.voice_unavailable, Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun rememberVoiceLauncher(onQuery: (String) -> Unit): VoiceLauncher {
    val context = LocalContext.current
    val prompt = stringResource(R.string.voice_prompt)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val heard = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
        VoiceQuery.toQuery(VoiceQuery.best(heard))?.let(onQuery)
    }
    return remember(context, prompt, launcher) { VoiceLauncher(context, prompt, launcher) }
}
