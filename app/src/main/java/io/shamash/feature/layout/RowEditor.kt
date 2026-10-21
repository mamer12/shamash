package io.shamash.feature.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.domain.Layout
import io.shamash.domain.LayoutOps
import io.shamash.domain.Placed
import io.shamash.domain.Shelf
import io.shamash.feature.settings.Explain
import io.shamash.ui.Palette
import io.shamash.ui.TextInput
import io.shamash.ui.shelfTitle

/**
 * Every row Home could show, with Up / Down, Hide / Show, Pin and Rename (D-pad only, no drag). Each change is saved
 * at once and Home follows. SPEC: LAY-02, LAY-05, LAY-06
 */
@Composable
internal fun RowEditor(rows: List<Shelf>, layout: Layout, onChange: (Layout) -> Unit) {
    val ids = rows.map { it.id }
    val byId = rows.associateBy { it.id }
    var renaming by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var confirmReset by remember { mutableStateOf(false) }

    Explain(stringResource(R.string.layout_rows_text))
    if (rows.isEmpty()) Explain(stringResource(R.string.layout_no_rows))
    val placed = LayoutOps.arrange(ids, layout)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        placed.forEach { row ->
            key(row.id) {
                val shelf = byId.getValue(row.id)
                RowLine(shelf, row, onChange = { change -> onChange(change(ids, layout)) }, onRename = {
                    renaming = row.id
                    draft = row.pref?.title.orEmpty()
                })
                if (renaming == row.id) {
                    TextInput(stringResource(R.string.layout_rename_label), draft, { draft = it.take(LayoutOps.TITLE_MAX) })
                    Button(onClick = {
                        onChange(LayoutOps.rename(ids, layout, row.id, draft))
                        renaming = null
                    }) { Text(stringResource(R.string.layout_rename_save)) }
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = {
        if (confirmReset) onChange(LayoutOps.reset(layout))
        confirmReset = !confirmReset
    }) { Text(stringResource(if (confirmReset) R.string.layout_reset_confirm else R.string.layout_reset)) }
}

@Composable
private fun RowLine(
    shelf: Shelf,
    row: Placed,
    onChange: ((List<String>, Layout) -> Layout) -> Unit,
    onRename: () -> Unit,
) {
    val title = row.pref?.title ?: shelf.title ?: shelfTitle(shelf.titleKey, shelf.titleArg)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (row.hidden) "$title (${stringResource(R.string.layout_hidden_tag)})" else title,
            color = if (row.hidden) Palette.Faint else Palette.Ink, fontSize = 20.sp, maxLines = 1,
            modifier = Modifier.width(300.dp),
        )
        OutlinedButton(onClick = { onChange { ids, l -> LayoutOps.move(ids, l, row.id, -1) } }) { Text(stringResource(R.string.layout_up)) }
        OutlinedButton(onClick = { onChange { ids, l -> LayoutOps.move(ids, l, row.id, 1) } }) { Text(stringResource(R.string.layout_down)) }
        OutlinedButton(onClick = { onChange { ids, l -> LayoutOps.setHidden(ids, l, row.id, !row.hidden) } }) {
            Text(stringResource(if (row.hidden) R.string.layout_show else R.string.layout_hide))
        }
        OutlinedButton(onClick = { onChange { ids, l -> LayoutOps.setPinned(ids, l, row.id, !row.pinned) } }) {
            Text(stringResource(if (row.pinned) R.string.layout_unpin else R.string.layout_pin))
        }
        OutlinedButton(onClick = onRename) { Text(stringResource(R.string.layout_rename)) }
    }
}
