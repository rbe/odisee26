/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.security;

import org.springframework.security.crypto.bcrypt.BCrypt;

/**
 * Prints one bcrypt hash. {@code odictl user} calls this. It is not a second CLI.
 */
public final class BcryptCli {

    private BcryptCli() {
    }

    public static void main(String[] args) {
        if (args.length != 1 || args[0] == null || args[0].trim().isEmpty()) {
            System.err.println("Password is required");
            System.exit(2);
        }
        System.out.println(BCrypt.hashpw(args[0], BCrypt.gensalt("$2a", 10)));
    }

}
