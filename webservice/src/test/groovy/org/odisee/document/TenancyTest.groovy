package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.DOMBuilder
import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.odisee.io.TenantPaths
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.w3c.dom.Element

import java.nio.file.Files
import java.nio.file.Path
import java.security.Principal

class TenancyTest extends GroovyTestCase {

    void testNoPrincipalIsUnauthorizedAndCreatesNothing() {
        Path users = OdiseePath.ODISEE_VAR.resolve('user')
        Set<String> before = names(users)
        OdiseeService service = new OdiseeService()
        try {
            service.generateDocument(null, null)
            fail('no principal')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNAUTHORIZED, e.httpStatus)
        }
        assertEquals(before, names(users))
    }

    void testBadPrincipalNameIs400AndCreatesNothingOutsideUser() {
        OdiseeService service = new OdiseeService()
        try {
            service.generateDocument(principal('../evil'), null)
            fail('bad name')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
        assertFalse(Files.exists(OdiseePath.ODISEE_VAR.resolve('evil')))
        assertFalse(Files.exists(OdiseePath.ODISEE_VAR.parent.resolve('evil')))
    }

    void testSecurityContextIsTheCallerWhenTheRequestHasNone() {
        SecurityContextHolder.context.authentication =
                new UsernamePasswordAuthenticationToken('ada', 'secret', [])
        try {
            java.security.Principal caller = DocumentController.requireCaller(DocumentController.callerFromContext(null))
            assertEquals('ada', caller.name)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    void testAnonymousSecurityContextIs401() {
        SecurityContextHolder.context.authentication = new AnonymousAuthenticationToken(
                'key', 'anonymousUser', [new SimpleGrantedAuthority('ROLE_ANONYMOUS')])
        try {
            DocumentController.requireCaller(DocumentController.callerFromContext(null))
            fail('anonymous')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNAUTHORIZED, e.httpStatus)
        } finally {
            SecurityContextHolder.clearContext()
        }
    }

    void testCallerNameWithSeparatorIs400() {
        try {
            DocumentController.requireCaller(principal('ada/extra'))
            fail('separator')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
    }

    void testNoLoginCannotCreateAUser() {
        try {
            DocumentController.requireAdmin(null, true)
            fail('no login')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNAUTHORIZED, e.httpStatus)
        }
    }

    void testLoggedInUserWithoutAdminIsForbidden() {
        try {
            DocumentController.requireAdmin(principal('ada'), false)
            fail('not admin')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.FORBIDDEN, e.httpStatus)
        }
    }

    void testUserDoesNotSeeAnotherUsersTemplateOrTheSharedDirectory() {
        Path shared = OdiseePath.ODISEE_VAR.resolve('template')
        Files.createDirectories(shared)
        Files.write(shared.resolve('Letter.ott'), 'shared'.getBytes('UTF-8'))
        Path bee = TenantPaths.templateDir('bee')
        Files.createDirectories(bee)
        Files.write(bee.resolve('Letter.ott'), 'bee'.getBytes('UTF-8'))

        TemplateService templates = new TemplateService()
        Map arg = RequestContext.create()
        arg.principal = principal('ada')
        arg.requestDir = Files.createTempDirectory('odisee-request')
        arg.template = 'Letter'
        arg.revision = '1'
        try {
            templates.copyTemplateToRequest(arg)
            fail('ada has no template')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
            assertTrue(e.message.contains("user 'ada'"))
            assertTrue(arg.templateDir.toString().contains("${File.separator}user${File.separator}ada${File.separator}template"))
        } finally {
            arg.requestDir.toFile().deleteDir()
        }
    }

    void testLatestRevisionStaysInsideTheUserDirectory() {
        Path dir = TenantPaths.templateDir('ada')
        Files.createDirectories(dir)
        Files.write(dir.resolve('Letter_rev2.ott'), 'two'.getBytes('UTF-8'))
        Files.write(dir.resolve('Letter_rev10.ott'), 'ten'.getBytes('UTF-8'))
        TemplateService templates = new TemplateService()
        Map arg = RequestContext.create()
        arg.principal = principal('ada')
        arg.requestDir = Files.createTempDirectory('odisee-request')
        arg.template = 'Letter'
        arg.revision = 'LATEST'
        try {
            templates.copyTemplateToRequest(arg)
            assertEquals('10', arg.revision.toString())
            assertEquals('Letter_rev10.ott', arg.templateFile.fileName.toString())
            assertTrue(arg.templateFile.startsWith(dir))
        } finally {
            arg.requestDir.toFile().deleteDir()
        }
    }

    void testMissingTemplateWritesUnderTheUserOutputDirectory() {
        Files.createDirectories(OdiseePath.ODISEE_VAR.resolve('template'))
        Files.write(OdiseePath.ODISEE_VAR.resolve('template').resolve('Letter.ott'), 'shared'.getBytes('UTF-8'))
        OdiseeService service = new OdiseeService()
        service.templateService = new TemplateService()
        service.requestService = new RequestService()
        try {
            service.generateDocument(principal('cara'), request())
            fail('missing template')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
            assertTrue(e.message.contains("user 'cara'"))
        }
        Path output = TenantPaths.outputDir('cara')
        assertTrue(Files.isDirectory(output))
        Files.newDirectoryStream(output).withCloseable { stream ->
            assertTrue(stream.iterator().hasNext())
        }
        assertFalse(Files.exists(OdiseePath.ODISEE_VAR.resolve('document')))
    }

    private static Principal principal(String name) {
        [getName: { name }] as Principal
    }

    private static Element request() {
        DOMBuilder.parse(new StringReader('''<odisee>
  <request name="One">
    <template name="Letter" outputFormat="pdf"/>
  </request>
</odisee>''')).documentElement
    }

    private static Set<String> names(Path dir) {
        if (!Files.isDirectory(dir)) {
            return [] as Set
        }
        Set<String> found = [] as Set
        Files.newDirectoryStream(dir).withCloseable { stream ->
            stream.each { found << it.fileName.toString() }
        }
        found
    }

}
