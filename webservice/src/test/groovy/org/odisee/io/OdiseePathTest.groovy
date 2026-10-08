package org.odisee.io

import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException

import java.nio.file.Path

/**
 * Home resolution is a pure method. The static block is not re-run.
 */
class OdiseePathTest extends GroovyTestCase {

    void testMissingHomeIsOdiseeException() {
        assertMissing(null, null)
        assertMissing('', null)
        assertMissing('   ', null)
        assertMissing(null, '')
        assertMissing(' ', '  ')
    }

    void testEnvironmentWinsAndIsAbsolute() {
        Path resolved = OdiseePath.resolveHome('rel-home', '/ignored/property')

        assertTrue(resolved.isAbsolute())
        assertTrue(resolved.endsWith('rel-home'))
        assertFalse(resolved.toString().contains('ignored'))
    }

    void testSystemPropertyIsUsedWhenEnvironmentIsBlank() {
        Path resolved = OdiseePath.resolveHome(null, 'rel-home')

        assertEquals('rel-home', resolved.toString())
    }

    private static void assertMissing(String env, String property) {
        try {
            OdiseePath.resolveHome(env, property)
            fail("env=${env} property=${property}")
        } catch (OdiseeException e) {
            assertTrue(e.message.contains('ODISEE_HOME'))
        } catch (NullPointerException e) {
            fail('NullPointerException')
        }
    }

}
