/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 23.11.14, 16:10
 */

package org.odisee.ooo.process;

import java.nio.file.Path;
import java.util.List;

public final class ProcessHelper {

    private static final String OS_NAME = System.getProperty("os.name");

    private ProcessHelper() {
        throw new AssertionError();
    }

    public static String[] convert(final Path executable, final List<String> options) {
        final int arguments = options.size() + 1;
        final String[] cmd = new String[arguments];
        final String prefix = OS_NAME.startsWith("Windows") ? "" : "./";
        cmd[0] = String.format("%s%s", prefix, executable.getFileName());
        for (int i = 0; i < options.size(); i++) {
            cmd[i + 1] = options.get(i);
        }
        for (String o : cmd) {
            System.out.printf(" %s", o);
        }
        return cmd;
    }

}
