/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 09.11.14, 12:04
 */

package eu.artofcoding.odisee.server;

public class OdiseeServerException extends Exception {

    public OdiseeServerException(String message) {
        super(message);
    }

    public OdiseeServerException(String message, Throwable cause) {
        super(message, cause);
    }

    public OdiseeServerException(Throwable cause) {
        super(cause);
    }

}
