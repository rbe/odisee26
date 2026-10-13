/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.security

import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.SecurityFilterChain

/**
 * Bearer JWT against {@code $ODISEE_HOME/etc/oidc}. Embedded Tomcat stays.
 * {@code GET /ready} is anonymous. There is no HTTP Basic login.
 */
@Configuration
@EnableWebSecurity
class OdiseeSecurityConfiguration {

    /**
     * Stops Boot from generating a local password. Passwords are not an Odisee login.
     */
    @Bean
    UserDetailsService noPasswordUsers() {
        return { String username ->
            throw new UsernameNotFoundException(username ?: '')
        } as UserDetailsService
    }

    @Bean
    SecurityFilterChain odiseeSecurity(HttpSecurity http) throws Exception {
        http
                .csrf { it.disable() }
                .anonymous { it.disable() }
                .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
                .oauth2ResourceServer { oauth2 ->
                    oauth2.bearerTokenResolver(bearerTokenResolver())
                    oauth2.jwt { jwt ->
                        jwt.decoder(new MachineJwtDecoder())
                        jwt.jwtAuthenticationConverter(new MachineJwtAuthenticationConverter())
                    }
                }
                .authorizeHttpRequests { auth ->
                    auth.requestMatchers(HttpMethod.GET, '/ready', '/ready/**').permitAll()
                    auth.anyRequest().authenticated()
                }
        http.build()
    }

    /**
     * {@code GET /ready} does not read Authorization. A probe stays anonymous.
     */
    static BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver()
        return { HttpServletRequest request ->
            anonymousReady(request) ? null : delegate.resolve(request)
        } as BearerTokenResolver
    }

    static boolean anonymousReady(HttpServletRequest request) {
        if (request == null || !'GET'.equalsIgnoreCase(request.method)) {
            return false
        }
        String path = request.servletPath
        if (!path) {
            path = request.pathInfo
        }
        if (!path) {
            String uri = request.requestURI ?: ''
            String context = request.contextPath ?: ''
            path = context && uri.startsWith(context) ? uri.substring(context.length()) : uri
        }
        path == '/ready' || (path != null && path.startsWith('/ready/'))
    }

}
