package io.shamash.domain

import kotlinx.serialization.Serializable

/**
 * Everything personal about one profile, as one small JSON document (<= 300 KB). Add-on list keys seen at the last
 * sync live here too. SPEC: PER-01, PER-07, PER-13, PER-17
 */
@Serializable
data class PersonalData(
    val signals: List<Signal> = emptyList(),
    val mutes: List<Mute> = emptyList(),
    val myList: List<MyListEntry> = emptyList(),
    val mirrorSeen: Map<String, List<String>> = emptyMap(),
    val learnOrder: Boolean = true,
    val version: Int = 1,
) {
    val muteIndex: MuteIndex by lazy(LazyThreadSafetyMode.NONE) { MuteIndex(mutes) }
}

/** The active profile's [PersonalData] as Home reads it. */
data class PersonalState(val profileId: String = "", val data: PersonalData = PersonalData()) {
    companion object {
        val EMPTY = PersonalState()
    }
}
