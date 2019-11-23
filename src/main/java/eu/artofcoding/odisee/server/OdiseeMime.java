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

package eu.artofcoding.odisee.server;

/**
 * TODO Use Beetlejuice.MimeType
 */
public class OdiseeMime {

    /**
     * Mime types. See http://de.selfhtml.org/diverses/mimetypen.htm.
     */
    public static final String[][] mimeTypes;

    static {
        mimeTypes = new String[][]{
                {"Unknown".intern(), "*".intern(), "application/octet-stream".intern()},
                {"OpenOffice.org Writer (odt)".intern(), "odt".intern(), "application/vnd.oasis.opendocument.text".intern()},
                {"OpenOffice.org Writer Template (ott)".intern(), "ott".intern(), "application/vnd.oasis.opendocument.text-template".intern()},
                {"OpenOffice.org Writer Web (oth)".intern(), "oth".intern(), "application/vnd.oasis.opendocument.text-web".intern()},
                {"OpenOffice.org Writer Masterdocument (odm)".intern(), "odm".intern(), "application/vnd.oasis.opendocument.text-master".intern()},
                {"OpenOffice.org Draw (odg)".intern(), "odg".intern(), "application/vnd.oasis.opendocument.graphics".intern()},
                {"OpenOffice.org Draw Template (otg)".intern(), "otg".intern(), "application/vnd.oasis.opendocument.graphics-template".intern()},
                {"OpenOffice.org Impress (odp)".intern(), "odp".intern(), "application/vnd.oasis.opendocument.presentation".intern()},
                {"OpenOffice.org Impress Template (otp)".intern(), "otp".intern(), "application/vnd.oasis.opendocument.presentation-template".intern()},
                {"OpenOffice.org Calc (ods)".intern(), "ods".intern(), "application/vnd.oasis.opendocument.spreadsheet".intern()},
                {"OpenOffice.org Calc Template (ots)".intern(), "ots".intern(), "application/vnd.oasis.opendocument.spreadsheet-template".intern()},
                {"OpenOffice.org Chart (odc)".intern(), "odc".intern(), "application/vnd.oasis.opendocument.chart".intern()},
                {"OpenOffice.org Formula (odf)".intern(), "odf".intern(), "application/vnd.oasis.opendocument.formula".intern()},
                {"OpenOffice.org Image (odi)".intern(), "odi".intern(), "application/vnd.oasis.opendocument.image".intern()},
                {"JPEG (jpg)".intern(), "jpg".intern(), "image/jpeg".intern()},
                {"JPEG (jpeg)".intern(), "jpeg".intern(), "image/jpeg".intern()},
                {"TIFF (tif)".intern(), "tif".intern(), "image/tif".intern()},
                {"TIFF (tiff)".intern(), "tiff".intern(), "image/tif".intern()},
                {"Portable Network Graphics (png)".intern(), "png".intern(), "image/png".intern()},
                {"Portable Document Format (pdf)".intern(), "pdf".intern(), "application/pdf".intern()},
                {"Rich Text Format (rtf)".intern(), "rtf".intern(), "application/rtf".intern()},
                {"Microsoft Office Word 97-2003 (doc)".intern(), "doc".intern(), "application/msword".intern()},
                {"Microsoft Office Word 97-2003 Template (dot)".intern(), "dot".intern(), "application/msword".intern()},
                {"Microsoft Office Excel 97-2003 (xls)".intern(), "xls".intern(), "application/vnd.ms-excel".intern()},
                {"Microsoft Office Excel 97-2003 Template (xlt)".intern(), "xlt".intern(), "application/vnd.ms-excel".intern()},
                {"Microsoft Office Excel 97-2003 Addin (xla)".intern(), "xla".intern(), "application/vnd.ms-excel".intern()},
                {"Microsoft Office PowerPoint 97-2003 (ppt)".intern(), "ppt".intern(), "application/vnd.ms-powerpoint".intern()},
                {"Microsoft Office PowerPoint 97-2003 Template (pot)".intern(), "pot".intern(), "application/vnd.ms-powerpoint".intern()},
                {"Microsoft Office PowerPoint 97-2003 (pps)".intern(), "pps".intern(), "application/vnd.ms-powerpoint".intern()},
                {"Microsoft Office PowerPoint 97-2003 (ppa)".intern(), "ppa".intern(), "application/vnd.ms-powerpoint".intern()},
                {"Microsoft Office Word 2007 (docx)".intern(), "docx".intern(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document".intern()},
                {"Microsoft Office Word 2007 Template (dotx)".intern(), "dotx".intern(), "application/vnd.openxmlformats-officedocument.wordprocessingml.template".intern()},
                {"Microsoft Office Word 2007 (docm)".intern(), "docm".intern(), "application/vnd.ms-word.document.macroEnabled.12".intern()},
                {"Microsoft Office Word 2007 Template (dotm)".intern(), "dotm".intern(), "application/vnd.ms-word.template.macroEnabled.12".intern()},
                {"Microsoft Office Excel 2007 (xlsx)".intern(), "xlsx".intern(), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet".intern()},
                {"Microsoft Office Excel 2007 Template (xltx)".intern(), "xltx".intern(), "application/vnd.openxmlformats-officedocument.spreadsheetml.template".intern()},
                {"Microsoft Office Excel 2007 (xlsm)".intern(), "xlsm".intern(), "application/vnd.ms-excel.sheet.macroEnabled.12".intern()},
                {"Microsoft Office Excel 2007 Template (xltm)".intern(), "xltm".intern(), "application/vnd.ms-excel.template.macroEnabled.12".intern()},
                {"Microsoft Office Excel 2007 Addin (xlam)".intern(), "xlam".intern(), "application/vnd.ms-excel.addin.macroEnabled.12".intern()},
                {"Microsoft Office Excel 2007 Binary (xlsb)".intern(), "xlsb".intern(), "application/vnd.ms-excel.sheet.binary.macroEnabled.12".intern()},
                {"Microsoft Office PowerPoint 2007 (pptx)".intern(), "pptx".intern(), "application/vnd.openxmlformats-officedocument.presentationml.presentation".intern()},
                {"Microsoft Office PowerPoint 2007 Template (potx)".intern(), "potx".intern(), "application/vnd.openxmlformats-officedocument.presentationml.template".intern()},
                {"Microsoft Office PowerPoint 2007 (ppsx)".intern(), "ppsx".intern(), "application/vnd.openxmlformats-officedocument.presentationml.slideshow".intern()},
                {"Microsoft Office PowerPoint 2007 Addin (ppam)".intern(), "ppam".intern(), "application/vnd.ms-powerpoint.addin.macroEnabled.12".intern()},
                {"Microsoft Office PowerPoint 2007 (pptm)".intern(), "pptm".intern(), "application/vnd.ms-powerpoint.presentation.macroEnabled.12".intern()},
                {"Microsoft Office PowerPoint 2007 Template (potm)".intern(), "potm".intern(), "application/vnd.ms-powerpoint.template.macroEnabled.12".intern()},
                {"Microsoft Office PowerPoint 2007 Slideshow (ppsm)".intern(), "ppsm".intern(), "application/vnd.ms-powerpoint.slideshow.macroEnabled.12".intern()}
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
