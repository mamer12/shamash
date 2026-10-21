package io.shamash.data

import io.shamash.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BuildKeysTest {
    private val build = BuildKeys(tmdb = "T-FIXTURE", youtubeClientId = "Y-ID-FIXTURE", youtubeClientSecret = "Y-SECRET-FIXTURE")

    @Test fun `build keys win over stored keys and are never written back`() {
        val stored = Profile(tmdbKey = "old", youtubeClientId = "old-id", youtubeClientSecret = "old-secret")
        val seen = build.applyTo(stored)
        assertEquals("T-FIXTURE", seen.tmdbKey)
        assertEquals("Y-ID-FIXTURE", seen.youtubeClientId)
        val saved = build.stripFrom(seen)
        assertEquals("", saved.tmdbKey)
        assertEquals("", saved.youtubeClientSecret)
    }

    @Test fun `without build keys stored keys are kept`() {
        val stored = Profile(tmdbKey = "mine")
        assertEquals("mine", BuildKeys().applyTo(stored).tmdbKey)
        assertEquals("mine", BuildKeys().stripFrom(stored).tmdbKey)
    }

    @Test fun `toString never shows a key`() {
        assertFalse(build.toString().contains("FIXTURE"))
    }
}
