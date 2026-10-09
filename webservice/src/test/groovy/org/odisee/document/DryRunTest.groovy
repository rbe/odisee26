package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.DOMBuilder
import groovy.xml.XmlSlurper
import org.odisee.api.OdiseeException
import org.odisee.io.TenantPaths
import org.odisee.ooo.connection.UnoDeadlineExceeded
import org.w3c.dom.Element

import java.nio.file.Files
import java.nio.file.Path
import java.security.Principal

class DryRunTest extends GroovyTestCase {

    void testDryRunFlagSkipsTheSaveAndADeadlineStillDropsTheSlot() {
        assertFalse(OdiseeXmlCategory.writesFile([dryRun: true]))
        assertTrue(OdiseeXmlCategory.writesFile([dryRun: false]))
        assertTrue(OdiseeXmlCategory.writesFile([:]))
        assertTrue(DocumentController.dryRunRequested('true'))
        assertTrue(DocumentController.dryRunRequested('TRUE'))
        assertFalse(DocumentController.dryRunRequested('false'))
        assertFalse(DocumentController.dryRunRequested(null))

        OdiseeException instruction = new OdiseeException(
                'Document instructions failed: userfield Hallo: missing', OdiseeException.UNPROCESSABLE)
        assertFalse(OdiseeXmlCategory.dropsSlot(instruction))
        assertEquals(OdiseeException.UNPROCESSABLE, instruction.httpStatus)
        assertTrue(OdiseeXmlCategory.dropsSlot(new UnoDeadlineExceeded('instruction', 50)))
    }

    void testGroupNameComesFromTheV2Element() {
        def writers = new XmlSlurper().parseText('<request><group name="writers"/><template name="Letter"/></request>')
        def omitted = new XmlSlurper().parseText('<request><template name="Letter"/></request>')
        def legacy = new XmlSlurper().parseText('<request><ooo group="writers"/><template name="Letter"/></request>')

        assertEquals('writers', OdiseeXmlCategory.groupOf(writers))
        assertEquals('group0', OdiseeXmlCategory.groupOf(omitted))
        assertEquals('group0', OdiseeXmlCategory.groupOf(legacy))
    }

    void testDryRunDoesNotWriteUnderOutput() {
        Path output = TenantPaths.outputDir('drynora')
        Set<String> before = names(output)
        OdiseeService service = new OdiseeService()
        service.templateService = new TemplateService()
        service.requestService = new RequestService()
        try {
            service.generateDocument(principal('drynora'), request(), true)
            fail('missing template')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
        }
        assertEquals(before, names(output))
    }

    void testUserCannotSeeAnotherUsersRevisionDirectory() {
        Path ada = TenantPaths.templateDir('revada')
        Path bee = TenantPaths.templateDir('revbee').resolve('Letter').resolve('rev')
        Files.createDirectories(ada)
        Files.createDirectories(bee)
        Files.write(bee.resolve('4.ott'), 'bee'.getBytes('UTF-8'))
        TemplateService templates = new TemplateService()
        Map arg = RequestContext.create()
        arg.principal = principal('revada')
        arg.requestDir = Files.createTempDirectory('odisee-request')
        arg.template = 'Letter'
        arg.revision = 'LATEST'
        try {
            templates.copyTemplateToRequest(arg)
            fail('ada has no revision')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
            assertTrue(e.message.contains("user 'revada'"))
        } finally {
            arg.requestDir.toFile().deleteDir()
        }
        Map bees = RequestContext.create()
        bees.principal = principal('revbee')
        bees.requestDir = Files.createTempDirectory('odisee-request')
        bees.template = 'Letter'
        bees.revision = '4'
        try {
            templates.copyTemplateToRequest(bees)
            assertEquals('4', bees.revision.toString())
            assertTrue(bees.templateFile.startsWith(bee.parent))
        } finally {
            bees.requestDir.toFile().deleteDir()
        }
    }

    private static Principal principal(String name) {
        [getName: { name }] as Principal
    }

    private static Element request() {
        DOMBuilder.parse(new StringReader('''<odisee>
  <request name="Dry">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">dry</userfield></instructions>
  </request>
</odisee>''')).documentElement
    }

    private static Set<String> names(Path dir) {
        if (!Files.isDirectory(dir)) {
            return [] as Set
        }
        Set<String> found = [] as Set
        Files.walk(dir).withCloseable { stream ->
            stream.each { found << dir.relativize(it).toString() }
        }
        found
    }

}
