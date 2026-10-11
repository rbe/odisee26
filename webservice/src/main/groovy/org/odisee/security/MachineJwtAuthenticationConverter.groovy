/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken

/**
 * The authentication name is the Odisee user from {@code etc/oidc}.
 */
final class MachineJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    AbstractAuthenticationToken convert(Jwt jwt) {
        new JwtAuthenticationToken(jwt, Collections.emptyList(), MachineToken.user(jwt))
    }

}
