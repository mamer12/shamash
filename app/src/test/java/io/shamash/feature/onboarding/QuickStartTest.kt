package io.shamash.feature.onboarding

import io.shamash.domain.Profile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class QuickStartTest {
    private val dir = File("src/main/assets/presets")
    private fun preset(id: String) = QuickStart.parse(File(dir, "$id.json").readText())!!
    private fun blank() = Answers.of(Profile())

    @Test
    fun `PLT_18 every shipped preset parses and the four cards exist`() {
        val ids = dir.listFiles { f -> f.extension == "json" }!!.map { preset(it.nameWithoutExtension).id }.toSet()
        assertEquals(QuickStart.ORDER.toSet(), ids)
    }

    @Test
    fun `PLT_17 Netflix and YouTube ticks only the installed apps`() {
        val installed = setOf("com.netflix.ninja", "com.other")   // YouTube is not installed
        val out = QuickStart.apply(preset("netflix_youtube"), "US", installed, blank())

        assertEquals(setOf("com.netflix.ninja"), out.subscriptions)
        assertEquals("cinema", out.preset)
        assertEquals(setOf("com.netflix.ninja"), out.keepRunning)
    }

    @Test
    fun `PLT_17 Shahid and OSN turn Cinemana on, and what was ticked stays`() {
        val before = blank().copy(subscriptions = setOf("com.mine"))
        val out = QuickStart.apply(preset("shahid_osn"), "IQ", setOf("net.mbc.shahidTV", "com.mine"), before)

        assertEquals(setOf("com.mine", "net.mbc.shahidTV"), out.subscriptions)
        assertEquals(setOf("cinemana"), out.addons)
    }

    @Test
    fun `PLT_17 sports first picks the sports layout and the basics the minimal one`() {
        assertEquals("sports", QuickStart.apply(preset("sports_first"), "US", emptySet(), blank()).preset)
        val basics = QuickStart.apply(preset("basics"), "US", setOf("com.x"), blank())
        assertEquals("minimal", basics.preset)
        assertTrue(basics.subscriptions.isEmpty())
        assertNull(basics.keepRunning)
    }

    @Test
    fun `PLT_18 a preset can list apps per region`() {
        val p = QuickStart.parse("""{"id":"x","packages":{"*":["a"],"SA":["b"]}}""")!!
        assertEquals(listOf("b"), p.packagesFor("sa"))
        assertEquals(listOf("a"), p.packagesFor("US"))
    }

    @Test
    fun `PLT_18 a broken preset file is ignored`() {
        assertNull(QuickStart.parse("{not json"))
        assertNotNull(QuickStart.parse("""{"id":"ok"}"""))
    }

    @Test
    fun `PLT_17 the answers carry the preset into the profile`() {
        val out = QuickStart.apply(preset("shahid_osn"), "IQ", setOf("com.osn.go"), blank()).into(Profile())
        assertEquals(setOf("cinemana"), out.addons)
        assertEquals(setOf("com.osn.go"), out.keepRunning)
        assertTrue("com.osn.go" in out.subscriptions)
    }
}
