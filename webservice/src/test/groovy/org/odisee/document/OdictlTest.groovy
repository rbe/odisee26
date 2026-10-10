package org.odisee.document

import groovy.test.GroovyTestCase
import org.odisee.io.OdiseePath
import org.odisee.io.TenantPaths
import org.odisee.security.PasswordFile
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.TimeUnit

/**
 * {@code odictl} writes the files the server reads.
 * The script runs from the source tree. It does not need Docker.
 */
class OdictlTest extends GroovyTestCase {

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder()

    @Override
    protected void setUp() {
        Files.deleteIfExists(PasswordFile.location())
        Files.deleteIfExists(CallbackAllowList.location())
        Files.deleteIfExists(BucketFile.location())
        deleteUser('ctlada')
        deleteUser('ctlbucket')
    }

    @Override
    protected void tearDown() {
        Files.deleteIfExists(PasswordFile.location())
        Files.deleteIfExists(CallbackAllowList.location())
        Files.deleteIfExists(BucketFile.location())
        deleteUser('ctlada')
        deleteUser('ctlbucket')
    }

    void testUserWritesBcryptLineAndDirectories() {
        String output = succeed('user', 'ctlada', 's3cret-value')

        assertTrue(output.contains("User 'ctlada' created."))
        String text = Files.readString(PasswordFile.location())
        assertFalse(text.contains('s3cret-value'))
        PasswordFile.Record record = PasswordFile.find(PasswordFile.location(), 'ctlada')
        assertNotNull(record)
        assertFalse(record.admin)
        assertTrue(encoder.matches('s3cret-value', record.hash))
        assertTrue(Files.isDirectory(TenantPaths.templateDir('ctlada')))
        assertTrue(Files.isDirectory(TenantPaths.workDir('ctlada')))
        assertTrue(Files.isDirectory(TenantPaths.outputDir('ctlada')))
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(PasswordFile.location())
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))
    }

    void testBadUserNameFailsAndCreatesNothing() {
        String output = fail('user', '../ctlada', 's3cret-value')

        assertTrue(output.contains('Invalid user'))
        assertFalse(Files.exists(PasswordFile.location()))
        assertFalse(Files.exists(OdiseePath.ODISEE_VAR.resolve('ctlada')))
        assertFalse(Files.exists(OdiseePath.ODISEE_VAR.resolve('user').resolve('ctlada')))
    }

    void testExistingUserFails() {
        succeed('user', 'ctlada', 's3cret-value')
        String hash = PasswordFile.find(PasswordFile.location(), 'ctlada').hash

        String output = fail('user', 'ctlada', 'other-value')

        assertTrue(output.contains("User 'ctlada' already exists"))
        assertEquals(hash, PasswordFile.find(PasswordFile.location(), 'ctlada').hash)
        assertFalse(PasswordFile.find(PasswordFile.location(), 'ctlada').admin)
    }

    void testUserRequiresPassword() {
        String missing = fail('user', 'ctlada')
        String blank = fail('user', 'ctlada', '   ')

        assertTrue(missing.contains('Password is required') || missing.contains('usage:'))
        assertTrue(blank.contains('Password is required'))
        assertFalse(Files.exists(PasswordFile.location()))
        assertFalse(Files.exists(TenantPaths.userRoot('ctlada')))
    }

    void testCallbackHostAppendsAHostTheServerReads() {
        succeed('callback-host', 'hooks.example')
        succeed('callback-host', 'Hooks.Example')

        assertTrue(CallbackAllowList.permits('https://hooks.example/done'))
        assertFalse(CallbackAllowList.permits('https://other.example/done'))
        List<String> hosts = Files.readAllLines(CallbackAllowList.location()).findAll { it.trim() && !it.trim().startsWith('#') }
        assertEquals(['hooks.example'], hosts)
    }

    void testBadCallbackHostFails() {
        String output = fail('callback-host', 'https://hooks.example/done')

        assertTrue(output.contains('Invalid callback host'))
        assertFalse(Files.exists(CallbackAllowList.location()))
        assertFalse(CallbackAllowList.permits('https://hooks.example/done'))
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
        String output = fail('bucket', 'ctlbucket', 'ftp://minio:9000', 'us-east-1', 'odisee-files', 'minio', 'minio-secret')

        assertTrue(output.contains('Invalid endpoint'))
        assertFalse(Files.exists(BucketFile.location()))
    }

    private static String succeed(String... args) {
        run(true, args)
    }

    private static String fail(String... args) {
        run(false, args)
    }

    private static String run(boolean ok, String... args) {
        File script = new File('src/main/docker/bin/odictl')
        assertTrue(script.file)
        String home = System.getProperty('ODISEE_HOME')
        assertNotNull(home)
        ProcessBuilder builder = new ProcessBuilder([script.absolutePath, *args])
        builder.environment().put('ODISEE_HOME', home)
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

    private static void deleteUser(String name) {
        PathTree.delete(OdiseePath.ODISEE_VAR.resolve('user').resolve(name))
    }

}
