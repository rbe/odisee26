package org.odisee.document

import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.odisee.ooo.connection.OdiseeServerException
import org.odisee.ooo.connection.OdiseeServerRuntimeException
import org.odisee.ooo.connection.UnoDeadlineExceeded
import org.xml.sax.SAXException

import java.nio.file.Files
import java.nio.file.Path

/**
 * Test 4. Missing template, a bad post-process action, and an empty office pool
 * each get their own status. The response body is the exception message.
 */
class HttpStatusTest extends GroovyTestCase {

    void testMissingTemplateIsNotFound() {
        Path requestDir = Files.createTempDirectory('odisee-request')
        try {
            Path shared = OdiseePath.ODISEE_VAR.resolve('template')
            Files.createDirectories(shared)
            Files.write(shared.resolve('DoesNotExist.ott'), 'shared'.getBytes('UTF-8'))
            Files.createDirectories(OdiseePath.ODISEE_VAR.resolve('user').resolve('odisee').resolve('template'))
            TemplateService templates = new TemplateService()
            Map arg = RequestContext.create()
            arg.principal = [getName: { 'odisee' }] as java.security.Principal
            arg.requestDir = requestDir
            arg.template = 'DoesNotExist'
            arg.revision = '1'
            templates.copyTemplateToRequest(arg)
            fail('template is missing')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
            assertTrue(e.message.contains("user 'odisee'"))
            assertBody(e, OdiseeException.NOT_FOUND)
        } finally {
            requestDir.toFile().deleteDir()
        }
    }

    void testUnsupportedActionIsBadRequest() {
        OdiseeException error = new OdiseeException("Unsupported post-process action 'delete-everything'", OdiseeException.BAD_REQUEST)
        assertBody(error, OdiseeException.BAD_REQUEST)
    }

    void testOfficePoolFailureIsUnavailable() {
        OdiseeServerException error = new OdiseeServerException('Could not fetch connection from pool, sorry.')
        assertBody(error, 503)
    }

    void testOfficeDeadlineIsUnavailable() {
        UnoDeadlineExceeded error = new UnoDeadlineExceeded('save', 120000)
        assertBody(error, 503)
    }

    void testParseFailureIsBadRequest() {
        SAXException error = new SAXException('DOCTYPE is disallowed')
        assertBody(error, OdiseeException.BAD_REQUEST)
    }

    void testNoLoginIsUnauthorized() {
        OdiseeException error = new OdiseeException('Authentication required', OdiseeException.UNAUTHORIZED)
        assertBody(error, OdiseeException.UNAUTHORIZED)
    }

    void testForbiddenIsForbidden() {
        OdiseeException error = new OdiseeException('Admin role required', OdiseeException.FORBIDDEN)
        assertBody(error, OdiseeException.FORBIDDEN)
    }

    void testExistingUserIsConflict() {
        OdiseeException error = new OdiseeException("User 'ada' already exists", OdiseeException.CONFLICT)
        assertBody(error, OdiseeException.CONFLICT)
    }

    void testInstructionFailureIsUnprocessable() {
        OdiseeException error = new OdiseeException('Document instructions failed: userfield Hallo: missing', OdiseeException.UNPROCESSABLE)
        assertBody(error, OdiseeException.UNPROCESSABLE)
    }

    void testPoolBootstrapRuntimeFailureIsUnavailable() {
        OdiseeServerRuntimeException error = new OdiseeServerRuntimeException('[group=group0] No office connections could be bootstrapped')
        assertBody(error, 503)
    }

    void testRequestServicePoolSetupFailureMapsTo503AndSynchronizesAccess() {
        Path odiinst = OdiseePath.ODISEE_HOME.resolve(OdiseePath.S_ETC_ODIINST)
        byte[] previous = Files.exists(odiinst) ? Files.readAllBytes(odiinst) : null
        try {
            Files.deleteIfExists(odiinst)
            RequestService missing = new RequestService()
            try {
                missing.getOfficeConnectionFactory()
                fail('missing odiinst')
            } catch (Throwable error) {
                assertBody(error, 503)
            }

            Files.createDirectories(odiinst.parent)
            Files.writeString(odiinst, '# only comments\n')
            RequestService empty = new RequestService()
            try {
                empty.refreshPool()
                fail('empty odiinst')
            } catch (Throwable error) {
                assertBody(error, 503)
            }

            Files.writeString(odiinst, 'odi1|127.0.0.1|2091|/usr/lib/libreoffice||nologo|true|group0\n')
            RequestService configured = new RequestService()
            def first = configured.officeConnectionFactory
            assertNotNull(first)
            assertEquals(1, first.poolSize())

            Files.writeString(odiinst,
                    'odi1|127.0.0.1|2091|/usr/lib/libreoffice||nologo|true|group0\n'
                            + 'odi2|127.0.0.1|2092|/usr/lib/libreoffice||nologo|true|writers\n')
            def reloaded = configured.officeConnectionFactory
            assertSame(first, reloaded)
            assertEquals(2, reloaded.poolSize())
        } finally {
            if (previous != null) {
                Files.createDirectories(odiinst.parent)
                Files.write(odiinst, previous)
            } else {
                Files.deleteIfExists(odiinst)
            }
        }
    }

    void testUnexpectedServerExceptionsAreInternalServerError() {
        assertBody(new NullPointerException('boom'), OdiseeException.SERVER_ERROR)
        assertBody(new IOException('disk full'), OdiseeException.SERVER_ERROR)
        assertBody(new IllegalStateException('unexpected state'), OdiseeException.SERVER_ERROR)
    }

    private static void assertBody(Throwable error, int status) {
        int[] seen = [0] as int[]
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        def response = new Object() {
            void reset() {
            }

            void setStatus(int value) {
                seen[0] = value
            }

            OutputStream getOutputStream() {
                bytes
            }
        }
        HttpStatuses.apply(response, error)
        assertEquals(status, seen[0])
        assertEquals(error.message + '\n', bytes.toString('UTF-8'))
    }

}
