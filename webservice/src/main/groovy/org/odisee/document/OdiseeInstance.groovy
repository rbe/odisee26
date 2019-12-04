/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2017 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 16.03.17 19:52
 */

package org.odisee.document

import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import groovy.util.logging.Log

import java.nio.file.Files
import java.nio.file.Path

@Singleton
@Log
final class OdiseeInstance {

    public static final String S_UTF8 = "UTF-8";

    public static final String S_GROUP0 = "group0";

    private final Map oooGroup = [:]

    List<String> readOdiinst() {
        final Path odiinstPath = OdiseePath.ODISEE_HOME.resolve(OdiseePath.S_ETC_ODIINST)
        final List<String> odiinst = []
        boolean odiinstExists = Files.exists(odiinstPath)
        if (odiinstExists) {
            odiinstPath.toFile().eachLine S_UTF8, {
                odiinst << it.split('[|]')
            }
            final Map ipPortGroup = [:]
            final Map<String, List<String>> groupIpPort = odiinst.groupBy { it[1] }
            groupIpPort.each { k, v ->
                ipPortGroup[k] = v.collect { it[2] }
            }
            ipPortGroup.eachWithIndex { it, i ->
                final Map m = [:]
                m[it.key] = it.value
                synchronized (oooGroup) {
                    oooGroup[S_GROUP0] = m
                }
            }
        } else {
            /*
            log.warning('Odisee: No odiinst found, using default 127.0.0.1:2001')
            odiinst << ['odi1', '127.0.0.1', '2001', '', '', '', 'false']
            oooGroup[S_GROUP0] = ['127.0.0.1': 2001]
            */
            throw new OdiseeException('No odiinst found')
        }
        odiinst
    }

}
