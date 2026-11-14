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
package org.odisee.document

import org.odisee.api.OdiseeException

/**
 * Helper/parser for sheet, table, row, column coordinates.
 */
class Coordinate {

    private static final String CELL_PATTERN = /^([A-Za-z]+)(\d+)$/
    private static final String NUMERIC_PAIR_PATTERN = /^(\d+)\$(\d+)$/
    private static final String TABLE_ONLY_PATTERN = /^[A-Za-z_][A-Za-z_-]*$/

    /**
     * Parse TextTable and spreadsheet coordinates:
     * !A1            -> [table: "", sheet: "", column: "A", columnIndex: 0, row: 1, rowIndex: 0, coord: "!A1"]
     * $A$1           -> [table: null, sheet: null, column: "A", columnIndex: 0, row: 1, rowIndex: 0, coord: "$A$1"]
     * tablename      -> [table: tablename, sheet: tablename]
     * tablename$0$0  -> [table: tablename, sheet: tablename, column: "A", columnIndex: 0, row: 1, rowIndex: 0, coord: "tablename$0$0"]
     * tablename$A1   -> [table: tablename, sheet: tablename, column: "A", columnIndex: 0, row: 1, rowIndex: 0, coord: "tablename$A1"]
     * @return Map keys: table: name, row and column, 0-indexed rowIndex and columnIndex.
     */
    static Map parseCoordinate(String coordinate) {
        if (coordinate == null || coordinate.trim().isEmpty()) {
            throw new OdiseeException("Invalid coordinate '${coordinate}'", OdiseeException.BAD_REQUEST)
        }
        String trimmed = coordinate.trim()
        String sheet = null
        String cellPart = trimmed

        int bang = trimmed.indexOf('!')
        if (bang >= 0) {
            sheet = trimmed.substring(0, bang)
            cellPart = trimmed.substring(bang + 1)
        } else if (!trimmed.startsWith('$') && trimmed.contains('$')) {
            int firstDollar = trimmed.indexOf('$')
            sheet = trimmed.substring(0, firstDollar)
            String afterDollar = trimmed.substring(firstDollar + 1)
            def numericPair = (afterDollar =~ NUMERIC_PAIR_PATTERN)
            if (numericPair.matches()) {
                int r = Integer.parseInt(numericPair.group(1))
                int c = Integer.parseInt(numericPair.group(2))
                int rowNumber = r >= 1 ? r : (r + 1)
                int colIdx = c
                String colLetters = columnName(colIdx)
                return [
                        table      : sheet,
                        sheet      : sheet,
                        column     : colLetters,
                        columnIndex: colIdx,
                        row        : rowNumber,
                        rowIndex   : rowNumber - 1,
                        coord      : coordinate
                ]
            }
            cellPart = afterDollar
        } else if (!trimmed.contains('$') && (trimmed ==~ TABLE_ONLY_PATTERN)) {
            return [
                    table: trimmed,
                    sheet: trimmed
            ]
        }

        String cleaned = cellPart.replace('$', '')
        def matcher = (cleaned =~ CELL_PATTERN)
        if (!matcher.matches()) {
            throw new OdiseeException("Invalid coordinate '${coordinate}'", OdiseeException.BAD_REQUEST)
        }
        String col = matcher.group(1).toUpperCase(Locale.ROOT)
        String rowDigits = matcher.group(2)
        int colIdx = columnIndex(col)
        int rowNumber
        try {
            rowNumber = Integer.parseInt(rowDigits)
        } catch (NumberFormatException e) {
            throw new OdiseeException("Invalid row in coordinate '${coordinate}'", OdiseeException.BAD_REQUEST)
        }
        if (rowNumber < 1) {
            throw new OdiseeException("Invalid row in coordinate '${coordinate}'", OdiseeException.BAD_REQUEST)
        }
        [
                table      : sheet,
                sheet      : sheet,
                column     : col,
                columnIndex: colIdx,
                row        : rowNumber,
                rowIndex   : rowNumber - 1,
                coord      : coordinate
        ]
    }

    /**
     * Parse a coordinate that must resolve to a specific cell (both column and row).
     */
    static Map requireCellCoordinate(String coordinate) {
        Map parsed = parseCoordinate(coordinate)
        if (parsed.columnIndex == null || parsed.rowIndex == null) {
            throw new OdiseeException("Invalid cell coordinate '${coordinate}'", OdiseeException.BAD_REQUEST)
        }
        parsed
    }

    /**
     * Convert spreadsheet column letters (A, Z, AA, BA, AAA) to a 0-based column index.
     */
    static int columnIndex(String letters) {
        if (letters == null || letters.trim().isEmpty()) {
            throw new OdiseeException("Invalid column '${letters}'", OdiseeException.BAD_REQUEST)
        }
        String upper = letters.trim().toUpperCase(Locale.ROOT)
        int acc = 0
        for (int i = 0; i < upper.length(); i++) {
            char ch = upper.charAt(i)
            if (ch < ('A' as char) || ch > ('Z' as char)) {
                throw new OdiseeException("Invalid column '${letters}'", OdiseeException.BAD_REQUEST)
            }
            acc = acc * 26 + ((int) ch - (int) ('A' as char) + 1)
        }
        acc - 1
    }

    /**
     * Convert a 0-based column index to spreadsheet column letters (0 -> A, 25 -> Z, 26 -> AA, 52 -> BA, 702 -> AAA).
     */
    static String columnName(int index) {
        if (index < 0) {
            throw new OdiseeException("Invalid column index '${index}'", OdiseeException.BAD_REQUEST)
        }
        StringBuilder sb = new StringBuilder()
        int n = index + 1
        while (n > 0) {
            int rem = (n - 1) % 26
            sb.append((char) (((int) ('A' as char)) + rem))
            n = (n - 1).intdiv(26)
        }
        sb.reverse().toString()
    }

    /**
     * Create a coordinate like A1 depending on row and column.
     * @param row Integer, 0-indexed
     * @param column Integer, 0-indexed
     */
    static String createCoordinate(row, column) {
        "${columnName(column as int)}${(row as int) + 1}"
    }

    /**
     * Access parseCoordinate through missing property: Coordinate.'A1'
     */
    static def $static_propertyMissing(String coordinate) {
        return parseCoordinate(coordinate)
    }

}
