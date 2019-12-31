/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 23.11.14, 16:26
 */

package org.odisee.ooo.process;

import org.odisee.ooo.connection.OdiseeServerException;

import java.nio.file.Path;
import java.nio.file.Paths;

public class JvmHelper {

    /**
     * Name of operating system.
     */
    public static final String OS_NAME = System.getProperty("os.name");

    private static final String[] TMP_DIR_NAMES = {"TMP", "TMPDIR", "TEMP"};

    private JvmHelper() {
        throw new AssertionError();
    }

    public static Path findArchiveOfClass(Class<?> clazz) throws ClassNotFoundException {
        String path = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
        if (null == path) {
            throw new ClassNotFoundException(String.format("Could not find class %s", clazz));
        }
        return Paths.get(path.substring(1));
    }

    public static Path findArchiveOfClass(String clazz) throws ClassNotFoundException {
        Class<?> klass = Class.forName(clazz);
        return findArchiveOfClass(klass);
    }

    public static String findTemporaryDirectory() throws OdiseeServerException {
        String odiseeTmp = System.getenv("ODISEE_TMP");
        if (null == odiseeTmp) {
            odiseeTmp = System.getProperty("java.io.tmpdir");
        }
        if (null == odiseeTmp) {
            for (String t : TMP_DIR_NAMES) {
                if (null == odiseeTmp) {
                    odiseeTmp = System.getenv(t);
                } else {
                    break;
                }
            }
        }
        if (null == odiseeTmp) {
            throw new OdiseeServerException("Cannot find temporary directory, please set ODISEE_TMP");
        }
        String odiseeTmpWithSlash = odiseeTmp.replace('\\', '/');
        if (odiseeTmpWithSlash.endsWith("/")) {
            odiseeTmpWithSlash = odiseeTmpWithSlash.substring(0, odiseeTmpWithSlash.length() - 1);
        }
        return odiseeTmpWithSlash;
    }

}
