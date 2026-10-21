package io.shamash.data.security

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * No secret in a log line: a source scan of every log call in the app. Logs go through `Logger` only (it prints an
 * exception's class, never its message, which can hold a credential-bearing URL); a log message never interpolates an
 * exception, a credential, a URL or a request body. SPEC: PEX-04, ARCH-05
 */
class LogHygieneTest {
    private val root: File = listOf("src/main/java", "app/src/main/java").map(::File).first { it.isDirectory }
    private val sources = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    /** Calls whose first argument is a log message. */
    private val call = Regex("""(?:Logger\.w|softly?|Log\.[vdiwe])\(\s*"((?:[^"\\]|\\.)*)"""")
    private val interpolated = Regex("""\$\{([^}]*)}|\$([A-Za-z_][A-Za-z0-9_]*)""")
    private val secretish = Regex(
        "(?i)(token|secret|password|passw|passphrase|\\bpin\\b|pin(code|hash|s)\\b|(profile|owner|companion)pin|credential|api_?key|apikey|bearer|cookie|" +
            "session|auth|\\burl\\b|\\buri\\b|address|\\bbody\\b|header|query|\\bkey\\b)",
    )
    private val throwable = Regex("^(e|t|ex|err|error|exception|throwable|cause)(\\.(message|localizedMessage|cause|stackTrace.*|toString\\(\\)))?$")

    private fun messages(): List<Pair<String, String>> = sources.flatMap { file ->
        call.findAll(file.readText()).map { "${file.relativeTo(root)}: ${it.value}" to it.groupValues[1] }.toList()
    }

    @Test fun `PEX_04 sources are found`() {
        assertTrue(sources.size > 50)
        assertTrue(messages().size > 20)
    }

    @Test fun `ARCH_05 every log line goes through Logger`() {
        val direct = sources.filter { it.name != "Http.kt" }.flatMap { f ->
            f.readLines().filter { "android.util.Log" in it || Regex("""\bLog\.[vdiwe]\(""").containsMatchIn(it) || "printStackTrace" in it }
                .map { "${f.name}: ${it.trim()}" }
        }
        assertTrue("direct logging bypasses Logger: $direct", direct.isEmpty())
    }

    @Test fun `PEX_04 no log message interpolates a credential, URL, body or exception`() {
        val bad = messages().filter { (_, message) ->
            interpolated.findAll(message).any { m ->
                val expr = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
                secretish.containsMatchIn(expr) || throwable.matches(expr)
            }
        }.map { it.first }
        assertTrue("log messages that may carry a secret: $bad", bad.isEmpty())
    }

    @Test fun `PEX_04 the scanner catches the patterns it is meant to`() {
        fun flagged(message: String) = interpolated.findAll(message).any { m ->
            val expr = (m.groupValues[1].ifEmpty { m.groupValues[2] }).trim()
            secretish.containsMatchIn(expr) || throwable.matches(expr)
        }
        listOf("sign-in \$token", "feed \${feed.url}", "x \$e", "x \${e.message}", "p \$pin", "k \${creds.apiKey}", "h \$authHeader")
            .forEach { assertTrue(it, flagged(it)) }
        listOf("TMDB \$kind", "\$id rows", "search \${provider.id}", "close \$packageName", "adb> \$command")
            .forEach { assertTrue(it, !flagged(it)) }
    }
}
