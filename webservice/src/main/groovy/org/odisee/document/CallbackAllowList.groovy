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
import org.odisee.io.OdiseePath
import org.odisee.io.SafePaths

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Per-user callback hosts in {@code $ODISEE_HOME/etc/callback-hosts}.
 * One line is {@code username host}. A URL is posted only when its host is listed for that user.
 * A host listed for one user does not allow another user's callback.
 */
final class CallbackAllowList {

    private static final Object LOCK = new Object()

    private CallbackAllowList() {
    }

    static Path location() {
        OdiseePath.ODISEE_HOME.resolve('etc').resolve('callback-hosts')
    }

    static Set<String> hosts(Path file, String user) {
        Set<String> names = new LinkedHashSet<>()
        if (file == null || !Files.exists(file) || user == null) {
            return names
        }
        String name
        try {
            name = SafePaths.requireSimpleName(user, 'user')
        } catch (OdiseeException ignored) {
            return names
        }
        Files.readAllLines(file, StandardCharsets.UTF_8).each { String line ->
            String trimmed = line.trim()
            if (!trimmed || trimmed.startsWith('#')) {
                return
            }
            String[] parts = trimmed.split(/\s+/)
            if (parts.length == 2 && parts[0] == name) {
                names.add(parts[1].toLowerCase())
            }
        }
        names
    }

    /**
     * @return true when this user did not already list the host
     */
    static boolean add(String user, String host) {
        String name = SafePaths.requireSimpleName(user, 'user')
        String safe = requireHost(host)
        synchronized (LOCK) {
            Path file = location()
            Files.createDirectories(file.parent)
            if (hosts(file, name).contains(safe.toLowerCase())) {
                return false
            }
            boolean empty = !Files.exists(file) || Files.size(file) == 0
            String prefix = empty
                    ? "# Odisee callback hosts. One line: username host${System.lineSeparator()}# A host listed for one user does not allow another user's callback.${System.lineSeparator()}"
                    : ''
            Files.writeString(file, prefix + name + ' ' + safe + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            return true
        }
    }

    static boolean permits(String user, String url) {
        permits(url, hosts(location(), user))
    }

    static boolean permits(String url, Set<String> allowed) {
        String host = hostOf(url)
        host != null && allowed != null && allowed.contains(host)
    }

    static String hostOf(String url) {
        if (url == null || url.trim().isEmpty()) {
            return null
        }
        try {
            URI uri = new URI(url.trim())
            String scheme = uri.scheme?.toLowerCase()
            if (scheme != 'http' && scheme != 'https') {
                return null
            }
            String host = uri.host
            host ? host.toLowerCase() : null
        } catch (URISyntaxException ignored) {
            null
        }
    }

    static String requireHost(String host) {
        String value = host == null ? '' : host.trim()
        if (!(value ==~ /[A-Za-z0-9]([A-Za-z0-9.-]*[A-Za-z0-9])?/)) {
            throw new OdiseeException("Invalid callback host '${host}'", OdiseeException.BAD_REQUEST)
        }
        value
    }

}
