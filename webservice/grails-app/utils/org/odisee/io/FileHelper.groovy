/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 16.03.17, 19:34
 */
package org.odisee.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

final class FileHelper {

    public static final String S_DOT = ".";

    private static final String S_UTF8 = 'UTF-8'

    private static final String S_NAME = 'name'

    private static final String S_EXT = 'ext'

    /**
     * Decompose a filename in name and extension.
     * @param file
     * @return Map Keys: name, ext.
     */
    public static Map decomposeFilename(final String filename) {
        final Map<String, String> map = [:]
        if (filename) {
            if (filename.contains('.')) {
                final String[] split = filename.split('[.]')
                map[S_NAME] = split[0..-2].join('')
                map[S_EXT] = split[-1]
            } else {
                map[S_NAME] = filename
                map[S_EXT] = 'xxx'
            }
        }
        // Return
        map
    }

    /**
     * Convenience method for {@link #decomposeFilename(String)}.
     * @see #decomposeFilename(String)
     * @param file
     * @return Map Keys: name, ext.
     */
    public static Map decomposeFilename(final Path file) {
        decomposeFilename(file?.fileName?.toString())
    }

    /**
     * Check if two file have the same format/extension or not.
     * @param file1 Left/first file for check.
     * @param file2 Right/second file for check.
     * @return boolean
     */
    public static boolean isDifferentFileFormat(Path file1, Path file2) {
        // Decompose both filenames
        Map<String, String> decomp1 = decomposeFilename(file1)
        Map<String, String> decomp2 = decomposeFilename(file2)
        // Extensions different?
        decomp1.ext.toLowerCase() != decomp2.ext.toLowerCase()
    }

    public static byte[] fromFile(path) {
        final Path f = path instanceof Path ? path : Paths.get(path)
        if (Files.exists(f) && Files.isReadable(f)) {
            f.toFile().readBytes()
        } else {
            null
        }
    }

    /**
     * Write some bytes to an UTF-8 encoded file.
     * @param file
     * @param str
     */
    public static void writeUTF8(final Path file, final String str) {
        Files.write(file, str.getBytes(S_UTF8))
    }

    public static String getPlainName(String filename) {
        String[] s = filename.split('\\.')
        s[0..s.length - 2].join(S_DOT)
    }

}
