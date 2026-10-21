package io.shamash.feature.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.data.TvApps
import io.shamash.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun names(id: String): Pair<Int, Int> = when (id) {
    "netflix_youtube" -> R.string.quick_netflix_youtube to R.string.quick_netflix_youtube_desc
    "shahid_osn" -> R.string.quick_shahid_osn to R.string.quick_shahid_osn_desc
    "sports_first" -> R.string.quick_sports to R.string.quick_sports_desc
    else -> R.string.quick_basics to R.string.quick_basics_desc
}

/** The "Quick start" step: a card per preset; picking one pre-fills the next screens. SPEC: PLT-17 */
@Composable
internal fun QuickStartStep(answers: Answers, onChange: (Answers) -> Unit) {
    val context = LocalContext.current
    val presets by produceState(emptyList<QuickStartPreset>()) {
        value = withContext(Dispatchers.IO) { QuickStart.load(context) }
    }
    val installed by produceState(emptySet<String>()) {
        value = withContext(Dispatchers.IO) {
            try { TvApps(context).installed().map { it.packageName }.toSet() } catch (e: Exception) { emptySet() }
        }
    }
    var picked by remember { mutableStateOf<String?>(null) }
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        presets.forEach { preset ->
            val (name, desc) = names(preset.id)
            val shape = RoundedCornerShape(12.dp)
            // The picked mark is the card's own border, so it scales with the focus animation; no hairline otherwise.
            val isPicked = picked == preset.id
            Card(
                onClick = { picked = preset.id; onChange(QuickStart.apply(preset, answers.region, installed, answers)) },
                modifier = Modifier.width(240.dp),
                shape = CardDefaults.shape(shape),
                border = CardDefaults.border(
                    border = if (isPicked) Border(BorderStroke(3.dp, Palette.Gold), shape = shape) else Border.None,
                    focusedBorder = Border(BorderStroke(3.dp, if (isPicked) Palette.Gold else Palette.Ink), shape = shape),
                ),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(name), color = Palette.Ink, fontSize = 22.sp)
                    Text(stringResource(desc), color = Palette.Dim, fontSize = 16.sp)
                }
            }
        }
    }
}
