package org.odisee.ooo.connection;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Test 5. A dead slot is returned to the pool and the next slot is used.
 */
public class OfficeConnectionPoolTest {

    @Test
    public void skipsADeadSlot() throws OdiseeServerException {
        ScriptedOfficeConnection dead = new ScriptedOfficeConnection(true);
        ScriptedOfficeConnection live = new ScriptedOfficeConnection(false);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Arrays.asList(dead, live));

        OfficeConnection fetched = factory.fetchConnection(false);

        assertSame(live, fetched);
        factory.repositConnection(fetched);
        assertEquals(2, factory.waiting());
    }

    @Test
    public void bothSlotsDownThrowsAndReturnsBothToThePool() {
        ScriptedOfficeConnection first = new ScriptedOfficeConnection(true);
        ScriptedOfficeConnection second = new ScriptedOfficeConnection(true);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Arrays.asList(first, second));

        try {
            factory.fetchConnection(false);
            fail("both slots are down");
        } catch (OdiseeServerException e) {
            assertEquals("down", e.getMessage());
        }
        assertEquals(2, factory.waiting());
        assertFalse(factory.acceptsUnoConnection());
        assertEquals(2, factory.waiting());
    }

    @Test
    public void liveSlotIsReadyAndCheckedOutCountsAsInUse() throws OdiseeServerException {
        ScriptedOfficeConnection live = new ScriptedOfficeConnection(false);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Collections.singletonList(live));

        assertTrue(factory.acceptsUnoConnection());
        OfficeConnection fetched = factory.fetchConnection(false);
        assertEquals(1, factory.inUse());
        assertEquals(1, factory.poolSize());
        assertTrue(factory.acceptsUnoConnection());
        factory.repositConnection(fetched);
        assertEquals(0, factory.inUse());
        assertEquals(1, factory.waiting());
    }

    @Test
    public void deadlineDropsTheSlotAfterClose() throws Exception {
        ScriptedOfficeConnection connection = new ScriptedOfficeConnection(false);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Collections.singletonList(connection));
        OfficeConnection fetched = factory.fetchConnection(false);
        AtomicBoolean closed = new AtomicBoolean();

        try {
            UnoCall.within("open", 50, () -> {
                Thread.sleep(5_000);
                return null;
            });
            fail("open should hit the deadline");
        } catch (UnoDeadlineExceeded e) {
            assertEquals("open", e.phase());
            UnoCall.within("close", 1_000, () -> {
                closed.set(true);
                return null;
            });
            factory.dropSlot(fetched);
        }

        assertTrue(closed.get());
        assertTrue(connection.retired());
        assertEquals(0, factory.waiting());
        assertEquals(0, factory.inUse());
        assertEquals(1, factory.droppedCount());
        assertEquals(1, factory.gauges().sofficeRestarts());
        factory.repositConnection(fetched);
        assertEquals(0, factory.waiting());
    }

    @Test
    public void wedgedCloseStillDropsTheSlot() throws Exception {
        ScriptedOfficeConnection connection = new ScriptedOfficeConnection(false);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Collections.singletonList(connection));
        OfficeConnection fetched = factory.fetchConnection(false);

        try {
            UnoCall.within("save", 50, () -> {
                Thread.sleep(5_000);
                return null;
            });
            fail("save should hit the deadline");
        } catch (UnoDeadlineExceeded e) {
            try {
                UnoCall.within("close", 50, () -> {
                    Thread.sleep(5_000);
                    return null;
                });
                fail("close should hit the deadline");
            } catch (UnoDeadlineExceeded closeDeadline) {
                assertEquals("close", closeDeadline.phase());
            }
            factory.dropSlot(fetched);
        }

        assertTrue(connection.retired());
        assertEquals(0, factory.inUse());
        assertEquals(1, factory.gauges().sofficeRestarts());
    }

    @Test
    public void droppedSlotRejoinsWhenOfficeAcceptsAgain() throws OdiseeServerException {
        ScriptedOfficeConnection connection = new ScriptedOfficeConnection(false);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Collections.singletonList(connection));
        OfficeConnection fetched = factory.fetchConnection(false);
        connection.setFailConnect(true);
        factory.dropSlot(fetched);

        assertEquals(0, factory.recoverDropped());
        assertFalse(factory.acceptsUnoConnection());
        assertEquals(1, factory.gauges().sofficeRestarts());

        connection.setFailConnect(false);
        assertEquals(1, factory.recoverDropped());
        assertEquals(1, factory.waiting());
        assertEquals(0, factory.droppedCount());
        assertTrue(factory.acceptsUnoConnection());
        assertEquals(1, factory.gauges().sofficeRestarts());
    }

    @Test
    public void wedgedProbeDropsTheSlot() {
        ScriptedOfficeConnection connection = new ScriptedOfficeConnection(false);
        connection.setHangProbe(true);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Collections.singletonList(connection));
        System.setProperty(UnoCall.RECOVER_DEADLINE_PROPERTY, "50");
        try {
            assertFalse(factory.acceptsUnoConnection());
        } finally {
            System.clearProperty(UnoCall.RECOVER_DEADLINE_PROPERTY);
        }
        assertTrue(connection.retired());
        assertEquals(0, factory.waiting());
        assertEquals(1, factory.droppedCount());
        assertEquals(1, factory.gauges().sofficeRestarts());
    }

    @Test
    public void failedRemoteHealthCheckDropsTheHostAndLeavesLocalSoffice() throws OdiseeServerException {
        ScriptedOfficeConnection remote = new ScriptedOfficeConnection(false, "10.0.0.8", 2002);
        remote.setFailConnect(true);
        ScriptedOfficeConnection local = new ScriptedOfficeConnection(false, "127.0.0.1", 2001);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Arrays.asList(remote, local));

        assertTrue(factory.acceptsUnoConnection());

        assertTrue(remote.wasDropped());
        assertFalse(remote.retired());
        assertFalse(local.wasDropped());
        assertFalse(local.retired());
        assertEquals(1, factory.droppedCount());
        assertEquals(0, factory.gauges().sofficeRestarts());
        OfficeConnection fetched = factory.fetchConnection(false);
        assertSame(local, fetched);
        factory.repositConnection(fetched);
    }

    @Test
    public void wedgedRemoteProbeDropsTheHostWithoutKillingLocalSoffice() {
        ScriptedOfficeConnection remote = new ScriptedOfficeConnection(false, "10.0.0.8", 2002);
        remote.setHangProbe(true);
        ScriptedOfficeConnection local = new ScriptedOfficeConnection(false, "127.0.0.1", 2001);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Arrays.asList(remote, local));
        System.setProperty(UnoCall.RECOVER_DEADLINE_PROPERTY, "50");
        try {
            assertTrue(factory.acceptsUnoConnection());
        } finally {
            System.clearProperty(UnoCall.RECOVER_DEADLINE_PROPERTY);
        }
        assertTrue(remote.wasDropped());
        assertFalse(remote.retired());
        assertFalse(local.retired());
        assertEquals(0, factory.gauges().sofficeRestarts());
        assertEquals(1, factory.waiting());
    }

    @Test
    public void reloadAddsAHostOnTheSameFactoryAndTheGroupSelectsIt() throws OdiseeServerException {
        ScriptedOfficeConnection local = new ScriptedOfficeConnection(false, "127.0.0.1", 2001);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Collections.singletonList(local));
        ScriptedOfficeConnection added = new ScriptedOfficeConnection(false, "10.1.0.5", 2010);
        Map<String, List<OfficeConnection>> groups = new LinkedHashMap<>();
        groups.put("group0", Collections.singletonList(local));
        groups.put("writers", Collections.singletonList(added));

        factory.reloadConnections(groups);

        assertEquals(2, factory.poolSize());
        assertEquals(2, factory.waiting());
        OfficeConnection writers = factory.fetchConnection("writers", false);
        assertSame(added, writers);
        factory.repositConnection(writers);
        OfficeConnection fallback = factory.fetchConnection("group0", false);
        assertSame(local, fallback);
        factory.repositConnection(fallback);
        try {
            factory.fetchConnection("missing", false);
            fail("missing group");
        } catch (OdiseeServerException e) {
            assertTrue(e.getMessage().contains("missing"));
        }
    }

    @Test
    public void gaugesKeepTheLastGenerationAndInstructionFailures() {
        ScriptedOfficeConnection live = new ScriptedOfficeConnection(false);
        OfficeConnectionFactory factory = OfficeConnectionFactory.forTest(Collections.singletonList(live));
        factory.recordGenerationMillis(42);
        factory.recordInstructionFailure();
        factory.recordInstructionFailure();

        PoolGauges gauges = factory.gauges();
        assertEquals(1, gauges.poolSize());
        assertEquals(0, gauges.inUse());
        assertEquals(42L, gauges.generationMillis());
        assertEquals(2L, gauges.instructionFailures());
        assertEquals(0L, gauges.sofficeRestarts());
        assertEquals(
                "{\"ready\":true,\"poolSize\":1,\"inUse\":0,\"generationMillis\":42,\"instructionFailures\":2,\"sofficeRestarts\":0}",
                gauges.toJson(true));
    }

}
