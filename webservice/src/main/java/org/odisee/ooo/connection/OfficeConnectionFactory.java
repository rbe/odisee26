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
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

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

    private List<String> addressGroups;

    private final Map<OfficeConnection, String> connectionGroup = new ConcurrentHashMap<>();

    private LinkedBlockingQueue<OfficeConnection> connections;

    private final Set<OfficeConnection> checkedOut = ConcurrentHashMap.newKeySet();

    private final Set<OfficeConnection> dropped = new CopyOnWriteArraySet<>();

    private final AtomicLong generationMillis = new AtomicLong();

    private final LongAdder instructionFailures = new LongAdder();

    private final LongAdder sofficeRestarts = new LongAdder();

    private static final OfficeConnectionFactory OFFICE_CONNECTION_FACTORY = new OfficeConnectionFactory();

    public static OfficeConnectionFactory getInstance(final String groupname, final List<InetSocketAddress> addresses) {
        Map<String, List<InetSocketAddress>> grouped = new LinkedHashMap<>();
        grouped.put(groupname, addresses);
        return load(grouped);
    }

    /**
     * Replace the singleton pool from {@code etc/odiinst} without starting a new JVM.
     * Each key is a v2 {@code <group name="..."/>} value. Missing groups are not dialed.
     */
    public static OfficeConnectionFactory load(final Map<String, List<InetSocketAddress>> byGroup) {
        OFFICE_CONNECTION_FACTORY.applyGroups(byGroup);
        return OFFICE_CONNECTION_FACTORY;
    }

    public static OfficeConnectionFactory getInstance(final String groupname, final String host, final int basePort, final int count) {
        OFFICE_CONNECTION_FACTORY.groupname = groupname;
        OFFICE_CONNECTION_FACTORY.addresses = new ArrayList<>();
        OFFICE_CONNECTION_FACTORY.addressGroups = null;
        OFFICE_CONNECTION_FACTORY.addConnections(host, basePort, count);
        OFFICE_CONNECTION_FACTORY.initializeConnections();
        return OFFICE_CONNECTION_FACTORY;
    }

    private OfficeConnectionFactory() {
    }

    /**
     * A pool that does not dial LibreOffice. Tests supply the connections.
     */
    public static OfficeConnectionFactory forTest(final List<OfficeConnection> ready) {
        OfficeConnectionFactory factory = new OfficeConnectionFactory();
        factory.groupname = "test";
        factory.addresses = new ArrayList<>();
        factory.connections = new LinkedBlockingQueue<>(Math.max(1, ready.size()));
        factory.shuttingDown.set(false);
        for (OfficeConnection connection : ready) {
            factory.addresses.add(new InetSocketAddress("127.0.0.1", 9));
            if (!factory.connections.offer(connection)) {
                throw new OdiseeServerRuntimeException("test pool rejected a connection");
            }
        }
        return factory;
    }

    int waiting() {
        return connections == null ? 0 : connections.size();
    }

    public void addConnections(final String host, final int basePort, final int count) {
        for (int i = 0; i < count; i++) {
            addresses.add(new InetSocketAddress(host, basePort + i));
        }
    }

    /**
     * Take a slot from the named group. A test pool with no groups uses every slot.
     */
    public OfficeConnection fetchConnection(final String group, final boolean waitForever) throws OdiseeServerException {
        if (connectionGroup.isEmpty()) {
            return fetchConnection(waitForever);
        }
        if (shuttingDown.get()) {
            throw new OdiseeServerException("Shutdown in progress");
        }
        recoverDropped();
        String wanted = group == null || group.isBlank() ? "group0" : group;
        int poolSize = addresses == null ? 1 : Math.max(1, addresses.size());
        Set<OfficeConnection> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        OdiseeServerException lastFailure = null;
        boolean sawGroup = false;
        for (int attempt = 0; attempt < poolSize; attempt++) {
            OfficeConnection officeConnection = pollConnection(waitForever && attempt == 0);
            if (officeConnection == null) {
                break;
            }
            if (!seen.add(officeConnection)) {
                offerQuietly(officeConnection);
                break;
            }
            if (!wanted.equals(connectionGroup.get(officeConnection))) {
                offerQuietly(officeConnection);
                continue;
            }
            sawGroup = true;
            try {
                officeConnection.connect();
                if (officeConnection.isConnected()) {
                    checkedOut.add(officeConnection);
                    return officeConnection;
                }
                repositConnection(officeConnection);
            } catch (OdiseeServerException e) {
                lastFailure = e;
                officeConnection.setFaulted(true);
                repositConnection(officeConnection);
            } catch (RuntimeException e) {
                lastFailure = new OdiseeServerException("Office connection failed", e);
                officeConnection.setFaulted(true);
                repositConnection(officeConnection);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        if (!sawGroup && !groupConfigured(wanted)) {
            throw new OdiseeServerException(String.format("[group=%s] No office instances configured", wanted));
        }
        throw new OdiseeServerException(String.format("[group=%s] Could not fetch connection from pool, sorry.", wanted));
    }

    private boolean groupConfigured(final String group) {
        if (addressGroups == null) {
            return false;
        }
        for (String name : addressGroups) {
            if (group.equals(name)) {
                return true;
            }
        }
        return false;
    }

    public OfficeConnection fetchConnection(final boolean waitForever) throws OdiseeServerException {
        if (shuttingDown.get()) {
            throw new OdiseeServerException("Shutdown in progress");
        }
        recoverDropped();
        int poolSize = addresses == null ? 1 : Math.max(1, addresses.size());
        OdiseeServerException lastFailure = null;
        for (int attempt = 0; attempt < poolSize; attempt++) {
            OfficeConnection officeConnection = pollConnection(waitForever && attempt == 0);
            if (officeConnection == null) {
                break;
            }
            try {
                officeConnection.connect();
                if (officeConnection.isConnected()) {
                    checkedOut.add(officeConnection);
                    return officeConnection;
                }
                repositConnection(officeConnection);
            } catch (OdiseeServerException e) {
                lastFailure = e;
                officeConnection.setFaulted(true);
                repositConnection(officeConnection);
            } catch (RuntimeException e) {
                lastFailure = new OdiseeServerException("Office connection failed", e);
                officeConnection.setFaulted(true);
                repositConnection(officeConnection);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new OdiseeServerException(String.format("[group=%s] Could not fetch connection from pool, sorry.", groupname));
    }

    private OfficeConnection pollConnection(final boolean waitForever) throws OdiseeServerException {
        try {
            if (!waitForever) {
                return connections.poll(QUEUE_POLL_TIMEOUT, QUEUE_POLL_TIMEUNIT);
            }
            return connections.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OdiseeServerException("Interrupted while waiting for an office connection", e);
        }
    }

    public void repositConnection(final OfficeConnection officeConnection) throws OdiseeServerException {
        if (null == officeConnection) {
            return;
        }
        checkedOut.remove(officeConnection);
        if (officeConnection.wasDropped()) {
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
     * A UNO deadline fired. Close is the caller's job. This slot does not return to the queue.
     * The local soffice is stopped so {@code odiwatchdog} restarts it.
     */
    public void dropSlot(final OfficeConnection officeConnection) {
        if (officeConnection == null || !dropped.add(officeConnection)) {
            return;
        }
        checkedOut.remove(officeConnection);
        officeConnection.markDropped();
        boolean signaled = false;
        if (officeConnection.isLocal()) {
            try {
                signaled = officeConnection.releaseForWatchdog();
            } catch (RuntimeException e) {
                LOGGER.error("Could not release {} for an soffice restart", officeConnection, e);
            }
        } else {
            LOGGER.info("Dropped remote host {} after a failed health check", officeConnection);
        }
        if (signaled) {
            sofficeRestarts.increment();
            LOGGER.info("Dropped {} after a UNO deadline; odiwatchdog restarts soffice", officeConnection);
        }
    }

    /**
     * Try to put dropped slots back once their office accepts a connection again.
     * @return how many slots rejoined the queue
     */
    public int recoverDropped() {
        if (connections == null || dropped.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (OfficeConnection connection : dropped) {
            try {
                UnoCall.within("recover", UnoCall.recoverDeadlineMillis(), () -> {
                    connection.connect();
                    return null;
                });
            } catch (OdiseeServerException | RuntimeException e) {
                continue;
            }
            if (!connection.isConnected()) {
                continue;
            }
            connection.clearDropped();
            if (connections.offer(connection)) {
                dropped.remove(connection);
                recovered++;
            } else {
                connection.markDropped();
            }
        }
        return recovered;
    }

    /**
     * True when at least one office port accepts a UNO connection.
     * A slot that is checked out already did. Otherwise an idle slot is probed.
     */
    public boolean acceptsUnoConnection() {
        if (shuttingDown.get() || connections == null) {
            return false;
        }
        try {
            recoverDropped();
        } catch (RuntimeException e) {
            LOGGER.error("Could not recover a dropped office slot", e);
        }
        int slots = addresses == null ? 0 : addresses.size();
        Set<OfficeConnection> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < slots; i++) {
            OfficeConnection connection = connections.poll();
            if (connection == null) {
                break;
            }
            if (!seen.add(connection)) {
                offerQuietly(connection);
                break;
            }
            boolean accepted = false;
            try {
                accepted = UnoCall.within("probe", UnoCall.recoverDeadlineMillis(), connection::responds);
            } catch (UnoDeadlineExceeded e) {
                dropSlot(connection);
            } catch (OdiseeServerException | RuntimeException e) {
                if (connection.isLocal()) {
                    connection.setFaulted(true);
                } else {
                    dropSlot(connection);
                }
            } finally {
                if (!accepted && !connection.isLocal() && !connection.wasDropped()) {
                    dropSlot(connection);
                }
                offerQuietly(connection);
            }
            if (accepted) {
                return true;
            }
        }
        return !checkedOut.isEmpty();
    }

    public void recordGenerationMillis(final long millis) {
        generationMillis.set(Math.max(0L, millis));
    }

    public void recordInstructionFailure() {
        instructionFailures.increment();
    }

    public PoolGauges gauges() {
        return new PoolGauges(poolSize(), inUse(), generationMillis.get(), instructionFailures.sum(), sofficeRestarts.sum());
    }

    public int poolSize() {
        return addresses == null ? 0 : addresses.size();
    }

    public int inUse() {
        return checkedOut.size();
    }

    int droppedCount() {
        return dropped.size();
    }

    private void offerQuietly(final OfficeConnection connection) {
        if (connection != null && connections != null && !connection.wasDropped()) {
            connections.offer(connection);
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

    private synchronized void applyGroups(final Map<String, List<InetSocketAddress>> byGroup) {
        if (byGroup == null || byGroup.isEmpty()) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        List<InetSocketAddress> nextAddresses = new ArrayList<>();
        List<String> nextGroups = new ArrayList<>();
        String firstGroup = null;
        for (Map.Entry<String, List<InetSocketAddress>> entry : byGroup.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (firstGroup == null) {
                firstGroup = entry.getKey();
            }
            for (InetSocketAddress address : entry.getValue()) {
                nextAddresses.add(address);
                nextGroups.add(entry.getKey());
            }
        }
        if (nextAddresses.isEmpty()) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        groupname = firstGroup == null ? "group0" : firstGroup;
        addresses = nextAddresses;
        addressGroups = nextGroups;
        initializeConnections();
    }

    /**
     * Swap the idle slots on this factory. Tests use this to prove a reload keeps the same object.
     * Production reload goes through {@link #load(Map)}, which dials each new address.
     */
    public synchronized void reloadConnections(final Map<String, List<OfficeConnection>> byGroup) {
        if (byGroup == null || byGroup.isEmpty()) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        shuttingDown.set(false);
        checkedOut.clear();
        dropped.clear();
        connectionGroup.clear();
        addresses = new ArrayList<>();
        addressGroups = new ArrayList<>();
        int count = 0;
        for (List<OfficeConnection> ready : byGroup.values()) {
            if (ready != null) {
                count += ready.size();
            }
        }
        connections = new LinkedBlockingQueue<>(Math.max(1, count));
        String firstGroup = null;
        for (Map.Entry<String, List<OfficeConnection>> entry : byGroup.entrySet()) {
            if (firstGroup == null) {
                firstGroup = entry.getKey();
            }
            if (entry.getValue() == null) {
                continue;
            }
            for (OfficeConnection connection : entry.getValue()) {
                addresses.add(connection.socketAddress() == null
                        ? new InetSocketAddress("127.0.0.1", 9)
                        : connection.socketAddress());
                addressGroups.add(entry.getKey());
                connectionGroup.put(connection, entry.getKey());
                if (!connections.offer(connection)) {
                    throw new OdiseeServerRuntimeException("reload rejected a connection");
                }
            }
        }
        groupname = firstGroup == null ? "test" : firstGroup;
    }

    private synchronized void initializeConnections() {
        shuttingDown.set(false);
        checkedOut.clear();
        dropped.clear();
        connectionGroup.clear();
        // Check state
        if (null == addresses || addresses.isEmpty()) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        // Setup queue for connections
        connections = new LinkedBlockingQueue<>(addresses.size());
        // Process all TCP/IP addresses
        for (int index = 0; index < addresses.size(); index++) {
            final InetSocketAddress socketAddress = addresses.get(index);
            final String slotGroup = addressGroups != null && index < addressGroups.size()
                    ? addressGroups.get(index)
                    : groupname;
            final OfficeConnection officeConnection = new OfficeConnection(socketAddress);
            try {
                officeConnection.bootstrap(false);
                connectionGroup.put(officeConnection, slotGroup == null ? "group0" : slotGroup);
                final boolean offer = connections.offer(officeConnection);
                if (offer) {
                    LOGGER.info("Added connection {} to queue", officeConnection);
                } else {
                    LOGGER.error("Could not add connection {} to queue", officeConnection);
                }
            } catch (OdiseeServerException e) {
                LOGGER.error("[group={}] Could not bootstrap connection to {}: {}",
                        slotGroup, socketAddress, e.getLocalizedMessage());
            }
        }
        if (connections.isEmpty()) {
            throw new OdiseeServerRuntimeException(String.format(
                    "[group=%s] No office connections could be bootstrapped", groupname));
        }
    }

}
