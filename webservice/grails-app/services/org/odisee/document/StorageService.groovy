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
package org.odisee.document

import org.odisee.io.FileHelper
import org.odisee.lang.NameHelper

import java.nio.file.Path
import java.nio.file.Paths

class StorageService {

    static scope = 'singleton'

    /**
     * Create a document with 'name' from path. If the document does not exist, revision 1 is created
     * otherwise the revision is incremented by 1.
     * @param arg A map
     * @return Instance of domain class Document
     */
    Document createDocument(final Map arg) {
        // Got filename?
        if (!arg.filename) {
            if (arg.data instanceof Path) {
                Path p = (Path) arg.data
                arg.filename = p.fileName.toString()
            } else if (arg.data instanceof String) {
                arg.data = Paths.get(arg.data)
                arg.filename = arg.data.name
            } else {
                arg.filename = 'unknown'
            }
        }
        // Load data depending on type?
        if (!arg.bytes) {
            if (arg.data instanceof String || arg.data instanceof Path) {
                arg.bytes = FileHelper.fromFile(arg.data)
            } else if (arg.data instanceof InputStream) {
                arg.bytes = arg.data.readBytes()
            } else if (arg.data instanceof byte[]) {
                arg.bytes = arg.data
            }
        }
        // Create new Document
        if (arg.bytes) {
            final Document document = new Document()
            document.name = NameHelper.getName(arg.filename)
            document.filename = arg.filename
            document.instanceOfName = arg.instanceOf?.name
            //document.instanceOfRevision = arg.instanceOf?.revision?.toLong()
            document.odiseeXmlRequest = arg.odiseeRequest
            document.bytes = arg.bytes
            document
        } else {
            log.error 'Failed to create instance of Document, got no bytes'
            null
        }
    }

    Document createDocument(final String documentName, final String odiseeXmlRequest, final byte[] documentData) {
        final Document document = new Document()
        document.name = documentName
        document.odiseeXmlRequest = odiseeXmlRequest
        document.bytes = documentData
        document
    }

}
