/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 02.02.15 18:36
 */
package eu.artofcoding.odisee.ooo.server

import eu.artofcoding.odisee.OdiseePath
import eu.artofcoding.odisee.helper.GroovyJvmHelper

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class OOoInstallation {

    private static final String SOFFICE_PROGRAM_DIR = 'program'
    private static final String OS_MAC_OS = 'MacOS'
    private static final String SOFFICE_BIN = 'soffice.bin'

    /**
     * Recurse a directory and look for all files with a certain name.
     */

    private List look(String pat, File dir, List result = []) {
        dir.listFiles().each {
            if (it.isDirectory() /* JDK7 && !it.isSymbolicLink()*/) {
                look(pat, it, result)
            } else if (it.name ==~ pat) {
                result << it
            }
        }
        result
    }

    /**
     * Find OpenOffice installation.
     * @return List < File >  Path to installation (program/ folder).
     */
    List<File> findOOoInstallation() {
        List<File> result
        // Check environment variable OOO_HOME
        if (OdiseePath.OOO_HOME) {
            switch (OS_NAME) {
            // MacOS X: Don't use program link
            // http://www.openoffice.org/issues/show_bug.cgi?id=101203
                case { it ==~ GroovyJvmHelper.OS_DARWIN }:
                    Path file = Paths.get(OdiseePath.OOO_HOME, OS_MAC_OS)
                    if (Files.exists(file)) {
                        result = [file]
                    }
                    break
            // All other
                default:
                    Path file = Paths.get(OdiseePath.OOO_HOME, SOFFICE_PROGRAM_DIR)
                    if (Files.exists(file)) {
                        result = [file]
                    }
                    break
            }
        }
        if (result) {
            result
        } else {
            // Look for soffice executable depending on operating system
            switch (OS_NAME) {
            // Mac OS X: make an educated guess
                case { it ==~ OS_DARWIN }:
                    Path guess = Paths.get('/Applications/OpenOffice.org.app/Contents/MacOS/soffice')
                    if (Files.exists(guess)) {
                        result = [guess]
                    } else {
                        result = File.listRoots().collectMany { root ->
                            ['Applications/Oracle Open Office.app', 'Applications/LibreOffice.app'].collect { folder ->
                                look(SOFFICE_BIN, Paths.get(root, folder))
                            }
                        }
                    }
                    break
            // UNIX: look in /usr/lib and /opt folders
                case { it in [GroovyJvmHelper.OS_LINUX, GroovyJvmHelper.OS_SUNOS] || it ==~ GroovyJvmHelper.OS_BSD }:
                    result = ['/usr/lib', '/opt'].collectMany { folder ->
                        look(SOFFICE_BIN, new File('/', folder))
                    }
                    break
            // Windows: look in different drives, Program* folders
                case { it ==~ OS_WINDOWS }:
                    List drives = File.listRoots().findAll {
                        it.absolutePath ==~ /[CDE]:.*/
                    }
                    result = drives.collectMany { root ->
                        ['Programme', 'Program Files', 'Program Files (x86)'].collect { folder ->
                            look(SOFFICE_BIN, new File(root, folder))
                        }
                    }
                    break
            }
            //println "result=${result?.dump()}"
            result ? result.parentFile : null
        }
    }

}
