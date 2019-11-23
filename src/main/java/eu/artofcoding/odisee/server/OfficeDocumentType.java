/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 14.11.14, 09:55
 */

package eu.artofcoding.odisee.server;

public enum OfficeDocumentType {

    TEXT("Text document", "swriter", "ott", "odt", "writer_pdf_Export"),
    SPREADSHEET("Spreadsheet", "scalc", "ots", "ots", "calc_pdf_Export");

    private final String description;
    private final String internalType;
    private final String pdfExportFilter;
    private final String templateExtension;
    private final String documentExtension;

    OfficeDocumentType(final String description, final String internalType, final String templateExtension, final String documentExtension, final String pdfExportFilter) {
        this.description = description;
        this.internalType = internalType;
        this.templateExtension = templateExtension;
        this.documentExtension = documentExtension;
        this.pdfExportFilter = pdfExportFilter;
    }

    public String getDescription() {
        return description;
    }

    public String getInternalType() {
        return internalType;
    }

    public String getTemplateExtension() {
        return templateExtension;
    }

    public String getDocumentExtension() {
        return documentExtension;
    }

    public String getPdfExportFilter() {
        return pdfExportFilter;
    }

    /**
     * Find an enum constant by a search term.
     * @param what Can be description, internalType of pdfExportFilter.
     * @return OfficeDocumentType
     */
    public static OfficeDocumentType find(final String what) {
        OfficeDocumentType r = null;
        for (final OfficeDocumentType t : OfficeDocumentType.values()) {
            if (t.getDescription().equals(what) || t.getInternalType().equals(what)
                    || t.getTemplateExtension().equals(what) || t.getDocumentExtension().equals(what)
                    || t.getPdfExportFilter().equals(what)) {
                r = t;
                break;
            }
        }
        return r;
    }

}
