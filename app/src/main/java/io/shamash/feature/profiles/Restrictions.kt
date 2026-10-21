package io.shamash.feature.profiles

import io.shamash.domain.ContentLevel
import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind

/**
 * What a kind of profile can reach. KIDS: name and avatar only: no Settings, Inputs, TV Settings, phone remote, row
 * editor or add-on switches. GUEST: nothing of the owner's (no Settings) and nothing saved. SPEC: PRF-10, PRF-11, PRF-13
 */
data class Restrictions(
    val settings: Boolean,
    val tvSettings: Boolean,
    val inputs: Boolean,
    val phoneRemote: Boolean,
    val savesHistory: Boolean,
) {
    companion object {
        private val OPEN = Restrictions(settings = true, tvSettings = true, inputs = true, phoneRemote = true, savesHistory = true)

        fun of(kind: ProfileKind): Restrictions = when (kind) {
            ProfileKind.ADULT -> OPEN
            ProfileKind.KIDS -> Restrictions(false, false, false, false, true)
            ProfileKind.GUEST -> OPEN.copy(settings = false, phoneRemote = false, savesHistory = false)
        }

        /** The kind of the active profile; no profile yet means the owner is setting up (open). */
        fun of(profile: Profile?): Restrictions = profile?.let { of(it.kind) } ?: OPEN
    }
}

/** The app allow-list (PRF-13): enforced for KIDS always, for FAMILY and KIDS levels once the owner has picked apps. */
object AppAccess {
    fun allows(profile: Profile, limits: ProfileLimits, packageName: String, ownPackage: String): Boolean {
        if (packageName == ownPackage) return true
        val strict = profile.kind == ProfileKind.KIDS || profile.contentLevel == ContentLevel.KIDS
        val list = limits.allowedApps
        return when {
            strict -> packageName in list.orEmpty()
            profile.contentLevel == ContentLevel.FAMILY && list != null -> packageName in list
            else -> true
        }
    }
}

/** Whether the picker shows at launch. SPEC: PRF-04 */
object PickerRule {
    fun needed(profileCount: Int, guestEnabled: Boolean, askOnLaunch: Boolean): Boolean =
        askOnLaunch && (profileCount >= 2 || (profileCount >= 1 && guestEnabled))
}

/** SHARED keys come from the owner when the profile has none of its own (PRF-09). */
object ProfileKeys {
    /** The reverse, on save: a borrowed key is not copied into the profile's own store. */
    fun stripShared(p: Profile, owner: Profile?): Profile {
        if (owner == null || owner.id == p.id || p.keyMode != io.shamash.domain.KeyMode.SHARED) return p
        fun own(mine: String, theirs: String) = if (mine == theirs) "" else mine
        return p.copy(
            tmdbKey = own(p.tmdbKey, owner.tmdbKey),
            youtubeClientId = own(p.youtubeClientId, owner.youtubeClientId),
            youtubeClientSecret = own(p.youtubeClientSecret, owner.youtubeClientSecret),
        )
    }

    fun effective(active: Profile, owner: Profile?): Profile {
        if (owner == null || owner.id == active.id || active.keyMode != io.shamash.domain.KeyMode.SHARED) return active
        return active.copy(
            tmdbKey = active.tmdbKey.ifBlank { owner.tmdbKey },
            youtubeClientId = active.youtubeClientId.ifBlank { owner.youtubeClientId },
            youtubeClientSecret = active.youtubeClientSecret.ifBlank { owner.youtubeClientSecret },
        )
    }
}
