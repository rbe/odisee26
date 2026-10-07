package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.dom.DOMCategory
import org.odisee.api.OdiseeException
import org.odisee.json.JsonRequest

/**
 * The server validates the v2 request schema and rejects the other request namespaces.
 */
class RequestSchemaTest extends GroovyTestCase {

    void testUnnamespacedV2Request() {
        String xml = '''<odisee>
  <request name="One">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">alpha</userfield></instructions>
  </request>
</odisee>'''

        def root = RequestSchema.parse(xml.getBytes('UTF-8'))

        use(DOMCategory) {
            assertEquals('One', root.request[0].'@name')
            assertEquals('pdf', root.request[0].template[0].'@outputFormat')
            assertEquals('alpha', root.request[0].instructions.userfield[0].text())
        }
    }

    void testPrefixedV2RequestKeepsLocalNames() {
        String xml = '''<odisee:odisee xmlns:odisee="http://xmlns.odisee.de/v2/request">
  <odisee:request name="HalloOdisee">
    <odisee:ooo group="group0"/>
    <odisee:template name="HalloOdisee" outputFormat="odt"/>
    <odisee:instructions>
      <odisee:userfield name="Hallo">art of coding</odisee:userfield>
    </odisee:instructions>
  </odisee:request>
</odisee:odisee>'''

        def root = RequestSchema.parse(xml.getBytes('UTF-8'))

        use(DOMCategory) {
            assertEquals('odisee', root.nodeName)
            assertEquals('odt', root.request[0].template[0].'@outputFormat')
            assertEquals('art of coding', root.request[0].instructions.userfield[0].text())
        }
    }

    void testLegacyOooElementIsAccepted() {
        RequestSchema.parse('''<odisee>
  <request name="Hallo">
    <ooo group="group0"/>
    <template name="Hallo" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">x</userfield></instructions>
  </request>
</odisee>'''.getBytes('UTF-8'))
    }

    void testShippedV2SampleValidates() {
        InputStream sample = RequestSchema.class.getResourceAsStream('/xml/v2/request.xml')
        assertNotNull(sample)
        RequestSchema.parse(sample.bytes)
    }

    void testJsonRequestValidatesAsV2() {
        String json = '''
        {
          "request": {
            "name": "HalloOdisee3",
            "localDebug": false,
            "group": "group0",
            "template": { "name": "HalloOdisee", "outputFormat": "pdf", "preSaveMacro": "Standard.Module1.Before" },
            "instructions": [
              { "instruction": "macro", "name": "Standard.Module1.myMacro", "parameter": ["a", {"value": "b"}] },
              { "instruction": "image", "type": "image/png", "bookmark": "Header", "width": 1000, "height": 200, "value": "BASE64" },
              { "instruction": "table", "name": "Tabelle1", "coordinate": "A4", "value": "cell" }
            ],
            "postProcess": { "action": [{ "type": "merge-with", "content": [
              { "element": "result-placeholder" },
              { "element": "input", "filename": "pdf/AGB.pdf" }
            ] }] }
          },
          "response": { "base64": false }
        }
        '''
        RequestSchema.validate(JsonRequest.toElement(json))
    }

    void testV3NamespaceIsRejected() {
        OdiseeException error = reject('''<odisee xmlns="http://xmlns.odisee.de/v3/request">
  <request name="Edh">
    <template name="T" revision="1"/>
    <instructions><userfield name="Hallo">x</userfield></instructions>
    <output><format type="pdf"/></output>
  </request>
</odisee>''')
        assertTrue(error.message.contains('v3'))
    }

    void testV3OutputElementIsRejected() {
        OdiseeException error = reject('''<odisee>
  <request name="Edh">
    <template name="T"/>
    <instructions><userfield name="Hallo">x</userfield></instructions>
    <output><format type="pdf"/></output>
  </request>
</odisee>''')
        assertTrue(error.message.contains('output/format'))
    }

    void testV26NamespaceIsRejected() {
        OdiseeException error = reject('''<odisee xmlns="http://xmlns.odisee.org/v26/request">
  <request name="One">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><userfield name="Hallo">x</userfield></instructions>
  </request>
</odisee>''')
        assertTrue(error.message.contains('v2.6'))
    }

    void testUnknownInstructionIsBadRequest() {
        OdiseeException error = reject('''<odisee>
  <request name="Bad">
    <template name="Letter" outputFormat="pdf"/>
    <instructions><explode name="x">nope</explode></instructions>
  </request>
</odisee>''')
        assertTrue(error.message.startsWith('Invalid request:'))
    }

    void testDoctypeIsRejected() {
        String xml = '''<?xml version="1.0"?>
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
<odisee><request name="a"><template name="t" outputFormat="pdf"/>
<instructions><userfield name="h">&xxe;</userfield></instructions></request></odisee>'''
        OdiseeException error = reject(xml)
        assertTrue(error.message.toLowerCase().contains('doctype'))
        assertFalse(error.message.contains('root:'))
    }

    private static OdiseeException reject(String xml) {
        try {
            RequestSchema.parse(xml.getBytes('UTF-8'))
            fail('expected a 400')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            return e
        }
        null
    }

}
