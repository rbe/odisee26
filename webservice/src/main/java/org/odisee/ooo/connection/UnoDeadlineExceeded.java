/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */

package org.odisee.ooo.connection;

/**
 * A UNO call did not return before its deadline. The slot must leave the pool.
 */
public final class UnoDeadlineExceeded extends OdiseeServerException {

    private final String phase;

    private final long deadlineMillis;

    public UnoDeadlineExceeded(final String phase, final long deadlineMillis) {
        super(String.format("Office %s exceeded the %d ms deadline", phase, deadlineMillis));
        this.phase = phase;
        this.deadlineMillis = deadlineMillis;
    }

    public String phase() {
        return phase;
    }

    public long deadlineMillis() {
        return deadlineMillis;
    }

}
