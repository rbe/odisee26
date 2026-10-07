/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

/**
 * v2 requests put the extension on {@code template/@outputFormat} (comma separated).
 * v3 requests put it on {@code output/format/@type}. The attribute wins when it is non-blank.
 */
final class OutputFormats {

    private OutputFormats() {
    }

    static List<String> fromRequest(request) {
        List<String> formats = []
        def template = request?.template ? request.template[0] : null
        splitAttribute(textOf(template?.'@outputFormat')).each { formats << it }
        if (!formats) {
            request?.output?.format?.each { format ->
                String type = textOf(format?.'@type')
                if (type) {
                    formats << type
                }
            }
        }
        formats
    }

    private static List<String> splitAttribute(String attribute) {
        if (!attribute) {
            return []
        }
        attribute.split(',').collect { it.trim() }.findAll { it }
    }

    private static String textOf(value) {
        if (value == null) {
            return ''
        }
        value.toString().trim()
    }

}
