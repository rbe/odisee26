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
package org.odisee.document

import jakarta.servlet.http.HttpServletResponse
import java.security.SecureRandom

final class DocumentStreamer {

    private static final SecureRandom RANDOM = new SecureRandom()

    /**
     * Stream a Document or just bytes to client.
     */
    static void stream(final HttpServletResponse response, final Document document) {
        String contentFilename = document.filename
        if (!contentFilename) {
            contentFilename = String.format('Odisee_%d', RANDOM.nextInt(Integer.MAX_VALUE))
        }
        response.contentType = DocumentAnalyzer.guessContentType(contentFilename)
        response.setHeader('Content-Disposition', "inline; filename=${contentFilename}")
        if (document.size) {
            response.contentLength = document.size
        }
        if (document.bytes?.length > 0) {
            response.contentLength = document.bytes.length
        }
        response.outputStream << document.bytes
        response.outputStream.flush()
    }

}
