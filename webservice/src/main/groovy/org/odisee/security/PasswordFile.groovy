/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.security

import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.odisee.io.SafePaths
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * {@code $ODISEE_HOME/etc/users}. One line is {@code username bcrypt-hash} or {@code username bcrypt-hash admin}.
 * {@code java -jar} reads this file. It does not read {@code odisee-users.xml}.
 */
final class PasswordFile {

    static final String BOOTSTRAP_USER = 'ODISEE_BOOTSTRAP_USER'

    static final String BOOTSTRAP_PASSWORD = 'ODISEE_BOOTSTRAP_PASSWORD'

    private static final Object LOCK = new Object()

    private PasswordFile() {
    }

    static final class Record {
        final String name
        final String hash
        final boolean admin

        Record(String name, String hash, boolean admin) {
            this.name = name
            this.hash = hash
            this.admin = admin
        }
    }

    static Path location() {
        OdiseePath.ODISEE_HOME.resolve('etc').resolve('users')
    }

    static List<Record> read(Path file) {
        if (file == null || !Files.exists(file)) {
            return []
        }
        List<Record> records = []
        Files.readAllLines(file, StandardCharsets.UTF_8).each { String line ->
            String trimmed = line.trim()
            if (!trimmed || trimmed.startsWith('#')) {
                return
            }
            String[] parts = trimmed.split(/\s+/)
            if (parts.length < 2) {
                return
            }
            boolean admin = parts.length > 2 && parts[2..-1].any { it == 'admin' }
            records << new Record(parts[0], parts[1], admin)
        }
        records
    }

    static Record find(Path file, String name) {
        if (name == null) {
            return null
        }
        read(file).find { it.name == name }
    }

    static void create(Path file, String name, String password, boolean admin, PasswordEncoder encoder) {
        String safe = SafePaths.requireSimpleName(name, 'user')
        if (password == null || password.trim().isEmpty()) {
            throw new OdiseeException('Password is required', OdiseeException.BAD_REQUEST)
        }
        if (encoder == null) {
            throw new OdiseeException('Password encoder is not configured', OdiseeException.SERVER_ERROR)
        }
        synchronized (LOCK) {
            if (find(file, safe) != null) {
                throw new OdiseeException("User '${safe}' already exists", OdiseeException.CONFLICT)
            }
            String hash = encoder.encode(password)
            List<String> lines = []
            if (file != null && Files.exists(file)) {
                lines.addAll(Files.readAllLines(file, StandardCharsets.UTF_8))
            } else {
                lines << '# Odisee users. One line: username bcrypt-hash [admin]'
                lines << '# The server reads this file. It does not read odisee-users.xml.'
            }
            lines << (admin ? "${safe} ${hash} admin" : "${safe} ${hash}")
            write(file, lines)
        }
    }

    /**
     * Create the first admin only when the file is missing and both values are present.
     * Otherwise create no users.
     */
    static void bootstrap(Path file, String username, String password, PasswordEncoder encoder) {
        if (file != null && Files.exists(file)) {
            return
        }
        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            return
        }
        create(file, username.trim(), password, true, encoder)
    }

    static void bootstrapFromEnvironment() {
        bootstrap(location(), System.getenv(BOOTSTRAP_USER), System.getenv(BOOTSTRAP_PASSWORD), new BCryptPasswordEncoder())
    }

    private static void write(Path file, List<String> lines) {
        if (file == null) {
            throw new OdiseeException('Password file is not configured', OdiseeException.SERVER_ERROR)
        }
        Files.createDirectories(file.parent)
        Path tmp = Files.createTempFile(file.parent, 'users', '.tmp')
        try {
            Files.write(tmp, lines, StandardCharsets.UTF_8)
            ownerOnly(tmp)
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            ownerOnly(file)
        } catch (Throwable e) {
            Files.deleteIfExists(tmp)
            throw e
        }
    }

    private static void ownerOnly(Path file) {
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString('rw-------')
            Files.setPosixFilePermissions(file, perms)
        } catch (UnsupportedOperationException ignored) {
        }
    }

}
