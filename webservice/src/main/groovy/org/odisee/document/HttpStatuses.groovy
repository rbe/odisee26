/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import groovy.json.JsonException
import org.odisee.api.OdiseeException
import org.odisee.ooo.connection.OdiseeServerException
import org.xml.sax.SAXException

import java.util.zip.ZipException

/**
 * HTTP status and body for a failed generation.
 * Client errors are 400 (the request itself) or 422 (an instruction the schema allowed).
 * This is the only place that chooses the status.
 */
final class HttpStatuses {

    private HttpStatuses() {
    }

    static int statusFor(Throwable throwable) {
        Throwable current = throwable
        while (current != null) {
            if (current instanceof OdiseeServerException) {
                return 503
            }
            if (current instanceof OdiseeException) {
                return ((OdiseeException) current).httpStatus ?: OdiseeException.BAD_REQUEST
            }
            if (current instanceof SAXException || current instanceof JsonException || current instanceof ZipException) {
                return OdiseeException.BAD_REQUEST
            }
            current = current.cause
        }
        return OdiseeException.BAD_REQUEST
    }

    static void apply(Object response, Throwable throwable) {
        String msg = throwable?.message
        response.reset()
        response.status = statusFor(throwable)
        if (msg != null) {
            response.outputStream << String.format('%s%n', msg)
        }
        response.outputStream.flush()
    }

}
