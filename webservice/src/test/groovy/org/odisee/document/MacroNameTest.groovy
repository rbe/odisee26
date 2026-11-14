package org.odisee.document

import com.sun.star.lang.XComponent
import com.sun.star.script.provider.ScriptFrameworkErrorException
import com.sun.star.script.provider.XScript
import com.sun.star.script.provider.XScriptProvider
import com.sun.star.script.provider.XScriptProviderSupplier
import groovy.test.GroovyTestCase
import groovy.xml.XmlSlurper
import org.odisee.api.OdiseeException
import org.odisee.ooo.connection.UnoDeadlineExceeded
import org.odisee.writer.OOoDocumentCategory

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class MacroNameTest extends GroovyTestCase {

    void testPlainMacroNameReachesExecuteMacro() {
        def macro = new XmlSlurper().parseText('<macro name="Standard.Module1.myMacro" location="document" language="Basic"/>')
        List<String> invoked = []
        XScript script = (XScript) Proxy.newProxyInstance(
                XScript.classLoader,
                [XScript] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    if (method.name == 'invoke') {
                        invoked << 'invoked'
                    }
                    null
                } as InvocationHandler)
        List<String> requestedUris = []
        XScriptProvider provider = (XScriptProvider) Proxy.newProxyInstance(
                XScriptProvider.classLoader,
                [XScriptProvider] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    if (method.name == 'getScript') {
                        requestedUris << (args[0] as String)
                        return script
                    }
                    null
                } as InvocationHandler)
        XComponent template = (XComponent) Proxy.newProxyInstance(
                XComponent.classLoader,
                [XComponent, XScriptProviderSupplier] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    method.name == 'getScriptProvider' ? provider : null
                } as InvocationHandler)

        OdiseeXmlCategory.processMacro(template, [:], macro)

        assertEquals(['vnd.sun.star.script:Standard.Module1.myMacro?language=Basic&location=document'], requestedUris)
        assertEquals(['invoked'], invoked)
    }

    void testMissingMacroProviderFailsWith422() {
        XComponent template = (XComponent) Proxy.newProxyInstance(
                XComponent.classLoader,
                [XComponent] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args -> null } as InvocationHandler)
        try {
            OOoDocumentCategory.executeMacro(template, 'Standard.Module1.missingMacro')
            fail('expected missing script provider to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('Standard.Module1.missingMacro'))
        }
    }

    void testMissingMacroScriptFailsWith422() {
        XScriptProvider provider = (XScriptProvider) Proxy.newProxyInstance(
                XScriptProvider.classLoader,
                [XScriptProvider] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    if (method.name == 'getScript') {
                        throw new ScriptFrameworkErrorException('Macro not found', null, 'Standard.Module1.missing', 'Basic', 1)
                    }
                    null
                } as InvocationHandler)
        XComponent template = (XComponent) Proxy.newProxyInstance(
                XComponent.classLoader,
                [XComponent, XScriptProviderSupplier] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    method.name == 'getScriptProvider' ? provider : null
                } as InvocationHandler)
        def macro = new XmlSlurper().parseText('<macro name="Standard.Module1.missing" location="document" language="Basic"/>')
        try {
            OdiseeXmlCategory.processMacro(template, [:], macro)
            fail('expected missing macro to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('Standard.Module1.missing'))
        }
    }

    void testMacroInvocationExceptionFailsWith422() {
        XScript script = (XScript) Proxy.newProxyInstance(
                XScript.classLoader,
                [XScript] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    if (method.name == 'invoke') {
                        throw new RuntimeException('boom inside Basic')
                    }
                    null
                } as InvocationHandler)
        XScriptProvider provider = (XScriptProvider) Proxy.newProxyInstance(
                XScriptProvider.classLoader,
                [XScriptProvider] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    method.name == 'getScript' ? script : null
                } as InvocationHandler)
        XComponent template = (XComponent) Proxy.newProxyInstance(
                XComponent.classLoader,
                [XComponent, XScriptProviderSupplier] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    method.name == 'getScriptProvider' ? provider : null
                } as InvocationHandler)
        try {
            OOoDocumentCategory.executeMacro(template, 'Standard.Module1.explode')
            fail('expected macro runtime error to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('boom inside Basic'))
        }
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
