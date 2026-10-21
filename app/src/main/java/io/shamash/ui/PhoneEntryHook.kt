package io.shamash.ui

import androidx.compose.runtime.Composable

/**
 * Lets any [TextInput] offer "type it on your phone" (CMP-08, PEX-05): the phone companion sets [offer] once it is
 * running; null means no phone entry. The offer gets the field's label, whether it is secret, and where a value
 * from the phone goes; it never shows or keeps the value.
 */
object PhoneEntryHook {
    @Volatile
    var offer: (@Composable (label: String, secret: Boolean, onValue: (String) -> Unit) -> Unit)? = null
}
