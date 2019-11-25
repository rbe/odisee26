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

class MimeType {

    /**
     * Name of this mime type.
     */
    String name

    /**
     * Filename extension.
     */
    String extension

    /**
     * Representation of this mime type when sending from a web server.
     */
    String browser

    /**
     * Description.
     */
    String description

    static mapping = {
        //table 'MIMETYPE'
    }

    static constraints = {
        name(nullable: false)
        extension(nullable: true, unique: true)
        browser(nullable: true)
        description(nullable: true)
    }

}
