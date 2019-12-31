/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 31.08.14, 14:50
 */

package org.odisee.document;

public class OdiseeMime {

    /**
     * Mime types. See http://de.selfhtml.org/diverses/mimetypen.htm.
     */
    protected static final String[][] mimeTypes;

    public static final String IMAGE_JPEG = "image/jpeg";

    public static final String IMAGE_TIF = "image/tif";

    public static final String APPLICATION_PDF = "application/pdf";

    public static final String APPLICATION_MSWORD = "application/msword";

    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";

    public static final String APPLICATION_VND_MS_EXCEL = "application/vnd.ms-excel";

    public static final String APPLICATION_VND_MS_POWERPOINT = "application/vnd.ms-powerpoint";

    private OdiseeMime() {
        throw new AssertionError();
    }

    static {
        mimeTypes = new String[][]{
                {"Unknown", "*", APPLICATION_OCTET_STREAM},
                {"OpenOffice.org Writer (odt)", "odt", "application/vnd.oasis.opendocument.text"},
                {"OpenOffice.org Writer Template (ott)", "ott", "application/vnd.oasis.opendocument.text-template"},
                {"OpenOffice.org Writer Web (oth)", "oth", "application/vnd.oasis.opendocument.text-web"},
                {"OpenOffice.org Writer Masterdocument (odm)", "odm", "application/vnd.oasis.opendocument.text-master"},
                {"OpenOffice.org Draw (odg)", "odg", "application/vnd.oasis.opendocument.graphics"},
                {"OpenOffice.org Draw Template (otg)", "otg", "application/vnd.oasis.opendocument.graphics-template"},
                {"OpenOffice.org Impress (odp)", "odp", "application/vnd.oasis.opendocument.presentation"},
                {"OpenOffice.org Impress Template (otp)", "otp", "application/vnd.oasis.opendocument.presentation-template"},
                {"OpenOffice.org Calc (ods)", "ods", "application/vnd.oasis.opendocument.spreadsheet"},
                {"OpenOffice.org Calc Template (ots)", "ots", "application/vnd.oasis.opendocument.spreadsheet-template"},
                {"OpenOffice.org Chart (odc)", "odc", "application/vnd.oasis.opendocument.chart"},
                {"OpenOffice.org Formula (odf)", "odf", "application/vnd.oasis.opendocument.formula"},
                {"OpenOffice.org Image (odi)", "odi", "application/vnd.oasis.opendocument.image"},
                {"JPEG (jpg)", "jpg", IMAGE_JPEG},
                {"JPEG (jpeg)", "jpeg", IMAGE_JPEG},
                {"TIFF (tif)", "tif", IMAGE_TIF},
                {"TIFF (tiff)", "tiff", IMAGE_TIF},
                {"Portable Network Graphics (png)", "png", "image/png"},
                {"Portable Document Format (pdf)", "pdf", APPLICATION_PDF},
                {"Rich Text Format (rtf)", "rtf", "application/rtf"},
                {"Microsoft Office Word 97-2003 (doc)", "doc", APPLICATION_MSWORD},
                {"Microsoft Office Word 97-2003 Template (dot)", "dot", APPLICATION_MSWORD},
                {"Microsoft Office Excel 97-2003 (xls)", "xls", APPLICATION_VND_MS_EXCEL},
                {"Microsoft Office Excel 97-2003 Template (xlt)", "xlt", APPLICATION_VND_MS_EXCEL},
                {"Microsoft Office Excel 97-2003 Addin (xla)", "xla", APPLICATION_VND_MS_EXCEL},
                {"Microsoft Office PowerPoint 97-2003 (ppt)", "ppt", APPLICATION_VND_MS_POWERPOINT},
                {"Microsoft Office PowerPoint 97-2003 Template (pot)", "pot", APPLICATION_VND_MS_POWERPOINT},
                {"Microsoft Office PowerPoint 97-2003 (pps)", "pps", APPLICATION_VND_MS_POWERPOINT},
                {"Microsoft Office PowerPoint 97-2003 (ppa)", "ppa", APPLICATION_VND_MS_POWERPOINT},
                {"Microsoft Office Word 2007 (docx)", "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
                {"Microsoft Office Word 2007 Template (dotx)", "dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template"},
                {"Microsoft Office Word 2007 (docm)", "docm", "application/vnd.ms-word.document.macroEnabled.12"},
                {"Microsoft Office Word 2007 Template (dotm)", "dotm", "application/vnd.ms-word.template.macroEnabled.12"},
                {"Microsoft Office Excel 2007 (xlsx)", "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
                {"Microsoft Office Excel 2007 Template (xltx)", "xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template"},
                {"Microsoft Office Excel 2007 (xlsm)", "xlsm", "application/vnd.ms-excel.sheet.macroEnabled.12"},
                {"Microsoft Office Excel 2007 Template (xltm)", "xltm", "application/vnd.ms-excel.template.macroEnabled.12"},
                {"Microsoft Office Excel 2007 Addin (xlam)", "xlam", "application/vnd.ms-excel.addin.macroEnabled.12"},
                {"Microsoft Office Excel 2007 Binary (xlsb)", "xlsb", "application/vnd.ms-excel.sheet.binary.macroEnabled.12"},
                {"Microsoft Office PowerPoint 2007 (pptx)", "pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
                {"Microsoft Office PowerPoint 2007 Template (potx)", "potx", "application/vnd.openxmlformats-officedocument.presentationml.template"},
                {"Microsoft Office PowerPoint 2007 (ppsx)", "ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow"},
                {"Microsoft Office PowerPoint 2007 Addin (ppam)", "ppam", "application/vnd.ms-powerpoint.addin.macroEnabled.12"},
                {"Microsoft Office PowerPoint 2007 (pptm)", "pptm", "application/vnd.ms-powerpoint.presentation.macroEnabled.12"},
                {"Microsoft Office PowerPoint 2007 Template (potm)", "potm", "application/vnd.ms-powerpoint.template.macroEnabled.12"},
                {"Microsoft Office PowerPoint 2007 Slideshow (ppsm)", "ppsm", "application/vnd.ms-powerpoint.slideshow.macroEnabled.12"}
        };
    }

    public static String getExtensionByMimeType(String mimeType) {
        for (String[] m : OdiseeMime.mimeTypes) {
            if (m[2].equals(mimeType)) {
                return m[1];
            }
        }
        return null;
    }

    public static String getMimeTypeByExtension(String extension) {
        for (String[] m : OdiseeMime.mimeTypes) {
            if (m[1].equals(extension)) {
                return m[2];
            }
        }
        return null;
    }

    public static String getDescriptionByExtension(String extension) {
        for (String[] m : OdiseeMime.mimeTypes) {
            if (m[1].equals(extension)) {
                return m[0];
            }
        }
        return null;
    }

}
