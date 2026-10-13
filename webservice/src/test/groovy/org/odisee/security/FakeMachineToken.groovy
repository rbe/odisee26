package org.odisee.security

import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import java.util.Date

/**
 * A locally signed JWT and the Keycloak certificate path on localhost.
 * This is not a Keycloak process.
 */
final class FakeMachineToken {

    final RSAKey key

    final HttpServer server

    final String issuer

    private FakeMachineToken(RSAKey key, HttpServer server, String issuer) {
        this.key = key
        this.server = server
        this.issuer = issuer
    }

    static FakeMachineToken start() {
        RSAKey key = new RSAKeyGenerator(2048).keyID('odisee-fake').generate()
        byte[] jwks = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8)
        HttpServer server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        int port = server.address.port
        String issuer = "http://127.0.0.1:${port}/realms/odisee"
        server.createContext('/realms/odisee/protocol/openid-connect/certs', new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) {
                exchange.responseHeaders.set('Content-Type', 'application/json')
                exchange.sendResponseHeaders(200, jwks.length)
                exchange.responseBody.withCloseable { it.write(jwks) }
            }
        })
        server.start()
        new FakeMachineToken(key, server, issuer)
    }

    void writeBinding() {
        def file = OidcFile.location()
        Files.createDirectories(file.parent)
        String text = '# Odisee OpenID Connect. The server reads this file on use.\n' +
                "issuer ${issuer}\n" +
                "audience odisee\n" +
                "ui-client odisee-ui\n"
        Files.writeString(file, text)
    }

    /**
     * Claims in {@code extra} are copied onto the token.
     * {@code iss} defaults to this server. {@code aud} defaults to {@code odisee}.
     */
    String sign(Map<String, Object> extra = [:]) {
        String iss = extra.containsKey('iss') ? extra.iss.toString() : issuer
        List<String> audience
        if (!extra.containsKey('aud')) {
            audience = ['odisee']
        } else if (extra.aud instanceof List) {
            audience = extra.aud.collect { it.toString() }
        } else {
            audience = [extra.aud.toString()]
        }
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .issuer(iss)
                .audience(audience)
                .subject(extra.sub?.toString() ?: 'service-account')
                .issueTime(Date.from(Instant.now()))
                .expirationTime(Date.from(Instant.now().plusSeconds(300)))
        extra.each { Object name, Object value ->
            String claim = name.toString()
            if (claim == 'iss' || claim == 'aud' || claim == 'sub') {
                return
            }
            claims.claim(claim, value)
        }
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).type(JOSEObjectType.JWT).build(),
                claims.build()
        )
        jwt.sign(new RSASSASigner(key))
        jwt.serialize()
    }

    String signWithOtherKey(Map<String, Object> extra = [:]) {
        RSAKey other = new RSAKeyGenerator(2048).keyID('odisee-other').generate()
        String token = sign(extra)
        SignedJWT parsed = SignedJWT.parse(token)
        SignedJWT resigned = new SignedJWT(parsed.header, parsed.JWTClaimsSet)
        resigned.sign(new RSASSASigner(other))
        resigned.serialize()
    }

    void stop() {
        server?.stop(0)
    }

}
