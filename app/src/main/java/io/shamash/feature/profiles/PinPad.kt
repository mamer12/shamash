package io.shamash.feature.profiles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import io.shamash.R
import io.shamash.ui.Palette

private val DIGIT_KEYS = listOf(
    Key.Zero, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine,
)

/**
 * A 4-digit PIN entry for the D-pad: a number grid (the remote's number keys work too) and four dots. [onComplete]
 * gets the digits once there are four; the dots clear at once, so a wrong try starts over. [message] shows under the
 * dots (a wrong PIN, a lock); while [enabled] is false the keys do nothing. SPEC: PRF-05
 */
@Composable
fun PinPad(
    title: String,
    message: String?,
    enabled: Boolean,
    onComplete: (String) -> Unit,
    onCancel: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var digits by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    fun add(d: Char) {
        if (!enabled || digits.length >= PinHasher.PIN_LENGTH) return
        val next = digits + d
        if (next.length == PinHasher.PIN_LENGTH) { digits = ""; onComplete(next) } else digits = next
    }
    Column(
        modifier.onPreviewKeyEvent { e ->
            val i = DIGIT_KEYS.indexOf(e.key)
            if (i >= 0 && e.type == KeyEventType.KeyDown) { add('0' + i); true } else false
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, color = Palette.Ink, fontSize = 28.sp)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(PinHasher.PIN_LENGTH) { i ->
                Spacer(Modifier.size(18.dp).background(if (i < digits.length) Palette.Gold else Palette.Faint, CircleShape))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(message.orEmpty(), color = Palette.Dawn, fontSize = 17.sp, modifier = Modifier.height(26.dp))
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            listOf("123", "456", "789").forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { d ->
                        val mod = if (d == '1') Modifier.focusRequester(first) else Modifier
                        KeyButton(d.toString(), { add(d) }, mod)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { digits = digits.dropLast(1) }, modifier = Modifier.width(KEY_W)) {
                    Text(stringResource(R.string.pin_delete_key), fontSize = 16.sp)
                }
                KeyButton("0", { add('0') })
                if (onCancel != null) {
                    OutlinedButton(onClick = onCancel, modifier = Modifier.width(KEY_W)) {
                        Text(stringResource(R.string.profile_cancel), fontSize = 16.sp)
                    }
                } else Spacer(Modifier.width(KEY_W))
            }
        }
    }
}

private val KEY_W = 84.dp

@Composable
private fun KeyButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = onClick, modifier = modifier.width(KEY_W)) { Text(label, fontSize = 22.sp) }
}

/** A button that confirms only when OK is held for [ProfileSession.HOLD_MS]; releasing early starts over. SPEC: PRF-10 */
@Composable
fun HoldToConfirm(label: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier) {
    var startedAt by remember { mutableLongStateOf(0L) }
    var percent by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val confirm = androidx.compose.runtime.rememberUpdatedState(onConfirmed)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = {},
            modifier = Modifier.focusRequester(focus).onPreviewKeyEvent { e ->
                if (e.key != Key.DirectionCenter && e.key != Key.Enter && e.key != Key.NumPadEnter) return@onPreviewKeyEvent false
                if (e.type == KeyEventType.KeyUp) { startedAt = 0L; percent = 0f; return@onPreviewKeyEvent true }
                val now = System.currentTimeMillis()
                if (startedAt == 0L) startedAt = now
                val held = now - startedAt
                percent = (held * 100f / ProfileSession.HOLD_MS).coerceAtMost(100f)
                if (held >= ProfileSession.HOLD_MS) { startedAt = 0L; percent = 0f; confirm.value() }
                true
            },
        ) { Text(label, fontSize = 20.sp) }
        Spacer(Modifier.height(10.dp))
        Text(
            if (percent > 0f) stringResource(R.string.hold_ok_progress, percent.toInt()) else "",
            color = Palette.Dawn, fontSize = 18.sp,
        )
    }
}

/** What to tell the person after a PIN try; null when it was right. */
fun android.content.Context.pinMessage(check: PinCheck): String? = when (check) {
    PinCheck.Ok -> null
    is PinCheck.Wrong -> getString(R.string.pin_wrong, check.triesLeft)
    is PinCheck.Locked -> getString(R.string.pin_locked, (check.remainingMs / 60_000 + 1).toInt())
}
