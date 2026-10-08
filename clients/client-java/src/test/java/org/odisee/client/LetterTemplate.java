package org.odisee.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writer template with one user field, {@code Hallo}.
 * Same fixture as {@code MinimalTemplate} in the webservice tests.
 */
final class LetterTemplate {

    private LetterTemplate() {
    }

    static void write(final Path target) throws IOException {
        final byte[] mime = "application/vnd.oasis.opendocument.text-template".getBytes(StandardCharsets.UTF_8);
        final CRC32 crc = new CRC32();
        crc.update(mime);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            final ZipEntry stored = new ZipEntry("mimetype");
            stored.setMethod(ZipEntry.STORED);
            stored.setSize(mime.length);
            stored.setCrc(crc.getValue());
            zip.putNextEntry(stored);
            zip.write(mime);
            zip.closeEntry();
            entry(zip, "content.xml", contentXml());
            entry(zip, "styles.xml", stylesXml());
            entry(zip, "meta.xml", metaXml());
            entry(zip, "META-INF/manifest.xml", manifestXml());
        }
    }

    private static void entry(final ZipOutputStream zip, final String name, final String xml) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(xml.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static String contentXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
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
                """;
    }

    private static String stylesXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-styles xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:style="urn:oasis:names:tc:opendocument:xmlns:style:1.0" xmlns:fo="urn:oasis:names:tc:opendocument:xmlns:xsl-fo-compatible:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" office:version="1.2">
                 <office:styles>
                  <style:style style:name="Standard" style:family="paragraph">
                   <style:text-properties style:font-name="Liberation Serif" fo:font-size="12pt"/>
                  </style:style>
                 </office:styles>
                </office:document-styles>
                """;
    }

    private static String metaXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <office:document-meta xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:meta="urn:oasis:names:tc:opendocument:xmlns:meta:1.0" office:version="1.2">
                 <office:meta><meta:generator>Odisee test</meta:generator></office:meta>
                </office:document-meta>
                """;
    }

    private static String manifestXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <manifest:manifest xmlns:manifest="urn:oasis:names:tc:opendocument:xmlns:manifest:1.0" manifest:version="1.2">
                 <manifest:file-entry manifest:full-path="/" manifest:media-type="application/vnd.oasis.opendocument.text-template"/>
                 <manifest:file-entry manifest:full-path="content.xml" manifest:media-type="text/xml"/>
                 <manifest:file-entry manifest:full-path="styles.xml" manifest:media-type="text/xml"/>
                 <manifest:file-entry manifest:full-path="meta.xml" manifest:media-type="text/xml"/>
                </manifest:manifest>
                """;
    }

}
