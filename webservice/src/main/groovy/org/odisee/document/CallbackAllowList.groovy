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

import java.net.InetAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.Locale

/**
 * Per-user callback hosts in {@code $ODISEE_HOME/etc/callback-hosts}.
 * One line is {@code username host}. A URL is posted only when its host is listed for that user
 * and is a public external host. Loopback, link-local, RFC 1918, CGNAT, ULA, and internal names
 * are rejected.
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
                String allowed = normalizeAllowedHost(parts[1])
                if (allowed != null) {
                    names.add(allowed)
                }
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
        String normalized = normalizeAllowedHost(safe)
        synchronized (LOCK) {
            Path file = location()
            Files.createDirectories(file.parent)
            if (hosts(file, name).contains(normalized)) {
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
            String scheme = uri.scheme?.toLowerCase(Locale.ROOT)
            if (scheme != 'http' && scheme != 'https') {
                return null
            }
            if (uri.userInfo != null) {
                return null
            }
            if (uri.port != -1 && (uri.port < 1 || uri.port > 65535)) {
                return null
            }
            return normalizeAllowedHost(uri.host)
        } catch (URISyntaxException ignored) {
            return null
        }
    }

    static String requireHost(String host) {
        String value = host == null ? '' : host.trim()
        if (normalizeAllowedHost(value) == null) {
            throw new OdiseeException("Invalid callback host '${host}'", OdiseeException.BAD_REQUEST)
        }
        value
    }

    static String normalizeAllowedHost(String rawHost) {
        if (rawHost == null) {
            return null
        }
        String value = rawHost.trim()
        if (!value || value.length() > 253 || value.contains('%')) {
            return null
        }
        boolean bracketed = value.startsWith('[') && value.endsWith(']')
        String unbracketed = bracketed ? value.substring(1, value.length() - 1) : value
        if (!unbracketed || unbracketed.contains('[') || unbracketed.contains(']')) {
            return null
        }
        String lower = unbracketed.toLowerCase(Locale.ROOT)
        if (lower == 'localhost' || lower.endsWith('.localhost') || lower.endsWith('.local')) {
            return null
        }
        if (lower.contains(':')) {
            if (!(lower ==~ /[0-9a-f:.]+/)) {
                return null
            }
            try {
                InetAddress addr = InetAddress.getByName(lower)
                return isPublicAddress(addr) ? "[${lower}]".toString() : null
            } catch (Exception ignored) {
                return null
            }
        }
        if (bracketed) {
            return null
        }
        def ipv4 = (lower =~ /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/)
        if (ipv4.matches()) {
            byte[] octets = new byte[4]
            for (int i = 1; i <= 4; i++) {
                String part = ipv4.group(i)
                if (part.length() > 1 && part.startsWith('0')) {
                    return null
                }
                int n = Integer.parseInt(part)
                if (n < 0 || n > 255) {
                    return null
                }
                octets[i - 1] = (byte) n
            }
            try {
                InetAddress addr = InetAddress.getByAddress(octets)
                return isPublicAddress(addr) ? lower : null
            } catch (Exception ignored) {
                return null
            }
        }
        if (!lower.contains('.') || lower.startsWith('.') || lower.endsWith('.') || lower.contains('..')) {
            return null
        }
        String[] labels = lower.split('\\.')
        if (labels.length < 2) {
            return null
        }
        String tld = labels[labels.length - 1]
        if (tld ==~ /[0-9].*/) {
            return null
        }
        for (String label : labels) {
            if (!(label ==~ /[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?/)) {
                return null
            }
        }
        lower
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address == null) {
            return false
        }
        byte[] raw = address.address
        if (raw == null) {
            return false
        }
        if (raw.length == 4) {
            return isPublicIpv4(raw)
        }
        if (raw.length == 16) {
            return isPublicIpv6(address, raw)
        }
        false
    }

    private static boolean isPublicIpv4(byte[] raw) {
        int b0 = raw[0] & 0xFF
        int b1 = raw[1] & 0xFF
        int b2 = raw[2] & 0xFF
        if (b0 == 0 || b0 == 10 || b0 == 127 || b0 >= 224) {
            return false
        }
        if (b0 == 100 && (b1 & 0xC0) == 64) {
            return false
        }
        if (b0 == 169 && b1 == 254) {
            return false
        }
        if (b0 == 172 && b1 >= 16 && b1 <= 31) {
            return false
        }
        if (b0 == 192 && b1 == 0 && b2 == 0) {
            return false
        }
        if (b0 == 192 && b1 == 168) {
            return false
        }
        if (b0 == 198 && (b1 == 18 || b1 == 19)) {
            return false
        }
        true
    }

    private static boolean isPublicIpv6(InetAddress address, byte[] raw) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false
        }
        int b0 = raw[0] & 0xFF
        int b1 = raw[1] & 0xFF
        if ((b0 & 0xFE) == 0xFC) {
            return false
        }
        if (b0 == 0x20 && b1 == 0x01 && raw[2] == 0 && raw[3] == 0) {
            return false
        }
        if (b0 == 0x20 && b1 == 0x02) {
            return isPublicIpv4([raw[2], raw[3], raw[4], raw[5]] as byte[])
        }
        boolean zeroPrefix80 = (0..9).every { int idx -> raw[idx] == (byte) 0 }
        if (zeroPrefix80) {
            int b10 = raw[10] & 0xFF
            int b11 = raw[11] & 0xFF
            if ((b10 == 0 && b11 == 0) || (b10 == 0xFF && b11 == 0xFF)) {
                return isPublicIpv4([raw[12], raw[13], raw[14], raw[15]] as byte[])
            }
        }
        if (b0 == 0x00 && b1 == 0x64 && (raw[2] & 0xFF) == 0xFF && (raw[3] & 0xFF) == 0x9B
                && (4..11).every { int idx -> raw[idx] == (byte) 0 }) {
            return isPublicIpv4([raw[12], raw[13], raw[14], raw[15]] as byte[])
        }
        true
    }

}
