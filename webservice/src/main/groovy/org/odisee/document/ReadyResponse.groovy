/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import org.odisee.ooo.connection.PoolGauges

/**
 * {@code /ready} body. Healthy only when an office port accepts a UNO connection.
 */
final class ReadyResponse {

    private ReadyResponse() {
    }

    static void apply(Object response, boolean ready, PoolGauges gauges) {
        PoolGauges figures = gauges == null ? PoolGauges.EMPTY : gauges
        response.status = ready ? 200 : 503
        response.contentType = 'application/json'
        response.outputStream << figures.toJson(ready)
        response.outputStream.flush()
    }

}
