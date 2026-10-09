package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.DOMBuilder
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.w3c.dom.Element

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipInputStream

/**
 * Tests 1-3 against LibreOffice listening on 127.0.0.1:2002.
 * {@code ./gradlew libreOfficeTest} starts that process in Docker and mounts
 * {@code ODISEE_HOME} at the same path inside the container.
 */
class GenerationBasisTest extends GroovyTestCase {

    private static final int PORT = Integer.getInteger('odisee.libreoffice.port', 2002)

    private OdiseeService service

    private java.security.Principal caller = [getName: { 'odisee' }] as java.security.Principal

    @Override
    protected void setUp() {
        Socket socket = new Socket()
        try {
            socket.connect(new InetSocketAddress('127.0.0.1', PORT), 2000)
        } catch (IOException e) {
            fail("LibreOffice is not accepting connections on 127.0.0.1:${PORT}. Run ./gradlew libreOfficeTest")
        } finally {
            socket.close()
        }
        Path home = OdiseePath.ODISEE_HOME
        Files.createDirectories(home.resolve('etc'))
        Files.write(home.resolve('etc/odiinst'),
                "odi1|127.0.0.1|${PORT}|/usr/lib/libreoffice||nologo|true\n".getBytes('UTF-8'))
        Path templates = OdiseePath.ODISEE_VAR.resolve('user').resolve('odisee').resolve('template')
        Files.createDirectories(templates)
        MinimalTemplate.write(templates.resolve('Letter.ott'))

        RequestService requests = new RequestService()
        requests.afterPropertiesSet()
        service = new OdiseeService()
        service.templateService = new TemplateService()
        service.requestService = requests
        service.storageService = new StorageService()
        PdfService pdf = new PdfService()
        PostProcessService post = new PostProcessService()
        post.storageService = service.storageService
        post.pdfService = pdf
        service.postProcessService = post
    }

    void test1_twoCallsDoNotShareDocuments() {
        List first = service.generateDocument(caller, request('One', 'alpha-field'))
        List second = service.generateDocument(caller, request('Two', 'beta-field'))

        assertEquals(1, first.size())
        assertEquals(1, second.size())
        assertFalse(first.is(second))
        assertTrue(pdfText(first[0].bytes).contains('alpha-field'))
        assertTrue(pdfText(second[0].bytes).contains('beta-field'))
        assertFalse(pdfText(second[0].bytes).contains('alpha-field'))
    }

    void test2_twoRequestsStayInOrderAndMerge() {
        String xml = '''<odisee>
  <request name="Left">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">left-side</userfield></instructions>
  </request>
  <request name="Right">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">right-side</userfield></instructions>
  </request>
  <post-process><action type="merge-results"/></post-process>
</odisee>'''
        List documents = service.generateDocument(caller, parse(xml))

        assertEquals(3, documents.size())
        assertEquals('Left.pdf', documents[0].filename)
        assertEquals('Right.pdf', documents[1].filename)
        assertTrue(pdfText(documents[0].bytes).contains('left-side'))
        assertTrue(pdfText(documents[1].bytes).contains('right-side'))
        String merged = pdfText(documents[2].bytes)
        assertTrue(merged.contains('left-side'))
        assertTrue(merged.contains('right-side'))
    }

    void test3_badInstructionDoesNotSaveAndOfficeStaysUsable() {
        String xml = '''<odisee>
  <request name="Bad">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><explode name="x">nope</explode></instructions>
  </request>
</odisee>'''
        try {
            service.generateDocument(caller, parse(xml))
            fail('unsupported instruction')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
        }
        Path documents = OdiseePath.ODISEE_VAR.resolve('user').resolve('odisee').resolve('output')
        if (Files.exists(documents)) {
            Files.walk(documents).withCloseable { stream ->
                assertFalse(stream.any { it.fileName.toString() == 'Bad.pdf' })
            }
        }

        List recovered = service.generateDocument(caller, request('After', 'still-open'))
        assertEquals(1, recovered.size())
        assertTrue(pdfText(recovered[0].bytes).contains('still-open'))
    }

