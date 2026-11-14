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

    public static final String POLL_TIMEOUT_PROPERTY = "odisee.pool.poll.timeout.ms";

    private static final Logger LOGGER = LoggerFactory.getLogger(OfficeConnectionFactory.class);

    private static final int QUEUE_POLL_TIMEOUT = 5;

    private static final TimeUnit QUEUE_POLL_TIMEUNIT = TimeUnit.SECONDS;

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private volatile String groupname;

    private volatile boolean groupedPool;

    private volatile List<InetSocketAddress> addresses;

    private volatile List<String> addressGroups;

    private final Map<String, LinkedBlockingQueue<OfficeConnection>> queuesByGroup = new ConcurrentHashMap<>();

    private final Map<String, Integer> groupCapacities = new ConcurrentHashMap<>();

    private final Map<OfficeConnection, String> connectionGroup = new ConcurrentHashMap<>();

    private final Set<OfficeConnection> checkedOut = ConcurrentHashMap.newKeySet();

    private final Set<OfficeConnection> retired = ConcurrentHashMap.newKeySet();

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
        List<InetSocketAddress> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(new InetSocketAddress(host, basePort + i));
        }
        Map<String, List<InetSocketAddress>> grouped = new LinkedHashMap<>();
        grouped.put(groupname, list);
        return load(grouped);
    }

    private OfficeConnectionFactory() {
    }

    /**
     * A pool that does not dial LibreOffice. Tests supply the connections.
     */
    public static OfficeConnectionFactory forTest(final List<OfficeConnection> ready) {
        OfficeConnectionFactory factory = new OfficeConnectionFactory();
        factory.groupname = "test";
        factory.groupedPool = false;
        factory.addresses = new ArrayList<>();
        int capacity = Math.max(1, ready.size());
        LinkedBlockingQueue<OfficeConnection> queue = new LinkedBlockingQueue<>(capacity);
        factory.shuttingDown.set(false);
        for (OfficeConnection connection : ready) {
            factory.addresses.add(connection.socketAddress() == null
                    ? new InetSocketAddress("127.0.0.1", 9)
                    : connection.socketAddress());
            factory.connectionGroup.put(connection, "test");
            if (!queue.offer(connection)) {
                throw new OdiseeServerRuntimeException("test pool rejected a connection");
            }
        }
        factory.queuesByGroup.put("test", queue);
        factory.groupCapacities.put("test", capacity);
        return factory;
    }

    int waiting() {
        int sum = 0;
        for (LinkedBlockingQueue<OfficeConnection> queue : queuesByGroup.values()) {
            sum += queue.size();
        }
        return sum;
    }

    public synchronized void addConnections(final String host, final int basePort, final int count) {
        if (addresses == null) {
            addresses = new ArrayList<>();
        }
        for (int i = 0; i < count; i++) {
            addresses.add(new InetSocketAddress(host, basePort + i));
        }
    }

    /**
     * Take a slot from the named group. A test pool with no groups uses every slot.
     */
    public OfficeConnection fetchConnection(final String group, final boolean waitForever) throws OdiseeServerException {
        if (!groupedPool) {
            return fetchConnection(waitForever);
        }
        if (shuttingDown.get()) {
            throw new OdiseeServerException("Shutdown in progress");
        }
        recoverDropped();
        String wanted = group == null || group.isBlank() ? "group0" : group;
        LinkedBlockingQueue<OfficeConnection> queue = queuesByGroup.get(wanted);
        if (queue == null) {
            throw new OdiseeServerException(String.format("[group=%s] No office instances configured", wanted));
        }
        int capacity = groupCapacities.getOrDefault(wanted, 1);
        return fetchFromQueue(wanted, queue, capacity, waitForever);
    }

    public OfficeConnection fetchConnection(final boolean waitForever) throws OdiseeServerException {
        if (shuttingDown.get()) {
            throw new OdiseeServerException("Shutdown in progress");
        }
        recoverDropped();
        String target = groupname == null || groupname.isBlank() ? "group0" : groupname;
        LinkedBlockingQueue<OfficeConnection> queue = queuesByGroup.get(target);
        if (queue == null && !queuesByGroup.isEmpty()) {
            Map.Entry<String, LinkedBlockingQueue<OfficeConnection>> first = queuesByGroup.entrySet().iterator().next();
            target = first.getKey();
            queue = first.getValue();
        }
        if (queue == null) {
            throw new OdiseeServerException(String.format("[group=%s] Could not fetch connection from pool, sorry.", target));
        }
        int capacity = groupCapacities.getOrDefault(target, addresses == null ? 1 : Math.max(1, addresses.size()));
        return fetchFromQueue(target, queue, capacity, waitForever);
    }

    private OfficeConnection fetchFromQueue(
            final String label,
            final LinkedBlockingQueue<OfficeConnection> queue,
            final int groupSize,
            final boolean waitForever) throws OdiseeServerException {
        int maxAttempts = Math.max(1, groupSize);
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(pollTimeoutMillis());
        List<OfficeConnection> failed = new ArrayList<>();
        OdiseeServerException lastFailure = null;
        try {
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                OfficeConnection officeConnection;
                if (waitForever && attempt == 0) {
                    officeConnection = takeConnection(queue);
                } else {
                    long remainingNanos = deadlineNanos - System.nanoTime();
                    officeConnection = remainingNanos <= 0L
                            ? queue.poll()
                            : pollConnection(queue, remainingNanos);
                }
                if (officeConnection == null) {
                    break;
                }
                if (retired.remove(officeConnection)) {
                    closeQuietly(officeConnection);
                    attempt--;
                    continue;
                }
                try {
                    officeConnection.connect();
                    if (officeConnection.isConnected()) {
                        checkedOut.add(officeConnection);
                        return officeConnection;
                    }
                    failed.add(officeConnection);
                } catch (OdiseeServerException e) {
                    lastFailure = e;
                    officeConnection.setFaulted(true);
                    failed.add(officeConnection);
                } catch (RuntimeException e) {
                    lastFailure = new OdiseeServerException("Office connection failed", e);
                    officeConnection.setFaulted(true);
                    failed.add(officeConnection);
                }
            }
        } finally {
            for (OfficeConnection failedConnection : failed) {
                offerQuietly(failedConnection);
            }
        }
        if (lastFailure != null) {
            throw lastFailure;
        }
        throw new OdiseeServerException(String.format("[group=%s] Could not fetch connection from pool, sorry.", label));
    }

    private static OfficeConnection takeConnection(final LinkedBlockingQueue<OfficeConnection> queue)
            throws OdiseeServerException {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OdiseeServerException("Interrupted while waiting for an office connection", e);
        }
    }

    private static OfficeConnection pollConnection(
            final LinkedBlockingQueue<OfficeConnection> queue,
            final long timeoutNanos) throws OdiseeServerException {
        try {
            return queue.poll(timeoutNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OdiseeServerException("Interrupted while waiting for an office connection", e);
        }
    }

    static long pollTimeoutMillis() {
        String raw = System.getProperty(POLL_TIMEOUT_PROPERTY);
        if (raw != null && !raw.isBlank()) {
            try {
                long parsed = Long.parseLong(raw.trim());
                if (parsed > 0L) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return QUEUE_POLL_TIMEUNIT.toMillis(QUEUE_POLL_TIMEOUT);
    }

    public void repositConnection(final OfficeConnection officeConnection) throws OdiseeServerException {
        if (null == officeConnection) {
            return;
        }
        checkedOut.remove(officeConnection);
        if (retired.remove(officeConnection)) {
            closeQuietly(officeConnection);
            return;
        }
        if (officeConnection.wasDropped()) {
            return;
        }
        if (shuttingDown.get()) {
            closeQuietly(officeConnection);
            throw new OdiseeServerException("Shutdown in progress");
        }
        LinkedBlockingQueue<OfficeConnection> queue = queueFor(officeConnection);
        if (queue == null) {
            closeQuietly(officeConnection);
            return;
        }
        boolean connectionWasPutBack = false;
        int i = 0;
        while (i++ < 3 && !connectionWasPutBack) {
            connectionWasPutBack = queue.offer(officeConnection);
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
        if (officeConnection == null) {
            return;
        }
        checkedOut.remove(officeConnection);
        if (retired.remove(officeConnection)) {
            officeConnection.markDropped();
            signalLocalIfNeeded(officeConnection);
            return;
        }
        if (!dropped.add(officeConnection)) {
            return;
        }
        officeConnection.markDropped();
        signalLocalIfNeeded(officeConnection);
    }

    private void signalLocalIfNeeded(final OfficeConnection officeConnection) {
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
        if (queuesByGroup.isEmpty() || dropped.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (OfficeConnection connection : dropped) {
            if (retired.remove(connection)) {
                dropped.remove(connection);
                closeQuietly(connection);
                continue;
            }
            LinkedBlockingQueue<OfficeConnection> queue = queueFor(connection);
            if (queue == null) {
                dropped.remove(connection);
                closeQuietly(connection);
                continue;
            }
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
            if (queue.offer(connection)) {
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
        if (shuttingDown.get() || queuesByGroup.isEmpty()) {
            return false;
        }
        try {
            recoverDropped();
        } catch (RuntimeException e) {
            LOGGER.error("Could not recover a dropped office slot", e);
        }
        Set<OfficeConnection> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Map.Entry<String, LinkedBlockingQueue<OfficeConnection>> entry : queuesByGroup.entrySet()) {
            LinkedBlockingQueue<OfficeConnection> queue = entry.getValue();
            int slots = groupCapacities.getOrDefault(entry.getKey(), Math.max(1, queue.size()));
            for (int i = 0; i < slots; i++) {
                OfficeConnection connection = queue.poll();
                if (connection == null) {
                    break;
                }
                if (retired.remove(connection)) {
                    closeQuietly(connection);
                    continue;
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
        List<InetSocketAddress> current = addresses;
        return current == null ? 0 : current.size();
    }

    public int inUse() {
        return checkedOut.size();
    }

    int droppedCount() {
        return dropped.size();
    }

    private LinkedBlockingQueue<OfficeConnection> queueFor(final OfficeConnection connection) {
        if (connection == null) {
            return null;
        }
        String group = connectionGroup.get(connection);
        if (group != null) {
            return queuesByGroup.get(group);
        }
        if (!groupedPool) {
            return queuesByGroup.get("test");
        }
        return null;
    }

    private void offerQuietly(final OfficeConnection connection) {
        if (connection == null || connection.wasDropped()) {
            return;
        }
        if (retired.remove(connection)) {
            closeQuietly(connection);
            return;
        }
        LinkedBlockingQueue<OfficeConnection> queue = queueFor(connection);
        if (queue != null) {
            queue.offer(connection);
        } else {
            closeQuietly(connection);
        }
    }

    private static void closeQuietly(final OfficeConnection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (Exception e) {
            LOGGER.debug("Could not close retired office connection {}", connection, e);
        }
    }

    /**
     * Shutdown all connections and the factory.
     * @param cleanup If false, OfficeConnection objects remain in the pool, otherwise they are removed.
     */
    public synchronized void shutdown(final boolean cleanup) {
        shuttingDown.getAndSet(true);
        for (LinkedBlockingQueue<OfficeConnection> queue : queuesByGroup.values()) {
            Iterator<OfficeConnection> iter = queue.iterator();
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
        initializeConnections(firstGroup == null ? "group0" : firstGroup, nextAddresses, nextGroups);
    }

    /**
     * Swap the idle slots on this factory. Tests use this to prove a reload keeps the same object.
     * Production reload goes through {@link #load(Map)}, which dials each new address.
     */
    public synchronized void reloadConnections(final Map<String, List<OfficeConnection>> byGroup) {
        if (byGroup == null || byGroup.isEmpty()) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        Set<OfficeConnection> keep = Collections.newSetFromMap(new IdentityHashMap<>());
        int totalCount = 0;
        for (List<OfficeConnection> ready : byGroup.values()) {
            if (ready != null) {
                for (OfficeConnection conn : ready) {
                    if (conn != null) {
                        keep.add(conn);
                        totalCount++;
                    }
                }
            }
        }
        if (totalCount == 0) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        Map<String, LinkedBlockingQueue<OfficeConnection>> nextQueues = new LinkedHashMap<>();
        Map<String, Integer> nextCapacities = new LinkedHashMap<>();
        Map<OfficeConnection, String> nextConnectionGroup = new IdentityHashMap<>();
        List<InetSocketAddress> nextAddresses = new ArrayList<>();
        List<String> nextGroups = new ArrayList<>();
        String firstGroup = null;
        for (Map.Entry<String, List<OfficeConnection>> entry : byGroup.entrySet()) {
            String key = entry.getKey() == null || entry.getKey().isBlank() ? "group0" : entry.getKey();
            if (firstGroup == null) {
                firstGroup = key;
            }
            List<OfficeConnection> groupList = entry.getValue();
            if (groupList == null || groupList.isEmpty()) {
                continue;
            }
            int cap = groupList.size();
            LinkedBlockingQueue<OfficeConnection> queue = new LinkedBlockingQueue<>(cap);
            for (OfficeConnection connection : groupList) {
                nextAddresses.add(connection.socketAddress() == null
                        ? new InetSocketAddress("127.0.0.1", 9)
                        : connection.socketAddress());
                nextGroups.add(key);
                nextConnectionGroup.put(connection, key);
                if (!checkedOut.contains(connection)) {
                    if (!queue.offer(connection)) {
                        throw new OdiseeServerRuntimeException("reload rejected a connection");
                    }
                }
            }
            nextQueues.put(key, queue);
            nextCapacities.put(key, cap);
        }
        retirePreviousPool(keep);
        installPool(firstGroup == null ? "test" : firstGroup, true, nextAddresses, nextGroups,
                nextQueues, nextCapacities, nextConnectionGroup);
    }

    private synchronized void initializeConnections(
            final String defaultGroup,
            final List<InetSocketAddress> nextAddresses,
            final List<String> nextGroups) {
        if (nextAddresses == null || nextAddresses.isEmpty()) {
            throw new OdiseeServerRuntimeException("Initialization error");
        }
        Map<String, List<OfficeConnection>> bootstrappedByGroup = new LinkedHashMap<>();
        for (int index = 0; index < nextAddresses.size(); index++) {
            final InetSocketAddress socketAddress = nextAddresses.get(index);
            final String rawGroup = nextGroups != null && index < nextGroups.size()
                    ? nextGroups.get(index)
                    : defaultGroup;
            final String slotGroup = rawGroup == null || rawGroup.isBlank() ? "group0" : rawGroup;
            final OfficeConnection officeConnection = new OfficeConnection(socketAddress);
            try {
                officeConnection.bootstrap(false);
                bootstrappedByGroup.computeIfAbsent(slotGroup, k -> new ArrayList<>()).add(officeConnection);
                LOGGER.info("Added connection {} to queue", officeConnection);
            } catch (OdiseeServerException e) {
                LOGGER.error("[group={}] Could not bootstrap connection to {}: {}",
                        slotGroup, socketAddress, e.getLocalizedMessage());
            }
        }
        if (bootstrappedByGroup.isEmpty()) {
            throw new OdiseeServerRuntimeException(String.format(
                    "[group=%s] No office connections could be bootstrapped", defaultGroup));
        }
        Map<String, LinkedBlockingQueue<OfficeConnection>> nextQueues = new LinkedHashMap<>();
        Map<String, Integer> nextCapacities = new LinkedHashMap<>();
        Map<OfficeConnection, String> nextConnectionGroup = new IdentityHashMap<>();
        for (Map.Entry<String, List<OfficeConnection>> entry : bootstrappedByGroup.entrySet()) {
            String key = entry.getKey();
            List<OfficeConnection> list = entry.getValue();
            LinkedBlockingQueue<OfficeConnection> queue = new LinkedBlockingQueue<>(list.size());
            for (OfficeConnection conn : list) {
                nextConnectionGroup.put(conn, key);
                if (!queue.offer(conn)) {
                    LOGGER.error("Could not add connection {} to queue", conn);
                }
            }
            nextQueues.put(key, queue);
            nextCapacities.put(key, list.size());
        }
        retirePreviousPool(Collections.emptySet());
        installPool(defaultGroup, true, nextAddresses, nextGroups, nextQueues, nextCapacities, nextConnectionGroup);
    }

    private void retirePreviousPool(final Set<OfficeConnection> keep) {
        List<OfficeConnection> oldIdle = new ArrayList<>();
        for (LinkedBlockingQueue<OfficeConnection> oldQueue : queuesByGroup.values()) {
            oldQueue.drainTo(oldIdle);
        }
        for (OfficeConnection inFlight : checkedOut) {
            if (!keep.contains(inFlight)) {
                retired.add(inFlight);
            }
        }
        dropped.removeIf(conn -> !keep.contains(conn));
        for (OfficeConnection conn : oldIdle) {
            if (!keep.contains(conn) && !checkedOut.contains(conn)) {
                closeQuietly(conn);
            }
        }
    }

    private void installPool(
            final String nextGroupName,
            final boolean nextGrouped,
            final List<InetSocketAddress> nextAddresses,
            final List<String> nextGroups,
            final Map<String, LinkedBlockingQueue<OfficeConnection>> nextQueues,
            final Map<String, Integer> nextCapacities,
            final Map<OfficeConnection, String> nextConnectionGroup) {
        shuttingDown.set(false);
        queuesByGroup.clear();
        queuesByGroup.putAll(nextQueues);
        groupCapacities.clear();
        groupCapacities.putAll(nextCapacities);
        connectionGroup.clear();
        connectionGroup.putAll(nextConnectionGroup);
        addresses = nextAddresses;
        addressGroups = nextGroups;
        groupname = nextGroupName;
        groupedPool = nextGrouped;
    }

}
