package org.odisee.document

import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException

import java.net.InetSocketAddress

class OdiinstParserTest extends GroovyTestCase {

    void testPortsComeFromTheFile() {
        String text = '''
# comment

odi1|10.0.0.1|2005|/usr/lib/libreoffice||nologo|true
odi2|10.0.0.2|2010|/usr/lib/libreoffice||nologo|true
'''
        List<String[]> rows = OdiinstParser.parse(text)
        List<InetSocketAddress> addresses = OdiinstParser.addresses(rows)

        assertEquals(2, addresses.size())
        assertEquals('10.0.0.1', addresses[0].hostString)
        assertEquals(2005, addresses[0].port)
        assertEquals('10.0.0.2', addresses[1].hostString)
        assertEquals(2010, addresses[1].port)
    }

    void testMissingGroupIsGroup0AndTheEighthFieldSelectsOne() {
        String text = '''
odi1|127.0.0.1|2001|/usr/lib/libreoffice||nologo|true
odi2|10.0.0.9|2010|/usr/lib/libreoffice||nologo|true|writers
'''
        Map<String, List<InetSocketAddress>> groups = OdiinstParser.byGroup(OdiinstParser.parse(text))

        assertEquals(1, groups['group0'].size())
        assertEquals(2001, groups['group0'][0].port)
        assertEquals('127.0.0.1', groups['group0'][0].hostString)
        assertEquals(1, groups['writers'].size())
        assertEquals('10.0.0.9', groups['writers'][0].hostString)
        assertEquals(2010, groups['writers'][0].port)
    }

    void testReloadSeesANewHostWhenTheTextChanges() {
        OdiinstReloader reloader = new OdiinstReloader()
        String first = 'odi1|127.0.0.1|2001|/usr/lib/libreoffice||nologo|true\n'
        String again = first
        String second = first + 'odi2|10.1.0.5|2010|/usr/lib/libreoffice||nologo|true|writers\n'

        Map firstGroups = reloader.update(first)
        reloader.markApplied(first)
        assertEquals(2001, firstGroups['group0'][0].port)
        assertEquals(null, reloader.update(again))
        Map secondGroups = reloader.update(second)
        assertEquals('10.1.0.5', secondGroups['writers'][0].hostString)
        assertEquals(2010, secondGroups['writers'][0].port)
        assertEquals(2001, secondGroups['group0'][0].port)
    }

    void testInvalidLineIsRejected() {
        try {
            OdiinstParser.parse('odi1|127.0.0.1')
            fail('short line')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.SERVER_ERROR, e.httpStatus)
        }
    }

}
