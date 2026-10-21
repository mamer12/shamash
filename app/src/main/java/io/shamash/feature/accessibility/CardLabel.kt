package io.shamash.feature.accessibility

/** The spoken form of a card: "<title>, <kind>, <provider>, <progress>%". Pure, so it is tested. SPEC: PLT-13 */
object CardLabel {
    fun join(vararg parts: String?): String = parts.filter { !it.isNullOrBlank() }.joinToString(", ")

    /** Whole percent for a started title; null when there is no progress to say. */
    fun percent(progress: Float?): Int? =
        progress?.takeIf { it > 0f }?.let { (it.coerceIn(0f, 1f) * 100).toInt().coerceAtLeast(1) }

    /** "3 of 12" for the carousel; blank when the position does not fit. */
    fun position(index: Int, count: Int, template: String): String =
        if (count < 1 || index !in 0 until count) "" else String.format(template, index + 1, count)
}
