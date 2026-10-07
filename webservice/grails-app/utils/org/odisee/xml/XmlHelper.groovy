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

import groovy.xml.XmlNodePrinter
import groovy.xml.XmlSlurper
import groovy.xml.slurpersupport.GPathResult
import groovy.xml.slurpersupport.NodeChild
import groovy.xml.StreamingMarkupBuilder
import groovy.xml.XmlUtil
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.EntityResolver
import org.xml.sax.InputSource
import org.xml.sax.SAXException

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.parsers.ParserConfigurationException

final class XmlHelper {

    private static final String S_NEWLINE = '\n'

    private static final DocumentBuilderFactory SECURE_FACTORY = secureFactory()

    private XmlHelper() {
        throw new AssertionError();
    }

    /**
     * A parser that does not read a DTD or an external entity.
     * A {@code DOCTYPE} is rejected.
     */
    static Document parseDocument(final String xml) throws SAXException, IOException {
        parseDocument(new StringReader(xml))
    }

    static Document parseDocument(final Reader reader) throws SAXException, IOException {
        parse(new InputSource(reader))
    }

    static Document parseDocument(final InputStream input) throws SAXException, IOException {
        parse(new InputSource(input))
    }

    static Document newDocument() {
        try {
            return SECURE_FACTORY.newDocumentBuilder().newDocument()
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException('Cannot build an XML document', e)
        }
    }

    private static Document parse(final InputSource source) throws SAXException, IOException {
        try {
            final DocumentBuilder builder = SECURE_FACTORY.newDocumentBuilder()
            return builder.parse(source)
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException('Cannot build an XML parser', e)
        }
    }

    private static DocumentBuilderFactory secureFactory() {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance()
        factory.setNamespaceAware(true)
        factory.setXIncludeAware(false)
        factory.setExpandEntityReferences(false)
        setFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true)
        setFeature(factory, 'http://apache.org/xml/features/disallow-doctype-decl', true)
        setFeature(factory, 'http://xml.org/sax/features/external-general-entities', false)
        setFeature(factory, 'http://xml.org/sax/features/external-parameter-entities', false)
        setFeature(factory, 'http://apache.org/xml/features/nonvalidating/load-external-dtd', false)
        factory
    }

    private static void setFeature(final DocumentBuilderFactory factory, final String name, final boolean value) {
        try {
            factory.setFeature(name, value)
        } catch (ParserConfigurationException ignored) {
            // A parser that does not know one feature still has the others.
        }
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
     * Convert an XML string to a DOM element.
     * @param xml String
     * @return org.w3c.dom.Element The document element.
     */
    static Element asElement(final String xml) {
        parseDocument(xml).documentElement
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
            parseDocument(xmlString).documentElement
        } else {
            null
        }
    }

    /**
     * Convert an XML string to a DOM element.
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
        parseDocument(xmlString).documentElement
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
    static String asString(final org.w3c.dom.Node requestXML) {
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
    static String asString(final GPathResult xml) {
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
