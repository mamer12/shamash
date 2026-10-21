package io.shamash.feature.settings

import io.shamash.domain.MediaItem
import io.shamash.domain.Profile
import io.shamash.domain.ProfileListsOwner
import io.shamash.domain.Provider
import io.shamash.domain.Shelf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Remove forgets an add-on's per-profile lists too, failing soft. SPEC: PEX-05 */
class AddonRemoveTest {
    private class Lists(override val id: String, private val fail: Boolean = false) : Provider, ProfileListsOwner {
        var cleared = 0
        override fun isEnabled(profile: Profile) = true
        override suspend fun shelves(profile: Profile) = emptyList<Shelf>()
        override suspend fun featured(profile: Profile) = emptyList<MediaItem>()
        override suspend fun search(query: String, profile: Profile) = emptyList<MediaItem>()
        override suspend fun clearProfileLists() {
            cleared++
            if (fail) error("store")
        }
    }

    @Test fun `PEX_05 Remove clears only that add-on's lists and a failing store does not stop it`() = runBlocking {
        val radio = Lists("radio")
        val iptv = Lists("iptv")
        clearLists(listOf(radio, iptv), "radio")
        assertEquals(1, radio.cleared)
        assertEquals(0, iptv.cleared)
        clearLists(listOf(Lists("live", fail = true)), "live")       // no exception
        clearLists(emptyList(), "nothing")
    }
}
