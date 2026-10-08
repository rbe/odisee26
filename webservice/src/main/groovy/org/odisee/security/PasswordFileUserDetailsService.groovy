/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.security

import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException

/**
 * Loads bcrypt hashes from {@link PasswordFile}. Role {@code admin} is {@code ROLE_ADMIN}.
 * Other users have no role. There is no macro role.
 */
class PasswordFileUserDetailsService implements UserDetailsService {

    @Override
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        PasswordFile.Record record = PasswordFile.find(PasswordFile.location(), username)
        if (record == null) {
            throw new UsernameNotFoundException(username ?: '')
        }
        Collection authorities = record.admin ? [new SimpleGrantedAuthority('ROLE_ADMIN')] : []
        new User(record.name, record.hash, authorities)
    }

}
