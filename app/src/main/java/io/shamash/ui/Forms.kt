package io.shamash.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.Text

private val FieldShape = RoundedCornerShape(16.dp)

/**
 * A labelled one-line text field for the D-pad: OK opens the TV keyboard. [secret] hides what's typed (keys), so a
 * key is never shown on screen once entered.
 */
@Composable
fun TextInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    secret: Boolean = false,
    width: Dp = 640.dp,
    hint: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier) {
        Text(label, color = Palette.Dim, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = value, onValueChange = onChange, singleLine = true,
            interactionSource = interaction,
            textStyle = TextStyle(color = Palette.Ink, fontSize = 22.sp),
            cursorBrush = SolidColor(Palette.Gold),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                imeAction = ImeAction.Done,
                autoCorrectEnabled = !secret,
            ),
            modifier = Modifier
                .width(width)
                .background(Palette.Dusk, FieldShape)
                .border(2.dp, if (focused) Palette.Gold else Palette.Faint, FieldShape)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        )
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(hint, color = Palette.Faint, fontSize = 15.sp)
        }
    }
}

/** Chips to pick several of [all] (shown with [label]); [picked] are on. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun <T> ChipPicker(
    all: List<T>,
    picked: Set<T>,
    onChange: (Set<T>) -> Unit,
    label: @Composable (T) -> String = { it.toString() },
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        all.forEach { item ->
            val on = item in picked
            FilterChip(selected = on, onClick = { onChange(if (on) picked - item else picked + item) }) {
                Text(label(item))
            }
        }
    }
}

/** Chips where exactly one of [all] is on. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalTvMaterial3Api::class)
@Composable
fun <T> OnePicker(all: List<T>, picked: T, onPick: (T) -> Unit, label: @Composable (T) -> String) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        all.forEach { item -> FilterChip(selected = item == picked, onClick = { onPick(item) }) { Text(label(item)) } }
    }
}
