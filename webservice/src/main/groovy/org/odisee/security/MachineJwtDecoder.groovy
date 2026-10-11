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
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtIssuerValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.web.client.RestTemplate

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * Checks the signature against the Keycloak certificate URL of the configured issuer.
 * The certificate URL is not taken from the token.
 */
final class MachineJwtDecoder implements JwtDecoder {

    private final Map<String, JwtDecoder> decoders = new ConcurrentHashMap<>()

    @Override
    Jwt decode(String token) throws JwtException {
        MachineFile.Record record
        try {
            record = MachineFile.read(MachineFile.location())
        } catch (OdiseeException ex) {
            throw new BadJwtException('Machine client is not configured', ex)
        }
        if (record == null) {
            throw new BadJwtException('Machine client is not configured')
        }
        String key = record.issuer + ' ' + record.audience
        Jwt jwt = decoders.computeIfAbsent(key, { String ignored -> nimbus(record) }).decode(token)
        MachineToken.user(jwt, record)
        jwt
    }

    private static JwtDecoder nimbus(MachineFile.Record record) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(Duration.ofSeconds(3))
        factory.setReadTimeout(Duration.ofSeconds(3))
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(MachineFile.jwksUri(record.issuer))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(factory))
                .build()
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<Jwt>(
                new JwtTimestampValidator(),
                new JwtIssuerValidator(record.issuer),
                audience(record.audience)
        ))
        decoder
    }

    private static OAuth2TokenValidator<Jwt> audience(String expected) {
        new OAuth2TokenValidator<Jwt>() {
            @Override
            OAuth2TokenValidatorResult validate(Jwt token) {
                if (token?.audience?.contains(expected)) {
                    return OAuth2TokenValidatorResult.success()
                }
                OAuth2Error error = new OAuth2Error('invalid_token', 'The audience is not configured', null)
                OAuth2TokenValidatorResult.failure(error)
            }
        }
    }

}
