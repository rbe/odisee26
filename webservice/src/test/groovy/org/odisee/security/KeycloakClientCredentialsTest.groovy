package org.odisee.security

import groovy.json.JsonSlurper
import groovy.test.GroovyTestCase
import org.springframework.security.oauth2.jwt.Jwt

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64

/**
 * One client-credentials call against Keycloak in Docker.
 * {@code :webservice:test} does not run this class.
 */
class KeycloakClientCredentialsTest extends GroovyTestCase {

    @Override
    protected void tearDown() {
        Files.deleteIfExists(MachineFile.location())
    }

    void testClientCredentialsTokenMapsToTheConfiguredUser() {
        String issuer = System.getProperty('odisee.keycloak.issuer')
        String tokenUrl = System.getProperty('odisee.keycloak.tokenUrl')
        assertNotNull('odisee.keycloak.issuer', issuer)
        assertNotNull('odisee.keycloak.tokenUrl', tokenUrl)

        def realm = new JsonSlurper().parse(new File('src/main/docker/keycloak/odisee-realm.json'))
        String clientId = realm.clients[0].clientId
        String secret = realm.clients[0].secret
        String audience = realm.clients[0].protocolMappers[0].config.'included.custom.audience'
        assertEquals('odisee-machine', clientId)
        assertEquals('odisee', audience)

        def file = MachineFile.location()
        Files.createDirectories(file.parent)
        Files.writeString(file, "${clientId} press ${issuer} ${audience}\n")

        String body = 'grant_type=client_credentials' +
                '&client_id=' + URLEncoder.encode(clientId, 'UTF-8') +
                '&client_secret=' + URLEncoder.encode(secret, 'UTF-8')
        HttpURLConnection connection = new URL(tokenUrl).openConnection() as HttpURLConnection
        connection.requestMethod = 'POST'
        connection.doOutput = true
        connection.setConnectTimeout(5000)
        connection.setReadTimeout(10000)
        connection.setRequestProperty('Content-Type', 'application/x-www-form-urlencoded')
        connection.outputStream.withCloseable { it.write(body.getBytes(StandardCharsets.UTF_8)) }
        int status = connection.responseCode
        InputStream stream = status >= 400 ? connection.errorStream : connection.inputStream
        String text = stream.getText('UTF-8')
        assertEquals(text, 200, status)

        def json = new JsonSlurper().parseText(text)
        assertEquals('Bearer', json.token_type)
        String access = json.access_token
        assertNotNull(access)
        def claims = new JsonSlurper().parseText(payload(access))
        assertEquals(claims.toString(), issuer, claims.iss)
        assertEquals(clientId, claims.azp)
        assertTrue(claims.aud instanceof Collection ? claims.aud.contains(audience) : claims.aud == audience)
        assertEquals('service-account-' + clientId, claims.preferred_username)
        assertFalse(claims.preferred_username == 'press')

        Jwt jwt = new MachineJwtDecoder().decode(access)
        assertEquals('press', MachineToken.user(jwt))
    }

    private static String payload(String jwt) {
        String part = jwt.split('\\.')[1]
        int pad = (4 - (part.length() % 4)) % 4
        part = part + ('=' * pad)
        new String(Base64.urlDecoder.decode(part), StandardCharsets.UTF_8)
    }

}
