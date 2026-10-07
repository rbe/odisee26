/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */

package org.odisee.ooo.connection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Ends a local {@code soffice} so {@code odiwatchdog} sees the port close and starts it again.
 * The watchdog only restarts an instance after the process has left.
 */
public final class SofficeWatchdog {

    private static final Logger LOGGER = LoggerFactory.getLogger(SofficeWatchdog.class);

    private SofficeWatchdog() {
    }

    /**
     * @return true when a local soffice command line for this port was signaled
     */
    public static boolean terminate(final int port) {
        boolean signaled = false;
        for (ProcessHandle handle : ProcessHandle.allProcesses().toList()) {
            String command = handle.info().commandLine().orElse("");
            if (!listensOnPort(command, port)) {
                continue;
            }
            LOGGER.info("Stopping soffice pid {} on port {} so odiwatchdog can restart it", handle.pid(), port);
            if (handle.destroyForcibly()) {
                signaled = true;
            }
        }
        if (!signaled) {
            LOGGER.warn("No local soffice process for port {}; the slot stays out of the pool until that office accepts a connection", port);
        }
        return signaled;
    }

    static boolean listensOnPort(final String commandLine, final int port) {
        if (commandLine == null || commandLine.isEmpty() || port < 0) {
            return false;
        }
        String lower = commandLine.toLowerCase(Locale.ROOT);
        if (!lower.contains("soffice")) {
            return false;
        }
        return Pattern.compile("port=" + port + "([^0-9]|$)").matcher(lower).find();
    }

}
