package org.odisee.document

import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.odisee.ooo.connection.OdiseeServerException

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
            TemplateService templates = new TemplateService()
            Map arg = RequestContext.create()
            arg.principal = [getName: { 'odisee' }] as java.security.Principal
            arg.requestDir = requestDir
            arg.template = 'DoesNotExist'
            arg.revision = '1'
            Files.createDirectories(OdiseePath.ODISEE_VAR.resolve('template'))
            templates.copyTemplateToRequest(arg)
            fail('template is missing')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
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
