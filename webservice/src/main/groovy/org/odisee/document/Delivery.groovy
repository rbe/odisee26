/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import groovy.json.JsonOutput
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.odisee.api.OdiseeException
import org.odisee.io.SafePaths
import org.w3c.dom.Element
import org.w3c.dom.NamedNodeMap
import org.w3c.dom.Node

import java.nio.charset.StandardCharsets

/**
 * {@code odisee/@delivery} is {@code stream}, {@code store}, or {@code both}.
 * Absent means {@code stream}. The bucket is the one configured for the caller.
 */
final class Delivery {

    static final String STREAM = 'stream'

    static final String STORE = 'store'

    static final String BOTH = 'both'

    static final Set<String> MODES = [STREAM, STORE, BOTH] as Set

    /**
     * One header for {@code both}. The value is {@code bucket/key}. The key is the remainder after the first slash.
     */
    static final String OBJECT_HEADER = 'X-Odisee-Object'

    private static final Set<String> FORBIDDEN = [
            'bucket', 'endpoint', 'region', 'accesskey', 'secret', 'secretkey',
            'credentials', 'objectkey', 'key'
    ] as Set

    private Delivery() {
    }

    static final class StoredObject {
        final String bucket
        final String key

        StoredObject(String bucket, String key) {
            this.bucket = bucket
            this.key = key
        }
    }

    /**
     * A stream over the existing array. The constructor keeps that array; it does not copy it.
     */
    static final class SharedBytes extends ByteArrayInputStream {
        final byte[] bytes

        SharedBytes(byte[] bytes) {
            super(bytes)
            this.bytes = bytes
        }
    }

    static String mode(Element xml) {
        String value = xml?.getAttribute('delivery')
        if (value == null || value.isEmpty()) {
            return STREAM
        }
        if (!MODES.contains(value)) {
            throw new OdiseeException("Invalid delivery '${value}'", OdiseeException.BAD_REQUEST)
        }
        value
    }

    static void rejectNamedStorage(HttpServletRequest request) {
        if (request == null) {
            return
        }
        Enumeration<String> names = request.parameterNames
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement()
            if (!forbidden(name)) {
                continue
            }
            String value = request.getParameter(name)
            if (value != null && !value.trim().isEmpty()) {
                throw new OdiseeException('The request cannot name a bucket', OdiseeException.BAD_REQUEST)
            }
        }
    }

    static void rejectOverrides(Node node) {
        if (!(node instanceof Element)) {
            return
        }
        Element element = (Element) node
        String local = element.localName ?: element.nodeName
        if (forbidden(local)) {
            throw new OdiseeException('The request cannot name a bucket', OdiseeException.BAD_REQUEST)
        }
        NamedNodeMap attributes = element.attributes
        if (attributes != null) {
            for (int i = 0; i < attributes.length; i++) {
                Node attribute = attributes.item(i)
                if ('http://www.w3.org/2000/xmlns/' == attribute.namespaceURI) {
                    continue
                }
                String attributeName = attribute.localName ?: attribute.nodeName
                if (forbidden(attributeName)) {
                    throw new OdiseeException('The request cannot name a bucket', OdiseeException.BAD_REQUEST)
                }
            }
        }
        Node child = element.firstChild
        while (child != null) {
            rejectOverrides(child)
            child = child.nextSibling
        }
    }

    static void rejectKeys(Object value) {
        if (value instanceof Map) {
            ((Map) value).each { Object key, Object child ->
                if (forbidden(key?.toString())) {
                    throw new OdiseeException('The request cannot name a bucket', OdiseeException.BAD_REQUEST)
                }
                rejectKeys(child)
            }
        } else if (value instanceof Collection) {
            ((Collection) value).each { Object child -> rejectKeys(child) }
        }
    }

    static boolean forbidden(String name) {
        if (name == null) {
            return false
        }
        String norm = name.toLowerCase()
        int colon = norm.lastIndexOf(':')
        if (colon >= 0) {
            norm = norm.substring(colon + 1)
        }
        FORBIDDEN.contains(norm.replace('-', '').replace('_', ''))
    }

    static void respond(HttpServletResponse response, String user, Element xml, Document document, ObjectStorage storage) {
        String chosen = mode(xml)
        StoredObject stored = null
        if (chosen != STREAM) {
            stored = put(user, document, storage)
        }
        if (chosen == STORE) {
            writeStored(response, stored)
            return
        }
        if (stored != null) {
            response.setHeader(OBJECT_HEADER, headerValue(stored))
        }
        DocumentStreamer.stream(response, document)
    }

    static StoredObject put(String user, Document document, ObjectStorage storage) {
        if (document == null || document.bytes == null || document.bytes.length == 0) {
            throw new OdiseeException('Cannot store an empty document')
        }
        if (storage == null) {
            throw new OdiseeException('Object storage is not configured', OdiseeException.SERVER_ERROR)
        }
        BucketFile.Record record = BucketFile.require(user)
        String key = objectKey(document.filename)
        String type = DocumentAnalyzer.guessContentType(document.filename ?: 'document.bin')
        byte[] bytes = document.bytes
        storage.put(record, key, type, new SharedBytes(bytes), bytes.length)
        new StoredObject(record.bucket, key)
    }

    /**
     * The server chooses the key. A client key is not accepted. The key stays inside the user's bucket.
     */
    static String objectKey(String filename) {
        String name = filename == null ? '' : filename.trim()
        if (!name) {
            name = 'document.bin'
        }
        SafePaths.requireSimpleName(name, 'document name')
        UUID.randomUUID().toString() + '/' + name
    }

    static String headerValue(StoredObject stored) {
        stored.bucket + '/' + stored.key
    }

    private static void writeStored(HttpServletResponse response, StoredObject stored) {
        byte[] json = JsonOutput.toJson([bucket: stored.bucket, key: stored.key]).getBytes(StandardCharsets.UTF_8)
        response.status = 200
        response.contentType = 'application/json; charset=UTF-8'
        response.contentLength = json.length
        response.outputStream.write(json)
        response.outputStream.flush()
    }

}
