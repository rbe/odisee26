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
package org.odisee.writer

import org.odisee.api.OdiseeException
import org.odisee.uno.UnoCategory

/**
 * Writer Autotexts.
 */
class OOoAutotextCategory {

    /**
     * Get an autotext.
     */
    static com.sun.star.text.XAutoTextEntry getAutotext(com.sun.star.lang.XComponent component, String autotextGroup, String autotext) {
        use(UnoCategory) {
            // Create service
            def oAutotextContainer = component.oooConnection.xMultiComponentFactory.createInstanceWithContext('com.sun.star.text.AutoTextContainer', component.oooConnection.xOfficeComponentContext)
            // Get autotext group: com.sun.star.text.XAutoTextGroup
            def xAutotextGroup = null
            try {
                xAutotextGroup = oAutotextContainer.uno(com.sun.star.container.XNameAccess).getByName(autotextGroup)
            } catch (com.sun.star.container.NoSuchElementException e) {
                println "Odisee: Could not find autotext group ${autotextGroup}"
            }
            if (xAutotextGroup) {
                try {
                    // Get autotext entry: com.sun.star.text.XAutoTextEntry
                    def theAutotext = xAutotextGroup.uno(com.sun.star.container.XNameAccess).getByName(autotext).uno(com.sun.star.text.XAutoTextEntry)
                    return theAutotext
                } catch (e) {
                    throw new OdiseeException("Cannot find autotext ${autotextGroup}.${autotext}", e)
                }
            }
        }
    }

    /**
     * Insert an autotext at a bookmark.
     *
     * Sub Main
     *     oDoc = ThisComponent
     *     oText = oDoc.Text
     *     oTextCursor = oText.CreateTextCursor
     *     oTextCursor.gotoEnd(False)
     *     oAutoTextContainer = getProcessServiceManager().createInstance("com.sun.star.text.AutoTextContainer")
     *     oGroup = oAutoTextContainer.getByName("standard")
     *     oEntry = oGroup.getByName("mfg")
     *     oEntry.applyTo(oTextCursor)
     * End Sub
     */
    static void insertAutotextAtBookmark(com.sun.star.lang.XComponent component, String autotextGroup, String autotext, String bookmark) {
        com.sun.star.text.XAutoTextEntry theAutotext = component.getAutotext(autotextGroup, autotext)
        if (theAutotext) {
            use(UnoCategory) {
                try {
                    // Goto bookmark and insert text
                    def bm = component.uno(com.sun.star.text.XBookmarksSupplier).bookmarks.getByName(bookmark).uno(com.sun.star.text.XTextContent)
                    // Apply autotext at cursor position
                    def anchor = bm.anchor
                    theAutotext.applyTo(anchor)
                } catch (com.sun.star.container.NoSuchElementException e) {
                    println "Odisee: Could not find bookmark ${bookmark}"
                }
            }
        }
    }

    /**
     * Insert an autotext at the end of the document.
     */
    static void insertAutotextAtEnd(com.sun.star.lang.XComponent component, String autotextGroup, String autotext) {
        com.sun.star.text.XAutoTextEntry theAutotext = component.getAutotext(autotextGroup, autotext)
        if (theAutotext) {
            use(UnoCategory) {
                // Create cursor and put it at end of document
                /*com.sun.star.text.XText*/
                def sup = component.uno(com.sun.star.text.XTextDocument).text
                /*com.sun.star.text.XTextCursor*/
                def cur = sup.createTextCursor()
                cur.gotoEnd(false)
                // Apply autotext at cursor position
                theAutotext.applyTo(cur)
            }
        }
    }

}
