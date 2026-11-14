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
import com.sun.star.text.XBookmarksSupplier
import com.sun.star.text.XTextContent
import com.sun.star.uno.Any
import org.odisee.api.OdiseeException
import org.odisee.uno.UnoCategory

/**
 * Writer Bookmarks.
 */
class OOoBookmarkCategory {

    private static Object unwrapAny(Object value) {
        value instanceof Any ? ((Any) value).object : value
    }

    /**
     * Insert a graphic at a bookmark.
     */
    def static insertGraphicAtBookmark(com.sun.star.lang.XComponent component, String bookmark, java.io.File url) {
        use(OOoDocumentCategory) {
            component.executeOdiseeMacro("Graphic.insertGraphicAtBookmarkUsingDispatcher", [bookmark, url] as Object[])
        }
    }

    /**
     * Insert some text at a bookmark.
     */
    def static setTextAtBookmark(com.sun.star.lang.XComponent component, String bookmark, String text) {
        use(UnoCategory) {
            def bookmarks = component?.uno(XBookmarksSupplier)?.getBookmarks()
            if (bookmarks == null) {
                throw new OdiseeException("Bookmark '${bookmark}' was not found", OdiseeException.UNPROCESSABLE)
            }
            Object raw
            try {
                raw = unwrapAny(bookmarks.getByName(bookmark))
            } catch (NoSuchElementException e) {
                OdiseeException ex = new OdiseeException("Bookmark '${bookmark}' was not found", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
            XTextContent content = raw == null ? null : (XTextContent) raw.uno(XTextContent)
            if (content?.anchor == null) {
                throw new OdiseeException("Bookmark '${bookmark}' was not found", OdiseeException.UNPROCESSABLE)
            }
            content.getAnchor().setString(text)
        }
    }

    /**
     * Get text/a paragraph from a bookmark.
     */
    def static getParagraphAtBookmark(com.sun.star.lang.XComponent component, String bookmark) {
        use(UnoCategory) {
            def bookmarks = component?.uno(XBookmarksSupplier)?.getBookmarks()
            if (bookmarks == null) {
                throw new OdiseeException("Bookmark '${bookmark}' was not found", OdiseeException.UNPROCESSABLE)
            }
            Object raw
            try {
                raw = unwrapAny(bookmarks.getByName(bookmark))
            } catch (NoSuchElementException e) {
                OdiseeException ex = new OdiseeException("Bookmark '${bookmark}' was not found", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
            XTextContent content = raw == null ? null : (XTextContent) raw.uno(XTextContent)
            if (content?.anchor == null) {
                throw new OdiseeException("Bookmark '${bookmark}' was not found", OdiseeException.UNPROCESSABLE)
            }
            content.getAnchor().getString()
        }
    }

    def static get(com.sun.star.lang.XComponent component, String name) {
        getParagraphAtBookmark(component, name)
    }

    /**
     * Set text at bookmark.
     */
    def static set(com.sun.star.lang.XComponent component, String name, Object value) {
        setTextAtBookmark(component, name, (value ?: '') as String)
    }

}
