package io.shamash.feature.layout

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.domain.LayoutPreset
import io.shamash.ui.Palette

/** The preset's name and one-line description. SPEC: LAY-08, LAY-18 */
internal fun presetText(id: String): Pair<Int, Int> = when (id) {
    LayoutPreset.SPORTS -> R.string.preset_sports to R.string.preset_sports_desc
    LayoutPreset.KIDS -> R.string.preset_kids to R.string.preset_kids_desc
    LayoutPreset.MINIMAL -> R.string.preset_minimal to R.string.preset_minimal_desc
    else -> R.string.preset_cinema to R.string.preset_cinema_desc
}

/** Four cards, one per preset; the picked one has the accent border. Shared by setup and Settings. SPEC: LAY-08 */
@Composable
fun PresetPicker(ids: List<String>, picked: String?, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        ids.forEach { id ->
            val (name, desc) = presetText(id)
            val shape = RoundedCornerShape(12.dp)
            Card(
                onClick = { onPick(id) },
                modifier = Modifier.width(260.dp).border(if (id == picked) 4.dp else 0.dp, Palette.Gold, shape),
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(name), color = Palette.Ink, fontSize = 24.sp)
                    Text(stringResource(desc), color = Palette.Dim, fontSize = 16.sp)
                }
            }
        }
    }
}
