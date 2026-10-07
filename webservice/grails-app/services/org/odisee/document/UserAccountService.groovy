/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import org.odisee.io.TenantPaths
import org.odisee.security.PasswordFile
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * Adds a user to the password file and creates {@code template}, {@code work}, and {@code output}.
 * The new user is not an admin. There is no default password.
 */
class UserAccountService {

    static scope = 'singleton'

    PasswordEncoder passwordEncoder

    String create(String name, String password) {
        PasswordFile.create(PasswordFile.location(), name, password, false, passwordEncoder)
        String safe = TenantPaths.requireUserName(name)
        TenantPaths.createDirectories(safe)
        safe
    }

}
