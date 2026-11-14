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
import com.sun.star.container.NoSuchElementException
import com.sun.star.container.XNameAccess
import com.sun.star.lang.XComponent
import com.sun.star.lang.XMultiServiceFactory
import com.sun.star.text.*
import com.sun.star.uno.Any
import org.odisee.api.OdiseeException
import org.odisee.debug.Profile
import org.odisee.uno.UnoCategory

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.net.UnknownHostException
import java.nio.file.Path

/**
 * Images.
 */
class OOoImageCategory {

    private static final String STRICT_IPV4 = /^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$/

    static Closure<InetAddress[]> hostResolver = { String host -> InetAddress.getAllByName(host) }

    /**
     * Validate an external image URL supplied by a request.
     * Rejects {@code file://} and non-HTTP(S) schemes, single-label/local hosts, and private/internal IPs.
     */
    static String requireSafeImageUrl(final String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new OdiseeException("Invalid image URL '${rawUrl}'", OdiseeException.UNPROCESSABLE)
        }
        final URI uri
        try {
            uri = new URI(rawUrl.trim())
        } catch (URISyntaxException e) {
            OdiseeException ex = new OdiseeException("Invalid image URL '${rawUrl}'", e)
            ex.httpStatus = OdiseeException.UNPROCESSABLE
            throw ex
        }
        final String scheme = uri.scheme?.toLowerCase(Locale.ROOT)
        if (scheme != 'http' && scheme != 'https') {
            throw new OdiseeException("Unsupported image URL scheme '${uri.scheme ?: ''}' in '${rawUrl}'", OdiseeException.UNPROCESSABLE)
        }
        if (uri.userInfo != null || uri.rawAuthority == null || uri.rawAuthority.contains('@') || !uri.host) {
            throw new OdiseeException("Invalid image URL '${rawUrl}'", OdiseeException.UNPROCESSABLE)
        }
        String host = uri.host.trim()
        if (host.startsWith('[') && host.endsWith(']')) {
            host = host.substring(1, host.length() - 1)
        }
        if (!host || host.contains('%')) {
            throw new OdiseeException("Invalid image URL '${rawUrl}'", OdiseeException.UNPROCESSABLE)
        }
        String lower = host.toLowerCase(Locale.ROOT)
        if (lower == 'localhost'
                || lower.endsWith('.localhost')
                || lower.endsWith('.local')
                || lower.endsWith('.internal')
                || lower.endsWith('.home.arpa')) {
            throw new OdiseeException("Disallowed image host '${host}'", OdiseeException.UNPROCESSABLE)
        }
        if (!lower.contains(':') && !lower.contains('.')) {
            throw new OdiseeException("Disallowed image host '${host}'", OdiseeException.UNPROCESSABLE)
        }
        if (!lower.contains(':')) {
            String[] labels = lower.split('\\.', -1)
            if (labels.any { !it }) {
                throw new OdiseeException("Invalid image URL '${rawUrl}'", OdiseeException.UNPROCESSABLE)
            }
            String tld = labels[-1]
            if ((tld ==~ /^(0x[0-9a-f]+|\d+)$/) && !(lower ==~ STRICT_IPV4)) {
                throw new OdiseeException("Disallowed image host '${host}'", OdiseeException.UNPROCESSABLE)
            }
        }
        InetAddress[] resolved
        try {
            Closure<InetAddress[]> resolver = hostResolver ?: { String h -> InetAddress.getAllByName(h) }
            resolved = resolver.call(host)
        } catch (UnknownHostException e) {
            OdiseeException ex = new OdiseeException("Cannot resolve image host '${host}'", e)
            ex.httpStatus = OdiseeException.UNPROCESSABLE
            throw ex
        }
        if (resolved == null || resolved.length == 0) {
            throw new OdiseeException("Cannot resolve image host '${host}'", OdiseeException.UNPROCESSABLE)
        }
        for (InetAddress address : resolved) {
            if (isInternalOrReservedAddress(address)) {
                throw new OdiseeException("Disallowed internal image URL '${rawUrl}'", OdiseeException.UNPROCESSABLE)
            }
        }
        uri.toASCIIString()
    }

    static boolean isInternalOrReservedAddress(final InetAddress address) {
        if (address == null) {
            return true
        }
        if (address.anyLocalAddress
                || address.loopbackAddress
                || address.linkLocalAddress
                || address.siteLocalAddress
                || address.multicastAddress) {
            return true
        }
        byte[] bytes = address.address
        if (bytes == null) {
            return true
        }
        if (bytes.length == 4) {
            return isDisallowedIpv4(bytes, 0)
        }
        if (bytes.length == 16) {
            int b0 = bytes[0] & 0xFF
            int b1 = bytes[1] & 0xFF
            if ((b0 & 0xFE) == 0xFC) {
                return true
            }
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) {
                return true
            }
            boolean zerosPrefix = (0..<10).every { int idx -> bytes[idx] == (byte) 0 }
            if (zerosPrefix) {
                int b10 = bytes[10] & 0xFF
                int b11 = bytes[11] & 0xFF
                if ((b10 == 0 && b11 == 0) || (b10 == 0xFF && b11 == 0xFF)) {
                    return isDisallowedIpv4(bytes, 12)
                }
            }
            if (b0 == 0x00 && b1 == 0x64 && (bytes[2] & 0xFF) == 0xFF && (bytes[3] & 0xFF) == 0x9B
                    && (4..<12).every { int idx -> bytes[idx] == (byte) 0 }) {
                return isDisallowedIpv4(bytes, 12)
            }
        }
        false
    }

    private static boolean isDisallowedIpv4(final byte[] bytes, final int offset) {
        int b0 = bytes[offset] & 0xFF
        int b1 = bytes[offset + 1] & 0xFF
        if (b0 == 0 || b0 == 10 || b0 == 127) {
            return true
        }
        if (b0 == 100 && (b1 & 0xC0) == 64) {
            return true
        }
        if (b0 == 169 && b1 == 254) {
            return true
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return true
        }
        if (b0 == 192 && b1 == 168) {
            return true
        }
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return true
        }
        if (b0 >= 224) {
            return true
        }
        false
    }

    /**
     * Insert a validated external image URL at a bookmark.
     */
    static void insertImageAtBookmark(final XComponent component, final String imageType, final String bookmarkName, final String imageUrl, final int width, final int height) {
        final String safeUrl = requireSafeImageUrl(imageUrl)
        insertGraphicUrlAtBookmark(component, bookmarkName, safeUrl, width, height)
    }

    /**
     * Insert a local temporary image file created by Odisee at a bookmark.
     */
    static void insertImageAtBookmark(final XComponent component, final String imageType, final String bookmarkName, final Path imageFile, final int width, final int height) {
        if (imageFile == null) {
            throw new OdiseeException('Image file is missing', OdiseeException.UNPROCESSABLE)
        }
        insertGraphicUrlAtBookmark(component, bookmarkName, imageFile.toAbsolutePath().toUri().toString(), width, height)
    }

    private static void insertGraphicUrlAtBookmark(final XComponent component, final String bookmarkName, final String graphicUrl, final int width, final int height) {
        Profile.time "OOoImageCategory.insertImageAtBookmark($bookmarkName)", {
            use(UnoCategory) {
                final XBookmarksSupplier xBookmarksSupplier = component == null ? null : (XBookmarksSupplier) component.uno(XBookmarksSupplier)
                final XNameAccess bookmarks = xBookmarksSupplier?.getBookmarks()
                if (bookmarks == null) {
                    throw new OdiseeException("Bookmark '${bookmarkName}' was not found", OdiseeException.UNPROCESSABLE)
                }
                Object rawBookmark
                try {
                    rawBookmark = bookmarks.getByName(bookmarkName)
                    if (rawBookmark instanceof Any) {
                        rawBookmark = ((Any) rawBookmark).object
                    }
                } catch (NoSuchElementException e) {
                    OdiseeException ex = new OdiseeException("Bookmark '${bookmarkName}' was not found", e)
                    ex.httpStatus = OdiseeException.UNPROCESSABLE
                    throw ex
                }
                final XTextContent oBookmark = rawBookmark == null ? null : (XTextContent) rawBookmark.uno(XTextContent)
                final XTextRange oBookmarkAnchor = oBookmark?.getAnchor()
                if (oBookmarkAnchor == null) {
                    throw new OdiseeException("Bookmark '${bookmarkName}' was not found", OdiseeException.UNPROCESSABLE)
                }
                XMultiServiceFactory xMultiServiceFactory = (XMultiServiceFactory) component.uno(com.sun.star.lang.XMultiServiceFactory)
                Object oGraphic = xMultiServiceFactory.createInstance("com.sun.star.text.TextGraphicObject")
                XTextContent oGraphicTextContent = com.sun.star.uno.UnoRuntime.queryInterface(XTextContent.class, oGraphic)
                XTextDocument xTextDocument = (XTextDocument) component.uno(XTextDocument)
                XText xText = xTextDocument.getText()
                XTextCursor xTextCursor = xText.createTextCursor()
                xTextCursor.gotoRange(oBookmarkAnchor, false)
                xText.insertTextContent(xTextCursor, oGraphicTextContent, true)
                XPropertySet xPropSet = com.sun.star.uno.UnoRuntime.queryInterface(XPropertySet.class, oGraphic)
                xPropSet.setPropertyValue("AnchorType", TextContentAnchorType.AT_PARAGRAPH)
                xPropSet.setPropertyValue("GraphicURL", graphicUrl)
                if (width > 0) {
                    xPropSet.setPropertyValue("Width", Integer.valueOf(width))
                }
                if (height > 0) {
                    xPropSet.setPropertyValue("Height", Integer.valueOf(height))
                }
            }
        }
    }

}
