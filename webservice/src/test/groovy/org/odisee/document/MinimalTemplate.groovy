package org.odisee.document

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * A Writer template with one user field, {@code Hallo}.
 */
final class MinimalTemplate {

    private MinimalTemplate() {
    }

    static void write(Path target) {
        byte[] mime = 'application/vnd.oasis.opendocument.text-template'.getBytes('UTF-8')
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
            entry(zip, 'styles.xml', stylesXml())
            entry(zip, 'meta.xml', metaXml())
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
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" office:version="1.2">
 <office:scripts/>
 <office:font-face-decls/>
 <office:automatic-styles/>
 <office:body>
  <office:text>
   <text:user-field-decls>
    <text:user-field-decl office:value-type="string" office:string-value="placeholder" text:name="Hallo"/>
   </text:user-field-decls>
   <text:p text:style-name="Standard"><text:user-field-get text:name="Hallo">placeholder</text:user-field-get></text:p>
  </office:text>
 </office:body>
</office:document-content>
'''
    }

    private static String stylesXml() {
        '''<?xml version="1.0" encoding="UTF-8"?>
<office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.2">
 <office:styles>
  <style:style style:name="Standard" style:family="paragraph">
   <style:text-properties style:font-name="Liberation Serif" fo:font-size="12pt"/>
  </style:style>
 </office:styles>
</office:document-styles>
'''
    }

    private static String metaXml() {
        '''<?xml version="1.0" encoding="UTF-8"?>
<office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0" office:version="1.2">
 <office:meta><meta:generator>Odisee test</meta:generator></office:meta>
</office:document-meta>
'''
    }

    private static String manifestXml() {
        '''<?xml version="1.0" encoding="UTF-8"?>
<manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
 <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text-template"/>
 <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
 <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
 <manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
</manifest:manifest>
'''
    }

}
