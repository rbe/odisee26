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

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * One server allow-list of callback hosts. An admin maintains
 * {@code $ODISEE_HOME/etc/callback-hosts}. A URL is posted only when its host is on that list.
 */
final class CallbackAllowList {

    private static final Object LOCK = new Object()

    private CallbackAllowList() {
    }

    static Path location() {
        OdiseePath.ODISEE_HOME.resolve('etc').resolve('callback-hosts')
    }

    static Set<String> hosts(Path file) {
        Set<String> names = new LinkedHashSet<>()
        if (file == null || !Files.exists(file)) {
            return names
        }
        Files.readAllLines(file, StandardCharsets.UTF_8).each { String line ->
            String trimmed = line.trim()
            if (!trimmed || trimmed.startsWith('#')) {
                return
            }
            names.add(trimmed.toLowerCase())
        }
        names
    }

    /**
     * @return true when the host was not already on the list
     */
    static boolean add(String host) {
        String safe = requireHost(host)
        synchronized (LOCK) {
            Path file = location()
            Files.createDirectories(file.parent)
            Set<String> current = hosts(file)
            if (current.contains(safe.toLowerCase())) {
                return false
            }
            String prefix = Files.exists(file) && Files.size(file) > 0 ? System.lineSeparator() : ''
            Files.writeString(file, prefix + safe + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            return true
        }
    }

    static boolean permits(String url) {
        permits(url, hosts(location()))
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
