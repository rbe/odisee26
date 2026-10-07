package org.odisee.security

import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

class PasswordFileTest extends GroovyTestCase {

    private Path file
    private PasswordEncoder encoder = new BCryptPasswordEncoder()

    @Override
    protected void setUp() {
        file = Files.createTempDirectory('odisee-users').resolve('users')
    }

    @Override
    protected void tearDown() {
        file?.parent?.toFile()?.deleteDir()
    }

    void testCreateStoresBcryptAndNotThePassword() {
        PasswordFile.create(file, 'ada', 's3cret-value', false, encoder)

        String text = Files.readString(file)
        assertFalse(text.contains('s3cret-value'))
        PasswordFile.Record record = PasswordFile.find(file, 'ada')
        assertNotNull(record)
        assertFalse(record.admin)
        assertTrue(encoder.matches('s3cret-value', record.hash))
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(file)
        assertFalse(perms.contains(PosixFilePermission.GROUP_READ))
        assertFalse(perms.contains(PosixFilePermission.OTHERS_READ))
    }

    void testExistingUserIsConflict() {
        PasswordFile.create(file, 'ada', 's3cret-value', false, encoder)
        try {
            PasswordFile.create(file, 'ada', 'other-value', true, encoder)
            fail('exists')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.CONFLICT, e.httpStatus)
        }
        assertFalse(PasswordFile.find(file, 'ada').admin)
    }

    void testBlankPasswordIs400() {
        try {
            PasswordFile.create(file, 'ada', '   ', false, encoder)
            fail('blank')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
        assertFalse(Files.exists(file))
    }

    void testBadNameIs400() {
        try {
            PasswordFile.create(file, '../ada', 's3cret-value', false, encoder)
            fail('bad name')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
        assertFalse(Files.exists(file))
    }

    void testBootstrapNeedsBothValuesAndOnlyWhenTheFileIsMissing() {
        PasswordFile.bootstrap(file, 'root', null, encoder)
        PasswordFile.bootstrap(file, 'root', '   ', encoder)
        PasswordFile.bootstrap(file, null, 's3cret-value', encoder)
        PasswordFile.bootstrap(file, '   ', 's3cret-value', encoder)
        assertFalse(Files.exists(file))

        PasswordFile.bootstrap(file, 'root', 's3cret-value', encoder)
        PasswordFile.Record admin = PasswordFile.find(file, 'root')
        assertTrue(admin.admin)
        assertTrue(encoder.matches('s3cret-value', admin.hash))
        assertFalse(Files.readString(file).contains('s3cret-value'))

        PasswordFile.bootstrap(file, 'other', 'nope', encoder)
        assertNull(PasswordFile.find(file, 'other'))
    }

}
