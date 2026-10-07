/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.io

import org.odisee.api.OdiseeException
import org.odisee.shared.OdiseeConstant

import java.nio.file.Files
import java.nio.file.Path

/**
 * Per-user files under {@code var/user/{name}/}.
 * {@code template} holds templates. {@code work} holds merge inputs. {@code output} holds generated files.
 */
final class TenantPaths {

    static final String TEMPLATE = 'template'

    static final String WORK = 'work'

    static final String OUTPUT = 'output'

    private TenantPaths() {
    }

    static String requireUserName(String name) {
        SafePaths.requireSimpleName(name, 'user')
    }

    static Path userRoot(String name) {
        String safe = requireUserName(name)
        Path users = OdiseePath.ODISEE_VAR.resolve(OdiseeConstant.S_USER).toAbsolutePath().normalize()
        Path root = users.resolve(safe).normalize()
        if (!root.startsWith(users)) {
            throw new OdiseeException("Invalid user '${name}'", OdiseeException.BAD_REQUEST)
        }
        root
    }

    static Path templateDir(String name) {
        userRoot(name).resolve(TEMPLATE)
    }

    static Path workDir(String name) {
        userRoot(name).resolve(WORK)
    }

    static Path outputDir(String name) {
        userRoot(name).resolve(OUTPUT)
    }

    static void createDirectories(String name) {
        Files.createDirectories(templateDir(name))
        Files.createDirectories(workDir(name))
        Files.createDirectories(outputDir(name))
    }

}
