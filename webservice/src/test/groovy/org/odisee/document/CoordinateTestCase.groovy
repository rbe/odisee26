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

}
