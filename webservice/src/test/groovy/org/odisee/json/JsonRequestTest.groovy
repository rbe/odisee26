/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.json

import groovy.xml.XmlUtil
import groovy.xml.dom.DOMCategory
import org.junit.Test
import org.odisee.api.OdiseeException
import org.odisee.document.RequestSchema
import org.w3c.dom.Element

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

class JsonRequestTest {

    @Test
    void singleRequestBecomesXml() {
        final String json = '''
        {
          "request": {
            "name": "HalloOdisee",
            "template": { "name": "HalloOdisee", "outputFormat": "odt", "revision": "LATEST" },
            "archive": { "files": false, "database": true },
            "instructions": [
              { "instruction": "userfield", "name": "Hallo", "value": "art of coding UG (haftungsbeschränkt)" },
              { "instruction": "userfield", "name": "Tabelle1!A4", "value": "support@odisee.de" }
            ]
          }
        }
        '''
        final Element xml = JsonRequest.toElement(json)
        use(DOMCategory) {
            assertEquals('odisee', xml.nodeName)
            assertEquals('HalloOdisee', xml.request[0].'@name')
            assertEquals('odt', xml.request[0].template[0].'@outputFormat')
            assertEquals('LATEST', xml.request[0].template[0].'@revision')
            assertEquals('false', xml.request[0].archive[0].'@files')
            assertEquals('true', xml.request[0].archive[0].'@database')
            assertEquals('art of coding UG (haftungsbeschränkt)', xml.request[0].instructions.userfield[0].text())
            assertEquals('Tabelle1!A4', xml.request[0].instructions.userfield[1].'@name')
        }
        assertTrue(XmlUtil.serialize(xml).contains('haftungsbeschränkt'))
    }

    @Test
    void macroMergeAndImage() {
        final String json = '''
        {
          "request": [{
            "name": "HalloOdisee3",
            "localDebug": false,
            "group": "group0",
            "template": {
              "name": "HalloOdisee",
              "outputFormat": "pdf",
              "preSaveMacro": "Standard.Module1.Before"
            },
            "instructions": [
              {
                "instruction": "macro",
                "name": "Standard.Module1.myMacro",
                "language": "Basic",
                "location": "document",
                "parameter": ["a", {"value": "b"}]
              },
              {
                "instruction": "image",
                "type": "image/png",
                "bookmark": "Header",
                "width": 1000,
                "height": 200,
                "value": "BASE64"
              },
              { "instruction": "table", "name": "Tabelle1", "coordinate": "A4", "value": "cell" }
            ],
            "postProcess": {
              "action": [{
                "type": "merge-with",
                "content": [
                  { "element": "result-placeholder" },
                  { "element": "input", "filename": "pdf/AGB.pdf" }
                ]
              }]
            }
          }],
          "response": { "base64": false }
        }
        '''
        final Element xml = JsonRequest.toElement(json)
        use(DOMCategory) {
            assertEquals('false', xml.request[0].'@local-debug')
            assertEquals('group0', xml.request[0].group[0].'@name')
            assertEquals('Standard.Module1.Before', xml.request[0].template[0].'@pre-save-macro')
            assertEquals('a', xml.request[0].instructions.macro[0].parameter[0].'@value')
            assertEquals('a', xml.request[0].instructions.macro[0].parameter[0].text())
            assertEquals('b', xml.request[0].instructions.macro[0].parameter[1].text())
            assertEquals('BASE64', xml.request[0].instructions.image[0].text())
            assertEquals('1000', xml.request[0].instructions.image[0].'@width')
            assertEquals('cell', xml.request[0].instructions.texttable[0].text())
            assertEquals('merge-with', xml.request[0].'post-process'.action[0].'@type')
            assertEquals('result-placeholder', xml.request[0].'post-process'.action[0].'*'[0].name())
            assertEquals('pdf/AGB.pdf', xml.request[0].'post-process'.action[0].input[0].'@filename')
            assertEquals('false', xml.response.base64.text())
        }
    }

    @Test
    void outputFormatAndApplicationInstructionsBecomeXml() {
        final String json = '''
        {
          "request": {
            "name": "Budget",
            "template": { "name": "Budget", "outputFormat": "ods" },
            "instructions": [
              { "instruction": "cell", "sheet": "Sheet1", "coordinate": "B2", "value": "42" },
              { "instruction": "shape", "name": "Title", "value": "Hello" }
            ],
            "output": {
              "format": {
                "type": "pdf",
                "options": [
                  { "name": "pdf-version", "value": "1.4" },
                  { "name": "tagged", "value": "true" },
                  { "name": "watermark", "value": "DRAFT" }
                ]
              }
            }
          }
        }
        '''
        final Element xml = JsonRequest.toElement(json)
        use(DOMCategory) {
            assertEquals('Sheet1', xml.request[0].instructions.cell[0].'@sheet')
            assertEquals('B2', xml.request[0].instructions.cell[0].'@coordinate')
            assertEquals('42', xml.request[0].instructions.cell[0].text())
            assertEquals('Title', xml.request[0].instructions.shape[0].'@name')
            assertEquals('Hello', xml.request[0].instructions.shape[0].text())
            assertEquals('pdf', xml.request[0].output.format[0].'@type')
            assertEquals('watermark', xml.request[0].output.format.options.option[2].'@name')
            assertEquals('DRAFT', xml.request[0].output.format.options.option[2].'@value')
        }
        RequestSchema.validate(xml)
    }

    @Test
    void rejectsIncompleteJson() {
        assertRejected('not json')
        assertRejected('[]')
        assertRejected('{"request":[]}')
        assertRejected('{"request":{"name":"x"}}')
        assertRejected('{"request":{"template":{"name":"t"},"instructions":[{"instruction":"nope"}]}}')
        assertRejected('{"request":{"template":{"name":"t"},"postProcess":{"action":[{"type":"merge-with","content":[{"element":"script"}]}]}}}')
    }

    @Test
    void deliveryIsTheV2AttributeAndABucketIsRejected() {
        final Element xml = JsonRequest.toElement('''
        {"delivery":"both","request":{"template":{"name":"Letter","outputFormat":"pdf"},
          "archive":{"files":true},"instructions":[{"instruction":"userfield","name":"Hallo","value":"x"}]}}
        ''')
        assertEquals('both', xml.getAttribute('delivery'))
        use(DOMCategory) {
            assertEquals('true', xml.request[0].archive[0].'@files')
        }
        RequestSchema.validate(xml)
        try {
            JsonRequest.toElement('''
            {"delivery":"store","bucket":"other-bucket","request":{"template":{"name":"Letter"},"instructions":[]}}
            ''')
            fail('bucket field')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            assertEquals('The request cannot name a bucket', e.message)
        }
    }

    @Test
    void detectsJsonBody() {
        assertTrue(JsonRequest.looksLikeJson('  \n {"request":[]}'.bytes))
        assertTrue(!JsonRequest.looksLikeJson('<odisee/>'.bytes))
        byte[] bom = [0xEF, 0xBB, 0xBF, (byte) '{'] as byte[]
        assertTrue(JsonRequest.looksLikeJson(bom))
    }

    private static void assertRejected(String json) {
        try {
            JsonRequest.toElement(json)
            fail('Expected OdiseeException for ' + json)
        } catch (OdiseeException expected) {
            assertTrue(expected.message?.length() > 0)
        }
    }

}
