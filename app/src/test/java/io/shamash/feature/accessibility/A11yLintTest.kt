package io.shamash.feature.accessibility

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** An automated check that lists focusables without a description. SPEC: PLT-16 */
class A11yLintTest {
    private val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()

    @Test
    fun `PLT_16 every FocusCard call gives a description`() {
        assertTrue("sources not found from ${File(".").absolutePath}", sources.isNotEmpty())
        val missing = sources.flatMap { file -> callsWithoutDescription(file) }
        assertTrue("FocusCard without description = ...:\n${missing.joinToString("\n")}", missing.isEmpty())
    }

    @Test
    fun `PLT_16 the lint sees a call that lacks one`() {
        val bad = "FocusCard(onClick, Modifier) { }"
        val good = "FocusCard(onClick, Modifier, description = d) { }"
        assertTrue(argumentsOf(bad, 0).contains("description").not())
        assertTrue(argumentsOf(good, 0).contains("description"))
    }

    private fun callsWithoutDescription(file: File): List<String> {
        val text = file.readText()
        val found = Regex("""(?<!fun )FocusCard\(""").findAll(text)
        return found.filter { !argumentsOf(text, it.range.last).contains("description") }
            .map { "${file.path}:${text.substring(0, it.range.first).count { c -> c == '\n' } + 1}" }.toList()
    }

    /** The text between the call's parentheses, starting at the opening one at or after [open]. */
    private fun argumentsOf(text: String, open: Int): String {
        val start = text.indexOf('(', open.coerceAtLeast(0))
        var depth = 0
        for (i in start until text.length) {
            if (text[i] == '(') depth++
            if (text[i] == ')' && --depth == 0) return text.substring(start, i)
        }
        return text.substring(start)
    }
}
