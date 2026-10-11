package org.odisee.security

import groovy.test.GroovyTestCase
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt

import java.nio.file.Files
import java.time.Instant

/**
 * The validator sees a fake JWT. It does not call Keycloak.
 */
class MachineTokenTest extends GroovyTestCase {

    private MachineFile.Record press = new MachineFile.Record(
            'odisee-machine',
            'press',
            'http://keycloak:8080/realms/odisee',
            'odisee'
    )

    void testFakeJwtMapsToTheConfiguredUser() {
        Jwt jwt = fakeJwt(
                iss: 'http://keycloak:8080/realms/odisee',
                aud: ['odisee', 'account'],
                azp: 'odisee-machine',
                preferred_username: 'bob',
                sub: 'bob',
                user: 'carol'
        )

        assertEquals('press', MachineToken.user(jwt, press))
    }

    void testClientIdClaimMapsWhenAzpIsAbsent() {
        Jwt jwt = fakeJwt(
                iss: 'http://keycloak:8080/realms/odisee/',
                aud: 'odisee',
                client_id: 'odisee-machine',
                preferred_username: 'press'
        )

        assertEquals('press', MachineToken.user(jwt, press))
    }

    void testWrongIssuerIsRejected() {
        Jwt jwt = fakeJwt(iss: 'http://other.example/realms/odisee', aud: 'odisee', azp: 'odisee-machine')

        String message = shouldFail(BadJwtException) {
            MachineToken.user(jwt, press)
        }
        assertTrue(message.contains('issuer'))
    }

    void testWrongAudienceIsRejected() {
        Jwt jwt = fakeJwt(iss: press.issuer, aud: 'account', azp: 'odisee-machine', preferred_username: 'press')

        String message = shouldFail(BadJwtException) {
            MachineToken.user(jwt, press)
        }
        assertTrue(message.contains('audience'))
    }

    void testWrongClientIsRejected() {
        Jwt jwt = fakeJwt(iss: press.issuer, aud: 'odisee', azp: 'other-machine', preferred_username: 'press')

        String message = shouldFail(BadJwtException) {
            MachineToken.user(jwt, press)
        }
        assertTrue(message.contains('client'))
    }

    void testDisagreeingClientClaimsAreRejected() {
        Jwt jwt = fakeJwt(
                iss: press.issuer,
                aud: 'odisee',
                azp: 'odisee-machine',
                client_id: 'other-machine',
                preferred_username: 'press'
        )

        shouldFail(BadJwtException) {
            MachineToken.user(jwt, press)
        }
    }

    void testMissingMachineFileIsRejected() {
        Files.deleteIfExists(MachineFile.location())
        Jwt jwt = fakeJwt(iss: press.issuer, aud: 'odisee', azp: 'odisee-machine')

        String message = shouldFail(BadJwtException) {
            MachineToken.user(jwt)
        }
        assertTrue(message.contains('not configured'))
    }

    void testDecoderAcceptsASignedFakeJwtAndIgnoresTheUserClaim() {
        FakeMachineToken fake = FakeMachineToken.start()
        try {
            fake.writeBinding('odisee-machine', 'press', 'odisee')
            String token = fake.sign(
                    azp: 'odisee-machine',
                    preferred_username: 'bob',
                    sub: 'bob',
                    user: 'carol'
            )

            Jwt jwt = new MachineJwtDecoder().decode(token)

            assertEquals('press', MachineToken.user(jwt))
            assertEquals('bob', jwt.getClaimAsString('preferred_username'))
            assertEquals('bob', jwt.subject)
            assertEquals('carol', jwt.getClaimAsString('user'))
        } finally {
            fake.stop()
            Files.deleteIfExists(MachineFile.location())
        }
    }

    void testDecoderRejectsADifferentSignature() {
        FakeMachineToken fake = FakeMachineToken.start()
        try {
            fake.writeBinding('odisee-machine', 'press', 'odisee')
            String token = fake.signWithOtherKey(azp: 'odisee-machine', preferred_username: 'press')

            shouldFail {
                new MachineJwtDecoder().decode(token)
            }
        } finally {
            fake.stop()
            Files.deleteIfExists(MachineFile.location())
        }
    }

    void testDecoderRejectsAlgNone() {
        FakeMachineToken fake = FakeMachineToken.start()
        try {
            fake.writeBinding('odisee-machine', 'press', 'odisee')
            String none = 'eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJpc3MiOiJ4IiwiYXpwIjoib2Rpc2VlLW1hY2hpbmUifQ.'

            shouldFail {
                new MachineJwtDecoder().decode(none)
            }
        } finally {
            fake.stop()
            Files.deleteIfExists(MachineFile.location())
        }
    }

    private static Jwt fakeJwt(Map<String, Object> claims) {
        Instant now = Instant.now()
        Jwt.Builder builder = Jwt.withTokenValue('fake')
                .header('alg', 'none')
                .issuedAt(now)
                .expiresAt(now.plusSeconds(60))
        claims.each { Object name, Object value ->
            builder.claim(name.toString(), value)
        }
        builder.build()
    }

}
