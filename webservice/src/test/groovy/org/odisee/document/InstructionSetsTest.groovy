package org.odisee.document

import groovy.test.GroovyTestCase

class InstructionSetsTest extends GroovyTestCase {

    void testWriterKeepsItsOwnTags() {
        assertEquals(
                ['Userfield', 'Texttable', 'Image', 'Autotext', 'Bookmark', 'Macro'],
                InstructionSets.names(OfficeDocumentType.TEXT))
        assertFalse(InstructionSets.accepts(OfficeDocumentType.TEXT, 'Cell'))
        assertFalse(InstructionSets.accepts(OfficeDocumentType.TEXT, 'Shape'))
        assertFalse(InstructionSets.accepts(OfficeDocumentType.TEXT, 'Named'))
    }

    void testCalcStartsWithCellAndImpressStartsWithShape() {
        assertEquals(['Cell'], InstructionSets.names(OfficeDocumentType.SPREADSHEET))
        assertEquals(['Shape'], InstructionSets.names(OfficeDocumentType.PRESENTATION))
        assertTrue(InstructionSets.accepts(OfficeDocumentType.SPREADSHEET, 'Cell'))
        assertFalse(InstructionSets.accepts(OfficeDocumentType.SPREADSHEET, 'Userfield'))
        assertFalse(InstructionSets.accepts(OfficeDocumentType.SPREADSHEET, 'Shape'))
        assertTrue(InstructionSets.accepts(OfficeDocumentType.PRESENTATION, 'Shape'))
        assertFalse(InstructionSets.accepts(OfficeDocumentType.PRESENTATION, 'Cell'))
        assertFalse(InstructionSets.accepts(OfficeDocumentType.PRESENTATION, 'Named'))
    }

    void testASetCanGrowWithoutASharedNamedTag() {
        try {
            InstructionSets.register(OfficeDocumentType.SPREADSHEET, 'Chart')
            assertTrue(InstructionSets.accepts(OfficeDocumentType.SPREADSHEET, 'Chart'))
            assertFalse(InstructionSets.accepts(OfficeDocumentType.TEXT, 'Chart'))
            assertFalse(InstructionSets.accepts(OfficeDocumentType.PRESENTATION, 'Chart'))
            assertFalse(InstructionSets.names(OfficeDocumentType.SPREADSHEET).contains('Named'))
        } finally {
            InstructionSets.unregister(OfficeDocumentType.SPREADSHEET, 'Chart')
        }
        assertFalse(InstructionSets.accepts(OfficeDocumentType.SPREADSHEET, 'Chart'))
    }

    void testCalcSaveWithNoInstructionsIsNotTheCalcSet() {
        assertEquals(['Cell'], InstructionSets.names(OfficeDocumentType.SPREADSHEET))
        assertFalse(InstructionSets.names(OfficeDocumentType.SPREADSHEET).contains('Save'))
        assertEquals(OfficeDocumentType.SPREADSHEET, OfficeDocumentType.fromFileName('Budget.ots'))
        assertEquals(OfficeDocumentType.PRESENTATION, OfficeDocumentType.fromFileName('Deck.otp'))
        assertEquals(OfficeDocumentType.TEXT, OfficeDocumentType.fromFileName('Letter.ott'))
    }

    void testCellLabelUsesSheetAndCoordinate() {
        def cell = new groovy.xml.XmlSlurper().parseText('<cell sheet="Sheet1" coordinate="B2">42</cell>')

        assertEquals('Sheet1 B2', OdiseeXmlCategory.instructionLabel(cell))
    }

}
