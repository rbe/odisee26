/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 31.08.14, 14:50
 */

package eu.artofcoding.odisee.server;

public class OdiseeServerRuntimeException extends RuntimeException {

    public OdiseeServerRuntimeException() {
    }

    public OdiseeServerRuntimeException(String message) {
        super(message);
    }

    public OdiseeServerRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public OdiseeServerRuntimeException(Throwable cause) {
        super(cause);
    }

/*
    public OdiseeServerRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
*/

}
