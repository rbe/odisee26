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
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions

/**
 * {@code $ODISEE_HOME/etc/buckets}. One line per user:
 * {@code username endpoint region bucket accessKey secret}.
 * The file is read on each store. A restart is not required.
 * The secret is the rest of the line, so it may contain spaces.
 */
final class BucketFile {

    private static final Object LOCK = new Object()

    private BucketFile() {
    }

    static final class Record {
        final String user
        final String endpoint
        final String region
        final String bucket
        final String accessKey
        final String secret

        Record(String user, String endpoint, String region, String bucket, String accessKey, String secret) {
            this.user = user
            this.endpoint = endpoint
            this.region = region
            this.bucket = bucket
            this.accessKey = accessKey
            this.secret = secret
        }
    }

    static Path location() {
        OdiseePath.ODISEE_HOME.resolve('etc').resolve('buckets')
    }

    /**
     * Reads the file every time. There is no cached copy.
     */
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
            records << parseLine(trimmed)
        }
        records
    }

    static Record find(String user) {
        String name = SafePaths.requireSimpleName(user, 'user')
        read(location()).find { it.user == name }
    }

    static Record require(String user) {
        Record record = find(user)
        if (record == null) {
            throw new OdiseeException("No bucket is configured for user '${user}'", OdiseeException.UNPROCESSABLE)
        }
        record
    }

    /**
     * @return true when the user had no bucket yet
     */
    static boolean save(String user, String endpoint, String region, String bucket, String accessKey, String secret) {
        Record record = record(user, endpoint, region, bucket, accessKey, secret)
        synchronized (LOCK) {
            Path file = location()
            List<String> lines = []
            boolean replaced = false
            if (file != null && Files.exists(file)) {
                Files.readAllLines(file, StandardCharsets.UTF_8).each { String line ->
                    String trimmed = line.trim()
                    if (!trimmed || trimmed.startsWith('#')) {
                        lines << line
                        return
                    }
                    Record existing = parseLine(trimmed)
                    if (existing.user == record.user) {
                        if (!replaced) {
                            lines << format(record)
                            replaced = true
                        }
                        return
                    }
                    lines << line
                }
            } else {
                lines << '# Odisee object storage. One line: username endpoint region bucket accessKey secret'
                lines << '# The server reads this file on each store. A restart is not required.'
            }
            if (!replaced) {
                lines << format(record)
            }
            write(file, lines)
            return !replaced
        }
    }

    static Record parseLine(String line) {
        String[] parts = (line == null ? '' : line.trim()).split(/\s+/, 6)
        if (parts.length < 6) {
            throw new OdiseeException('Bucket configuration requires user, endpoint, region, bucket, accessKey, and secret', OdiseeException.BAD_REQUEST)
        }
        record(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5])
    }

    static Record record(String user, String endpoint, String region, String bucket, String accessKey, String secret) {
        String name = SafePaths.requireSimpleName(user, 'user')
        String url = requireEndpoint(endpoint)
        String regionName = requireToken(region, 'region', /[a-z0-9-]+/)
        String bucketName = requireBucket(bucket)
        String keyId = requireToken(accessKey, 'accessKey', /[A-Za-z0-9._-]{1,128}/)
        String secretValue = secret == null ? '' : secret.trim()
        if (!secretValue || secretValue.contains('\n') || secretValue.contains('\r')) {
            throw new OdiseeException('A secret is required', OdiseeException.BAD_REQUEST)
        }
        new Record(name, url, regionName, bucketName, keyId, secretValue)
    }

    static String format(Record record) {
        [record.user, record.endpoint, record.region, record.bucket, record.accessKey, record.secret].join(' ')
    }

    private static String requireEndpoint(String endpoint) {
        String value = endpoint == null ? '' : endpoint.trim()
        URI uri
        try {
            uri = new URI(value)
        } catch (URISyntaxException ignored) {
            throw new OdiseeException("Invalid endpoint '${endpoint}'", OdiseeException.BAD_REQUEST)
        }
        String scheme = uri.scheme?.toLowerCase()
        if ((scheme != 'http' && scheme != 'https') || !uri.host || uri.userInfo || uri.rawQuery || uri.fragment) {
            throw new OdiseeException("Invalid endpoint '${endpoint}'", OdiseeException.BAD_REQUEST)
        }
        value
    }

    private static String requireBucket(String bucket) {
        String value = requireToken(bucket, 'bucket', /[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]/)
        if (value.contains('..')) {
            throw new OdiseeException("Invalid bucket '${bucket}'", OdiseeException.BAD_REQUEST)
        }
        value
    }

    private static String requireToken(String value, String label, String pattern) {
        String token = value == null ? '' : value.trim()
        if (!(token ==~ pattern)) {
            throw new OdiseeException("Invalid ${label} '${value}'", OdiseeException.BAD_REQUEST)
        }
        token
    }

    private static void write(Path file, List<String> lines) {
        if (file == null) {
            throw new OdiseeException('Bucket file is not configured', OdiseeException.SERVER_ERROR)
        }
        Files.createDirectories(file.parent)
        Path tmp = Files.createTempFile(file.parent, 'buckets', '.tmp')
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
