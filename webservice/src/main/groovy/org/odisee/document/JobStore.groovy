/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.odisee.api.OdiseeException
import org.odisee.io.SafePaths
import org.odisee.io.TenantPaths

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * The job list is {@code var/user/{name}/jobs.json}. One file per user.
 * It is not process memory and not a shared table.
 */
final class JobStore {

    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>()

    private JobStore() {
    }

    static Path file(String user) {
        TenantPaths.userRoot(user).resolve('jobs.json')
    }

    static Map create(String user, String id, String callback) {
        mutate(user) { List<Map> jobs ->
            jobs << [
                    id               : id,
                    status           : 'queued',
                    failedInstruction: null,
                    file             : null,
                    filename         : null,
                    callback         : callback
            ]
        }
        find(user, id)
    }

    static void update(String user, String id, Map fields) {
        mutate(user) { List<Map> jobs ->
            Map job = jobs.find { it.id == id }
            if (job == null) {
                throw new OdiseeException("Job '${id}' does not exist", OdiseeException.NOT_FOUND)
            }
            fields?.each { key, value -> job[key.toString()] = value }
        }
    }

    static Map find(String user, String id) {
        read(user).find { it.id == id }
    }

    static Map require(String user, String id) {
        Map job = find(user, id)
        if (job == null) {
            throw new OdiseeException("Job '${id}' does not exist", OdiseeException.NOT_FOUND)
        }
        job
    }

    static byte[] fileBytes(String user, Map job) {
        if (job == null || job.file == null) {
            return null
        }
        Path path = SafePaths.resolveInside(TenantPaths.userRoot(user), job.file.toString())
        if (!Files.isRegularFile(path)) {
            return null
        }
        Files.readAllBytes(path)
    }

    static List<Map> read(String user) {
        Path path = file(user)
        if (!Files.isRegularFile(path)) {
            return []
        }
        Object parsed = new JsonSlurper().parse(path.toFile())
        Object jobs = parsed instanceof Map ? ((Map) parsed).jobs : null
        if (!(jobs instanceof List)) {
            return []
        }
        ((List) jobs).collect { Object item -> copy(item) }
    }

    private static void mutate(String user, Closure change) {
        Object lock = LOCKS.computeIfAbsent(user, { new Object() })
        synchronized (lock) {
            List<Map> jobs = read(user)
            change.call(jobs)
            write(user, jobs)
        }
    }

    private static void write(String user, List<Map> jobs) {
        Path path = file(user)
        Files.createDirectories(path.parent)
        String json = JsonOutput.prettyPrint(JsonOutput.toJson([jobs: jobs]))
        Path tmp = path.resolveSibling(path.fileName.toString() + '.tmp')
        Files.writeString(tmp, json + System.lineSeparator(), StandardCharsets.UTF_8)
        Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private static Map copy(Object item) {
        Map copy = new LinkedHashMap()
        if (item instanceof Map) {
            ((Map) item).each { key, value -> copy[key.toString()] = value }
        }
        copy
    }

}
