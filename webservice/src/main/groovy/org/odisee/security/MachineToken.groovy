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
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Issuer, audience, and client id must match {@code $ODISEE_HOME/etc/oidc}.
 * The Odisee user is the username on that line.
 * {@code preferred_username}, {@code sub}, and any other user claim are ignored.
 */
final class MachineToken {

    private MachineToken() {
    }

    static String user(Jwt jwt) {
        MachineFile.Record record
        try {
            record = MachineFile.read(MachineFile.location())
        } catch (OdiseeException ex) {
            throw new BadJwtException('Machine client is not configured', ex)
        }
        if (record == null) {
            throw new BadJwtException('Machine client is not configured')
        }
        user(jwt, record)
    }

    static String user(Jwt jwt, MachineFile.Record record) {
        if (jwt == null || record == null) {
            throw new BadJwtException('Machine client is not configured')
        }
        String issuer = jwt.getClaimAsString('iss')
        if (!issuer || MachineFile.normalizeIssuer(issuer) != record.issuer) {
            throw new BadJwtException('The issuer is not configured')
        }
        List<String> audience = jwt.audience ?: []
        if (!audience.contains(record.audience)) {
            throw new BadJwtException('The audience is not configured')
        }
        if (clientId(jwt) != record.clientId) {
            throw new BadJwtException('The client is not configured')
        }
        record.username
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
