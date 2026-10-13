package org.odisee.security

import groovy.test.GroovyTestCase
import org.odisee.document.PathTree
import org.odisee.io.OdiseePath
import org.odisee.io.TenantPaths
import org.springframework.security.oauth2.jwt.Jwt

import java.nio.file.Files
import java.time.Instant

/**
 * The validator sees a fake JWT. It does not call Keycloak.
 */
class OidcTokenTest extends GroovyTestCase {

    private OidcFile.Record press = new OidcFile.Record(
            'http://keycloak:8080/realms/odisee',
            'odisee',
            'odisee-ui'
    )

    @Override
    protected void setUp() {
        TenantPaths.createDirectories('press')
    }

    @Override
    protected void tearDown() {
        deleteUser('press')
        deleteUser('ada')
        Files.deleteIfExists(OidcFile.location())
    }

    void testApiClientIdIsTheUser() {
        Jwt jwt = fakeJwt(
                iss: 'http://keycloak:8080/realms/odisee',
                aud: ['odisee', 'account'],
                azp: 'press',
                client_id: 'press',
                preferred_username: 'service-account-press',
                sub: 'bob',
                user: 'carol'
        )

        assertEquals('press', OidcToken.user(jwt, press))
    }

    void testApiClientIgnoresADifferentUsernameClaim() {
        Jwt jwt = fakeJwt(
                iss: press.issuer,
                aud: 'odisee',
                azp: 'press',
                preferred_username: 'ada',
                sub: 'ada'
        )

        assertEquals('press', OidcToken.user(jwt, press))
    }

    void testUiClientUsesTheLoggedInUsername() {
        TenantPaths.createDirectories('ada')
        Jwt jwt = fakeJwt(
                iss: press.issuer,
                aud: 'odisee',
                azp: 'odisee-ui',
                preferred_username: 'ada',
                sub: 'not-ada'
        )

        assertEquals('ada', OidcToken.user(jwt, press))
    }

    void testWrongIssuerIsRejected() {
        Jwt jwt = fakeJwt(iss: 'http://other.example/realms/odisee', aud: 'odisee', azp: 'press')

        String message = shouldFail(org.springframework.security.oauth2.jwt.BadJwtException) {
            OidcToken.user(jwt, press)
        }
        assertTrue(message.contains('issuer'))
    }

    void testWrongAudienceIsRejected() {
        Jwt jwt = fakeJwt(iss: press.issuer, aud: 'account', azp: 'press', preferred_username: 'press')

        String message = shouldFail(org.springframework.security.oauth2.jwt.BadJwtException) {
            OidcToken.user(jwt, press)
        }
        assertTrue(message.contains('audience'))
    }

    void testMissingDirectoryIsRejected() {
        deleteUser('press')
        Jwt jwt = fakeJwt(iss: press.issuer, aud: 'odisee', azp: 'press')

        String message = shouldFail(org.springframework.security.oauth2.jwt.BadJwtException) {
            OidcToken.user(jwt, press)
        }
        assertTrue(message.contains('not configured'))
    }

    void testDisagreeingClientClaimsAreRejected() {
        Jwt jwt = fakeJwt(
                iss: press.issuer,
                aud: 'odisee',
                azp: 'press',
                client_id: 'other-machine'
        )

        shouldFail(org.springframework.security.oauth2.jwt.BadJwtException) {
            OidcToken.user(jwt, press)
        }
    }

    void testMissingOidcFileIsRejected() {
        Files.deleteIfExists(OidcFile.location())
        Jwt jwt = fakeJwt(iss: press.issuer, aud: 'odisee', azp: 'press')

        String message = shouldFail(org.springframework.security.oauth2.jwt.BadJwtException) {
            OidcToken.user(jwt)
        }
        assertTrue(message.contains('not configured'))
    }

    void testDecoderAcceptsASignedFakeJwtAndIgnoresTheUserClaim() {
        FakeMachineToken fake = FakeMachineToken.start()
        try {
            fake.writeBinding()
            TenantPaths.createDirectories('press')
            String token = fake.sign(
                    azp: 'press',
                    client_id: 'press',
                    preferred_username: 'bob',
                    sub: 'bob',
                    user: 'carol'
            )

            Jwt jwt = new MachineJwtDecoder().decode(token)

            assertEquals('press', OidcToken.user(jwt))
            assertEquals('bob', jwt.getClaimAsString('preferred_username'))
        } finally {
            fake.stop()
            Files.deleteIfExists(OidcFile.location())
        }
    }

    void testDecoderRejectsADifferentSignature() {
        FakeMachineToken fake = FakeMachineToken.start()
        try {
            fake.writeBinding()
            TenantPaths.createDirectories('press')
            String token = fake.signWithOtherKey(azp: 'press', client_id: 'press')

            shouldFail {
                new MachineJwtDecoder().decode(token)
            }
        } finally {
            fake.stop()
            Files.deleteIfExists(OidcFile.location())
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

    private static void deleteUser(String name) {
        PathTree.delete(OdiseePath.ODISEE_VAR.resolve('user').resolve(name))
    }

}
