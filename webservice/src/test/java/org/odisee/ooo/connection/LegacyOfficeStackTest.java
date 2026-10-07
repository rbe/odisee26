package org.odisee.ooo.connection;

import org.junit.Test;

import static org.junit.Assert.fail;

/**
 * The unused office stack is not compiled, so the next edit cannot land there.
 */
public class LegacyOfficeStackTest {

    @Test
    public void oooConnectionStackIsAbsent() {
        assertAbsent("org.odisee.ooo.connection.OOoConnection");
        assertAbsent("org.odisee.ooo.connection.OOoConnectionManager");
        assertAbsent("org.odisee.ooo.process.OOoProcess");
    }

    private static void assertAbsent(final String name) {
        try {
            Class.forName(name);
            fail(name + " is still on the classpath");
        } catch (ClassNotFoundException e) {
            // deleted from the source set
        }
    }

}
