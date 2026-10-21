package io.shamash.data

/** Recorded service answers under `src/test/resources/fixtures` (trimmed, no keys). SPEC: ARCH-09 */
object Fixtures {
    fun read(path: String): String =
        checkNotNull(javaClass.classLoader?.getResource("fixtures/$path")) { "missing fixture $path" }.readText()
}
