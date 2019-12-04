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

import com.sun.star.container.NoSuchElementException
import com.sun.star.container.XNameAccess
import com.sun.star.lang.XComponent
import com.sun.star.text.AutoTextContainer
import com.sun.star.text.XAutoTextEntry
import com.sun.star.text.XAutoTextGroup
import com.sun.star.text.XBookmarksSupplier
import com.sun.star.text.XText
import com.sun.star.text.XTextContent
import com.sun.star.text.XTextCursor
import com.sun.star.text.XTextDocument
import com.sun.star.text.XTextRange
import groovy.util.logging.Log
import org.odisee.uno.UnoCategory

/**
 * Writer Autotexts.
 */
@Log
class OOoAutotextCategory {

    /**
     * Get an autotext.
     */
    static XAutoTextEntry getAutotext(XComponent component, String autotextGroup, String autotext) {
        use(UnoCategory) {
            // Create service
            def connection = component.oooConnection
            AutoTextContainer oAutotextContainer = connection.xMultiComponentFactory
                    .createInstanceWithContext('com.sun.star.text.AutoTextContainer',
                            connection.xOfficeComponentContext)
            XAutoTextGroup xAutotextGroup = null
            try {
                XNameAccess xNameAccess = oAutotextContainer.uno(XNameAccess)
                xAutotextGroup = (XAutoTextGroup) xNameAccess.getByName(autotextGroup)
            } catch (NoSuchElementException e) {
                log.error "Could not find autotext group ${autotextGroup}"
            }
            if (xAutotextGroup) {
                try {
                    // Get autotext entry: com.sun.star.text.XAutoTextEntry
                    XNameAccess xNameAccess = xAutotextGroup.uno(XNameAccess)
                    XAutoTextEntry autoTextEntry = xNameAccess.getByName(autotext)
                    return autoTextEntry.uno(XAutoTextEntry)
                } catch (NoSuchElementException e) {
                    log.error "Cannot find autotext ${autotextGroup}.${autotext}", e
                }
            }
        } as XAutoTextEntry
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
    static void insertAutotextAtBookmark(XComponent component, String autotextGroup, String autotext, String bookmark) {
        XAutoTextEntry theAutotext = component.getAutotext(autotextGroup, autotext)
        if (theAutotext) {
            use(UnoCategory) {
                try {
                    // Goto bookmark and insert text
                    XBookmarksSupplier xBookmarksSupplier = component.uno(XBookmarksSupplier)
                    XNameAccess bookmarks = xBookmarksSupplier.bookmarks
                    XTextContent textContent = bookmarks.getByName(bookmark).uno(XTextContent)
                    // Apply autotext at cursor position
                    XTextRange anchor = textContent.anchor
                    theAutotext.applyTo(anchor)
                } catch (NoSuchElementException e) {
                    log.info "Could not find bookmark ${bookmark}"
                }
            }
        }
    }

    /**
     * Insert an autotext at the end of the document.
     */
    static void insertAutotextAtEnd(XComponent component, String autotextGroup, String autotext) {
        XAutoTextEntry theAutotext = component.getAutotext(autotextGroup, autotext)
        if (theAutotext) {
            use(UnoCategory) {
                // Create cursor and put it at end of document
                XTextDocument xTextDocument = component.uno(XTextDocument)
                XText sup = xTextDocument.text
                XTextCursor cursor = sup.createTextCursor()
                cursor.gotoEnd(false)
                // Apply autotext at cursor position
                theAutotext.applyTo(cursor)
            }
        }
    }

}
