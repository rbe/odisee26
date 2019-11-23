/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 02.09.14 08:21
 */
package eu.artofcoding.odisee

import eu.artofcoding.odisee.server.OdiseeConstant

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Odisee's paths, built from environment variables.
 */
@Singleton
public class OdiseePath {

    /**
     * Profile?
     */
    public static final Boolean ODISEE_PROFILE = Boolean.valueOf(System.getenv(OdiseeConstant.S_ODISEE_PROFILE) ?: OdiseeConstant.S_FALSE)

    /**
     * Debug?
     */
    public static final Boolean ODISEE_DEBUG = Boolean.valueOf(System.getenv(OdiseeConstant.S_ODISEE_DEBUG) ?: OdiseeConstant.S_FALSE)

    /**
     * Home: installation path of OpenOffice.
     */
    public static final String OOO_HOME = System.getenv('OOO_HOME')

    /**
     * Java Home.
     */
    public static final Path JAVA_HOME = Path.of(System.getProperty('java.home'))

    /**
     * Base directory for Odisee.
     */
    public static final Path ODISEE_HOME

    /**
     * Variable data directory for Odisee.
     */
    public static final Path ODISEE_VAR
    public static final Path ODISEE_USER
    public static final Path ODISEE_TMP

    /**
     * Deployment directory for Odisee, contains additional files.
     */
    public static final Path ODISEE_DEPLOY

    public static final String S_ETC_ODIINST = "etc/odiinst";

    static {
        // ODISEE_HOME
        // Is Odisee home set?
        String envOdiseeHome = System.getenv(OdiseeConstant.S_ODISEE_HOME)
        if (!envOdiseeHome) {
            ODISEE_HOME = Path.of(System.getProperty(OdiseeConstant.S_ODISEE_HOME))
            if (!ODISEE_HOME) ODISEE_HOME = Paths.get(OdiseeConstant.S_DOT).toAbsolutePath()
            println "Consider setting ODISEE_HOME, using directory ${ODISEE_HOME}"
        } else {
            ODISEE_HOME = Paths.get(envOdiseeHome).toAbsolutePath()
        }
        // ODISEE_DEPLOY
        String envOdiseeDeploy = System.getenv(OdiseeConstant.S_ODISEE_DEPLOY)
        if (envOdiseeDeploy) {
            ODISEE_DEPLOY = Paths.get(envOdiseeDeploy).toAbsolutePath()
        } else {
            ODISEE_DEPLOY = ODISEE_HOME.resolve(OdiseeConstant.S_VAR_DEPLOY).toAbsolutePath()
        }
        Files.createDirectories(ODISEE_DEPLOY)
        // ODISEE_VAR
        String envOdiseeVar = System.getenv(OdiseeConstant.S_ODISEE_VAR)
        if (envOdiseeVar) {
            ODISEE_VAR = Paths.get(envOdiseeVar).toAbsolutePath()
        } else {
            ODISEE_VAR = ODISEE_HOME.resolve(OdiseeConstant.S_VAR).toAbsolutePath()
        }
        Files.createDirectories(ODISEE_VAR)
        // ODISEE_USER -> var
        String envOdiseeUser = System.getenv(OdiseeConstant.S_ODISEE_VAR)
        if (envOdiseeUser) {
            ODISEE_USER = Paths.get(envOdiseeUser).toAbsolutePath()
        } else {
            ODISEE_USER = ODISEE_VAR.toAbsolutePath()
        }
        // ODISEE_TMP
        String envOdiseeTmp = System.getenv(OdiseeConstant.S_ODISEE_TMP)
        if (envOdiseeTmp) {
            ODISEE_TMP = Paths.get(envOdiseeTmp).toAbsolutePath()
        } else {
            ODISEE_TMP = ODISEE_HOME.resolve(OdiseeConstant.S_VAR_TMP).toAbsolutePath()
        }
        Files.createDirectories(ODISEE_TMP)
    }

    static void dumpEnv() {
        println 'Odisee environment variables:'
        println "  JAVA_HOME:     ${JAVA_HOME}"
        println "  Java Version:  ${System.getProperty('java.version')}"
        println "  ODISEE_HOME:   ${ODISEE_HOME}"
        println "  ODISEE_VAR:    ${ODISEE_VAR}"
        if (ODISEE_DEBUG) {
            println "  ODISEE_DEBUG  =${ODISEE_DEBUG}"
        }
        if (ODISEE_PROFILE) {
            println "  ODISEE_PROFILE=${ODISEE_PROFILE}"
        }
    }

}
