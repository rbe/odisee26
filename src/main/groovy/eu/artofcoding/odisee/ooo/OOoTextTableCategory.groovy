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
package eu.artofcoding.odisee.ooo

import com.sun.star.beans.XPropertySet
import com.sun.star.container.XNamed
import com.sun.star.lang.XComponent
import com.sun.star.table.XCell
import com.sun.star.table.XCellRange
import com.sun.star.table.XTableRows
import com.sun.star.text.XText
import com.sun.star.text.XTextTable
import com.sun.star.text.XTextTablesSupplier
import com.sun.star.uno.Any
import eu.artofcoding.odisee.OdiseeException
import eu.artofcoding.odisee.helper.Coordinate
import eu.artofcoding.odisee.helper.Profile

/**
 * Work with Writer's text tables.
 */
class OOoTextTableCategory {

    private static final String EXCLAMATION_MARK = '!'

    private static final String DOLLAR_SIGN = '$'

    /**
     * Get TextFieldSupplier from OpenOffice document.
     */
    static XTextTablesSupplier textTablesSupplier(XComponent component) {
        Profile.time 'OOoTextTableCategory.textTablesSupplier', {
            use(UnoCategory) {
                component.uno(XTextTablesSupplier)
            }
        }
    }

    /**
     * Does a certain TextTable exist?
     */
    static boolean hasTextTable(XComponent component, String name) {
        Profile.time "OOoTextTableCategory.hasTextTable(name=$name)", {
            XTextTablesSupplier xTextTablesSupplier = (XTextTablesSupplier) textTablesSupplier(component)
            xTextTablesSupplier?.textTables?.hasByName(name)
        }
    }

    /**
     * Get a reference to a TextTable.
     */
    static XTextTable getTextTable(XComponent component, String name) {
        Profile.time "OOoTextTableCategory.getTextTable(name=$name)", {
            use(UnoCategory) {
                XTextTablesSupplier xTextTablesSupplier = (XTextTablesSupplier) component.uno(XTextTablesSupplier)
                Any any = xTextTablesSupplier?.textTables?.getByName(name)
                (XTextTable) any.object
            }
        }
    }

    /**
     * Get cell of a TextTable.
     */
    static XCell getTextTableCell(XTextTable textTable, Integer row, Integer column) {
        Profile.time "OOoTextTableCategory.getTextTableCell($row, $column)", {
            use(UnoCategory) {
                XCellRange xCellRange = (XCellRange) textTable.uno(XCellRange)
                xCellRange.getCellByPosition(column, row)
            }
        }
    }

    /**
     * Get cell of a TextTable.
     */
    static XCell getTextTableCell(XTextTable textTable, String coord) {
        Profile.time "OOoTextTableCategory.getTextTableCell(coord=$coord)", {
            use(UnoCategory) {
                textTable.getCellByName(coord)
            }
        }
    }

    /**
     * Get content of a cell in a TextTable.
     */
    static String getTextTableCellContent(XTextTable textTable, Integer row, Integer column) {
        Profile.time "OOoTextTableCategory.getTextTableCellContent(textTable=$textTable, $row, $column)", {
            use(UnoCategory) {
                XCell xCell = getTextTableCell(textTable, row, column)
                if (xCell) {
                    XText xText = (XText) xCell.uno(XText)
                    xText.string
                }
            }
        }
    }

    /**
     * Get content of a cell in a TextTable.
     */
    static String getTextTableCellContent(XTextTable textTable, String coord) {
        Profile.time "OOoTextTableCategory.getTextTableCellContent(textTable=$textTable, coord=$coord)", {
            use(UnoCategory) {
                def xCell = textTable.getTextTableCell(coord)
                if (xCell) {
                    XText xText = (XText) xCell.uno(XText)
                    xText.string
                }
            }
        }
    }

