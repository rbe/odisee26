/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 02.02.15 18:35
 */
package org.odisee.debug

import groovy.util.logging.Log
import org.odisee.io.OdiseePath

import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import java.text.DecimalFormat

@Log
class Profile {

    private static final DecimalFormat DECIMAL_FORMAT = (DecimalFormat) DecimalFormat.instance
    private static final int FRACTION_DIGITS = 2
    private static final String DATE_FORMAT = 'dd.MM. HH:mm:ss.SSS'
    private static long counter = 0
    private static final BigDecimal BD_HUNDRED = 100.0
    private static final BigDecimal BD_MILLION = 1000000.0
    private static final String S_ZERO = '0.00'

    static {
        // Format decimals
        DECIMAL_FORMAT.decimalSeparatorAlwaysShown = true
        DECIMAL_FORMAT.minimumFractionDigits = FRACTION_DIGITS
        DECIMAL_FORMAT.maximumFractionDigits = FRACTION_DIGITS
        // Profiling enabled?
        if (OdiseePath.ODISEE_DEBUG || OdiseePath.ODISEE_PROFILE) {
            log.debug "Profile.<init>: Profiling is ${OdiseePath.ODISEE_PROFILE ? 'enabled' : 'disabled'}"
        }
    }

    /**
     * Execute a closure and measure time.
     */
    static time = { String text, Closure closure ->
        if (OdiseePath.ODISEE_PROFILE) {
            synchronized (Profile) {
                counter++
            }
            log.debug "${new java.util.Date().format(DATE_FORMAT)} ${Thread.currentThread().name}: BEGIN-${counter} ${text}"
            // Remember start time
            long startWall = System.currentTimeMillis()
            long startCpuTime = Profile.cpuTime
            long startSystemTime = Profile.systemTime
            long startUserTime = Profile.userTime
            // Excecute code
            def result = closure()
            // Get stop time
            long stopWall = System.currentTimeMillis()
            long stopCpuTime = Profile.cpuTime
            long stopSystemTime = Profile.systemTime
            long stopUserTime = Profile.userTime
            // Calculate delta
            // Wall clock
            long deltaWall = stopWall - startWall
            // CPU time
            long deltaCpuTime = stopCpuTime - startCpuTime
            double deltaCpuTimeInMs = deltaCpuTime > 0 ? deltaCpuTime / BD_MILLION : deltaCpuTime
            double deltaCpuTimePct = 100.0
            // System time
            long deltaSystemTime = stopSystemTime - startSystemTime
            double deltaSystemTimeInMs = deltaSystemTime > 0 ? deltaSystemTime / BD_MILLION : deltaSystemTime
            String deltaSystemTimePct = S_ZERO
            if (deltaSystemTime > 0 && deltaCpuTime > 0) {
                deltaSystemTimePct = DECIMAL_FORMAT.format((double) (deltaSystemTime / deltaCpuTime * BD_HUNDRED))
            }
            // User time
            long deltaUserTime = stopUserTime - startUserTime
            double deltaUserTimeInMs = deltaUserTime > 0 ? deltaUserTime / BD_MILLION : deltaUserTime
            String deltaUserTimePct = S_ZERO
            if (deltaUserTime > 0 && deltaCpuTime > 0) {
                deltaUserTimePct = DECIMAL_FORMAT.format((double) (deltaUserTime / deltaCpuTime * BD_HUNDRED))
            }
            // Gap between wall and CPU time
            double wallCpuGap = Math.round(deltaWall - deltaCpuTimeInMs)
            // Log
            String total = String.format("total(%.0${FRACTION_DIGITS}f%%)=%.0${FRACTION_DIGITS}f ms", deltaCpuTimePct, deltaCpuTimeInMs)
            String user = String.format("user (%s%%)=%.0${FRACTION_DIGITS}f ms", deltaUserTimePct, deltaUserTimeInMs)
            String sys = String.format("sys  (%s%%)=%.0${FRACTION_DIGITS}f ms", deltaSystemTimePct, deltaSystemTimeInMs)
            String wall = String.format('wall=%d ms', deltaWall)
            String wallGap = String.format("wallCpuGap=%.0${FRACTION_DIGITS}f ms", wallCpuGap)
            log.debug "${new java.util.Date().format(DATE_FORMAT)} ${Thread.currentThread().name}: END-${counter} ${text}, ${total}, ${user}, ${sys}, ${wall}, ${wallGap}"
            // Return result
            result
        } else {
            closure()
        }
    }

    /**
     * Get CPU time in nanoseconds.
     */
    static long getCpuTime() {
        ThreadMXBean bean = ManagementFactory.threadMXBean
        bean.currentThreadCpuTimeSupported ? bean.currentThreadCpuTime : 0L
    }

    /**
     * Get user time in nanoseconds.
     */
    static long getUserTime() {
        ThreadMXBean bean = ManagementFactory.threadMXBean
        bean.currentThreadCpuTimeSupported ? bean.currentThreadUserTime : 0L
    }

    /**
     * Get system time in nanoseconds.
     */
    static long getSystemTime() {
        ThreadMXBean bean = ManagementFactory.threadMXBean
        bean.currentThreadCpuTimeSupported ? (bean.currentThreadCpuTime - bean.currentThreadUserTime) : 0L
    }

}
