package io.shamash.feature.matchcentre

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import io.shamash.ui.ChipPicker
import io.shamash.ui.Palette

/**
 * The catalog's competitions as chips under each sport's localized name; shared by setup and Settings. Codes the
 * catalog doesn't list (older profiles) stay picked. SPEC: MC-07, MC-15
 */
@Composable
fun CompetitionChips(picked: Set<String>, onChange: (Set<String>) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MatchSettingsEdits.groups().forEach { (sport, group) ->
            Text(MatchLabels.sport(context, sport), color = Palette.Dim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            val byId = group.associateBy { it.id }
            ChipPicker(
                group.map { it.id },
                picked.filterTo(HashSet()) { it in byId },
                { chosen -> onChange(MatchSettingsEdits.withGroup(picked, group, chosen)) },
            ) { byId[it]?.name ?: it }
        }
    }
}
