package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.DOMBuilder
import groovy.xml.dom.DOMCategory

import java.nio.file.Files
import java.nio.file.Path

class RequestExtractTest extends GroovyTestCase {

    void testDomRequestIsSavedWithoutAGPathCast() {
        RequestService requests = new RequestService()
        Map arg = RequestContext.create()
        arg.uniqueRequestId = UUID.randomUUID()
        arg.requestDir = Files.createTempDirectory('odisee-request')
        arg.documentDir = arg.requestDir
        arg.xml = DOMBuilder.parse(new StringReader('''<odisee>
  <request name="One">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">client-hallo</userfield></instructions>
  </request>
</odisee>''')).documentElement
        try {
            Path file
            use(DOMCategory) {
                file = requests.extractRequestAndSaveToDisk(arg, 0)
            }
            String text = Files.readString(file)
            assertTrue(text.contains('One'))
            assertTrue(text.contains('Letter'))
            assertTrue(text.contains('client-hallo'))
        } finally {
            arg.requestDir.toFile().deleteDir()
        }
    }

}
