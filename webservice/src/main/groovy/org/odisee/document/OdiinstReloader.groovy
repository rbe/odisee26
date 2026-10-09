/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import java.net.InetSocketAddress

/**
 * Reloads {@code etc/odiinst} when its text changes.
 * The same JVM keeps the pool. A repeat of the same text does not rebuild it.
 */
final class OdiinstReloader {

    private String applied

    /**
     * @return the new groups, or null when {@code text} is already the pool's text
     */
    Map<String, List<InetSocketAddress>> update(String text) {
        if (text == applied) {
            return null
        }
        Map<String, List<InetSocketAddress>> groups = OdiinstParser.byGroup(OdiinstParser.parse(text))
        return groups
    }

    void markApplied(String text) {
        applied = text
    }

    String appliedText() {
        applied
    }

}
