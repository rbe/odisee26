package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.XmlSlurper

class OutputFormatsTest extends GroovyTestCase {

    void testV2Attribute() {
        def request = new XmlSlurper().parseText(
                '<request><template name="A" outputFormat="odt, pdf"/></request>')

        assertEquals(['odt', 'pdf'], OutputFormats.fromRequest(request))
    }

    void testV3FormatElement() {
        def request = new XmlSlurper().parseText(
                '<request><template name="A"/><output><format type="pdfa"/></output></request>')

        assertEquals(['pdfa'], OutputFormats.fromRequest(request))
    }

    void testAttributeWinsWhenBothArePresent() {
        def request = new XmlSlurper().parseText(
                '<request><template name="A" outputFormat="pdf"/><output><format type="odt"/></output></request>')

        assertEquals(['pdf'], OutputFormats.fromRequest(request))
    }

}
