/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 31.08.14 14:50
 */
package eu.artofcoding.odisee

/**
 * OpenOffice file formats, extensions, filter names.
 */
class OdiseeFileFormat {

    /**
     * Map for file formats.
     */
    private static final List<Map> OOO_FORMAT = [
            [description: "OpenDocument Text", filter: "writer8", extension: "odt"],
            [description: "OpenDocument Text Template", filter: "writer8_template", extension: "ott"],
            [description: "StarOffice Text Document", filter: "StarOffice XML (Writer)", extension: "sdt"],
            [description: "StarOffice Text Document Template", filter: "writer_StarOffice_XML_Writer_Template", extension: "stt"],
            [description: "Writer Portable Document Format", filter: "writer_pdf_Export", extension: "pdf"],
            [description: "Microsoft Word 97/2000/XP", filter: "MS Word 97", extension: "doc"],
            [description: "Microsoft Word 97/2000/XP Template", filter: "MS Word 97 Vorlage", extension: "dot"],
            [description: "Microsoft Word 2003 XML", filter: "MS Word 2003 XML", extension: "xml"],
            [description: "Microsoft Word 2007 XML", filter: "MS Word 2007 XML", extension: "docx"],
            [description: "RTF", filter: "Rich Text Format", extension: "rtf"],
            [description: "OpenDocument Calc", filter: "calc8", extension: "ods"],
            [description: "OpenDocument Calc Template", filter: "calc8_template", extension: "ots"],
            [description: "Calc Portable Document Format", filter: "calc_pdf_Export", extension: "pdf"],
            [description: "Microsoft Excel 97/2000/XP", filter: "MS Excel 97", extension: "xls"],
            [description: "Microsoft Excel 2003 XML", filter: "MS Excel 2003 XML", extension: "xls"],
            [description: "Microsoft Excel 2007 XML", filter: "Calc MS Excel 2007 XML", extension: "xlsx"]
    ]

    /**
     * Lookup a file format by filter name or extension and return first found.
     */
    static Map get(String name) {
        OOO_FORMAT.find { it.filter == name || it.extension == name }
    }

}
