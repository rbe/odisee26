/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 31.08.14, 14:50
 */

package org.odisee.ooo.connection;

import groovy.lang.Singleton;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provide OfficeConnections, provide a pool for them and act as a watchdog.
 */
@Singleton
public class OfficeConnectionFactory {

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private String groupname;

    private final int QUEUE_POLL_TIMEOUT = 5;

    private final TimeUnit QUEUE_POLL_TIMEUNIT = TimeUnit.SECONDS;

    private List<InetSocketAddress> addresses;

    private LinkedBlockingQueue<OfficeConnection> connections;

    private static final OfficeConnectionFactory OFFICE_CONNECTION_FACTORY = new OfficeConnectionFactory();

    public static OfficeConnectionFactory getInstance(String groupname, List<InetSocketAddress> addresses) {
        OFFICE_CONNECTION_FACTORY.groupname = groupname;
        OFFICE_CONNECTION_FACTORY.addresses = addresses;
        OFFICE_CONNECTION_FACTORY.initializeConnections();
        return OFFICE_CONNECTION_FACTORY;
    }

    public static OfficeConnectionFactory getInstance(String groupname, String host, int basePort, int count) {
        OFFICE_CONNECTION_FACTORY.groupname = groupname;
        OFFICE_CONNECTION_FACTORY.addresses = new ArrayList<InetSocketAddress>();
        OFFICE_CONNECTION_FACTORY.addConnections(host, basePort, count);
        OFFICE_CONNECTION_FACTORY.initializeConnections();
        return OFFICE_CONNECTION_FACTORY;
    }

    private OfficeConnectionFactory() {
    }

    public void addConnections(String host, int basePort, int count) {
        for (int i = 0; i < count; i++) {
            addresses.add(new InetSocketAddress(host, basePort + i));
        }
    }

    public OfficeConnection fetchConnection(boolean waitForever) throws OdiseeServerException {
        // Check state
        if (shuttingDown.get()) {
            throw new OdiseeServerException("Shutdown in progress");
        }
        OfficeConnection officeConnection = null;
        // Poll a connection from queue, waiting some seconds if necessary
        try {
            if (!waitForever) {
                officeConnection = connections.poll(QUEUE_POLL_TIMEOUT, QUEUE_POLL_TIMEUNIT);
            } else {
                officeConnection = connections.take();
            }
            //dumpNextConnection("fetch", officeConnection);
        } catch (InterruptedException e) {
            // ignore
            Thread.currentThread().interrupt();
        }
        /*
        // TODO No connection polled and queue has capacity, create a new one
        if (null == officeConnection && connections.remainingCapacity() > 0) {
            officeConnection = new OfficeConnection();
            officeConnection.connect(unoURL);
        }
        */
        // Check if we could get an OfficeConnection
        if (null == officeConnection) {
            throw new OdiseeServerException(String.format("[group=%s] Could not fetch connection from pool, sorry.", groupname));
        }
        try {
            officeConnection.connect();
            if (!officeConnection.isConnected()) {
                // Put connection back into pool, better luck next time
                repositConnection(officeConnection);
                // Do not return a connection
                officeConnection = null;
            }
        } catch (OdiseeServerException e) {
            officeConnection.setFaulted(true);
            // Put connection back into pool, better luck next time
            repositConnection(officeConnection);
            // Do not return a connection
            officeConnection = null;
        }
        // Return connection
        return officeConnection;
    }

    public void repositConnection(OfficeConnection officeConnection) throws OdiseeServerException {
        if (null == officeConnection) {
            return;
        }
        // Check state
        if (shuttingDown.get()) {
            throw new OdiseeServerException("Shutdown in progress");
        }
        boolean connectionWasPutBack = false;
        int i = 0;
        while (i++ < 3 && !connectionWasPutBack) {
            connectionWasPutBack = connections.offer(officeConnection);
            //dumpNextConnection("reposit", officeConnection);
        }
        if (!connectionWasPutBack) {
            throw new OdiseeServerException(String.format("[group=%s] Could not reposit connection, I tried it more than once, sorry.", groupname));
        }
    }

    /**
     * Shutdown all connections and the factory.
     * @param cleanup If false, OfficeConnection objects remain in the pool, otherwise they are removed.
     */
    public void shutdown(boolean cleanup) {
        shuttingDown.getAndSet(true);
        Iterator<OfficeConnection> iter = connections.iterator();
        while (iter.hasNext()) {
            try {
                iter.next().close();
                if (cleanup) {
                    iter.remove();
                }
            } catch (OdiseeServerException e) {
                // ignore
            }
        }
    }

    private synchronized void initializeConnections() {
        // Check state
        if (null == addresses || addresses.size() == 0) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        // Setup queue for connections
        connections = new LinkedBlockingQueue<OfficeConnection>(addresses.size());
        // Process all TCP/IP addresses
        for (InetSocketAddress socketAddress : addresses) {
            OfficeConnection officeConnection = new OfficeConnection(socketAddress);
            try {
                officeConnection.bootstrap(false);
                connections.offer(officeConnection);
            } catch (OdiseeServerException e) {
                // ignore
                System.err.printf("[group=%s] Could not bootstrap connection to %s: %s%n", groupname, socketAddress, e.getLocalizedMessage());
            }
        }
    }

    private void dumpNextConnection(String action, OfficeConnection officeConnection) {
        try {
            System.out.printf("%s [group=%s] %s: %s, next is %s, %d available%n", Thread.currentThread().getName(), groupname, action, officeConnection, connections.element(), connections.size());
        } catch (NoSuchElementException e) {
            // ignore
            System.out.printf("%s [group=%s] %s: %s, no more connections available%n", Thread.currentThread().getName(), groupname, action, officeConnection);
        }
    }

}
