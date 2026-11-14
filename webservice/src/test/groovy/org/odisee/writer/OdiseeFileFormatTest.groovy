package org.odisee.writer

import com.sun.star.beans.PropertyValue
import com.sun.star.drawing.XDrawPagesSupplier
import com.sun.star.frame.XStorable
import com.sun.star.lang.XComponent
import com.sun.star.presentation.XPresentationSupplier
import com.sun.star.sheet.XSpreadsheetDocument
import com.sun.star.text.XTextDocument
import groovy.test.GroovyTestCase
import org.odisee.document.OfficeDocumentType

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class OdiseeFileFormatTest extends GroovyTestCase {

    void testDocxIsTheWholeSuffix() {
        assertEquals('docx', OdiseeFileFormat.extensionOf('HalloOdisee.DOCX'))
        assertEquals('pdf', OdiseeFileFormat.extensionOf('Letter.pdf'))
        assertEquals('MS Word 2007 XML', OdiseeFileFormat.get('docx').filter)
        assertEquals('writer_pdf_Export', OdiseeFileFormat['pdf'].filter)
    }

    void testApplicationPdfFiltersSelectWriterCalcAndImpress() {
        assertEquals('writer_pdf_Export', OdiseeFileFormat.get('pdf', OfficeDocumentType.TEXT).filter)
        assertEquals('calc_pdf_Export', OdiseeFileFormat.get('pdf', OfficeDocumentType.SPREADSHEET).filter)
        assertEquals('impress_pdf_Export', OdiseeFileFormat.get('pdf', OfficeDocumentType.PRESENTATION).filter)

        Path dir = Files.createTempDirectory('odisee-format')
        try {
            Map<String, Object> writerProps = captureSaveAs(dir.resolve('Letter.pdf'), XTextDocument)
            Map<String, Object> calcProps = captureSaveAs(dir.resolve('Budget.pdf'), XSpreadsheetDocument)
            Map<String, Object> impressProps = captureSaveAs(dir.resolve('Deck.pdf'), XPresentationSupplier)

            assertEquals('writer_pdf_Export', writerProps.FilterName)
            assertEquals('calc_pdf_Export', calcProps.FilterName)
            assertEquals('impress_pdf_Export', impressProps.FilterName)
        } finally {
            dir.toFile().deleteDir()
        }
    }

    void testPowerPointFormatsAreRegistered() {
        assertEquals('MS PowerPoint 97', OdiseeFileFormat.get('ppt').filter)
        assertEquals('Impress MS PowerPoint 2007 XML', OdiseeFileFormat.get('pptx').filter)
        assertEquals('impress8', OdiseeFileFormat.get('odp', OfficeDocumentType.PRESENTATION).filter)

        Path dir = Files.createTempDirectory('odisee-ppt')
        try {
            Map<String, Object> pptProps = captureSaveAs(dir.resolve('Deck.ppt'), XPresentationSupplier)
            Map<String, Object> pptxProps = captureSaveAs(dir.resolve('Deck.pptx'), XDrawPagesSupplier)

            assertEquals('MS PowerPoint 97', pptProps.FilterName)
            assertEquals('Impress MS PowerPoint 2007 XML', pptxProps.FilterName)
        } finally {
            dir.toFile().deleteDir()
        }
    }

    void testSaveAsPdfASelectsApplicationFilterAndCompliantPdfA1Properties() {
        Path dir = Files.createTempDirectory('odisee-pdfa')
        try {
            Map<String, Object> writerPdfa = captureSaveAsPdfA(dir.resolve('Letter.pdfa'), XTextDocument)
            Map<String, Object> calcPdfa = captureSaveAsPdfA(dir.resolve('Budget.pdfa'), XSpreadsheetDocument)
            Map<String, Object> impressPdfa = captureSaveAsPdfA(dir.resolve('Deck.pdfa'), XPresentationSupplier)

            assertEquals('writer_pdf_Export', writerPdfa.FilterName)
            assertEquals('calc_pdf_Export', calcPdfa.FilterName)
            assertEquals('impress_pdf_Export', impressPdfa.FilterName)

            [writerPdfa, calcPdfa, impressPdfa].each { Map<String, Object> outer ->
                assertEquals(Boolean.TRUE, outer.Overwrite)
                Map<String, Object> filterData = toMap((PropertyValue[]) outer.FilterData)
                assertEquals(1, filterData.SelectPdfVersion)
                assertEquals(Boolean.TRUE, filterData.UseTaggedPDF)
                assertEquals(Boolean.TRUE, filterData.UseLosslessCompression)
                assertEquals(Boolean.FALSE, filterData.ExportFormFields)
                assertFalse(filterData.containsKey('RestrictPermissions'))
                assertFalse(filterData.containsKey('PermissionPassword'))
                assertFalse(filterData.containsKey('EnableTextAccessForAccessibilityTools'))
            }
            assertEquals(Boolean.TRUE, toMap((PropertyValue[]) writerPdfa.FilterData).IsSkipEmptyPages)
            assertFalse(toMap((PropertyValue[]) calcPdfa.FilterData).containsKey('IsSkipEmptyPages'))
        } finally {
            dir.toFile().deleteDir()
        }
    }

    private static Map<String, Object> captureSaveAs(Path target, Class<?> markerInterface) {
        List<PropertyValue[]> captured = []
        XComponent component = storableComponent(markerInterface, captured)
        OOoDocumentCategory.saveAs(component, target)
        assertEquals(1, captured.size())
        toMap(captured[0])
    }

    private static Map<String, Object> captureSaveAsPdfA(Path target, Class<?> markerInterface) {
        List<PropertyValue[]> captured = []
        XComponent component = storableComponent(markerInterface, captured)
        OOoDocumentCategory.saveAsPDF_A(component, target)
        assertEquals(1, captured.size())
        toMap(captured[0])
    }

    private static XComponent storableComponent(Class<?> markerInterface, List<PropertyValue[]> captured) {
        (XComponent) Proxy.newProxyInstance(
                XComponent.classLoader,
                [XComponent, XStorable, markerInterface] as Class[],
                { Object proxy, java.lang.reflect.Method method, Object[] args ->
                    if (method.name == 'storeToURL') {
                        captured << ((PropertyValue[]) args[1])
                    }
                    null
                } as InvocationHandler)
    }

    private static Map<String, Object> toMap(PropertyValue[] values) {
        Map<String, Object> map = [:]
        values?.each { PropertyValue pv ->
            map[pv.Name] = pv.Value
        }
        map
    }

}
