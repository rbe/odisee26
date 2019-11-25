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

class Document {

    /**
     * Date of creation.
     */
    Date dateCreated

    /**
     * Date of last update.
     */
    Date lastUpdated

    /**
     * Name of this document.
     */
    String name

    /**
     * Is this a template?
     */
    boolean template

    /**
     * Revision of document.
     */
    long revision

    /**
     * This document is an instance of another -- a template.
     */
    String instanceOfName

    /**
     * This document is an instance of a certain revision of another document -- a template.
     */
    long instanceOfRevision

    /**
     * The type.
     */
    MimeType mimeType

    /**
     * Original filename.
     */
    String filename

    /**
     * The extension (automatically extracted from filename).
     */
    String extension

    /**
     * The Odisee request.
     */
    String odiseeXmlRequest

    /**
     * The binary data.
     */
    byte[] bytes

    /**
     * Size of data.
     */
    int size

    def beforeInsert = {
        check()
    }

    def beforeUpdate = {
        throw new IllegalStateException('Updates prohibited')
    }

    private void check() {
        if (!name && filename) {
            name = filename
        }
        try {
            extension = filename?.split("\\.").toList().last() // TODO Throw error when there's no extension?
        } catch (e) {
            // ignore
        }
        size = bytes.length
        // Check if it's a template
        template = false
        ['template'].each {
            if (mimeType?.name?.indexOf(it) > -1 || mimeType?.browser?.indexOf(it) > -1) {
                template = true
            }
        }
    }

    def getBytes() {
        bytes
    }

    def getSize() {
        size
    }

    /**
     * Constraints.
     */
    static constraints = {
        dateCreated(nullable: true, editable: false)
        lastUpdated(nullable: true, editable: false)
        name(nullable: true)
        template(nullable: true)
        revision(nullable: true, editable: false)
        instanceOfName(nullable: true, editable: false)
        instanceOfRevision(nullable: true, editable: false)
        filename(nullable: true, editable: false)
        extension(nullable: true, editable: false)
        mimeType(nullable: true)
        odiseeXmlRequest(nullable: true, editable: false)
        //data(nullable: false)
    }

}
