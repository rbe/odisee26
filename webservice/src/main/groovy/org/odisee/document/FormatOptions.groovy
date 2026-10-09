/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import org.odisee.api.OdiseeException

/**
 * PDF export options from {@code format/options/option}.
 * A {@code .pdfa} file with no PDF version still uses the PDF/A save path.
 */
final class FormatOptions {

    private FormatOptions() {
    }

    static Map<String, Object> filterData(List<Map<String, String>> options) {
        Map<String, Object> data = new LinkedHashMap<>()
        options?.each { Map<String, String> option ->
            String name = option?.name?.trim()
            if (!name) {
                return
            }
            String key = name.toLowerCase()
            String value = option.value
            if (key == 'selectpdfversion' || key == 'pdf-version' || key == 'pdfversion') {
                data.put('SelectPdfVersion', pdfVersion(value))
            } else if (key == 'exporttaggedpdf' || key == 'usetaggedpdf' || key == 'tagged' || key == 'tagged-pdf' || key == 'taggedpdf') {
                data.put('UseTaggedPDF', truth(value))
            } else if (key == 'watermark') {
                data.put('Watermark', value == null ? '' : value)
            }
        }
        data
    }

    /**
     * The {@code .pdfa} suffix is the PDF/A save path when the request did not set a PDF version.
     */
    static boolean pdfaFallback(String fileName, Map<String, Object> filterData) {
        fileName != null && fileName.toLowerCase().endsWith('.pdfa') && (filterData == null || !filterData.containsKey('SelectPdfVersion'))
    }

    static boolean pdfFamily(String fileName) {
        if (fileName == null) {
            return false
        }
        String lower = fileName.toLowerCase()
        lower.endsWith('.pdf') || lower.endsWith('.pdfa')
    }

    static int pdfVersion(String value) {
        String token = value == null ? '' : value.trim().toLowerCase()
        switch (token) {
            case '0':
            case '1.4':
            case 'pdf1.4':
            case 'pdf-1.4':
                return 0
            case '1':
            case 'a-1':
            case 'pdf/a-1':
            case 'pdfa-1':
            case 'pdfa':
                return 1
            case '2':
            case 'a-2':
            case 'pdf/a-2':
            case 'pdfa-2':
                return 2
            case '3':
            case 'a-3':
            case 'pdf/a-3':
            case 'pdfa-3':
                return 3
            default:
                if (token.isInteger()) {
                    return Integer.parseInt(token)
                }
                throw new OdiseeException("Unknown PDF version '${value}'", OdiseeException.BAD_REQUEST)
        }
    }

    private static boolean truth(String value) {
        String token = value == null ? '' : value.trim().toLowerCase()
        token == 'true' || token == '1' || token == 'yes'
    }

}
