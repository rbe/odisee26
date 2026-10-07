package org.odisee.ooo.connection;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
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
    }

}
