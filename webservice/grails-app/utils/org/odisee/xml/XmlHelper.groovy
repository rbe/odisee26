/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 02.02.15, 18:35
 */
package org.odisee.xml

import com.sun.org.apache.xerces.internal.dom.DeferredNode
import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.NodeChild
import groovy.xml.DOMBuilder
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource

final class XmlHelper {

    private static final String S_NEWLINE = '\n'

    private XmlHelper() {
        throw new AssertionError();
    }

    /**
     * Local entity resolver.
     */
    static class CachedEntityResolver implements EntityResolver {

        @Override
        InputSource resolveEntity(String publicId, String systemId) {
            new InputSource(XmlHelper.getResourceAsStream('dtd/' + systemId.split('/').last()))
        }

    }

    /**
     *
     static entityResolver = [
     resolveEntity: { String publicId, String systemId ->
     try {new InputSource(XmlHelper.getResourceAsStream('dtd/' + systemId.split('/').last()))} catch (e) {null}}] as EntityResolver
     */

    /**
     * Convert XML string to object implementing DeferredNode.
     * @param xml String
     * @return org.w3c.dom.Element The document element.
     */
    static Element asElement(final String xml) {
        final Document document = DOMBuilder.parse(new StringReader(xml))
        document.documentElement
    }

    /**
     *
     * @param arg
     * @return org.w3c.dom.Element The document element.
     */
    static Element asElement(final Object arg) {
        if (arg instanceof String || arg instanceof GString) {
            final String xmlString = new StreamingMarkupBuilder().bind {
                mkp.yieldUnescaped arg
            }.toString()
            DOMBuilder.parse(new StringReader(xmlString)).documentElement
        } else {
            null
        }
    }

    /**
     * Convert XML string to object implementing DeferredNode.
     * @param xml String
     * @return org.w3c.dom.Element The document element.
     */
    static Element asElement(final List<String> xml) {
        asElement(xml.join(''))
    }

    /**
     *
     * @param arg
     * @return org.w3c.dom.Element The document element.
     */
    static Element asElement(final NodeChild arg) {
        final String xmlString = new StreamingMarkupBuilder().bind {
            mkp.yield arg
        }.toString()
        DOMBuilder.parse(new StringReader(xmlString)).documentElement
    }

    /**
     * Convert XML to string.
     * Uses StreamingMarkupBuilder to generate XML with correct german umlauts.
     * @param b
     * @return
     */
    static String asString(final byte[] b) {
        final XmlSlurper parser = new XmlSlurper()
        final Writer writer = new StringWriter()
        final GPathResult root = parser.parse(new ByteArrayInputStream(b))
        new XmlNodePrinter(new PrintWriter(writer)).print(root)
        final String xml = new StreamingMarkupBuilder().bind {
            odisee { mkp.yieldUnescaped writer.toString() }
        }.toString()
        xml
    }

    /**
     * Convert XML to string.
     * Uses StreamingMarkupBuilder to generate XML with correct german umlauts.
     * @param str
     * @return
     */
    static String asString(final String str) {
        toString(str.bytes)
    }

    /**
     * Convert XML to string.
     * Uses StreamingMarkupBuilder to generate XML with correct german umlauts.
     * @param requestXML
     * @return
     */
    static String asString(final DeferredNode requestXML) {
        //String h = XmlUtil.serialize(requestXML).split(S_NEWLINE)[0..-1].join(S_NEWLINE)
        final String h = XmlUtil.serialize(requestXML) - ~'<\\?xml.*?>'
        def builder = new StreamingMarkupBuilder()
        //builder.encoding = 'UTF-8'
        final String xml = builder.bind {
            odisee { mkp.yieldUnescaped h }
        }.toString()
        xml
    }

    /**
     *
     * @param xml
     */
    static String asString(final NodeChild xml) {
        new StreamingMarkupBuilder().bind {
            odisee {
                mkp.yieldUnescaped xml
            }
        }.toString()
    }

    /**
     * Parse POST body: can be just text or gzip'ed stream.
     * Does not use request.XML as it relies on HTTP request headers.
     */
    static Element convertToXmlElement(final InputStream inputStream) {
        final List<String> lines = inputStream.readLines('UTF-8')
        final Element xml = XmlHelper.asElement(lines)
        xml
    }

}
