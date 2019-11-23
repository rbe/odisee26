/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 16.03.17, 19:41
 */

package eu.artofcoding.odisee.document

final class DocumentAnalyzer {

    static String guessContentType(final String contentName) {
        String contentType
        switch (contentName) {
            case { it.endsWith('.odt') }:
                contentType = MimeTypeConstant.MIME_TYPE_ODT
                break
            case { it.endsWith('.pdf') }:
                contentType = MimeTypeConstant.MIME_TYPE_PDF
                break
            case { it.endsWith('.docx') }:
                contentType = MimeTypeConstant.MIME_TYPE_WORDXML
                break
            case { it.endsWith('.doc') }:
                contentType = MimeTypeConstant.MIME_TYPE_WORD97
                break
            default:
                contentType = MimeTypeConstant.MIME_TYPE_OCTET_STREAM
        }
        contentType
    }

}
