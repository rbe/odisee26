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
 * One generation call owns one of these maps. The document list accumulates
 * every request inside that call and is not reused for the next HTTP request.
 */
final class RequestContext {

    private static final List<String> PER_REQUEST_KEYS = [
            'id', 'template', 'revision', 'documentName',
            'templateDir', 'templateFile', 'documentDir',
            'data', 'bytes', 'filename', 'result'
    ]

    private RequestContext() {
    }

    static Map<String, Object> create() {
        [
                principal      : null,
                xml            : null,
                activeIndex    : -1,
                uniqueRequestId: '',
                requestDir     : null,
                odiseeRequest  : null,
                documentName   : null,
                templateDir    : null,
                documentDir    : null,
                templateFile   : null,
                id             : null,
                template       : null,
                revision       : null,
                document       : [],
                data           : null,
                bytes          : null,
                filename       : null,
                result         : []
        ]
    }

    /**
     * Clear fields that belong to a single {@code <request>} inside one XML body.
     * Documents already produced stay on the map.
     */
    static void resetForNextRequest(Map<String, Object> arg) {
        PER_REQUEST_KEYS.each { arg.remove(it) }
    }

}
