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

import com.sun.star.beans.XPropertySet
import com.sun.star.container.XNameAccess
import com.sun.star.lang.XComponent
import com.sun.star.lang.XMultiServiceFactory
import com.sun.star.text.*
import org.odisee.debug.Profile
import org.odisee.uno.UnoCategory

/**
 * Images.
 */
class OOoImageCategory {

    /**
     * Insert an image at a bookmark.
     */
    static void insertImageAtBookmark(final XComponent component, final String imageType, final String bookmarkName, final String imageUrl, final int width, final int height) {
        Profile.time "OOoImageCategory.insertImageAtBookmark($bookmarkName)", {
            use(UnoCategory) {
                // Bookmark -> XTextRange
                final XBookmarksSupplier xBookmarksSupplier = (XBookmarksSupplier) component.uno(XBookmarksSupplier)
                final XNameAccess bookmarks = xBookmarksSupplier.getBookmarks()
                final XTextContent oBookmark = (XTextContent) bookmarks.getByName(bookmarkName).uno(XTextContent)
                final XTextRange oBookmarkAnchor = (XTextRange) oBookmark.getAnchor()
                // Graphic
                XMultiServiceFactory xMultiServiceFactory = (XMultiServiceFactory) component.uno(com.sun.star.lang.XMultiServiceFactory)
                Object oGraphic = xMultiServiceFactory.createInstance("com.sun.star.text.TextGraphicObject")
                XTextContent oGraphicTextContent = com.sun.star.uno.UnoRuntime.queryInterface(XTextContent.class, oGraphic)
                // XTextCursor
                XTextDocument xTextDocument = (XTextDocument) component.uno(XTextDocument)
                XText xText = xTextDocument.getText()
                XTextCursor xTextCursor = xText.createTextCursor()
                xTextCursor.gotoRange(oBookmarkAnchor, false)
                // Insert content
                xText.insertTextContent(xTextCursor, oGraphicTextContent, true);
                // Set properties for graphic
                XPropertySet xPropSet = com.sun.star.uno.UnoRuntime.queryInterface(XPropertySet.class, oGraphic);
                xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH);
                xPropSet.setPropertyValue("GraphicURL", imageUrl);
                /*
                xPropSet.setPropertyValue("HoriOrientPosition", new Integer(5500));
                xPropSet.setPropertyValue("VertOrientPosition", new Integer(4200));
                */
                if (width.intValue() > 0) {
                    xPropSet.setPropertyValue("Width", new Integer(width));
                }
                if (height.intValue() > 0) {
                    xPropSet.setPropertyValue("Height", new Integer(height));
                }
            }
        }

    }

}
