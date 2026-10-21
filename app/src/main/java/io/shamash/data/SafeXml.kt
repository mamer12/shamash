package io.shamash.data

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/** The XML was refused: a DTD or entity declaration, or an encoding it can't check. SPEC: PEX-22 */
class UnsafeXmlException(message: String) : IOException(message)

/**
 * Event-based XML reading for feeds and guides from the internet: DTDs and entity declarations are rejected outright
 * (XXE, entity expansion), external entities are never fetched, and the parser features are switched off where the
 * platform supports them. SPEC: PEX-15, PEX-22
 */
object SafeXml {
    private val FORBIDDEN = listOf("<!DOCTYPE", "<!ENTITY")

    private val factory: SAXParserFactory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = true
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
            "http://apache.org/xml/features/nonvalidating/load-external-dtd" to false,
        ).forEach { (feature, on) -> runCatching { setFeature(feature, on) } }
    }

    /** Parses [input], throwing [UnsafeXmlException] for a DTD/entity declaration, or an IOException/SAX error. */
    fun parse(input: InputStream, handler: DefaultHandler) {
        val guarded = Guard(input)
        val source = InputSource(guarded)
        val noExternals = object : DefaultHandler() {
            override fun resolveEntity(publicId: String?, systemId: String?) = InputSource(ByteArrayInputStream(ByteArray(0)))
        }
        val parser = factory.newSAXParser()
        parser.parse(source, Forward(handler, noExternals))
    }

    fun parse(text: String, handler: DefaultHandler) = parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)), handler)

    private class Forward(private val d: DefaultHandler, private val n: DefaultHandler) : DefaultHandler() {
        override fun resolveEntity(publicId: String?, systemId: String?) = n.resolveEntity(publicId, systemId)
        override fun startElement(u: String?, l: String?, q: String?, a: Attributes?) = d.startElement(u, l, q, a)
        override fun endElement(u: String?, l: String?, q: String?) = d.endElement(u, l, q)
        override fun characters(ch: CharArray, s: Int, len: Int) = d.characters(ch, s, len)
    }

    /** Watches the bytes going to the parser for declarations that must never reach it. */
    private class Guard(input: InputStream) : FilterInputStream(input) {
        private val tail = StringBuilder()

        private fun scan(b: Int) {
            if (b == 0) throw UnsafeXmlException("unsupported encoding")
            tail.append(b.toChar())
            if (tail.length > 16) tail.deleteCharAt(0)
            if (b != 'E'.code && b != 'e'.code && b != 'Y'.code && b != 'y'.code) return   // the last letter of both words
            val t = tail.toString().uppercase()
            if (FORBIDDEN.any { t.endsWith(it) }) throw UnsafeXmlException("doctype not allowed")
        }

        override fun read(): Int = super.read().also { if (it >= 0) scan(it) }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = super.read(b, off, len)
            for (i in 0 until n) scan(b[off + i].toInt() and 0xff)
            return n
        }
    }
}
