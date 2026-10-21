package org.odisee.document

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import groovy.test.GroovyTestCase
import org.odisee.io.OdiseePath
import org.odisee.io.TenantPaths
import org.odisee.security.OidcFile

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * {@code odictl} writes the files the server reads.
 * The script runs from the source tree. It does not need Docker.
 */
class OdictlTest extends GroovyTestCase {

    private HttpServer keycloak

    private String issuer

    @Override
    protected void setUp() {
        Files.deleteIfExists(OidcFile.location())
        Files.deleteIfExists(CallbackAllowList.location())
        Files.deleteIfExists(BucketFile.location())
        deleteUser('ctlada')
        deleteUser('ctlbucket')
        keycloak = fakeKeycloak()
        issuer = "http://127.0.0.1:${keycloak.address.port}/realms/odisee"
    }

    @Override
    protected void tearDown() {
        keycloak?.stop(0)
        Files.deleteIfExists(OidcFile.location())
        Files.deleteIfExists(CallbackAllowList.location())
        Files.deleteIfExists(BucketFile.location())
        deleteUser('ctlada')
        deleteUser('ctlbucket')
    }

    void testUserCreatesTheClientDirectoriesAndPrintsTheSecret() {
        String output = succeed('user', 'ctlada')

        assertTrue(output.contains("User 'ctlada' created."))
        assertTrue(output.contains('client_id=ctlada'))
        String secret = output.readLines().find { it.startsWith('client_secret=') }?.substring('client_secret='.length())
        assertNotNull(secret)
        assertTrue(secret.length() >= 32)
        assertFalse(Files.exists(OdiseePath.ODISEE_HOME.resolve('etc').resolve('users')))
        assertTrue(Files.isDirectory(TenantPaths.templateDir('ctlada')))
        assertTrue(Files.isDirectory(TenantPaths.workDir('ctlada')))
        assertTrue(Files.isDirectory(TenantPaths.outputDir('ctlada')))
        OidcFile.Record record = OidcFile.read(OidcFile.location())
        assertEquals(issuer, record.issuer)
        assertEquals('odisee', record.audience)
        assertEquals('odisee-ui', record.uiClient)
        String oidc = Files.readString(OidcFile.location())
        assertFalse(oidc.contains(secret))
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(OidcFile.location())
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))
    }

    void testBadUserNameFailsAndCreatesNothing() {
        String output = denied('user', '../ctlada')

        assertTrue(output.contains('Invalid user'))
        assertFalse(Files.exists(OidcFile.location()))
        assertFalse(Files.exists(TenantPaths.userRoot('ctlada')))
    }

    void testExistingUserFails() {
        succeed('user', 'ctlada')

        String output = denied('user', 'ctlada')

        assertTrue(output.contains("User 'ctlada' already exists"))
        assertTrue(Files.isDirectory(TenantPaths.templateDir('ctlada')))
    }

    void testUserTakesANameOnly() {
        String missing = denied('user')
        String extra = denied('user', 'ctlada', 's3cret-value')
        String reserved = denied('user', 'odisee-ui')

        assertTrue(missing.contains('usage:'))
        assertTrue(extra.contains('usage:'))
        assertTrue(reserved.contains('Invalid user'))
        assertFalse(Files.exists(TenantPaths.userRoot('ctlada')))
        assertFalse(Files.exists(TenantPaths.userRoot('odisee-ui')))
    }

    void testUserWithoutAdminCredentialsFails() {
        String output = run(false, ['ODISEE_KEYCLOAK_ADMIN': '', 'ODISEE_KEYCLOAK_ADMIN_PASSWORD': ''], 'user', 'ctlada')

        assertTrue(output.contains('Keycloak admin credentials are required'))
        assertFalse(Files.exists(TenantPaths.userRoot('ctlada')))
    }

    void testCallbackHostAppendsAHostTheServerReads() {
        succeed('callback-host', 'ctlada', 'hooks.example')
        succeed('callback-host', 'ctlada', 'Hooks.Example')
        succeed('callback-host', 'ctlbucket', 'other.example')

        assertTrue(CallbackAllowList.permits('ctlada', 'https://hooks.example/done'))
        assertFalse(CallbackAllowList.permits('ctlada', 'https://other.example/done'))
        assertTrue(CallbackAllowList.permits('ctlbucket', 'https://other.example/done'))
        assertFalse(CallbackAllowList.permits('ctlbucket', 'https://hooks.example/done'))
        List<String> lines = Files.readAllLines(CallbackAllowList.location()).findAll { it.trim() && !it.trim().startsWith('#') }
        assertEquals(['ctlada hooks.example', 'ctlbucket other.example'], lines)
    }

    void testBadCallbackHostFails() {
        String output = denied('callback-host', 'ctlada', 'https://hooks.example/done')
        String badUser = denied('callback-host', '../ctlada', 'hooks.example')

        assertTrue(output.contains('Invalid callback host'))
        assertTrue(badUser.contains('Invalid user'))
        assertFalse(Files.exists(CallbackAllowList.location()))
        assertFalse(CallbackAllowList.permits('ctlada', 'https://hooks.example/done'))
    }

    void testBucketWritesTheLineTheServerReads() {
        succeed('bucket', 'ctlbucket', 'http://minio:9000', 'us-east-1', 'odisee-files', 'minio', 'minio secret')

        BucketFile.Record record = BucketFile.require('ctlbucket')
        assertEquals('http://minio:9000', record.endpoint)
        assertEquals('us-east-1', record.region)
        assertEquals('odisee-files', record.bucket)
        assertEquals('minio', record.accessKey)
        assertEquals('minio secret', record.secret)
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(BucketFile.location())
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))

        succeed('bucket', 'ctlbucket', 'http://127.0.0.1:9000', 'us-east-1', 'other-files', 'minio', 'next secret')
        BucketFile.Record replaced = BucketFile.require('ctlbucket')
        assertEquals('other-files', replaced.bucket)
        assertEquals('next secret', replaced.secret)
        assertEquals('http://127.0.0.1:9000', replaced.endpoint)
    }

    void testBadBucketFails() {
        String output = denied('bucket', 'ctlbucket', 'ftp://minio:9000', 'us-east-1', 'odisee-files', 'minio', 'minio-secret')

        assertTrue(output.contains('Invalid endpoint'))
        assertFalse(Files.exists(BucketFile.location()))
    }

    void testUnsetHomeUsesTheUserShareDirectory() {
        Path tree = Files.createTempDirectory('odictl-home')
        try {
            Path home = tree.resolve('home')
            Files.createDirectories(home)
            String output = launch(true, [scriptPath(), 'callback-host', 'ctlpath', 'hooks.example'], [
                    HOME: home.toString(),
                    ODISEE_HOME: '',
                    JAVA_HOME: '',
                    PATH: '/usr/bin:/bin',
            ])

            assertTrue(output.contains("Callback host 'hooks.example' listed for 'ctlpath'."))
            Path root = home.resolve('.local/share/odisee')
            assertTrue(Files.readString(root.resolve('etc/callback-hosts')).contains('ctlpath hooks.example'))
            assertFalse(Files.exists(root.resolve('.fonts')))
            assertFalse(Files.exists(home.resolve('.bash_profile')))
            assertFalse(Files.exists(root.resolve('etc/odienv')))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testSetHomeWinsAndDoesNotNeedJavaHome() {
        Path tree = Files.createTempDirectory('odictl-set-home')
        try {
            Path home = tree.resolve('home')
            Path install = tree.resolve('opt')
            Path shim = tree.resolve('shim')
            Files.createDirectories(home)
            Files.createDirectories(install)
            Files.createDirectories(shim)
            Files.writeString(shim.resolve('java'), '#!/bin/sh\nexit 1\n')
            Files.setPosixFilePermissions(shim.resolve('java'), PosixFilePermissions.fromString('rwxr-xr-x'))
            String output = launch(true, [scriptPath(), 'callback-host', 'ctlhome', 'hooks.example'], [
                    HOME: home.toString(),
                    ODISEE_HOME: install.toString(),
                    JAVA_HOME: '',
                    PATH: "${shim}:/usr/bin:/bin",
            ])

            assertTrue(output.contains("Callback host 'hooks.example' listed for 'ctlhome'."))
            assertTrue(Files.isRegularFile(install.resolve('etc/callback-hosts')))
            assertFalse(Files.exists(home.resolve('.local/share/odisee')))
            assertFalse(Files.exists(install.resolve('.fonts')))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testLinkedCommandUsesTheDefaultHome() {
        Path tree = Files.createTempDirectory('odictl-link')
        try {
            Path home = tree.resolve('home')
            Path bindir = tree.resolve('bin')
            Files.createDirectories(home)
            Files.createDirectories(bindir)
            Files.createSymbolicLink(bindir.resolve('odictl'), Path.of(scriptPath()))
            String output = launch(true, ['/usr/bin/env', 'odictl', 'callback-host', 'ctllink', 'hooks.example'], [
                    HOME: home.toString(),
                    ODISEE_HOME: '',
                    JAVA_HOME: '',
                    PATH: "${bindir}:/usr/bin:/bin",
            ])

            assertTrue(output, output.contains("Callback host 'hooks.example' listed for 'ctllink'."))
            assertTrue(Files.readString(home.resolve('.local/share/odisee/etc/callback-hosts')).contains('ctllink hooks.example'))
        } finally {
            PathTree.delete(tree)
        }
    }

    void testJavaFromPathIsNotExported() {
        Path tree = Files.createTempDirectory('odictl-java')
        Path install = tree.resolve('odisee')
        try {
            Path home = tree.resolve('home')
            Path javaBin = tree.resolve('jdk/bin')
            Files.createDirectories(home)
            Files.createDirectories(install.resolve('etc'))
            Files.createDirectories(javaBin)
            Files.writeString(install.resolve('application.jar'), '')
            Path argsFile = tree.resolve('args.txt')
            Path envFile = tree.resolve('env.txt')
            Files.writeString(javaBin.resolve('java'), "#!/bin/sh\nprintf '%s\\n' \"\$*\" > '${argsFile}'\nenv > '${envFile}'\n")
            Files.setPosixFilePermissions(javaBin.resolve('java'), PosixFilePermissions.fromString('rwxr-xr-x'))
            String output = launch(true, [scriptPath(), '-q', 'start'], [
                    HOME: home.toString(),
                    ODISEE_HOME: install.toString(),
                    JAVA_HOME: '/decoy/jdk',
                    PATH: "${javaBin}:/usr/bin:/bin",
            ])

            assertTrue(output, output.contains('Starting Odisee Service'))
            for (int i = 0; i < 100 && !Files.exists(envFile); i++) {
                Thread.sleep(50)
            }
            assertTrue(output, Files.isRegularFile(envFile))
            List<String> env = Files.readAllLines(envFile)
            assertFalse(env.any { it.startsWith('JAVA_HOME=') })
            assertFalse(env.any { it.startsWith('ODISEE_ETC=') })
            assertTrue(env.any { it == "ODISEE_HOME=${install}" })
            String path = env.find { it.startsWith('PATH=') }
            assertNotNull(path)
            assertFalse(path.contains("${install}/bin"))
            String args = Files.readString(argsFile)
            assertTrue(args, args.contains('-jar'))
            assertTrue(args, args.contains(install.resolve('application.jar').toString()))
        } finally {
            Path pidfile = install.resolve('var/odisee.pid')
            if (Files.exists(pidfile)) {
                try {
                    long pid = Long.parseLong(Files.readString(pidfile).trim())
                    ProcessHandle.of(pid).ifPresent { handle ->
                        String command = handle.info().command().orElse('')
                        String args = handle.info().arguments().map { it.join(' ') }.orElse('')
                        if ((command + ' ' + args).contains(install.toString())) {
                            handle.destroyForcibly()
                        }
                    }
                } catch (NumberFormatException ignored) {
                }
            }
            PathTree.delete(tree)
        }
    }

    void testLoginProfileIsGone() {
        assertFalse(new File('src/main/docker/shell_profile').exists())
        assertFalse(new File('src/main/docker/etc/odienv').exists())
        String odictl = new File('src/main/docker/bin/odictl').getText('UTF-8')
        String odilib = new File('src/main/docker/bin/odilib').getText('UTF-8')
        String entry = new File('src/main/docker/entrypoint.sh').getText('UTF-8')
        String docker = new File('src/main/docker/Dockerfile').getText('UTF-8')
        String gradle = new File('../build.gradle.kts').getText('UTF-8')
        assertFalse(odictl.contains('odienv'))
        assertFalse(odictl.contains('export PATH'))
        assertFalse(odictl.contains('.fonts'))
        assertFalse(odilib.contains('.fonts'))
        assertFalse(odilib.contains('export PATH'))
        assertTrue(odilib.contains('/.local/share/odisee'))
        assertFalse(entry.contains('odienv'))
        assertFalse(docker.contains('shell_profile'))
        assertFalse(docker.contains('.bash_profile'))
        assertTrue(docker.contains('/home/odisee/.local/bin'))
        assertFalse(gradle.contains('shell_profile'))
        assertFalse(gradle.contains('.bash_profile'))
    }

    private String succeed(String... args) {
        run(true, [:], args)
    }

    private String denied(String... args) {
        run(false, [:], args)
    }

    private String run(boolean ok, Map<String, String> extra, String... args) {
        File script = new File('src/main/docker/bin/odictl')
        assertTrue(script.file)
        String home = System.getProperty('ODISEE_HOME')
        assertNotNull(home)
        ProcessBuilder builder = new ProcessBuilder([script.absolutePath, *args])
        builder.environment().put('ODISEE_HOME', home)
        builder.environment().put('ODISEE_KEYCLOAK_ISSUER', issuer)
        builder.environment().put('ODISEE_KEYCLOAK_ADMIN', 'admin')
        builder.environment().put('ODISEE_KEYCLOAK_ADMIN_PASSWORD', 'admin-secret')
        extra.each { String key, String value ->
            if (value == null || value.isEmpty()) {
                builder.environment().remove(key)
            } else {
                builder.environment().put(key, value)
            }
        }
        builder.redirectErrorStream(true)
        Process process = builder.start()
        String output = process.inputStream.getText('UTF-8')
        boolean finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        assertTrue(output, finished)
        if (ok) {
            assertEquals(output, 0, process.exitValue())
        } else {
            assertTrue(output, process.exitValue() != 0)
        }
        output
    }

    private static String scriptPath() {
        File script = new File('src/main/docker/bin/odictl')
        assertTrue(script.file)
        script.absolutePath
    }

    private String launch(boolean ok, List<String> command, Map<String, String> env) {
        ProcessBuilder builder = new ProcessBuilder(command)
        env.each { String key, String value ->
            if (value == null || value.isEmpty()) {
                builder.environment().remove(key)
            } else {
                builder.environment().put(key, value)
            }
        }
        builder.redirectErrorStream(true)
        Process process = builder.start()
        String output = process.inputStream.getText('UTF-8')
        boolean finished = process.waitFor(60, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
        }
        assertTrue(output, finished)
        if (ok) {
            assertEquals(output, 0, process.exitValue())
        } else {
            assertTrue(output, process.exitValue() != 0)
        }
        output
    }

    private static HttpServer fakeKeycloak() {
        HttpServer server = HttpServer.create(new InetSocketAddress('127.0.0.1', 0), 0)
        server.createContext('/realms/master/protocol/openid-connect/token', json('{"access_token":"admin-token"}'))
        server.createContext('/admin/realms/odisee/clients', empty(201))
        server.createContext('/admin/realms/odisee/users', empty(201))
        server.start()
        server
    }

    private static HttpHandler json(String body) {
        new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) {
                exchange.requestBody.readAllBytes()
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8)
                exchange.responseHeaders.set('Content-Type', 'application/json')
                exchange.sendResponseHeaders(200, bytes.length)
                exchange.responseBody.withCloseable { it.write(bytes) }
            }
        }
    }

    private static HttpHandler empty(int status) {
        new HttpHandler() {
            @Override
            void handle(HttpExchange exchange) {
                exchange.requestBody.readAllBytes()
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
        }
    }

    private static void deleteUser(String name) {
        PathTree.delete(OdiseePath.ODISEE_VAR.resolve('user').resolve(name))
    }

}
