/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import org.odisee.api.OdiseeException
import org.odisee.xml.XmlHelper
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node
import org.xml.sax.SAXException

import javax.xml.XMLConstants
import javax.xml.transform.dom.DOMSource
import javax.xml.validation.Schema
import javax.xml.validation.SchemaFactory
import javax.xml.validation.Validator

/**
 * The request the server accepts is the v2 schema.
 * {@code template/@outputFormat} selects the format when {@code output} is absent.
 * {@code output/format/@type} selects the format when it is present.
 * v3 and v2.6 namespaces are rejected. A document with no namespace is the same v2 shape.
 */
final class RequestSchema {

    static final String V2 = 'http://xmlns.odisee.de/v2/request'

    static final String V3 = 'http://xmlns.odisee.de/v3/request'

    static final String V26 = 'http://xmlns.odisee.org/v26/request'

    private static final Schema SCHEMA = loadSchema()

    private RequestSchema() {
    }

    static Element parse(byte[] body) {
        if (body == null || body.length == 0) {
            throw new OdiseeException('Invalid or missing XML request', OdiseeException.BAD_REQUEST)
        }
        final Document document
        try {
            document = XmlHelper.parseDocument(new ByteArrayInputStream(body))
        } catch (SAXException e) {
            throw new OdiseeException(parseMessage(e), OdiseeException.BAD_REQUEST)
        } catch (IOException e) {
            throw new OdiseeException('Invalid or missing XML request', e)
        }
        final Element root = document?.documentElement
        if (root == null) {
            throw new OdiseeException('Invalid or missing XML request', OdiseeException.BAD_REQUEST)
        }
        validate(root)
        copyWithoutNamespace(root)
    }

    static void validate(Element root) {
        if (root == null) {
            throw new OdiseeException('Invalid or missing XML request', OdiseeException.BAD_REQUEST)
        }
        final String local = root.localName ?: root.nodeName
        if ('odisee' != local) {
            throw new OdiseeException("Request root '${local}' is not accepted. Use the v2 request schema.", OdiseeException.BAD_REQUEST)
        }
        final String namespace = root.namespaceURI
        if (V3 == namespace) {
            throw new OdiseeException('The v3 request schema is not accepted. Set template/@outputFormat (v2).', OdiseeException.BAD_REQUEST)
        }
        if (V26 == namespace) {
            throw new OdiseeException('The v2.6 request schema is not accepted. Use the v2 request schema.', OdiseeException.BAD_REQUEST)
        }
        if (namespace && V2 != namespace) {
            throw new OdiseeException("Request namespace '${namespace}' is not accepted. Use the v2 request schema.", OdiseeException.BAD_REQUEST)
        }
        try {
            final Validator validator = SCHEMA.newValidator()
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, '')
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, '')
            validator.validate(new DOMSource(copyInNamespace(root, V2)))
        } catch (SAXException e) {
            throw new OdiseeException('Invalid request: ' + e.message, OdiseeException.BAD_REQUEST)
        } catch (IOException e) {
            throw new OdiseeException('Invalid request', e)
        }
    }

    private static String parseMessage(SAXException error) {
        final String message = error.message ?: ''
        if (message.toLowerCase().contains('doctype')) {
            return 'Invalid request: a DOCTYPE is not accepted'
        }
        message ? 'Invalid request: ' + message : 'Invalid or missing XML request'
    }

    private static Schema loadSchema() {
        final SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, '')
        factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, '')
        final URL resource = RequestSchema.class.getResource('/xml/v2/request.xsd')
        if (resource == null) {
            throw new IllegalStateException('v2 request schema is not on the classpath')
        }
        try {
            return factory.newSchema(resource)
        } catch (SAXException e) {
            throw new IllegalStateException('v2 request schema cannot be loaded', e)
        }
    }

    private static Element copyWithoutNamespace(Element root) {
        Document copy = XmlHelper.newDocument()
        Element stripped = copyElement(copy, root, null)
        copy.appendChild(stripped)
        stripped
    }

    private static Document copyInNamespace(Element root, String namespace) {
        Document copy = XmlHelper.newDocument()
        copy.appendChild(copyElement(copy, root, namespace))
        copy
    }

    private static Element copyElement(Document document, Element source, String namespace) {
        String local = source.localName ?: source.nodeName
        Element copy = namespace == null ? document.createElement(local) : document.createElementNS(namespace, local)
        NamedNodeMap attributes = source.attributes
        for (int i = 0; i < attributes.length; i++) {
            Node attribute = attributes.item(i)
            String attributeName = attribute.nodeName
            if ('http://www.w3.org/2000/xmlns/' == attribute.namespaceURI
                    || 'xmlns' == attributeName
                    || attributeName.startsWith('xmlns:')) {
                continue
            }
            if ('http://www.w3.org/2001/XMLSchema-instance' == attribute.namespaceURI) {
                continue
            }
            if (attribute.namespaceURI == null) {
                copy.setAttribute(attributeName, attribute.nodeValue)
            }
        }
        Node child = source.firstChild
        while (child != null) {
            if (child.nodeType == Node.ELEMENT_NODE) {
                copy.appendChild(copyElement(document, (Element) child, namespace))
            } else if (child.nodeType == Node.TEXT_NODE || child.nodeType == Node.CDATA_SECTION_NODE) {
                copy.appendChild(document.createTextNode(child.nodeValue))
            }
            child = child.nextSibling
        }
        copy
    }

}
