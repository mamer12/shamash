package io.shamash.ui

import androidx.compose.runtime.Composable
import io.shamash.domain.Profile

/** Optional provider-owned destinations; the application never needs to know an add-on's concrete type. */
interface ProviderPage {
    val entryUri: String
    val titleResource: Int
    fun accepts(uri: String): Boolean
    fun available(profile: Profile): Boolean

    @Composable
    fun Content(uri: String, profile: Profile, onSettings: () -> Unit, onBack: () -> Unit)
}
