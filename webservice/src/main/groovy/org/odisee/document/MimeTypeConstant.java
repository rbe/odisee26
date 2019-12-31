/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 17.03.17, 07:28
 */

package org.odisee.document;

final class MimeTypeConstant {

    private MimeTypeConstant() {
        throw new AssertionError();
    }

    public static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";

    public static final String MIME_TYPE_ODT = "application/vnd.oasis.opendocument.text";

    public static final String MIME_TYPE_WORD97 = "application/msword";

    public static final String MIME_TYPE_WORDXML = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public static final String MIME_TYPE_PDF = "application/pdf";

}
