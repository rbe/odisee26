package org.odisee.document

import groovy.test.GroovyTestCase
import groovy.xml.DOMBuilder
import org.odisee.api.OdiseeException
import org.odisee.io.TenantPaths

import java.nio.file.Path

class PostProcessServiceTest extends GroovyTestCase {

    private PostProcessService service = new PostProcessService()

    void testUnknownActionIsRejected() {
        OdiseeException error = runAction('<action type="delete-everything"/>')
        assertEquals(OdiseeException.BAD_REQUEST, error.httpStatus)
        assertTrue(error.message.contains('delete-everything'))
    }

    void testMergeInputCannotEscapeTheVarDirectory() {
        OdiseeException error = runAction('''
            <action type="merge-with">
                <input filename="../../etc/passwd"/>
            </action>
        ''', [output: ['/tmp/generated.pdf']])
        assertEquals(OdiseeException.BAD_REQUEST, error.httpStatus)
        assertTrue(error.message.contains('outside'))
    }

    void testMergeInputStaysUnderTheUserWorkDirectory() {
        Path resolved = service.mergeInput([principal: [getName: { 'ada' }] as java.security.Principal], 'merge/AGB.pdf')

        assertTrue(resolved.normalize().startsWith(TenantPaths.workDir('ada').normalize()))
        assertTrue(resolved.toString().endsWith("merge${File.separator}AGB.pdf"))
    }

    private OdiseeException runAction(String actionXml, Map result = null) {
        def document = DOMBuilder.parse(new StringReader("""
            <odisee><request><post-process>${actionXml}</post-process></request></odisee>
        """))
        Map arg = [
                xml        : document.documentElement,
                activeIndex: 0,
                result     : result,
                principal  : [getName: { 'odisee' }] as java.security.Principal
        ]
        try {
            service.postProcessRequest(arg)
            fail(actionXml)
            return null
        } catch (OdiseeException e) {
            return e
        }
    }

}
