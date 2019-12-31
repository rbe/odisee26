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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provide OfficeConnections, provide a pool for them and act as a watchdog.
 */
@Singleton
public class OfficeConnectionFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfficeConnectionFactory.class);

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private String groupname;

    private static final int QUEUE_POLL_TIMEOUT = 5;

    private static final TimeUnit QUEUE_POLL_TIMEUNIT = TimeUnit.SECONDS;

    private List<InetSocketAddress> addresses;

    private LinkedBlockingQueue<OfficeConnection> connections;

    private static final OfficeConnectionFactory OFFICE_CONNECTION_FACTORY = new OfficeConnectionFactory();

    public static OfficeConnectionFactory getInstance(final String groupname, final List<InetSocketAddress> addresses) {
        OFFICE_CONNECTION_FACTORY.groupname = groupname;
        OFFICE_CONNECTION_FACTORY.addresses = addresses;
        OFFICE_CONNECTION_FACTORY.initializeConnections();
        return OFFICE_CONNECTION_FACTORY;
    }

    public static OfficeConnectionFactory getInstance(final String groupname, final String host, final int basePort, final int count) {
        OFFICE_CONNECTION_FACTORY.groupname = groupname;
        OFFICE_CONNECTION_FACTORY.addresses = new ArrayList<>();
        OFFICE_CONNECTION_FACTORY.addConnections(host, basePort, count);
        OFFICE_CONNECTION_FACTORY.initializeConnections();
        return OFFICE_CONNECTION_FACTORY;
    }

    private OfficeConnectionFactory() {
    }

    public void addConnections(final String host, final int basePort, final int count) {
        for (int i = 0; i < count; i++) {
            addresses.add(new InetSocketAddress(host, basePort + i));
        }
    }

    public OfficeConnection fetchConnection(final boolean waitForever) throws OdiseeServerException {
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
        } catch (InterruptedException e) {
            // ignore
            Thread.currentThread().interrupt();
        }
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

    public void repositConnection(final OfficeConnection officeConnection) throws OdiseeServerException {
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
        }
        if (!connectionWasPutBack) {
            throw new OdiseeServerException(String.format("[group=%s] Could not reposit connection, I tried it more than once, sorry.", groupname));
        }
    }

    /**
     * Shutdown all connections and the factory.
     * @param cleanup If false, OfficeConnection objects remain in the pool, otherwise they are removed.
     */
    public void shutdown(final boolean cleanup) {
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
        if (null == addresses || addresses.isEmpty()) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        // Setup queue for connections
        connections = new LinkedBlockingQueue<>(addresses.size());
        // Process all TCP/IP addresses
        for (final InetSocketAddress socketAddress : addresses) {
            final OfficeConnection officeConnection = new OfficeConnection(socketAddress);
            try {
                officeConnection.bootstrap(false);
                final boolean offer = connections.offer(officeConnection);
                if (offer) {
                    LOGGER.info("Added connection {} to queue", officeConnection);
                } else {
                    LOGGER.error("Could not add connection {} to queue", officeConnection);
                }
            } catch (OdiseeServerException e) {
                LOGGER.error("[group=%{}] Could not bootstrap connection to {}: {}",
                        groupname, socketAddress, e.getLocalizedMessage());
            }
        }
    }

}
