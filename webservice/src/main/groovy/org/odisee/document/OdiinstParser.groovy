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

import java.net.InetSocketAddress

/**
 * {@code etc/odiinst} lines are
 * {@code name|host|port|office home| |options|autostart}.
 */
final class OdiinstParser {

    private OdiinstParser() {
    }

    static List<String[]> parse(String text) {
        List<String[]> rows = []
        text?.eachLine { String line ->
            String trimmed = line?.trim()
            if (!trimmed || trimmed.startsWith('#')) {
                return
            }
            String[] parts = trimmed.split('\\|', -1)
            if (parts.length < 3 || !parts[0]?.trim() || !parts[1]?.trim() || !parts[2]?.trim()) {
                throw new OdiseeException("Invalid odiinst line '${trimmed}'", OdiseeException.SERVER_ERROR)
            }
            rows << parts
        }
        if (!rows) {
            throw new OdiseeException('No office instances configured', OdiseeException.SERVER_ERROR)
        }
        rows
    }

    static List<InetSocketAddress> addresses(List rows) {
        if (rows == null || rows.isEmpty()) {
            throw new OdiseeException('No office instances configured', OdiseeException.SERVER_ERROR)
        }
        rows.collect { row ->
            String host = row[1]?.toString()?.trim()
            String portText = row[2]?.toString()?.trim()
            if (!host || !portText?.isInteger()) {
                throw new OdiseeException("Invalid office address '${host}:${portText}'", OdiseeException.SERVER_ERROR)
            }
            int port = portText as int
            if (port < 1 || port > 65535) {
                throw new OdiseeException("Invalid office port '${port}'", OdiseeException.SERVER_ERROR)
            }
            new InetSocketAddress(host, port)
        }
    }

}
