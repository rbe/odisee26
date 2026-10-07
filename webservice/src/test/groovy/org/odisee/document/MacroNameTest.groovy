package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.XmlSlurper
import org.odisee.api.OdiseeException
import org.odisee.ooo.connection.UnoDeadlineExceeded

class MacroNameTest extends GroovyTestCase {

    void testPlainMacroNameReachesExecuteMacro() {
        def macro = new XmlSlurper().parseText('<macro name="Standard.Module1.myMacro" location="document" language="Basic"/>')

        OdiseeXmlCategory.processMacro(null, [:], macro)
    }

    void testBadMacroNameIs400() {
        assertBad('<macro name="../x" location="document" language="Basic"/>', 'macro name')
    }

    void testBadMacroLibraryIs400() {
        assertBad('<macro name="MyMacro" location="../lib" language="Basic"/>', 'macro library')
    }

    void testBadMacroLanguageIs400() {
        assertBad('<macro name="MyMacro" location="document" language="Ba?sic"/>', 'macro language')
    }

    void testBadPostMacroIs400BeforeTheMacroRuns() {
        try {
            OdiseeXmlCategory.processInstruction(null, { }, [post: [name: '../x']])
            fail('bad post macro')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            assertFalse(OdiseeXmlCategory.dropsSlot(e))
        }
    }

    void testBadPreMacroDoesNotRunTheInstruction() {
        boolean ran = false
        try {
            OdiseeXmlCategory.processInstruction(null, { ran = true }, [pre: [name: 'a/b']])
            fail('bad pre macro')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
        assertFalse(ran)
    }

    void testMacroReferenceAcceptsLibraryAndLanguage() {
        MacroNames.requireReference('Library.Module.Name')
        MacroNames.requireReference('vnd.sun.star.script:Library.Module.Name?language=Basic&location=application')
    }

    void testMacroReferenceRejectsABadLibrary() {
        try {
            MacroNames.requireReference('Library.Module.Name?language=Basic&location=../x')
            fail('bad library')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
    }

    void testBadNameIsNotRecordedAsAnInstructionFailure() {
        List<String> failures = []
        try {
            OdiseeXmlCategory.keepOrRecord(
                    new OdiseeException("Invalid macro name '../x'", OdiseeException.BAD_REQUEST),
                    failures, 'macro x', null)
            fail('bad name')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
        }
        assertTrue(failures.isEmpty())
        assertFalse(OdiseeXmlCategory.dropsSlot(new OdiseeException('bad', OdiseeException.BAD_REQUEST)))
    }

    void testOrdinaryInstructionFailureIsRecorded() {
        List<String> failures = []
        OdiseeXmlCategory.keepOrRecord(new IllegalStateException('missing'), failures, 'userfield Hallo', null)

        assertEquals(1, failures.size())
        assertTrue(failures[0].contains('missing'))
    }

    void testDeadlineDropsTheSlotAndIsNotRecorded() {
        List<String> failures = []
        UnoDeadlineExceeded deadline = new UnoDeadlineExceeded('instruction', 10)
        try {
            OdiseeXmlCategory.keepOrRecord(deadline, failures, 'macro', null)
            fail('deadline')
        } catch (UnoDeadlineExceeded ignored) {
            assertTrue(failures.isEmpty())
        }
        assertTrue(OdiseeXmlCategory.dropsSlot(deadline))
    }

    private static void assertBad(String xml, String label) {
        def macro = new XmlSlurper().parseText(xml)
        try {
            OdiseeXmlCategory.processMacro(null, [:], macro)
            fail(xml)
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.BAD_REQUEST, e.httpStatus)
            assertTrue(e.message.contains(label))
            assertFalse(OdiseeXmlCategory.dropsSlot(e))
        }
    }

}
