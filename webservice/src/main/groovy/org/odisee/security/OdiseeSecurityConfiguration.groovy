/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.SecurityFilterChain

/**
 * HTTP Basic against {@code $ODISEE_HOME/etc/users}. Embedded Tomcat stays.
 * {@code GET /ready} is anonymous. This wave does not add a health endpoint.
 */
@Configuration
@EnableWebSecurity
class OdiseeSecurityConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        new BCryptPasswordEncoder()
    }

    @Bean
    UserDetailsService passwordFileUsers() {
        new PasswordFileUserDetailsService()
    }

    @Bean
    SecurityFilterChain odiseeSecurity(HttpSecurity http) throws Exception {
        http
                .csrf { it.disable() }
                .anonymous { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
                .httpBasic(Customizer.withDefaults())
                .authorizeHttpRequests { auth ->
                    auth.requestMatchers(HttpMethod.GET, '/ready', '/ready/**').permitAll()
                    auth.requestMatchers(HttpMethod.POST, '/user').hasRole('ADMIN')
                    auth.requestMatchers(HttpMethod.POST, '/callback-host').hasRole('ADMIN')
                    auth.anyRequest().authenticated()
                }
        http.build()
    }

}
