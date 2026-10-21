package io.shamash.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The add-on authoring guide exists, covers the contract, and Settings -> Add-ons points to it. SPEC: PEX-08, PLT-04 */
class AddOnGuideTest {
    private val guide = File("../docs/ADDONS.md")
    private val res = File("src/main/res")

    @Test fun `PEX_08 Settings - Add-ons shows the guide's address in English and Arabic`() {
        val url = Regex("""name="addons_guide_url"[^>]*>([^<]+)<""")
            .find(File(res, "values/strings_addons_guide.xml").readText())?.groupValues?.get(1)
        assertEquals("https://github.com/mamer12/shamash/blob/main/docs/ADDONS.md", url)
        listOf("values", "values-ar").forEach { dir ->
            val text = File(res, "$dir/strings_addons_guide.xml").readText()
            assertTrue("$dir lacks addons_guide_link with the address", Regex("""name="addons_guide_link">[^<]*%1\${'$'}s""").containsMatchIn(text))
        }
        val panel = File("src/main/java/io/shamash/feature/settings/AddonsPanel.kt").readText()
        assertTrue(panel.contains("R.string.addons_guide_link") && panel.contains("R.string.addons_guide_url"))
    }

    @Test fun `PLT_04 the guide covers the contract in under 150 lines`() {
        assertTrue("missing ${guide.absolutePath}", guide.isFile)
        val text = guide.readText()
        val lines = text.trimEnd().lines().size
        assertTrue("guide is $lines lines", lines <= 150)
        val topics = listOf(
            "AddOn", "Deps", "SecretCredentials", "RowSource", "TestableProvider", "ContentGate", "HttpPrivacy",
            "ServerProvider", "strings_", "PEX-31", "addons/example", "Sources.kt",
        )
        val missing = topics.filterNot { text.contains(it) }
        assertTrue("guide does not mention $missing", missing.isEmpty())
    }

    @Test fun `PLT_05 the guide's file references exist`() {
        val paths = Regex("""`(app/src/[^`*<>]+)`""").findAll(guide.readText()).map { it.groupValues[1] }.toList()
        assertTrue(paths.isNotEmpty())
        val absent = paths.filterNot { File("../$it").exists() }
        assertTrue("guide names paths that do not exist: $absent", absent.isEmpty())
    }
}
