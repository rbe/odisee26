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

import com.sun.star.beans.XPropertySet
import com.sun.star.container.XIndexAccess
import com.sun.star.uno.UnoRuntime

/**
 * Basic work with UNO.
 * TODO Think about UnoCategory at http://wiki.services.openoffice.org/wiki/API/Samples/Groovy/Office/RuntimeDialog
 */
class UnoCategory {

    /**
     *
     * @param unoObj
     * @param clazz
     * @return
     */
    static Object uno(Object unoObj, Class clazz) {
        UnoRuntime.queryInterface(clazz, unoObj)
    }

    /**
     *
     * @param propertySet
     * @param pname
     * @return
     */
    static Object getAt(XPropertySet propertySet, String pname) {
        propertySet.getPropertyValue(pname)
    }

    /**
     *
     * @param propertySet
     * @param pname
     * @param newValue
     */
    static void putAt(XPropertySet propertySet, String pname, Object newValue) {
        propertySet.setPropertyValue(pname, newValue)
    }

    /**
     *
     * @param ndx
     * @param x
     * @return
     */
    static Object getAt(XIndexAccess ndx, Integer x) {
        ndx.getByIndex(x)
    }

}
