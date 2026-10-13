package org.odisee.security

import groovy.json.JsonSlurper
import groovy.test.GroovyTestCase
import org.odisee.document.PathTree
import org.odisee.io.OdiseePath
import org.odisee.io.TenantPaths
import org.springframework.security.oauth2.jwt.Jwt

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit

/**
 * {@code odictl user} creates a Keycloak client, then one client-credentials call
 * maps that client id to the Odisee user.
 * {@code :webservice:test} does not run this class.
 */
class KeycloakClientCredentialsTest extends GroovyTestCase {

    @Override
    protected void tearDown() {
        Files.deleteIfExists(OidcFile.location())
        PathTree.delete(OdiseePath.ODISEE_VAR.resolve('user').resolve('press'))
    }

    void testClientCredentialsTokenMapsToTheUserOdictlCreated() {
        String issuer = System.getProperty('odisee.keycloak.issuer')
        String tokenUrl = System.getProperty('odisee.keycloak.tokenUrl')
        String admin = System.getProperty('odisee.keycloak.admin')
        String adminPassword = System.getProperty('odisee.keycloak.adminPassword')
        assertNotNull('odisee.keycloak.issuer', issuer)
        assertNotNull('odisee.keycloak.tokenUrl', tokenUrl)

        String output = odictl(issuer, admin, adminPassword, 'user', 'press')
        assertTrue(output.contains("User 'press' created."))
        String secret = output.readLines().find { it.startsWith('client_secret=') }?.substring('client_secret='.length())
        assertNotNull(secret)
        assertTrue(Files.isDirectory(TenantPaths.templateDir('press')))
        assertFalse(Files.exists(OdiseePath.ODISEE_HOME.resolve('etc').resolve('users')))
        assertFalse(Files.readString(OidcFile.location()).contains(secret))

        String body = 'grant_type=client_credentials' +
                '&client_id=' + URLEncoder.encode('press', 'UTF-8') +
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
        String access = json.access_token
        def claims = new JsonSlurper().parseText(payload(access))
        assertEquals(claims.toString(), issuer, claims.iss)
        assertEquals('press', claims.azp)
        assertEquals('service-account-press', claims.preferred_username)
        assertTrue(claims.aud instanceof Collection ? claims.aud.contains('odisee') : claims.aud == 'odisee')

        Jwt jwt = new MachineJwtDecoder().decode(access)
        assertEquals('press', OidcToken.user(jwt))
    }

    private static String odictl(String issuer, String admin, String adminPassword, String... args) {
        File script = new File('src/main/docker/bin/odictl')
        String home = System.getProperty('ODISEE_HOME')
        ProcessBuilder builder = new ProcessBuilder([script.absolutePath, *args])
        builder.environment().put('ODISEE_HOME', home)
        builder.environment().put('ODISEE_KEYCLOAK_ISSUER', issuer)
        builder.environment().put('ODISEE_KEYCLOAK_ADMIN', admin)
        builder.environment().put('ODISEE_KEYCLOAK_ADMIN_PASSWORD', adminPassword)
        builder.redirectErrorStream(true)
        Process process = builder.start()
        String output = process.inputStream.getText('UTF-8')
        boolean finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        assertTrue(output, finished)
        assertEquals(output, 0, process.exitValue())
        output
    }

    private static String payload(String jwt) {
        String part = jwt.split('\\.')[1]
        int pad = (4 - (part.length() % 4)) % 4
        part = part + ('=' * pad)
        new String(Base64.urlDecoder.decode(part), StandardCharsets.UTF_8)
    }

}
