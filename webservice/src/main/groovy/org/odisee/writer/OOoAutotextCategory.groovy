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
import com.sun.star.lang.XMultiComponentFactory
import com.sun.star.text.XAutoTextEntry
import com.sun.star.text.XAutoTextGroup
import com.sun.star.text.XBookmarksSupplier
import com.sun.star.text.XText
import com.sun.star.text.XTextContent
import com.sun.star.text.XTextCursor
import com.sun.star.text.XTextDocument
import com.sun.star.text.XTextRange
import com.sun.star.uno.Any
import com.sun.star.uno.XComponentContext
import groovy.util.logging.Log
import org.odisee.api.OdiseeException
import org.odisee.ooo.connection.OfficeConnection
import org.odisee.uno.UnoCategory

/**
 * Writer Autotexts.
 */
@Log
class OOoAutotextCategory {

    private static Object unwrapAny(Object value) {
        value instanceof Any ? ((Any) value).object : value
    }

    /**
     * Get an autotext.
     */
    static XAutoTextEntry getAutotext(XComponent component, String autotextGroup, String autotext) {
        use(UnoCategory) {
            OfficeConnection connection = OOoDocumentCategory.getOooConnection(component)
            if (connection == null) {
                throw new OdiseeException("Cannot load autotext '${autotextGroup}.${autotext}': no office connection", OdiseeException.UNPROCESSABLE)
            }
            XMultiComponentFactory serviceManager = OOoDocumentCategory.remoteServiceManager(connection)
            XComponentContext componentContext = OOoDocumentCategory.remoteComponentContext(connection)
            if (serviceManager == null || componentContext == null) {
                throw new OdiseeException("Cannot load autotext '${autotextGroup}.${autotext}': office context is not available", OdiseeException.UNPROCESSABLE)
            }
            Object oAutotextContainer = serviceManager.createInstanceWithContext('com.sun.star.text.AutoTextContainer', componentContext)
            XNameAccess groups = oAutotextContainer == null ? null : (XNameAccess) oAutotextContainer.uno(XNameAccess)
            if (groups == null) {
                throw new OdiseeException("Autotext group '${autotextGroup}' was not found", OdiseeException.UNPROCESSABLE)
            }
            XAutoTextGroup xAutotextGroup
            try {
                Object rawGroup = unwrapAny(groups.getByName(autotextGroup))
                xAutotextGroup = rawGroup == null ? null : (XAutoTextGroup) rawGroup.uno(XAutoTextGroup)
            } catch (NoSuchElementException e) {
                log.log(java.util.logging.Level.WARNING, "Could not find autotext group ${autotextGroup}", e)
                OdiseeException ex = new OdiseeException("Autotext group '${autotextGroup}' was not found", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
            if (xAutotextGroup == null) {
                throw new OdiseeException("Autotext group '${autotextGroup}' was not found", OdiseeException.UNPROCESSABLE)
            }
            XNameAccess entries = (XNameAccess) xAutotextGroup.uno(XNameAccess)
            if (entries == null) {
                throw new OdiseeException("Autotext '${autotextGroup}.${autotext}' was not found", OdiseeException.UNPROCESSABLE)
            }
            XAutoTextEntry autoTextEntry
            try {
                Object rawEntry = unwrapAny(entries.getByName(autotext))
                autoTextEntry = rawEntry == null ? null : (XAutoTextEntry) rawEntry.uno(XAutoTextEntry)
            } catch (NoSuchElementException e) {
                log.log(java.util.logging.Level.WARNING, "Cannot find autotext ${autotextGroup}.${autotext}", e)
                OdiseeException ex = new OdiseeException("Autotext '${autotextGroup}.${autotext}' was not found", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
            if (autoTextEntry == null) {
                throw new OdiseeException("Autotext '${autotextGroup}.${autotext}' was not found", OdiseeException.UNPROCESSABLE)
            }
            autoTextEntry
        } as XAutoTextEntry
    }

    /**
     * Insert an autotext at a bookmark.
     */
    static void insertAutotextAtBookmark(XComponent component, String autotextGroup, String autotext, String bookmark) {
        XAutoTextEntry theAutotext = getAutotext(component, autotextGroup, autotext)
        use(UnoCategory) {
            XBookmarksSupplier xBookmarksSupplier = component == null ? null : (XBookmarksSupplier) component.uno(XBookmarksSupplier)
            XNameAccess bookmarks = xBookmarksSupplier?.bookmarks
            if (bookmarks == null) {
                throw new OdiseeException("Bookmark '${bookmark}' was not found", OdiseeException.UNPROCESSABLE)
            }
            XTextContent textContent
            try {
                Object rawBookmark = unwrapAny(bookmarks.getByName(bookmark))
                textContent = rawBookmark == null ? null : (XTextContent) rawBookmark.uno(XTextContent)
            } catch (NoSuchElementException e) {
                log.info "Could not find bookmark ${bookmark}"
                OdiseeException ex = new OdiseeException("Bookmark '${bookmark}' was not found", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
            XTextRange anchor = textContent?.anchor
            if (anchor == null) {
                throw new OdiseeException("Bookmark '${bookmark}' was not found", OdiseeException.UNPROCESSABLE)
            }
            theAutotext.applyTo(anchor)
        }
    }

    /**
     * Insert an autotext at the end of the document.
     */
    static void insertAutotextAtEnd(XComponent component, String autotextGroup, String autotext) {
        XAutoTextEntry theAutotext = getAutotext(component, autotextGroup, autotext)
        use(UnoCategory) {
            XTextDocument xTextDocument = component == null ? null : (XTextDocument) component.uno(XTextDocument)
            XText sup = xTextDocument?.text
            if (sup == null) {
                throw new OdiseeException("Autotext '${autotextGroup}.${autotext}' requires a Writer document", OdiseeException.UNPROCESSABLE)
            }
            XTextCursor cursor = sup.createTextCursor()
            cursor.gotoEnd(false)
            theAutotext.applyTo(cursor)
        }
    }

}
