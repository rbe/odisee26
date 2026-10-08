package org.odisee.document

import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.odisee.io.TenantPaths
import org.odisee.security.PasswordFile
import org.odisee.security.UserCredentials
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder

import java.nio.file.Files
import java.nio.file.Path

class UserAccountServiceTest extends GroovyTestCase {

    private UserAccountService service

    @Override
    protected void setUp() {
        Files.deleteIfExists(PasswordFile.location())
        service = new UserAccountService()
        service.passwordEncoder = new BCryptPasswordEncoder()
    }

    @Override
    protected void tearDown() {
        Files.deleteIfExists(PasswordFile.location())
        deleteUser('ada')
    }

    void testCreateWritesThePasswordFileAndTheUserDirectories() {
        String name = service.create('ada', 's3cret-value')

        assertEquals('ada', name)
        String text = Files.readString(PasswordFile.location())
        assertFalse(text.contains('s3cret-value'))
        assertFalse(PasswordFile.find(PasswordFile.location(), 'ada').admin)
        assertTrue(Files.isDirectory(TenantPaths.templateDir('ada')))
        assertTrue(Files.isDirectory(TenantPaths.workDir('ada')))
        assertTrue(Files.isDirectory(TenantPaths.outputDir('ada')))
    }

    void testExistingUserIs409() {
        service.create('ada', 's3cret-value')
        try {
            service.create('ada', 'other-value')
            fail('exists')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.CONFLICT, e.httpStatus)
        }
    }

    void testBadNameIs400AndCreatesNoDirectory() {
        try {
            service.create('../ada', 's3cret-value')
            fail('bad name')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
        assertFalse(Files.exists(OdiseePath.ODISEE_VAR.resolve('ada')))
        assertFalse(Files.exists(PasswordFile.location()))
    }

    void testJsonBodySuppliesNameAndPassword() {
        UserCredentials credentials = UserCredentials.fromBody('application/json', '{"name":"ada","password":"s3cret-value"}'.getBytes('UTF-8'))

        assertEquals('ada', credentials.name)
        assertEquals('s3cret-value', credentials.password)
    }

    void testBadJsonIs400() {
        try {
            UserCredentials.fromBody('application/json', 'not-json'.getBytes('UTF-8'))
            fail('json')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
    }

    private static void deleteUser(String name) {
        PathTree.delete(OdiseePath.ODISEE_VAR.resolve('user').resolve(name))
    }

}
