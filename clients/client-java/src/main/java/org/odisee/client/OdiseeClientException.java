/*
 * odisee-client-java
 * odisee-client-java
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 14.01.13 11:15
 */

package org.odisee.client;

public final class OdiseeClientException extends RuntimeException {

    private final int httpStatus;

    public OdiseeClientException() {
        this.httpStatus = 0;
    }

    public OdiseeClientException(String message) {
        super(message);
        this.httpStatus = 0;
    }

    public OdiseeClientException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
    }

    public OdiseeClientException(Throwable cause) {
        super(cause);
        this.httpStatus = 0;
    }

    /**
     * @param httpStatus the HTTP status from the service, or {@code 0} when the call did not get one
     */
    public OdiseeClientException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    protected OdiseeClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
        this.httpStatus = 0;
    }

    /**
     * HTTP status from the service. {@code 0} when the failure was not an HTTP response.
     */
    public int getHttpStatus() {
        return httpStatus;
    }

}
