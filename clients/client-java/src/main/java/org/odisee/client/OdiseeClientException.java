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

    public OdiseeClientException() {
        super();
    }

    public OdiseeClientException(String message) {
        super(message);
    }

    public OdiseeClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public OdiseeClientException(Throwable cause) {
        super(cause);
    }

    protected OdiseeClientException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