    /**
     * Ensure that a TextTable has enough rows and columns.
     */
    static void ensureTextTableCapacity(XTextTable textTable, Integer row, Integer column) {
        Profile.time "OOoTextTableCategory.ensureTextTableCapacity(textTable=$textTable, $row, $column)", {
            // Check rows; extend table if needed
            if (row > 0) {
                int rowCount = textTable.rows.count - 1 // count is 1-indexed
                if (row > rowCount) {
                    //println "ensureTextTableCapacity: adding row ${row} at ${rowCount + 1} to ${textTable.getInfo().name}"
                    textTable.rows.insertByIndex(rowCount + 1, Math.max(row - rowCount, 1))
                }
            }
            // Check columns in row; add column if needed
            if (column > 0) {
                XTableRows rows = textTable.rows
                Object oRow = rows.getByIndex(row)
                XPropertySet xPropertySet = (XPropertySet) oRow.object
                if (xPropertySet) {
                    // Count columns in a certain row to see if we have to extend
                    // com.sun.star.uno.Any
                    int columnCount = xPropertySet.getPropertyValue('TableColumnSeparators')?.length
                    if (column > columnCount) {
                        //println "ensureTextTableCapacity: adding column ${column} at ${columnCount + 1} to ${textTable.getInfo().name}"
                        // Use column count when extending table as the table might not have same column count as a certain row (splitting)
                        columnCount = textTable.columns.count // count is 1-indexed
                        textTable.columns.insertByIndex(columnCount, Math.max(column - columnCount, 1))
                    }
                }
            }
        }
    }

    /**
     * Ensure that a TextTable has enough rows and columns.
     */
    static void ensureTextTableCapacity(XTextTable textTable, String coord) {
        Profile.time "OOoTextTableCategory.ensureTextTableCapacity(textTable=$textTable, coord=$coord)", {
            // Parse coordinate; does it have a leading dollar sign?
            Map m = coord.contains(EXCLAMATION_MARK) || coord.contains(DOLLAR_SIGN) ? Coordinate.parseCoordinate(coord) : Coordinate.parseCoordinate("!${coord}")
            if (m.containsKey('rowIndex') && m.containsKey('columnIndex')) {
                ensureTextTableCapacity(textTable, m.rowIndex, m.columnIndex)
            }
        }
    }

    /**
     * Set content of a cell in a TextTable; extend rows and columns if needed.
     */
    static void setTextTableCellContent(XTextTable textTable, Integer row, Integer column, Object content) {
        Profile.time "OOoTextTableCategory.setTextTableCellContent(textTable=$textTable, row=$row, column=$column, content=$content)", {
            ensureTextTableCapacity(textTable, row, column)
            use(UnoCategory) {
                XCell xCell = getTextTableCell(textTable, row, column)
                if (xCell) {
                    XText xText = (XText) xCell.uno(XText)
                    xText.string = (content ?: '') as String
                }
            }
        }
    }

    /**
     * Convenience method for setTextTableCell(XTextTable, ...).
     */
    static void setTextTableCellContent(XComponent component, String textTable, Integer row, Integer column, Object content) {
        Profile.time "OOoTextTableCategory.setTextTableCellContent(component=$component, textTable=$textTable, row=$row, column=$column, content=$content)", {
            XTextTable xTextTable = getTextTable(component, textTable)
            setTextTableCellContent(xTextTable, row, column, content)
        }
    }

    /**
     * Set content of a cell in a TextTable; extend rows and columns if needed.
     */
    static void setTextTableCellContent(XTextTable textTable, String coord, Object content) {
        Profile.time "OOoTextTableCategory.setTextTableCellContent(textTable=$textTable, coord=$coord, content=$content)", {
            ensureTextTableCapacity(textTable, coord)
            use(UnoCategory) {
                XCell xCell = getTextTableCell(textTable, coord)
                if (xCell) {
                    XText xText = (XText) xCell.uno(XText)
                    xText.string = (content ?: '') as String
                }
            }
        }
    }

    /**
     * Publish a 2-dimensional array [rows][cols] as a TextTable.
     */
    static void setTextTableContent(XTextTable textTable, String[][] content, Integer startRow = 0, Integer startColumn = 0) {
        Profile.time "OOoTextTableCategory.setTextTableContent(textTable=$textTable, content=$content, startRow=$startRow, startColumn=$startColumn)", {
            // Go through every row
            0.upto content.length - 1, { row ->
                // ...and column
                0.upto content[row].length - 1, { col ->
                    // Set content of cell
                    setTextTableCellContent(textTable, row + startRow, col + startColumn, content[row][col])
                }
            }
        }
    }

