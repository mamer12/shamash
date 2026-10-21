package io.shamash.feature.companion

import io.shamash.domain.Profile
import io.shamash.domain.ProfileKind

/** What the phone must add to the companion PIN before it may change something. */
enum class Need { NONE, PROFILE_PIN, OWNER_PIN, REFUSED }

/** The answer to a profile or owner PIN typed on the phone (the TV's own five-tries lockout applies). */
enum class PinAnswer { OK, WRONG, LOCKED }

/**
 * Who may edit what from the phone. The page always works on the active profile (PRF-06). A profile with a PIN
 * asks for it; Kids and Guest need the owner's PIN and, with no owner PIN set, cannot be edited from the phone at
 * all. A backup touches every profile, so it asks for the owner's PIN when there is one. Pure. SPEC: CMP-06, PRF-10
 */
object EditAccess {
    fun forProfile(active: Profile, owner: Profile?): Need = when {
        active.kind == ProfileKind.KIDS || active.kind == ProfileKind.GUEST ->
            if (owner?.pinHash != null) Need.OWNER_PIN else Need.REFUSED
        active.pinHash != null -> Need.PROFILE_PIN
        else -> Need.NONE
    }

    fun forBackup(active: Profile, owner: Profile?): Need = when {
        active.kind == ProfileKind.KIDS || active.kind == ProfileKind.GUEST ->
            if (owner?.pinHash != null) Need.OWNER_PIN else Need.REFUSED
        owner?.pinHash != null -> Need.OWNER_PIN
        else -> Need.NONE
    }
}