    void testDryRunResolvesInstructionsAndWritesNoFile() {
        Path output = OdiseePath.ODISEE_VAR.resolve('user').resolve('odisee').resolve('output')
        Set<String> before = names(output)
        String xml = '''<odisee>
  <request name="DryRunNoFile">
    <group name="group0"/>
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">dry-hallo</userfield></instructions>
  </request>
</odisee>'''

        List documents = service.generateDocument(caller, parse(xml), true)

        assertTrue(documents == null || documents.isEmpty())
        assertEquals(before, names(output))
    }

    void testDryRunBadInstructionIs422AndTheSlotStays() {
        Path output = OdiseePath.ODISEE_VAR.resolve('user').resolve('odisee').resolve('output')
        Set<String> before = names(output)
        String xml = '''<odisee>
  <request name="DryRunBad">
    <group name="group0"/>
    <template name="Letter" outputFormat="pdf"/>
    <instructions><explode name="x">nope</explode></instructions>
  </request>
</odisee>'''
        try {
            service.generateDocument(caller, parse(xml), true)
            fail('unsupported instruction')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
        }
        assertEquals(before, names(output))

        List recovered = service.generateDocument(caller, request('AfterDry', 'still-open'))
        assertEquals(1, recovered.size())
        assertTrue(pdfText(recovered[0].bytes).contains('still-open'))
    }

    void testOutputFormatTypeSelectsTheFile() {
        String xml = '''<odisee>
  <request name="Typed">
    <template name="Letter" outputFormat="odt"/>
    <instructions><userfield name="Hallo">typed-pdf</userfield></instructions>
    <output><format type="pdf">
      <options>
        <option name="pdf-version" value="1.4"/>
        <option name="tagged" value="true"/>
        <option name="watermark" value="DRAFT"/>
      </options>
    </format></output>
  </request>
</odisee>'''
        List documents = service.generateDocument(caller, parse(xml))

        assertEquals(1, documents.size())
        assertEquals('Typed.pdf', documents[0].filename)
        assertTrue(pdfText(documents[0].bytes).contains('typed-pdf'))
    }

    void testCalcCellInstructionIsWritten() {
        Path templates = OdiseePath.ODISEE_VAR.resolve('user').resolve('odisee').resolve('template')
        MinimalSpreadsheet.write(templates.resolve('Budget.ots'))
        String xml = '''<odisee>
  <request name="BudgetOut">
    <template name="Budget" outputFormat="ods"/>
    <instructions><cell sheet="Sheet1" coordinate="A1">cell-value</cell></instructions>
  </request>
</odisee>'''
        List documents = service.generateDocument(caller, parse(xml))

        assertEquals('BudgetOut.ods', documents[0].filename)
        assertTrue(zipText(documents[0].bytes, 'content.xml').contains('cell-value'))
    }

    void testImpressShapeInstructionIsWritten() {
        Path templates = OdiseePath.ODISEE_VAR.resolve('user').resolve('odisee').resolve('template')
        MinimalPresentation.write(templates.resolve('Deck.otp'))
        String xml = '''<odisee>
  <request name="DeckOut">
    <template name="Deck" outputFormat="odp"/>
    <instructions><shape name="Title">shape-value</shape></instructions>
  </request>
</odisee>'''
        List documents = service.generateDocument(caller, parse(xml))

        assertEquals('DeckOut.odp', documents[0].filename)
        assertTrue(zipText(documents[0].bytes, 'content.xml').contains('shape-value'))
    }

    private static Element request(String name, String value) {
        parse("""<odisee>
  <request name="${name}">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">${value}</userfield></instructions>
  </request>
</odisee>""")
    }

    private static Element parse(String xml) {
        DOMBuilder.parse(new StringReader(xml)).documentElement
    }

    private static Set<String> names(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return [] as Set
        }
        Set<String> found = [] as Set
        Files.walk(dir).withCloseable { stream ->
            stream.each { found << dir.relativize(it).toString() }
        }
        found
    }

    private static String zipText(byte[] bytes, String entryName) {
        ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))
        try {
            def entry
            while ((entry = zip.nextEntry) != null) {
                if (entry.name == entryName) {
                    return new String(zip.readAllBytes(), 'UTF-8')
                }
            }
        } finally {
            zip.close()
        }
        ''
    }

    private static String pdfText(byte[] bytes) {
        def document = Loader.loadPDF(bytes)
        try {
            new PDFTextStripper().getText(document)
        } finally {
            document.close()
        }
    }

}
