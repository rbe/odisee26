/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import groovy.json.JsonOutput
import groovy.xml.XmlSlurper
import org.odisee.api.OdiseeException
import org.odisee.io.SafePaths
import org.odisee.io.TenantPaths
import org.odisee.shared.OdiseeConstant

import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * {@code GET /template/{name}} reads one user's template.
 * Fields, bookmarks, and tables come from the latest revision.
 * The revision list is every number on disk under that user.
 */
final class TemplateCatalog {

    private TemplateCatalog() {
    }

    static Map describe(String user, String templateName) {
        String safeUser = TenantPaths.requireUserName(user)
        String safeName = SafePaths.requireSimpleName(templateName, 'template name')
        Path dir = TenantPaths.templateDir(safeUser)
        try {
            Path file = TemplateLocator.locate(dir, safeName, OdiseeConstant.S_LATEST)
            Map inventory = readInventory(file)
            return [
                    name      : safeName,
                    revision  : TemplateLocator.revisionOf(file),
                    userFields: inventory.userFields,
                    bookmarks : inventory.bookmarks,
                    tables    : inventory.tables,
                    revisions : TemplateLocator.revisions(dir, safeName),
            ]
        } catch (OdiseeException e) {
            if (e.httpStatus == OdiseeException.NOT_FOUND) {
                throw new OdiseeException("Template '${safeName}' does not exist for user '${safeUser}'", OdiseeException.NOT_FOUND)
            }
            throw e
        }
    }

    static String toJson(Map catalog) {
        JsonOutput.toJson([
                name      : catalog.name,
                revision  : catalog.revision,
                userFields: catalog.userFields,
                bookmarks : catalog.bookmarks,
                tables    : catalog.tables,
                revisions : catalog.revisions,
        ])
    }

    private static Map readInventory(Path file) {
        ZipFile zip = null
        try {
            zip = new ZipFile(file.toFile())
            def entry = zip.getEntry('content.xml')
            if (entry == null) {
                throw new OdiseeException("Template '${file.fileName}' has no content.xml", OdiseeException.UNPROCESSABLE)
            }
            def xml = new XmlSlurper(false, false).parse(zip.getInputStream(entry))
            List<String> userFields = []
            List<String> bookmarks = []
            List<String> tables = []
            xml.'**'.each { node ->
                String local = node.name()?.toString()
                Map attrs = node.attributes()
                if (local == 'text:user-field-decl' || local == 'text:user-field-get') {
                    add(userFields, attrs['text:name']?.toString())
                } else if (local == 'text:bookmark' || local == 'text:bookmark-start') {
                    add(bookmarks, attrs['text:name']?.toString())
                } else if (local == 'table:table') {
                    add(tables, attrs['table:name']?.toString())
                }
            }
            [userFields: userFields, bookmarks: bookmarks, tables: tables]
        } catch (OdiseeException e) {
            throw e
        } catch (Exception e) {
            throw new OdiseeException("Template '${file.fileName}' is not a readable document", OdiseeException.UNPROCESSABLE)
        } finally {
            zip?.close()
        }
    }

    private static void add(List<String> names, String value) {
        String trimmed = value?.trim()
        if (trimmed && !names.contains(trimmed)) {
            names.add(trimmed)
        }
    }

}
