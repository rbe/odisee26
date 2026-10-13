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
import org.odisee.io.SafePaths
import org.odisee.io.TenantPaths
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt

import java.nio.file.Files

/**
 * Issuer and audience must match {@code $ODISEE_HOME/etc/oidc}.
 * An API client's id is the Odisee user. {@code preferred_username} is ignored on that token.
 * The UI client uses {@code preferred_username}, which is the person who logged in.
 */
final class OidcToken {

    private OidcToken() {
    }

    static String user(Jwt jwt) {
        OidcFile.Record record
        try {
            record = OidcFile.read(OidcFile.location())
        } catch (OdiseeException ex) {
            throw new BadJwtException('OpenID Connect is not configured', ex)
        }
        if (record == null) {
            throw new BadJwtException('OpenID Connect is not configured')
        }
        user(jwt, record)
    }

    static String user(Jwt jwt, OidcFile.Record record) {
        if (jwt == null || record == null) {
            throw new BadJwtException('OpenID Connect is not configured')
        }
        String issuer = jwt.getClaimAsString('iss')
        if (!issuer || OidcFile.normalizeIssuer(issuer) != record.issuer) {
            throw new BadJwtException('The issuer is not configured')
        }
        List<String> audience = jwt.audience ?: []
        if (!audience.contains(record.audience)) {
            throw new BadJwtException('The audience is not configured')
        }
        String client = clientId(jwt)
        String name
        if (client == record.uiClient) {
            name = stringClaim(jwt, 'preferred_username')
            if (!name) {
                throw new BadJwtException('The user is not configured')
            }
        } else {
            name = client
        }
        try {
            name = SafePaths.requireSimpleName(name, 'user')
        } catch (OdiseeException ex) {
            throw new BadJwtException('The user is not configured', ex)
        }
        if (!Files.isDirectory(TenantPaths.userRoot(name))) {
            throw new BadJwtException('The user is not configured')
        }
        name
    }

    private static String clientId(Jwt jwt) {
        String azp = stringClaim(jwt, 'azp')
        String clientId = stringClaim(jwt, 'client_id')
        if (azp && clientId && azp != clientId) {
            throw new BadJwtException('The client is not configured')
        }
        String id = azp ?: clientId
        if (!id) {
            throw new BadJwtException('The client is not configured')
        }
        id
    }

    private static String stringClaim(Jwt jwt, String name) {
        Object value = jwt.getClaim(name)
        value instanceof CharSequence ? value.toString() : null
    }

}
