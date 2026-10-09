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
 * Find a template under one user's template directory.
 * Writer files are {@code .ott}. Calc files are {@code .ots}. Impress files are {@code .otp}.
 * {@code Name.ott} is revision 1 when no numbered file exists. {@code .ots} and {@code .otp}
 * are revision 1 when no {@code .ott} is present.
 * {@code Name_revN.ott} is revision N in that flat directory.
 * {@code Name/rev/N.ott} is the same revision in the per-template directory.
 * {@code LATEST} is the highest N across both layouts, or the plain file
 * when neither layout has a numbered file.
 */
final class TemplateLocator {

    private static final List<String> EXTENSIONS = ['ott', 'ots', 'otp']

    private static final String NUMBERED = /_rev(\d+)\.(ott|ots|otp)$/

    private static final String REV_FILE = /^(\d+)\.(ott|ots|otp)$/

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
        Path inDirectory = existingRevisionFile(templateDir, templateName, requested)
        if (inDirectory != null) {
            return inDirectory
        }
        Path numbered = existingFlatRevision(templateDir, templateName, requested)
        if (numbered != null) {
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

    /**
     * Revision numbers on disk for this template, lowest first.
     * {@code Name.ott} counts as 1. The two layouts share one number space.
     */
    static List<String> revisions(Path templateDir, String name) {
        String templateName = SafePaths.requireSimpleName(name, 'template name')
        if (templateDir == null || !Files.isDirectory(templateDir)) {
            return []
        }
        Set<Integer> numbers = new TreeSet<>()
        eachFlat(templateDir, templateName) { Path candidate, int revision ->
            numbers.add(revision)
        }
        eachRevDir(templateDir, templateName) { Path candidate, int revision ->
            numbers.add(revision)
        }
        if (Files.exists(plainTemplate(templateDir, templateName))) {
            numbers.add(1)
        }
        numbers.collect { Integer.toString(it) }
    }

    static String revisionOf(Path templateFile) {
        if (templateFile == null) {
            return '1'
        }
        String fileName = templateFile.fileName.toString()
        Matcher matcher = (fileName =~ NUMBERED)
        if (matcher.find()) {
            return matcher.group(1)
        }
        Matcher revFile = (fileName =~ REV_FILE)
        if (revFile.matches() && templateFile.parent?.fileName?.toString() == 'rev') {
            return revFile.group(1)
        }
        return '1'
    }

    private static Path plainTemplate(Path templateDir, String templateName) {
        for (String extension : EXTENSIONS) {
            Path candidate = templateDir.resolve("${templateName}.${extension}")
            if (Files.exists(candidate)) {
                return candidate
            }
        }
        templateDir.resolve("${templateName}.ott")
    }

    private static Path existingRevisionFile(Path templateDir, String templateName, String revision) {
        for (String extension : EXTENSIONS) {
            Path candidate = templateDir.resolve(templateName).resolve('rev').resolve("${revision}.${extension}")
            if (Files.exists(candidate)) {
                return candidate
            }
        }
        null
    }

    private static Path existingFlatRevision(Path templateDir, String templateName, String revision) {
        for (String extension : EXTENSIONS) {
            Path candidate = templateDir.resolve("${templateName}_rev${revision}.${extension}")
            if (Files.exists(candidate)) {
                return candidate
            }
        }
        null
    }

    private static Path highestRevision(Path templateDir, String templateName) {
        Path best = null
        int bestRevision = -1
        boolean bestFromRevDir = false
        eachFlat(templateDir, templateName) { Path candidate, int revision ->
            if (revision > bestRevision) {
                bestRevision = revision
                best = candidate
                bestFromRevDir = false
            }
        }
        eachRevDir(templateDir, templateName) { Path candidate, int revision ->
            if (revision > bestRevision || (revision == bestRevision && !bestFromRevDir)) {
                bestRevision = revision
                best = candidate
                bestFromRevDir = true
            }
        }
        best
    }

    private static void eachFlat(Path templateDir, String templateName, Closure visitor) {
        if (!Files.isDirectory(templateDir)) {
            return
        }
        EXTENSIONS.each { String extension ->
            Files.newDirectoryStream(templateDir, "${templateName}_rev*.${extension}").withCloseable { stream ->
                stream.each { Path candidate ->
                    Matcher matcher = (candidate.fileName.toString() =~ NUMBERED)
                    if (matcher.find()) {
                        visitor.call(candidate, Integer.parseInt(matcher.group(1)))
                    }
                }
            }
        }
    }

    private static void eachRevDir(Path templateDir, String templateName, Closure visitor) {
        Path revDir = templateDir.resolve(templateName).resolve('rev')
        if (!Files.isDirectory(revDir)) {
            return
        }
        EXTENSIONS.each { String extension ->
            Files.newDirectoryStream(revDir, "*.${extension}").withCloseable { stream ->
                stream.each { Path candidate ->
                    Matcher matcher = (candidate.fileName.toString() =~ REV_FILE)
                    if (matcher.matches()) {
                        visitor.call(candidate, Integer.parseInt(matcher.group(1)))
                    }
                }
            }
        }
    }

    private static OdiseeException missing(String templateName, String revision) {
        String label = revision ? "revision ${revision}" : 'LATEST'
        new OdiseeException("Template '${templateName}' (${label}) does not exist", OdiseeException.NOT_FOUND)
    }

}
