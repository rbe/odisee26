package org.odisee.writer

import com.sun.star.beans.XPropertySet
import com.sun.star.container.NoSuchElementException
import com.sun.star.container.XEnumerationAccess
import com.sun.star.container.XNameAccess
import com.sun.star.frame.XComponentLoader
import com.sun.star.frame.XStorable
import com.sun.star.lang.XComponent
import com.sun.star.lang.XMultiComponentFactory
import com.sun.star.table.XCell
import com.sun.star.table.XCellRange
import com.sun.star.table.XTableColumns
import com.sun.star.table.XTableRows
import com.sun.star.text.XAutoTextEntry
import com.sun.star.text.XAutoTextGroup
import com.sun.star.text.XBookmarksSupplier
import com.sun.star.text.XText
import com.sun.star.text.XTextContent
import com.sun.star.text.XTextDocument
import com.sun.star.text.XTextFieldsSupplier
import com.sun.star.text.XTextRange
import com.sun.star.text.XTextTable
import com.sun.star.text.XTextTablesSupplier
import com.sun.star.uno.Any
import com.sun.star.uno.Type
import com.sun.star.uno.XComponentContext
import com.sun.star.util.XRefreshable
import groovy.test.GroovyTestCase
import groovy.xml.XmlSlurper
import org.odisee.api.OdiseeException
import org.odisee.document.OdiseeXmlCategory
import org.odisee.ooo.connection.OfficeConnection
import org.odisee.ooo.connection.OfficeConnectionFactory
import org.odisee.ooo.connection.StubOfficeConnection

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class UnoInstructionFailureTest extends GroovyTestCase {

    void testMissingUserfieldFailsWith422() {
        XComponent component = fieldComponent([:])
        try {
            OOoFieldCategory.setUserFieldContent(component, 'MissingField', 'value')
            fail('expected missing userfield to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('MissingField'))
        }
    }

    void testExistingUserfieldUpdatesContent() {
        Map<String, Object> values = [:]
        XPropertySet halloMaster = propertySet(values)
        XComponent component = fieldComponent(['com.sun.star.text.FieldMaster.User.Hallo': new Any(new Type(XPropertySet), halloMaster)])

        OOoFieldCategory.setUserFieldContent(component, 'Hallo', 'Odisee')

        assertEquals('Odisee', values.Content)
    }

    void testMissingTextTableFailsWith422() {
        XComponent component = tableComponent([:])
        try {
            OOoTextTableCategory.set(component, 'MissingTable!A1', 'cell')
            fail('expected missing table to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('MissingTable'))
        }

        def texttable = new XmlSlurper().parseText('<texttable name="MissingTable" coordinate="A1">cell</texttable>')
        try {
            OdiseeXmlCategory.processTexttable(component, [:], texttable)
            fail('expected processTexttable on missing table to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('MissingTable'))
        }
    }

    void testExistingTextTableUpdatesCell() {
        List<String> written = []
        XCell cellText = proxy([XCell, XText]) { method, args ->
            if (method.name == 'setString') {
                written << (args[0] as String)
            }
            null
        }
        XTableRows rows = proxy([XTableRows]) { method, args ->
            method.name == 'getCount' ? 5 : null
        }
        XTableColumns cols = proxy([XTableColumns]) { method, args ->
            method.name == 'getCount' ? 5 : null
        }
        XTextTable table = proxy([XTextTable, XCellRange]) { method, args ->
            if (method.name == 'getRows') return rows
            if (method.name == 'getColumns') return cols
            if (method.name == 'getCellByPosition') return cellText
            null
        }
        XComponent component = tableComponent([Tabelle1: table])
        def texttable = new XmlSlurper().parseText('<texttable name="Tabelle1" coordinate="A1">hello-cell</texttable>')

        OdiseeXmlCategory.processTexttable(component, [:], texttable)

        assertEquals(['hello-cell'], written)
    }

    void testMissingAutotextGroupFailsWith422() {
        XComponent component = autotextComponent([:], [:])
        try {
            OOoAutotextCategory.insertAutotextAtBookmark(component, 'MissingGroup', 'mfg', 'Start')
            fail('expected missing autotext group to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('MissingGroup'))
        } finally {
            OOoDocumentCategory.unbindConnection(component)
        }
    }

    void testMissingAutotextEntryFailsWith422() {
        XAutoTextGroup group = nameAccessProxy([XAutoTextGroup, XNameAccess], [:])
        XComponent component = autotextComponent([Standard: group], [:])
        try {
            OOoAutotextCategory.insertAutotextAtBookmark(component, 'Standard', 'MissingEntry', 'Start')
            fail('expected missing autotext entry to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('Standard.MissingEntry'))
        } finally {
            OOoDocumentCategory.unbindConnection(component)
        }
    }

    void testMissingAutotextBookmarkFailsWith422() {
        XAutoTextEntry entry = proxy([XAutoTextEntry]) { method, args -> null }
        XAutoTextGroup group = nameAccessProxy([XAutoTextGroup, XNameAccess], [mfg: entry])
        XComponent component = autotextComponent([Standard: group], [:])
        try {
            OOoAutotextCategory.insertAutotextAtBookmark(component, 'Standard', 'mfg', 'MissingBookmark')
            fail('expected missing bookmark to fail')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
            assertTrue(e.message.contains('MissingBookmark'))
        } finally {
            OOoDocumentCategory.unbindConnection(component)
        }
    }

    void testExistingAutotextAppliesToBookmark() {
        List<XTextRange> applied = []
        XAutoTextEntry entry = proxy([XAutoTextEntry]) { method, args ->
            if (method.name == 'applyTo') {
                applied << (XTextRange) args[0]
            }
            null
        }
        XAutoTextGroup group = nameAccessProxy([XAutoTextGroup, XNameAccess], [mfg: entry])
        XTextRange anchor = proxy([XTextRange]) { method, args -> null }
        XTextContent bookmark = proxy([XTextContent]) { method, args ->
            method.name == 'getAnchor' ? anchor : null
        }
        XComponent component = autotextComponent([Standard: group], [Intro: bookmark])
        try {
            OOoAutotextCategory.insertAutotextAtBookmark(component, 'Standard', 'mfg', 'Intro')
            assertEquals(1, applied.size())
            assertSame(anchor, applied[0])
        } finally {
            OOoDocumentCategory.unbindConnection(component)
        }
    }

    void testProcessTemplatePropagatesSilentFailuresAs422() {
        Path dir = Files.createTempDirectory('odisee-b52-template')
        try {
            Path templateFile = dir.resolve('Letter.ott')
            Files.writeString(templateFile, 'stub')
            XComponent opened = fieldComponent([:])
            OfficeConnection connection = loaderConnection(opened)
            OfficeConnectionFactory factory = OfficeConnectionFactory.forTest([connection])
            def xml = new XmlSlurper().parseText("""
                <odisee>
                  <request name="Out">
                    <template name="Letter" outputFormat="pdf" outputPath="${dir}"/>
                    <instructions>
                      <userfield name="DoesNotExist">val</userfield>
                    </instructions>
                  </request>
                </odisee>
            """)
            try {
                OdiseeXmlCategory.processTemplate([
                        xml               : xml,
                        activeRequestIndex: 0,
                        id                : '1',
                        template          : templateFile
                ], connection, factory)
                fail('expected processTemplate to fail with 422')
            } catch (OdiseeException e) {
                assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
                assertTrue(e.message.contains('DoesNotExist'))
            }
            assertEquals(1L, factory.gauges().instructionFailures())
        } finally {
            dir.toFile().deleteDir()
        }
    }

    private static OfficeConnection loaderConnection(XComponent openedComponent) {
        XComponentLoader loader = proxy([XComponentLoader]) { method, args ->
            method.name == 'loadComponentFromURL' ? openedComponent : null
        }
        new StubOfficeConnection(loader, null)
    }

    private static XComponent fieldComponent(Map<String, Object> masters) {
        XNameAccess masterAccess = nameAccessProxy([XNameAccess], masters)
        Object refreshableFields = proxy([XEnumerationAccess, XRefreshable]) { method, args -> null }
        proxy([XComponent, XTextDocument, XTextFieldsSupplier, XStorable]) { method, args ->
            if (method.name == 'getTextFieldMasters') return masterAccess
            if (method.name == 'getTextFields') return refreshableFields
            null
        }
    }

    private static XComponent tableComponent(Map<String, Object> tables) {
        XNameAccess tableAccess = nameAccessProxy([XNameAccess], tables)
        proxy([XComponent, XTextDocument, XTextTablesSupplier]) { method, args ->
            method.name == 'getTextTables' ? tableAccess : null
        }
    }

    private static XComponent autotextComponent(Map<String, Object> groups, Map<String, Object> bookmarks) {
        XComponentContext ctx = proxy([XComponentContext]) { method, args -> null }
        XNameAccess container = nameAccessProxy([XNameAccess], groups)
        XMultiComponentFactory serviceManager = proxy([XMultiComponentFactory, XPropertySet]) { method, args ->
            if (method.name == 'getPropertyValue' && args[0] == 'DefaultContext') return ctx
            if (method.name == 'createInstanceWithContext' && args[0] == 'com.sun.star.text.AutoTextContainer') return container
            null
        }
        OfficeConnection connection = new StubOfficeConnection(null, serviceManager)
        XNameAccess bookmarkAccess = nameAccessProxy([XNameAccess], bookmarks)
        XComponent component = proxy([XComponent, XTextDocument, XBookmarksSupplier]) { method, args ->
            method.name == 'getBookmarks' ? bookmarkAccess : null
        }
        OOoDocumentCategory.bindConnection(component, connection)
        component
    }

    private static XPropertySet propertySet(Map<String, Object> store) {
        proxy([XPropertySet]) { method, args ->
            if (method.name == 'setPropertyValue') {
                store[args[0] as String] = args[1]
                return null
            }
            if (method.name == 'getPropertyValue') {
                return store[args[0] as String]
            }
            null
        }
    }

    private static <T> T nameAccessProxy(List<Class<?>> interfaces, Map<String, Object> items) {
        proxy(interfaces) { method, args ->
            if (method.name == 'hasByName') {
                return items.containsKey(args[0] as String)
            }
            if (method.name == 'getByName') {
                String key = args[0] as String
                if (!items.containsKey(key)) {
                    throw new NoSuchElementException(key)
                }
                return items[key]
            }
            null
        }
    }

    @SuppressWarnings('unchecked')
    private static <T> T proxy(List<Class<?>> interfaces, Closure<?> handler) {
        (T) Proxy.newProxyInstance(
                UnoInstructionFailureTest.classLoader,
                interfaces as Class[],
                { Object p, java.lang.reflect.Method method, Object[] args ->
                    if (method.declaringClass == Object) {
                        if (method.name == 'toString') return 'UnoProxy'
                        if (method.name == 'hashCode') return System.identityHashCode(p)
                        if (method.name == 'equals') return p.is(args[0])
                    }
                    handler.call(method, args)
                } as InvocationHandler)
    }

}
