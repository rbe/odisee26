package org.odisee.document

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A Calc template with one sheet, {@code Sheet1}.
 */
final class MinimalSpreadsheet {

    private MinimalSpreadsheet() {
    }

    static void write(Path target) {
        byte[] mime = 'application/vnd.oasis.opendocument.spreadsheet-template'.getBytes('UTF-8')
        CRC32 crc = new CRC32()
        crc.update(mime)
        ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))
        try {
            ZipEntry stored = new ZipEntry('mimetype')
            stored.method = ZipEntry.STORED
            stored.size = mime.length
            stored.crc = crc.value
            zip.putNextEntry(stored)
            zip.write(mime)
            zip.closeEntry()
            entry(zip, 'content.xml', contentXml())
            entry(zip, 'META-INF/manifest.xml', manifestXml())
        } finally {
            zip.close()
        }
    }

    private static void entry(ZipOutputStream zip, String name, String xml) {
        zip.putNextEntry(new ZipEntry(name))
        zip.write(xml.getBytes('UTF-8'))
        zip.closeEntry()
    }

    private static String contentXml() {
        '''<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.2">
 <office:body>
  <office:spreadsheet>
   <table:table table:name="Sheet1">
    <table:table-column/>
    <table:table-row>
     <table:table-cell office:value-type="string"><text:p>old</text:p></table:table-cell>
    </table:table-row>
   </table:table>
  </office:spreadsheet>
 </office:body>
</office:document-content>
'''
    }

    private static String manifestXml() {
        '''<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
 <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.spreadsheet-template"/>
 <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
</manifest:manifest>
'''
    }

}
