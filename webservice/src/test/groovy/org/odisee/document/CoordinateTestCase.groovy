/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 19.11.19, 19:25
 */
package org.odisee.document

import groovy.test.GroovyTestCase

class CoordinateTestCase extends GroovyTestCase {

    void testSimpleCoordinate() {
        // Z3 = column #26, 0-based index 25, row #3, 0-based index 2
        assertEquals([table: null, sheet: null,
                      column: 'Z', columnIndex: 25,
                      row: 3, rowIndex: 2,
                      coord: 'Z3'],
                Coordinate.'Z3')
        // AB12 = column #28, 0-based index 27, row #12, 0-based index 11
        assertEquals([table: null, sheet: null,
                      column: 'AB', columnIndex: 27,
                      row: 12, rowIndex: 11,
                      coord: 'AB12'],
                Coordinate.'AB12')
    }

    void testDollarCoordinate() {
        // Z3 = column #26, 0-based index 25, row #3, 0-based index 2
        assertEquals([table: null, sheet: null,
                      column: 'Z', columnIndex: 25,
                      row: 3, rowIndex: 2,
                      coord: '$Z$3'],
                Coordinate.'$Z$3')
        // AB12 = column #28, 0-based index 27, row #12, 0-based index 11
        assertEquals([table: null, sheet: null,
                      column: 'AB', columnIndex: 27,
                      row: 12, rowIndex: 11,
                      coord: '$AB$12'],
                Coordinate.'$AB$12')
    }

    void testExclamationMarkCoordinate() {
        // Z3 = column #26, 0-based index 25, row #3, 0-based index 2
        assertEquals([table: '', sheet: '',
                      column: 'Z', columnIndex: 25,
                      row: 3, rowIndex: 2,
                      coord: '!Z3'],
                Coordinate.'!Z3')
        // AB12 = column #28, 0-based index 27, row #12, 0-based index 11
        assertEquals([table: '', sheet: '',
                      column: 'AB', columnIndex: 27,
                      row: 12, rowIndex: 11,
                      coord: '!AB12'],
                Coordinate.'!AB12')
    }

    void testCoordinateWithSheet() {
        // Sheet1!AA54 = Sheet1, column #27, 0-based index 26, row #54, 0-based index 53
        assertEquals([table: 'Sheet1', sheet: 'Sheet1',
                      column: 'AA', columnIndex: 26,
                      row: 54, rowIndex: 53,
                      coord: 'Sheet1!AA54'],
                Coordinate.'Sheet1!AA54')
        assertEquals([table: 'Sheet1', sheet: 'Sheet1',
                      column: 'AA', columnIndex: 26,
                      row: 54, rowIndex: 53,
                      coord: 'Sheet1!AA$54'],
                Coordinate.'Sheet1!AA$54')
    }

    void testBase26MultiLetterColumns() {
        assertEquals(0, Coordinate.'A1'.columnIndex)
        assertEquals(25, Coordinate.'Z1'.columnIndex)
        assertEquals(26, Coordinate.'AA1'.columnIndex)
        assertEquals(27, Coordinate.'AB1'.columnIndex)
        assertEquals(51, Coordinate.'AZ1'.columnIndex)
        assertEquals(52, Coordinate.'BA1'.columnIndex)
        assertEquals(77, Coordinate.'BZ1'.columnIndex)
        assertEquals(701, Coordinate.'ZZ1'.columnIndex)
        assertEquals(702, Coordinate.'AAA1'.columnIndex)
        assertEquals('A1', Coordinate.createCoordinate(0, 0))
        assertEquals('Z1', Coordinate.createCoordinate(0, 25))
        assertEquals('AA1', Coordinate.createCoordinate(0, 26))
        assertEquals('BA4', Coordinate.createCoordinate(3, 52))
        assertEquals('AAA10', Coordinate.createCoordinate(9, 702))
    }

    void testLowercaseAndMixedCaseCoordinatesNormalizeColumn() {
        assertEquals([table: null, sheet: null,
                      column: 'BA', columnIndex: 52,
                      row: 12, rowIndex: 11,
                      coord: 'ba12'],
                Coordinate.parseCoordinate('ba12'))
        assertEquals([table: 'Sheet1', sheet: 'Sheet1',
                      column: 'AAA', columnIndex: 702,
                      row: 3, rowIndex: 2,
                      coord: 'Sheet1!aAa$3'],
                Coordinate.parseCoordinate('Sheet1!aAa$3'))
    }

    void testTableOnlyAndDigitlessCoordinatesDoNotThrowNumberFormatException() {
        assertEquals([table: 'Tabelle', sheet: 'Tabelle'], Coordinate.parseCoordinate('Tabelle'))
        ['', '   ', 'Sheet1!ABC', '!ABC', '$ABC', '123', 'A0', 'A1B2'].each { String invalid ->
            try {
                Coordinate.parseCoordinate(invalid)
                fail("Expected OdiseeException for '${invalid}'")
            } catch (org.odisee.api.OdiseeException e) {
                assertEquals(org.odisee.api.OdiseeException.BAD_REQUEST, e.httpStatus)
            }
        }
        try {
            Coordinate.requireCellCoordinate('Tabelle')
            fail('Expected requireCellCoordinate to reject table-only coordinate')
        } catch (org.odisee.api.OdiseeException e) {
            assertEquals(org.odisee.api.OdiseeException.BAD_REQUEST, e.httpStatus)
        }
    }

}
