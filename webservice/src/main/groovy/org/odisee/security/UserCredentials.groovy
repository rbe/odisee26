/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.security

import groovy.json.JsonSlurper
import org.odisee.api.OdiseeException

/**
 * Name and password for {@code POST /user}. There is no default password.
 */
final class UserCredentials {

    final String name
    final String password

    UserCredentials(String name, String password) {
        this.name = name
        this.password = password
    }

    static UserCredentials fromForm(String name, String password) {
        new UserCredentials(name, password)
    }

    static UserCredentials fromBody(String contentType, byte[] body) {
        String ct = contentType?.toLowerCase() ?: ''
        if (body == null || body.length == 0) {
            throw new OdiseeException('Name and password are required', OdiseeException.BAD_REQUEST)
        }
        if (ct.contains('xml')) {
            throw new OdiseeException('Name and password are required', OdiseeException.BAD_REQUEST)
        }
        if (!(ct.contains('json') || body[0] == ((byte) '{'))) {
            throw new OdiseeException('Name and password are required', OdiseeException.BAD_REQUEST)
        }
        Object parsed
        try {
            parsed = new JsonSlurper().parse(body, 'UTF-8')
        } catch (Exception e) {
            throw new OdiseeException('Invalid or missing JSON request', OdiseeException.BAD_REQUEST)
        }
        if (!(parsed instanceof Map)) {
            throw new OdiseeException('Invalid or missing JSON request', OdiseeException.BAD_REQUEST)
        }
        new UserCredentials(parsed.name?.toString(), parsed.password?.toString())
    }

}
