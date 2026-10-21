package io.shamash.addons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * An add-on can be removed by deleting its folders and its registration line (PEX-31). Each add-on registered in
 * `Sources.addOns` lives in `data/<name>`, `feature/<name>` and/or `addons/<name>`; outside those folders its packages
 * may be named only on its one registration line in Sources.kt and on one line in each documented hook below
 * (docs/ADDONS.md, "Removing an add-on"). SPEC: PEX-31, PLT-06
 */
class AddOnRemovabilityTest {
    private val sources = File("src/main/java/io/shamash").let { root ->
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }
            .associate { it.relativeTo(root).invariantSeparatorsPath to it.readText() }
    }

    @Test fun `PEX_31 each add-on is referenced from one Sources line and documented hooks only`() {
        assertTrue("sources not found from ${File(".").absolutePath}", sources.isNotEmpty())
        val names = Removability.registered(sources.getValue(Removability.SOURCES))
        assertTrue("no add-on found in Sources.addOns", "example" in names)
        val problems = names.distinct().flatMap { name -> Removability.problems(name, sources, names.count { it == name }) }
        assertTrue("add-ons that could not be removed cleanly:\n${problems.joinToString("\n")}", problems.isEmpty())
    }

    @Test fun `PEX_31 every registration line is understood`() {
        val text = sources.getValue(Removability.SOURCES)
        val block = Removability.block(text)
        val entries = block.filter { it.trim().removeSuffix(",").isNotBlank() }
        assertEquals("entries vs add-ons found", entries.size, Removability.registered(text).size)
    }

    @Test fun `PEX_31 the check catches a stray import, a second Sources line and a missing folder`() {
        val sources = """
            package io.shamash.data
            object Sources {
                val addOns: List<io.shamash.domain.AddOn> = listOf(
                    io.shamash.data.demo.DemoProvider.addOn,
                )
            }
        """.trimIndent()
        val clean = mapOf(Removability.SOURCES to sources, "data/demo/DemoProvider.kt" to "package io.shamash.data.demo")
        assertEquals(listOf("demo"), Removability.registered(sources))
        assertTrue(Removability.problems("demo", clean).isEmpty())

        val stray = clean + ("feature/home/Home.kt" to "import io.shamash.data.demo.DemoProvider")
        assertEquals(listOf("feature/home/Home.kt:1"), Removability.problems("demo", stray).map { it.substringBefore(' ') })

        val twice = clean + (Removability.SOURCES to "$sources\nval x = io.shamash.data.demo.DemoProvider.ID")
        assertTrue(Removability.problems("demo", twice).single().contains("2 lines"))
        assertTrue(Removability.problems("demo", twice, registrations = 2).isEmpty())       // two add-ons, one package

        val hook = clean + ("feature/settings/AddonsPanel.kt" to "\"demo\" -> io.shamash.feature.demo.Extra()")
        assertTrue(Removability.problems("demo", hook).isEmpty())
        val twoHookLines = clean + ("feature/settings/AddonsPanel.kt" to "io.shamash.data.demo.A()\nio.shamash.data.demo.B()")
        assertTrue(Removability.problems("demo", twoHookLines).isNotEmpty())

        assertTrue(Removability.problems("demo", mapOf(Removability.SOURCES to sources)).single().contains("no folder"))
        assertTrue(Removability.problems("demo", clean + ("feature/x/Y.kt" to "io.shamash.data.demos.Z")).isEmpty())   // whole names only
    }
}

/** The PEX-31 check over source texts keyed by their path under `io/shamash`. */
internal object Removability {
    const val SOURCES = "data/Sources.kt"
    private val LAYERS = listOf("data", "feature", "addons")

    /**
     * Files allowed to name an add-on's package on one line, and for which add-ons (null = any). Keep this list short
     * and in step with docs/ADDONS.md.
     */
    private val HOOKS: Map<String, Set<String>?> = mapOf(
        // Settings -> Add-ons: an add-on's own settings beside the shared panel (`when (addOn.id)` branch).
        "feature/settings/AddonsPanel.kt" to null,
        // Known debt: Continue watching resumes Watchme+ with Cinemana's constants. Delete the import and its two
        // branches with Cinemana; to be moved behind an interface.
        "feature/home/HomeActions.kt" to setOf("cinemana"),
        // Trakt / Simkl write-back starts with the app (one line).
        "app/ServiceLocator.kt" to setOf("trakt"),
    )

    /**
     * KNOWN DEBT, not hooks: folders that use an add-on's types freely, so removing that add-on also means editing
     * them. Listed so the check stays green while it is fixed; never add to it (docs/ADDONS.md, "Removing").
     */
    private val DEBT: Map<String, Set<String>> = mapOf(
        // The phone page's feed editor (CMP-10) uses My feed's types directly; should go through an interface.
        "feature/companion/" to setOf("myfeed"),
    )

    /** The lines of `val addOns = listOf(...)` in Sources.kt. */
    fun block(sourcesText: String): List<String> {
        val lines = sourcesText.lines()
        val start = lines.indexOfFirst { Regex("""\bval\s+addOns\b""").containsMatchIn(it) }
        require(start >= 0) { "Sources.addOns not found" }
        val end = (start + 1 until lines.size).first { lines[it].trim().startsWith(")") }
        return lines.subList(start + 1, end).map { it.substringBefore("//") }
    }

    /** The add-on names (their folder name) in registration order, from a fully qualified or imported entry. */
    fun registered(sourcesText: String): List<String> {
        val imports = Regex("""^import\s+io\.shamash\.(?:${LAYERS.joinToString("|")})\.(\w+)\.(\w+)""", RegexOption.MULTILINE)
            .findAll(sourcesText).associate { it.groupValues[2] to it.groupValues[1] }
        return block(sourcesText).mapNotNull { line ->
            val entry = line.trim().removeSuffix(",").trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            packageName(entry) ?: imports[entry.substringBefore('.')]
        }
    }

    private fun packageName(text: String): String? =
        Regex("""\bio\.shamash\.(?:${LAYERS.joinToString("|")})\.(\w+)\.""").find(text)?.groupValues?.get(1)

    /**
     * What would break if [name]'s folders and registration lines were deleted; empty when it removes cleanly. A package
     * may register more than one add-on ([registrations] lines, e.g. Trakt and Simkl); Sources.kt names it on no other line.
     */
    fun problems(name: String, files: Map<String, String>, registrations: Int = 1): List<String> {
        val own = LAYERS.map { "$it/$name/" }
        if (files.keys.none { path -> own.any { path.startsWith(it) } }) return listOf("$name: no folder in ${own.joinToString()}")
        val pattern = Regex("""\bio\.shamash\.(?:${LAYERS.joinToString("|")})\.$name\b""")
        val out = mutableListOf<String>()
        files.filterKeys { path -> own.none { path.startsWith(it) } }.forEach { (path, text) ->
            val hits = text.lines().withIndex().filter { pattern.containsMatchIn(it.value) }
            if (hits.isEmpty()) return@forEach
            if (DEBT.any { (prefix, names) -> path.startsWith(prefix) && name in names }) return@forEach
            val allowed = path == SOURCES || (path in HOOKS && HOOKS[path].let { it == null || name in it })
            val max = if (path == SOURCES) registrations else 1
            when {
                !allowed -> hits.forEach { out += "$path:${it.index + 1} names $name: ${it.value.trim()}" }
                hits.size > max -> out += "$path names $name on ${hits.size} lines (allowed: $max): lines ${hits.map { it.index + 1 }}"
            }
        }
        return out
    }
}
