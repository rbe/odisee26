/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 02.02.15 18:35
 */
package eu.artofcoding.odisee.ooo

/**
 * Writer Autotexts.
 */
class OOoCursorCategory {

    /**
     * Get an autotext.
     */
    static com.sun.star.text.XTextCursor getTextCursor(com.sun.star.lang.XComponent component) {
        use(UnoCategory) {
            com.sun.star.text.XText sup = component.uno(com.sun.star.text.XTextDocument).text
            /*com.sun.star.text.XTextCursor*/ sup.createTextCursor()
        }
    }

}
