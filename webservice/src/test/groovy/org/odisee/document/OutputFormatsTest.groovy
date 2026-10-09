package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.XmlSlurper

class OutputFormatsTest extends GroovyTestCase {

    void testV2Attribute() {
        def request = new XmlSlurper().parseText(
                '<request><template name="A" outputFormat="odt, pdf"/></request>')

        assertEquals(['odt', 'pdf'], OutputFormats.extensions(request))
    }

    void testFormatTypeIsTheExtensionWhenOutputIsPresent() {
        def request = new XmlSlurper().parseText(
                '<request><template name="A"/><output><format type="pdfa"><options><option name="watermark" value="DRAFT"/></options></format></output></request>')

        List<OutputFormats.Choice> choices = OutputFormats.fromRequest(request)

        assertEquals(['pdfa'], choices*.extension)
        assertEquals('watermark', choices[0].options[0].name)
        assertEquals('DRAFT', choices[0].options[0].value)
    }

    void testOutputFormatAppliesWhenOutputIsAbsent() {
        def request = new XmlSlurper().parseText(
                '<request><template name="A" outputFormat="pdf"/></request>')

        assertEquals(['pdf'], OutputFormats.extensions(request))
        assertEquals([], OutputFormats.fromRequest(request)[0].options)
    }

    void testFormatTypeWinsWhenBothArePresent() {
        def request = new XmlSlurper().parseText(
                '<request><template name="A" outputFormat="pdf"/><output><format type="odt"/></output></request>')

        assertEquals(['odt'], OutputFormats.extensions(request))
    }

}
