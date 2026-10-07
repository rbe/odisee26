/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import org.odisee.api.OdiseeException
import org.odisee.io.SafePaths

/**
 * Plain-name check for a macro name, its library, and its language.
 * A bad name is HTTP 400. Any authenticated user may run a macro that passes.
 */
final class MacroNames {

    private MacroNames() {
    }

    static void requireParts(String name, String library, String language) {
        SafePaths.requireSimpleName(name, 'macro name')
        SafePaths.requireSimpleName(library, 'macro library')
        SafePaths.requireSimpleName(language, 'macro language')
    }

    /**
     * A reference is {@code Library.Module.Name} or
     * {@code vnd.sun.star.script:Library.Module.Name?language=Basic&location=document}.
     * {@code location} is the library.
     */
    static void requireReference(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new OdiseeException("Invalid macro name '${raw}'", OdiseeException.BAD_REQUEST)
        }
        String text = raw.trim()
        String script = text
        String language = 'Basic'
        String library = 'document'
        int query = text.indexOf('?')
        if (query >= 0) {
            script = text.substring(0, query)
            String rest = text.substring(query + 1)
            if (rest.isEmpty()) {
                throw new OdiseeException("Invalid macro name '${raw}'", OdiseeException.BAD_REQUEST)
            }
            rest.split('&').each { String pair ->
                String[] kv = pair.split('=', 2)
                if (kv.length != 2 || kv[0].isEmpty()) {
                    throw new OdiseeException("Invalid macro name '${raw}'", OdiseeException.BAD_REQUEST)
                }
                if (kv[0] == 'language') {
                    language = kv[1]
                } else if (kv[0] == 'location') {
                    library = kv[1]
                } else {
                    throw new OdiseeException("Invalid macro name '${raw}'", OdiseeException.BAD_REQUEST)
                }
            }
        }
        if (script.startsWith('vnd.sun.star.script:')) {
            script = script.substring('vnd.sun.star.script:'.length())
        }
        requireParts(script, library, language)
    }

}
