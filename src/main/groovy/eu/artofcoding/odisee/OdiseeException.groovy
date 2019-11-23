/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 31.08.14 14:50
 */
package eu.artofcoding.odisee

/**
 * The one and only exception in Odisee.
 */
class OdiseeException extends RuntimeException {

    OdiseeException() {
    }

    OdiseeException(Throwable throwable) {
        super(throwable)
    }

    OdiseeException(String s, Throwable throwable, boolean b, boolean b1) {
        super(s, throwable, b, b1)
    }

    OdiseeException(String message) {
        super(message)
    }

    OdiseeException(String message, Throwable throwable) {
        super(message, throwable)
    }

}
