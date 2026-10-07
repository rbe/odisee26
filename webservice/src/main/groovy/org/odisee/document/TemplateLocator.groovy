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
import org.odisee.io.SafePaths
import org.odisee.shared.OdiseeConstant

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Matcher

/**
 * Find a Writer template under a single directory.
 * {@code Name.ott} is revision 1 when no numbered file exists.
 * {@code Name_revN.ott} is revision N. {@code LATEST} is the highest N,
 * or {@code Name.ott} when the directory has no numbered file.
 */
final class TemplateLocator {

    private static final String NUMBERED = /_rev(\d+)\.ott$/

    private TemplateLocator() {
    }

    static Path locate(Path templateDir, String name, String revision) {
        String templateName = SafePaths.requireSimpleName(name, 'template name')
        if (templateDir == null || !Files.isDirectory(templateDir)) {
            throw new OdiseeException("Template directory '${templateDir}' does not exist", OdiseeException.NOT_FOUND)
        }
        String requested = revision?.trim()
        if (!requested || requested.equalsIgnoreCase(OdiseeConstant.S_LATEST)) {
            Path latest = highestRevision(templateDir, templateName)
            if (latest != null) {
                return latest
            }
            Path plain = plainTemplate(templateDir, templateName)
            if (Files.exists(plain)) {
                return plain
            }
            throw missing(templateName, requested)
        }
        if (!requested.isInteger()) {
            throw new OdiseeException("Revision '${requested}' is not a number or LATEST", OdiseeException.BAD_REQUEST)
        }
        Path numbered = templateDir.resolve("${templateName}_rev${requested}.ott")
        if (Files.exists(numbered)) {
            return numbered
        }
        if (requested == '1') {
            Path plain = plainTemplate(templateDir, templateName)
            if (Files.exists(plain)) {
                return plain
            }
        }
        throw missing(templateName, requested)
    }

    static String revisionOf(Path templateFile) {
        if (templateFile == null) {
            return '1'
        }
        Matcher matcher = (templateFile.fileName.toString() =~ NUMBERED)
        if (matcher.find()) {
            return matcher.group(1)
        }
        return '1'
    }

    private static Path plainTemplate(Path templateDir, String templateName) {
        templateDir.resolve("${templateName}.ott")
    }

    private static Path highestRevision(Path templateDir, String templateName) {
        Path best = null
        int bestRevision = -1
        Files.newDirectoryStream(templateDir, "${templateName}_rev*.ott").withCloseable { stream ->
            stream.each { Path candidate ->
                Matcher matcher = (candidate.fileName.toString() =~ NUMBERED)
                if (matcher.find()) {
                    int revision = Integer.parseInt(matcher.group(1))
                    if (revision > bestRevision) {
                        bestRevision = revision
                        best = candidate
                    }
                }
            }
        }
        best
    }

    private static OdiseeException missing(String templateName, String revision) {
        String label = revision ? "revision ${revision}" : 'LATEST'
        new OdiseeException("Template '${templateName}' (${label}) does not exist", OdiseeException.NOT_FOUND)
    }

}
