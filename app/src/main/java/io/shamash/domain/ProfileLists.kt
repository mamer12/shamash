package io.shamash.domain

/**
 * Optional interface for an add-on that keeps lists per profile on the TV (favorites, follows, subscriptions, feeds).
 * Settings -> Add-ons -> Remove calls it for the active profile, beside forgetting the credentials and row choices, so
 * nothing of the add-on is left behind for that profile. Fails soft. SPEC: PEX-05
 */
interface ProfileListsOwner {
    suspend fun clearProfileLists()
}
