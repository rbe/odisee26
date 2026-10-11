/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.security

import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.odisee.io.SafePaths

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * {@code $ODISEE_HOME/etc/oidc}. One line is {@code client username issuer audience}.
 * {@code odictl machine} writes the line. The server reads the file on use.
 * The username is the Odisee user. The token does not choose it.
 */
final class MachineFile {

    private MachineFile() {
    }

    static final class Record {
        final String clientId
        final String username
        final String issuer
        final String audience

        Record(String clientId, String username, String issuer, String audience) {
            this.clientId = clientId
            this.username = username
            this.issuer = issuer
            this.audience = audience
        }
    }

    static Path location() {
        OdiseePath.ODISEE_HOME.resolve('etc').resolve('oidc')
    }

    /**
     * Reads the file every time. There is no cached copy.
     * A missing file is no machine client. Two client lines are an error.
     */
    static Record read(Path file) {
        if (file == null || !Files.exists(file)) {
            return null
        }
        Record found = null
        Files.readAllLines(file, StandardCharsets.UTF_8).each { String line ->
            String trimmed = line.trim()
            if (!trimmed || trimmed.startsWith('#')) {
                return
            }
            if (found != null) {
                throw new OdiseeException('Machine client file has more than one client', OdiseeException.SERVER_ERROR)
            }
            found = parse(trimmed)
        }
        found
    }

    static Record parse(String line) {
        String[] parts = line.trim().split(/\s+/)
        if (parts.length != 4) {
            throw new OdiseeException('Machine client line is invalid', OdiseeException.SERVER_ERROR)
        }
        String clientId = parts[0]
        if (!(clientId ==~ /[A-Za-z0-9][A-Za-z0-9._-]{0,127}/)) {
            throw new OdiseeException("Invalid client '${clientId}'", OdiseeException.SERVER_ERROR)
        }
        String username = SafePaths.requireSimpleName(parts[1], 'user')
        String issuer = normalizeIssuer(parts[2])
        String audience = parts[3]
        if (!(audience ==~ /[A-Za-z0-9][A-Za-z0-9._-]{0,127}/)) {
            throw new OdiseeException("Invalid audience '${audience}'", OdiseeException.SERVER_ERROR)
        }
        if (!(issuer ==~ /(?i)https?:\/\/.+/)) {
            throw new OdiseeException("Invalid issuer '${issuer}'", OdiseeException.SERVER_ERROR)
        }
        new Record(clientId, username, issuer, audience)
    }

    static String normalizeIssuer(String issuer) {
        String value = issuer?.trim()
        if (!value) {
            throw new OdiseeException('Invalid issuer', OdiseeException.SERVER_ERROR)
        }
        while (value.endsWith('/')) {
            value = value.substring(0, value.length() - 1)
        }
        value
    }

    /**
     * Keycloak publishes the signing keys at this path under the realm issuer.
     * The URL is the configured issuer. It is not a claim from the token.
     */
    static String jwksUri(String issuer) {
        normalizeIssuer(issuer) + '/protocol/openid-connect/certs'
    }

}