    /**
     * Publish a map [coordinate: value] as a TextTable.
     */
    static void setTextTableContent(XTextTable textTable, Map content, Integer startRow = 0, Integer startColumn = 0) {
        Profile.time "OOoTextTableCategory.setTextTableContent(textTable=$textTable, content=$content, startRow=$startRow, startColumn=$startColumn)", {
            Map m = null
            content.each { k, v ->
                // Parse coordinate; does it have a leading dollar sign?
                m = k.contains(EXCLAMATION_MARK) || k.contains(DOLLAR_SIGN) ? Coordinate.parseCoordinate(k) : Coordinate.parseCoordinate("\$${k}")
                if (!m.containsKey('row') || !m.containsKey('column')) {
                    throw new OdiseeException('Could not parse row or column from coordinates')
                }
                // Set content of cell
                setTextTableCellContent(textTable, m.coord, v)
                // TODO Honor startRow, startColumn
                // textTable.setTextTableCellContent(m.row + startRow, m.column + startColumn, v)
            }
        }
    }

    /**
     * Get information about a table.
     */
    static Map getInfo(XTextTable textTable) {
        use(UnoCategory) {
            [
                    name     : ((XNamed) textTable.uno(XNamed)).name,
                    cellNames: textTable.cellNames //getCellNames()
            ]
        }
    }

    /**
     * Get content of a TextTable cell by using:
     * <pre>
     * use (OOoTextTableCategory) {
     *     odt["tablename!rowCol"]
     *}
     * </pre>
     */
    static Object get(XTextTable textTable, String name) {
        Profile.time "OOoTextTableCategory.get(textTable=$textTable, name=$name)", {
            Map m = Coordinate.parseCoordinate(name)
            getTextTableCellContent(textTable, m.coord)
        }
    }

    /**
     * Convenience method.
     * See #get(XTextTable, String).
     */
    static Object get(XComponent component, String name) {
        Profile.time "OOoTextTableCategory.get(component=$component, name=$name)", {
            Map m = Coordinate.parseCoordinate(name)
            // Return cell content or whole table?
            if (m.containsKey('coord')) {
                XTextTable xTextTable = getTextTable(component, m.table)
                getTextTableCellContent(xTextTable, m.coord)
            } else {
                getTextTable(component, m.table)
            }
        }
    }

    /**
     * <pre>
     * use (OOoTextTableCategory) {
     *     table["$row$col"] = "value"
     *     table["tablename"] = String[][], List or Map:
     *           [["A1", "A2"], ["B1", "B2"]]
     *           [A1: "a1", A2: "a2", B1: "b1", B2: "B2"]
     *}
     * </pre>
     */
    static void set(XTextTable textTable, String name, Object value) {
        Profile.time "OOoTextTableCategory.set(textTable=$textTable, name=$name, value=$value)", {
            Map m = Coordinate.parseCoordinate(name)
            switch (value) {
            // Set simple value, coordinates must be a cell
                case { it.getClass() in [Number, String, GString] }:
                    //textTable.setTextTableCellContent(m.coord, value)
                    setTextTableCellContent(textTable, m.rowIndex, m.columnIndex, value)
                    break
            // Set whole table from list
                case { it instanceof List || it instanceof String[][] }:
                    setTextTableContent(textTable, value as String[][])
                    break
            // Set whole table from map
                case { it instanceof Map }:
                    setTextTableContent(textTable, value as Map)
                    break
            }
        }
    }

    /**
     * Convenience method.
     * See #set(XTextTable, String, Object).
     */
    static void set(XComponent component, String name, Object value) {
        Profile.time "OOoTextTableCategory.set(component=$component, name=$name, value=$value)", {
            Map m = Coordinate.parseCoordinate(name)
            XTextTable xTextTable = getTextTable(component, m.table)
            set(xTextTable, name, value)
        }
    }

}
