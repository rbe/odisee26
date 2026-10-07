/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import org.odisee.ooo.connection.OfficeConnectionFactory
import org.odisee.ooo.connection.PoolGauges

/**
 * Readiness is a live UNO connection, plus the pool gauges an operator watches.
 */
class ReadyController {

    RequestService requestService

    def index() {
        try {
            OfficeConnectionFactory factory = requestService?.officeConnectionFactory
            boolean ready = false
            PoolGauges gauges = PoolGauges.EMPTY
            if (factory != null) {
                ready = factory.acceptsUnoConnection()
                gauges = factory.gauges()
            }
            ReadyResponse.apply(response, ready, gauges)
        } catch (Throwable e) {
            log.error 'Readiness probe failed', e
            ReadyResponse.apply(response, false, PoolGauges.EMPTY)
        } finally {
            response.outputStream?.close()
        }
    }

}
