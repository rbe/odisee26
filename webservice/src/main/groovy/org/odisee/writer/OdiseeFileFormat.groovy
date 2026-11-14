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
package org.odisee.writer

import org.odisee.document.OfficeDocumentType

/**
 * OpenOffice file formats, extensions, filter names.
 */
class OdiseeFileFormat {

    /**
     * Map for file formats.
     */
    private static final List<Map> OOO_FORMAT = [
            [description: "OpenDocument Text", filter: "writer8", extension: "odt", application: OfficeDocumentType.TEXT],
            [description: "OpenDocument Text Template", filter: "writer8_template", extension: "ott", application: OfficeDocumentType.TEXT],
            [description: "StarOffice Text Document", filter: "StarOffice XML (Writer)", extension: "sdt", application: OfficeDocumentType.TEXT],
            [description: "StarOffice Text Document Template", filter: "writer_StarOffice_XML_Writer_Template", extension: "stt", application: OfficeDocumentType.TEXT],
            [description: "Writer Portable Document Format", filter: "writer_pdf_Export", extension: "pdf", application: OfficeDocumentType.TEXT],
            [description: "Microsoft Word 97/2000/XP", filter: "MS Word 97", extension: "doc", application: OfficeDocumentType.TEXT],
            [description: "Microsoft Word 97/2000/XP Template", filter: "MS Word 97 Vorlage", extension: "dot", application: OfficeDocumentType.TEXT],
            [description: "Microsoft Word 2003 XML", filter: "MS Word 2003 XML", extension: "xml", application: OfficeDocumentType.TEXT],
            [description: "Microsoft Word 2007 XML", filter: "MS Word 2007 XML", extension: "docx", application: OfficeDocumentType.TEXT],
            [description: "RTF", filter: "Rich Text Format", extension: "rtf", application: OfficeDocumentType.TEXT],
            [description: "OpenDocument Calc", filter: "calc8", extension: "ods", application: OfficeDocumentType.SPREADSHEET],
            [description: "OpenDocument Calc Template", filter: "calc8_template", extension: "ots", application: OfficeDocumentType.SPREADSHEET],
            [description: "Calc Portable Document Format", filter: "calc_pdf_Export", extension: "pdf", application: OfficeDocumentType.SPREADSHEET],
            [description: "Microsoft Excel 97/2000/XP", filter: "MS Excel 97", extension: "xls", application: OfficeDocumentType.SPREADSHEET],
            [description: "Microsoft Excel 2003 XML", filter: "MS Excel 2003 XML", extension: "xls", application: OfficeDocumentType.SPREADSHEET],
            [description: "Microsoft Excel 2007 XML", filter: "Calc MS Excel 2007 XML", extension: "xlsx", application: OfficeDocumentType.SPREADSHEET],
            [description: "OpenDocument Presentation", filter: "impress8", extension: "odp", application: OfficeDocumentType.PRESENTATION],
            [description: "OpenDocument Presentation Template", filter: "impress8_template", extension: "otp", application: OfficeDocumentType.PRESENTATION],
            [description: "Impress Portable Document Format", filter: "impress_pdf_Export", extension: "pdf", application: OfficeDocumentType.PRESENTATION],
            [description: "Microsoft PowerPoint 97/2000/XP", filter: "MS PowerPoint 97", extension: "ppt", application: OfficeDocumentType.PRESENTATION],
            [description: "Microsoft PowerPoint 2007 XML", filter: "Impress MS PowerPoint 2007 XML", extension: "pptx", application: OfficeDocumentType.PRESENTATION]
    ]

    /**
     * Lookup a file format by filter name or extension and return first found.
     */
    static Map get(String name) {
        if (!name) {
            return null
        }
        String lower = name.toLowerCase(Locale.ROOT)
        String extLookup = lower == 'pdfa' ? 'pdf' : lower
        OOO_FORMAT.find { it.filter == name || it.extension == extLookup }
    }

    /**
     * Lookup a file format for a specific office application, falling back to any matching format.
     */
    static Map get(String name, OfficeDocumentType application) {
        if (!name) {
            return null
        }
        if (application == null) {
            return get(name)
        }
        String lower = name.toLowerCase(Locale.ROOT)
        String extLookup = lower == 'pdfa' ? 'pdf' : lower
        Map match = OOO_FORMAT.find {
            it.application == application && (it.filter == name || it.extension == extLookup)
        }
        match ?: get(name)
    }

    static Map getAt(String name) {
        get(name)
    }

    /**
     * The suffix after the last dot. {@code docx} is four characters, so the last three are not the extension.
     */
    static String extensionOf(String fileName) {
        String lower = fileName == null ? '' : fileName.toLowerCase(Locale.ROOT)
        int dot = lower.lastIndexOf('.')
        dot >= 0 && dot < lower.length() - 1 ? lower.substring(dot + 1) : lower
    }

}
