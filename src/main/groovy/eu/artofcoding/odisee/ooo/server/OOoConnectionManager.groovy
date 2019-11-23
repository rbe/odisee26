/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 02.02.15 18:36
 */
package eu.artofcoding.odisee.ooo.server

import eu.artofcoding.odisee.OdiseeException
import eu.artofcoding.odisee.OdiseePath

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/*
TODO Rework load-balancing:
Instances in-use should not be taken again for approx. 5 seconds
Instances not-connected (due to instance crash) should be unusable for approx. 15 seconds to allow for restart by watchdog
Cache should store a use-count (0 or 1):
- don't reorder connections by remove(0) and put() at end (this occures if an instance has crashed)
- if a connection is taken set a property to 1
- when a connection is released to the pool wait for some time then set counter to 0
*/

/**
 * OpenOffice Connection Manager.
 */
class OOoConnectionManager {

    /**
     * OpenOffice program folder
     */
    private String oooProgram

    /**
     * Options for starting an OpenOffice instance.
     * TODO Differences between OOo/LO
     */
    private String[] oooOptions = [
            '-headless', '-nologo',
            '-nofirststartwizard', '-nodefault',
            '-nocrashreport', '-norestart', '-norestore',
            '-nolockcheck'
    ]

    /**
     * Connections already alreadySetup?
     */
    private boolean alreadySetup

    /**
     * Connection connectionCache: avaible, created but not in use connections.
     * unoURL -> [conn].
     */
    private Map<String, LinkedBlockingQueue<OOoConnection>> connectionCache

    /**
     * In-use pool: which connection(s) is/are in use?
     * unoURL -> [conn]
     */
    private Map<String, LinkedBlockingQueue<OOoConnection>> connectionsInUse

    /**
     * Constructor.
     */
    OOoConnectionManager(map) {
        // Get program path and options from map
        if (map.oooProgram) {
            oooProgram = map.remove('oooProgram')
        }
        if (map.oooOptions) {
            oooOptions = map.remove('oooOptions')
        }
        // Remaining keys in map must be groups
        setup(map)
    }

    /**
     *
     */
    void addOOoConnectionToGroup(group, unoURL) {
        OOoProcess oooProcess = new OOoProcess(oooProgram: oooProgram, oooOptions: oooOptions, unoURL: unoURL)
        if (OdiseePath.ODISEE_DEBUG) println "${this}.addOOoConnectionToGroup(${group}): Adding connection to '${unoURL}'"
        // Put connection into connectionCache
        def conn = new OOoConnection(group: group, oooProcess: oooProcess)
        connectionCache[group] << conn
    }

    /**
     * Setup connections to (multiple?) instance(s) of OpenOffice.
     * @param group Map: name: [host: [port, port, ...]], e.g. group1: [localhost: 2002 .. 2004]
     */
    synchronized void setup(Map group) {
        // Run only once
        if (alreadySetup) {
            throw new OdiseeException('Already alreadySetup, cannot do this twice!')
        }
        if (!group) {
            throw new OdiseeException('No group(s)')
        }
        // Maps for connections
        connectionCache = new ConcurrentHashMap<String, LinkedBlockingQueue<OOoConnection>>(group.size())
        connectionsInUse = new ConcurrentHashMap<String, LinkedBlockingQueue<OOoConnection>>(group.size())
        // Examine group(s)
        group.each { groupName, unoURL ->
            // Ensure list for UNO URLs in connection connectionCache and in-use pool
            if (!connectionCache[groupName]) {
                connectionCache.putIfAbsent(groupName, new LinkedBlockingQueue<OOoConnection>())
            }
            if (!connectionsInUse[groupName]) {
                connectionsInUse.putIfAbsent(groupName, new LinkedBlockingQueue<OOoConnection>())
            }
            if (unoURL instanceof Map) {
                // group: key=name value=[host: [port, port, ...]]
                unoURL.each { host, ports ->
                    ports.each { port ->
                        addOOoConnectionToGroup groupName, [host: host, port: port]
                    }
                }
            } else if (unoURL instanceof List) {
                unoURL.each { pipe ->
                    addOOoConnectionToGroup groupName, [pipe: pipe]
                }
            }
        }
        // Set flag
        alreadySetup = true
    }

    /**
     *
     */
    synchronized void dump(group, prefix) {
        int cacheSize = connectionCache[group].size()
        int inuseSize = connectionsInUse[group].size()
        int unusableCount = connectionCache[group].findAll { it.unusableSince > 0 }.size()
        println "${this}.${prefix}: ${cacheSize} connections available, ${inuseSize} in use = ${cacheSize + inuseSize}, ${unusableCount} temporarily unusable"
    }

    /**
     * Acquire a connection (from the connectionCache).
     * @param group Group to get connection from
     * @return OOoConnection
     */
    OOoConnection acquire(group) {
        // Check state
        if (!alreadySetup) {
            throw new OdiseeException('No groups were alreadySetup')
        }
        if (!group) {
            throw new OdiseeException('No group given')
        }
        if (!connectionCache[group] && !connectionsInUse[group]) {
            throw new OdiseeException("No such group ${group}")
        }
        // A connection we got from the pool
        OOoConnection conn = null
        // Count tries to find a connection; if == group.size() there is no usable connection
        int counter = 1
        // Get size of connectionCache
        def size = connectionCache[group].size()
        if (size == 0) {
            throw new OdiseeException('No connections in this group')
        }
        // Try all available connections
        while (!conn && counter <= size) {
            counter++
            // Get first connection from the list; wait for maximum of 5 seconds
            // Connections are in connectionCache after alreadySetup()
            conn = connectionCache[group].poll(5, TimeUnit.SECONDS)
            if (conn) {
                if (OdiseePath.ODISEE_DEBUG) println "${this}.acquire(${group}): got connection to ${conn.oooProcess}, it is ${conn.unusableSince > 0 ? 'unusable' : 'usable'} (unusableSince=${conn.unusableSince})"
                // Move connection into in-use pool
                connectionsInUse[group] << conn
                if (OdiseePath.ODISEE_DEBUG) dump group, "acquire(${group}): ${conn.oooProcess} <- ${conn.group}"
                try {
                    // Establish connection
                    conn.connect()
                } catch (e) {
                    // Move connection back to connectionCache
                    release(conn)
                    // Rethrow
                    throw e
                }
            }
        }
        conn
    }

    /**
     * Release a connection: put it back to the connectionCache.
     * @param conn Connection to release.
     */
    void release(conn) {
        if (!conn) {
            return
        }
        // Perform connection-specific release operations
        conn.release()
        // Move connection from in-use pool into connection connectionCache
        connectionsInUse[conn.group].remove(conn)
        // Append connection at end of list
        connectionCache[conn.group] << conn
        if (OdiseePath.ODISEE_DEBUG) dump conn.group, "release: ${conn.oooProcess} -> ${conn.group}"
    }

    /**
     * Shutdown all connections, release pool.
     */
    void shutdown(andTerminate = false) {
        def close = { group, v ->
            v.each { conn ->
                //println "closing in-use connections for ${group}: ${conn}"
                conn.close()
                if (andTerminate) {
                    conn.terminate()
                }
            }
        }
        synchronized (connectionCache) {
            // Close in-use connections
            connectionsInUse.each { group, v -> close group, v }
            // Close available connections
            connectionCache.each { group, v -> close group, v }
            // Move in-use connections into connectionCache
            connectionsInUse.each { group, v ->
                v.each { conn ->
                    release(conn)
                }
            }
        }
    }

}
