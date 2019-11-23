/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 02.02.15 18:35
 */
package eu.artofcoding.odisee.helper

/**
 * Helper/parser for sheet, table, row, column coordinates.
 */
class Coordinate {

    static {
        // Access parseCoordinate through missing property:
        // Coordinate.'A1'
        Coordinate.metaClass.'static'.propertyMissing = { String coordinate ->
            Coordinate.parseCoordinate(coordinate)
        }
    }

    /**
     * Parse TextTable coordinates:
     * !A1            -> [row: 0, column: 0, coord: "A1"]
     * $A$1           -> [row: 0, column: 0, coord: "A1"]
     * tablename      -> [table: tablename]
     * tablename$0$0  -> [table: tablename, row: 0, column: 0, coord: "A1"]
     * tablename$A1   -> [table: tablename, row: 0, column: 0, coord: "A1"]
     * @return Map keys: table: name, row and column: Integer, 0-indexed.
     */
    static Map parseCoordinate(String coordinate) {
        // Split by exclamation mark: Sheet!A1
        String[] splitCoordinate = coordinate.split('!')
        String sheet
        // If splitted string resulted in an array with more than one entry
        // there's a sheet name and coordinate, else it's just a coordinate
        int coordArrIdx = 0
        if (splitCoordinate.length > 1) {
            sheet = splitCoordinate[0]
            coordArrIdx = 1
            // Remove any dollar signs in coordinate
            splitCoordinate[1] = splitCoordinate[1].replace('$', '')
        }
        // Column: find all characters at the beginning of coordinate
        String col = splitCoordinate[coordArrIdx].inject('') { o, n ->
            Character.isLetter(n.toCharacter()) ? o + n : o
        }
        // Columns with more than one character: AA = first column after Z, index 26
        int colCounter = 0
        int colIdx = col.collect {
            colCounter++ * 26 + ((Integer) it) - 65
        }.sum() as int
        // Row: find all digits at the end of coordinate
        String row = splitCoordinate[coordArrIdx].inject('') { o, n ->
            Character.isDigit(n.toCharacter()) ? o + n : o
        }
        int rowIdx = (row as Integer) - 1
        // Return analysis
        [
                table : sheet, sheet: sheet,
                column: col, columnIndex: colIdx,
                row   : row as int, rowIndex: rowIdx,
                coord : coordinate
        ]
    }

    /**
     * Create a coordinate like A1 depending on row and column.
     * @param row Integer, 0-indexed
     * @param column Integer, 0-indexed
     */
    static String createCoordinate(row, column) {
        "${((Character) column) + 65}${row + 1}"
    }

}
