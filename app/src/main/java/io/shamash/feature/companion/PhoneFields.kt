package io.shamash.feature.companion

import java.security.SecureRandom
import java.util.Random

/**
 * Text fields on the TV that can be typed on the phone ("Type for the TV"): a field on screen offers itself with its
 * label and gets a short code the TV shows next to it; the phone lists the codes and labels and sends a value for
 * one. The value goes straight to the field and is never kept or listed here, so nothing can echo it, a secret
 * least of all. Thread-safe. SPEC: CMP-08, PEX-05
 */
class PhoneFields(private val random: Random = SecureRandom()) {
    private class Field(val label: String, val secret: Boolean, val fill: (String) -> Unit)

    private val open = LinkedHashMap<String, Field>()

    /** Offers a field; returns its code, or null when too many are open. [fill] gets the value (any thread). */
    @Synchronized
    fun offer(label: String, secret: Boolean, fill: (String) -> Unit): String? {
        if (open.size >= MAX_FIELDS) return null
        var code: String
        do code = newCode() while (code in open)
        open[code] = Field(label.take(MAX_LABEL), secret, fill)
        return code
    }

    @Synchronized
    fun withdraw(code: String) { open.remove(code) }

    @Synchronized
    fun list(): List<FieldOut> = open.map { (code, f) -> FieldOut(code, f.label, f.secret) }

    @Synchronized
    fun size(): Int = open.size

    /** Hands [value] to the field with [code]; false when there is no such field or the value is too long. */
    fun fill(code: String, value: String): Boolean {
        if (value.length > MAX_VALUE) return false
        val field = synchronized(this) { open[code.trim().uppercase()] } ?: return false
        field.fill(value)
        return true
    }

    private fun newCode(): String = String(CharArray(CODE_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    companion object {
        const val MAX_FIELDS = 24
        const val MAX_VALUE = 4_096
        const val MAX_LABEL = 80
        const val CODE_LENGTH = 2
        private const val ALPHABET = "ACDEFHJKMNPRTUVWXY34679"     // no look-alikes (0/O, 1/I/L, 2/Z, 5/S, 8/B)

        /** The one registry the TV's fields and the phone server share. */
        val shared = PhoneFields()
    }
}
