package io.shamash

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import io.shamash.app.ServiceLocator
import io.shamash.data.KNOWN_STREAMING
import io.shamash.domain.Profile
import io.shamash.feature.onboarding.Onboarding
import io.shamash.ui.Palette
import io.shamash.ui.ShamashTheme
import kotlinx.coroutines.launch

/** Shamash: the first-run setup until it's done, then the home screen. */
class MainActivity : ComponentActivity() {
    private val prefs by lazy { ServiceLocator.get(this).prefs }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ShamashTheme {
                val profile by prefs.profile.collectAsState(initial = null)
                val scope = rememberCoroutineScope()
                when (val p = profile) {
                    null -> Unit                                   // reading the saved answers (a moment)
                    else -> if (!p.onboarded) {
                        Onboarding(p) { answers -> scope.launch { prefs.save(answers) } }
                    } else {
                        HomeSoon(p) { scope.launch { prefs.save(p.copy(onboarded = false)) } }
                    }
                }
            }
        }
    }
}

/** Until the home screen lands (roadmap step 2): what setup learned, and a way to redo it. */
@Composable
private fun HomeSoon(p: Profile, redo: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Palette.Night).padding(96.dp)) {
        Text(if (p.name.isEmpty()) "Hello" else "Hello, ${p.name}", style = MaterialTheme.typography.displaySmall, color = Palette.Ink)
        Spacer(Modifier.height(24.dp))
        Text("City: ${p.city.ifEmpty { "—" }}", color = Palette.Dim)
        Text("Your apps: ${p.subscriptions.joinToString { KNOWN_STREAMING[it] ?: it }.ifEmpty { "—" }}", color = Palette.Dim)
        Text("Teams: ${p.teams.joinToString().ifEmpty { "—" }}", color = Palette.Dim)
        Text("You like: ${p.genres.joinToString().ifEmpty { "—" }}", color = Palette.Dim)
        Spacer(Modifier.height(32.dp))
        Button(onClick = redo) { Text("Redo setup") }
    }
}
