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
 * Pool figures an operator reads from {@code /ready}.
 */
public final class PoolGauges {

    public static final PoolGauges EMPTY = new PoolGauges(0, 0, 0L, 0L, 0L);

    private final int poolSize;

    private final int inUse;

    private final long generationMillis;

    private final long instructionFailures;

    private final long sofficeRestarts;

    public PoolGauges(final int poolSize, final int inUse, final long generationMillis,
                      final long instructionFailures, final long sofficeRestarts) {
        this.poolSize = poolSize;
        this.inUse = inUse;
        this.generationMillis = generationMillis;
        this.instructionFailures = instructionFailures;
        this.sofficeRestarts = sofficeRestarts;
    }

    public int poolSize() {
        return poolSize;
    }

    public int inUse() {
        return inUse;
    }

    public long generationMillis() {
        return generationMillis;
    }

    public long instructionFailures() {
        return instructionFailures;
    }

    public long sofficeRestarts() {
        return sofficeRestarts;
    }

    public String toJson(final boolean ready) {
        return "{\"ready\":" + ready
                + ",\"poolSize\":" + poolSize
                + ",\"inUse\":" + inUse
                + ",\"generationMillis\":" + generationMillis
                + ",\"instructionFailures\":" + instructionFailures
                + ",\"sofficeRestarts\":" + sofficeRestarts
                + "}";
    }

}
