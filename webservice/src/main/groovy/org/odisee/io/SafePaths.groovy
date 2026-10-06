/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.io

import org.odisee.api.OdiseeException

import java.nio.file.Path

/**
 * Names and relative paths that arrive in a request.
 */
final class SafePaths {

    private SafePaths() {
    }

    static String requireSimpleName(String name, String label) {
        if (name == null || name.trim().isEmpty()
                || name.contains('/') || name.contains('\\')
                || name.contains('..') || name.contains('\0')
                || name.contains('*') || name.contains('?') || name.contains('[')) {
            throw new OdiseeException("Invalid ${label} '${name}'", OdiseeException.BAD_REQUEST)
        }
        name.trim()
    }

    static Path resolveInside(Path root, String relative) {
        if (root == null) {
            throw new OdiseeException('Storage root is not configured', OdiseeException.SERVER_ERROR)
        }
        if (relative == null || relative.contains('\0') || relative.trim().isEmpty()) {
            throw new OdiseeException('Missing relative path', OdiseeException.BAD_REQUEST)
        }
        Path rootAbs = root.toAbsolutePath().normalize()
        Path candidate = rootAbs.resolve(relative).normalize()
        if (!candidate.startsWith(rootAbs)) {
            throw new OdiseeException("Path '${relative}' is outside ${rootAbs}", OdiseeException.BAD_REQUEST)
        }
        candidate
    }

}
