package org.odisee.document

import groovy.json.JsonSlurper
import groovy.test.GroovyTestCase
import org.odisee.api.OdiseeException
import org.odisee.io.TenantPaths
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder

import java.nio.file.Files
import java.nio.file.Path
import java.security.Principal
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TemplateCatalogTest extends GroovyTestCase {

    void testCatalogListsFieldsBookmarksTablesAndRevisions() {
        Path dir = TenantPaths.templateDir('catalogada')
        Path rev = dir.resolve('Letter').resolve('rev')
        Files.createDirectories(rev)
        writeTemplate(rev.resolve('2.ott'))
        Files.write(dir.resolve('Letter.ott'), 'plain'.getBytes('UTF-8'))

        Map catalog = TemplateCatalog.describe('catalogada', 'Letter')
        Map json = new JsonSlurper().parseText(TemplateCatalog.toJson(catalog)) as Map

        assertEquals('Letter', json.name)
        assertEquals('2', json.revision)
        assertEquals(['Hallo', 'City'], json.userFields)
        assertEquals(['Header'], json.bookmarks)
        assertEquals(['Tabelle1'], json.tables)
        assertEquals(['1', '2'], json.revisions)
    }

    void testAnotherUsersTemplateIsNotFound() {
        Path bee = TenantPaths.templateDir('catalogbee').resolve('Letter').resolve('rev')
        Files.createDirectories(bee)
        writeTemplate(bee.resolve('9.ott'))
        SecurityContextHolder.context.authentication =
                new UsernamePasswordAuthenticationToken('catalognone', 'secret', [])
        try {
            Principal caller = DocumentController.requireCaller(DocumentController.callerFromContext(null))
            TemplateCatalog.describe(caller.name, 'Letter')
            fail('ada has no Letter')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.NOT_FOUND, e.httpStatus)
            assertTrue(e.message.contains("user 'catalognone'"))
        } finally {
            SecurityContextHolder.clearContext()
        }
        Map bees = TemplateCatalog.describe('catalogbee', 'Letter')
        assertEquals('9', bees.revision)
        assertEquals(['9'], bees.revisions)
    }

    void testUnreadableTemplateIsUnprocessable() {
        Path dir = TenantPaths.templateDir('catalogcara')
        Files.createDirectories(dir)
        Files.write(dir.resolve('Letter.ott'), 'not-a-zip'.getBytes('UTF-8'))
        try {
            TemplateCatalog.describe('catalogcara', 'Letter')
            fail('not a document')
        } catch (OdiseeException e) {
            assertEquals(OdiseeException.UNPROCESSABLE, e.httpStatus)
        }
    }

    private static void writeTemplate(Path target) {
        String content = '''<?xml version="1.0" encoding="UTF-8"?>
<office:document-content xmlns:office="urn:oasis:names:tc:opendocument:xmlns:office:1.0" xmlns:text="urn:oasis:names:tc:opendocument:xmlns:text:1.0" xmlns:table="urn:oasis:names:tc:opendocument:xmlns:table:1.0">
 <office:body><office:text>
  <text:user-field-decls>
   <text:user-field-decl text:name="Hallo" office:value-type="string"/>
   <text:user-field-decl text:name="City" office:value-type="string"/>
  </text:user-field-decls>
  <text:p><text:user-field-get text:name="Hallo">x</text:user-field-get></text:p>
  <text:bookmark-start text:name="Header"/>
  <table:table table:name="Tabelle1"/>
 </office:text></office:body>
</office:document-content>
'''
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
            zip.putNextEntry(new ZipEntry('content.xml'))
            zip.write(content.getBytes('UTF-8'))
            zip.closeEntry()
        } finally {
            zip.close()
        }
    }

}
