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

    List<String[]> readOdiinst() {
        final Path odiinstPath = OdiseePath.ODISEE_HOME.resolve(OdiseePath.S_ETC_ODIINST)
        if (!Files.exists(odiinstPath)) {
            throw new OdiseeException('No odiinst found', OdiseeException.SERVER_ERROR)
        }
        final List<String[]> odiinst = OdiinstParser.parse(odiinstPath.getText(S_UTF8))
        final Map<String, List<String>> ipPortGroup = [:]
        odiinst.groupBy { it[1] }.each { host, rows ->
            ipPortGroup[host] = rows.collect { it[2] }
        }
        synchronized (oooGroup) {
            oooGroup[S_GROUP0] = ipPortGroup
        }
        odiinst
    }

}
