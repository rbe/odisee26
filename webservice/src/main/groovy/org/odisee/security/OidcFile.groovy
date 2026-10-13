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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * {@code $ODISEE_HOME/etc/oidc}. The server reads this file on use.
 * {@code issuer} and {@code audience} are the token checks.
 * {@code ui-client} is the later browser client. Every other client id is the Odisee user.
 */
final class OidcFile {

    static final String UI_CLIENT = 'odisee-ui'

    private OidcFile() {
    }

    static final class Record {
        final String issuer
        final String audience
        final String uiClient

        Record(String issuer, String audience, String uiClient) {
            this.issuer = issuer
            this.audience = audience
            this.uiClient = uiClient
        }
    }

    static Path location() {
        OdiseePath.ODISEE_HOME.resolve('etc').resolve('oidc')
    }

    static Record read(Path file) {
        if (file == null || !Files.exists(file)) {
            return null
        }
        String issuer = null
        String audience = null
        String uiClient = null
        Files.readAllLines(file, StandardCharsets.UTF_8).each { String line ->
            String trimmed = line.trim()
            if (!trimmed || trimmed.startsWith('#')) {
                return
            }
            int space = trimmed.indexOf(' ')
            if (space < 1) {
                throw new OdiseeException('OpenID Connect line is invalid', OdiseeException.SERVER_ERROR)
            }
            String key = trimmed.substring(0, space)
            String value = trimmed.substring(space).trim()
            if (!value) {
                throw new OdiseeException('OpenID Connect line is invalid', OdiseeException.SERVER_ERROR)
            }
            switch (key) {
                case 'issuer':
                    if (issuer != null) {
                        throw new OdiseeException('OpenID Connect line is invalid', OdiseeException.SERVER_ERROR)
                    }
                    issuer = normalizeIssuer(value)
                    break
                case 'audience':
                    if (audience != null) {
                        throw new OdiseeException('OpenID Connect line is invalid', OdiseeException.SERVER_ERROR)
                    }
                    audience = value
                    break
                case 'ui-client':
                    if (uiClient != null) {
                        throw new OdiseeException('OpenID Connect line is invalid', OdiseeException.SERVER_ERROR)
                    }
                    uiClient = value
                    break
                default:
                    throw new OdiseeException('OpenID Connect line is invalid', OdiseeException.SERVER_ERROR)
            }
        }
        if (!issuer || !audience) {
            throw new OdiseeException('OpenID Connect is not configured', OdiseeException.SERVER_ERROR)
        }
        new Record(issuer, audience, uiClient ?: UI_CLIENT)
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
