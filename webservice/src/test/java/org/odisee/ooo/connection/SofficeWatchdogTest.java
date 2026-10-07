package org.odisee.ooo.connection;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SofficeWatchdogTest {

    @Test
    public void portMustMatchTheWholeNumber() {
        String command = "soffice.bin --accept=socket,host=127.0.0.1,port=2002;urp;";
        assertTrue(SofficeWatchdog.listensOnPort(command, 2002));
        assertFalse(SofficeWatchdog.listensOnPort(command, 200));
        assertFalse(SofficeWatchdog.listensOnPort(command, 20020));
        assertFalse(SofficeWatchdog.listensOnPort("sleep 30", 2002));
        assertFalse(SofficeWatchdog.listensOnPort(null, 2002));
    }

    @Test
    public void terminateSignalsTheLocalSofficeForThatPort() throws Exception {
        int port = 27000 + (int) (System.nanoTime() % 1000);
        Path directory = Files.createTempDirectory("odisee-soffice");
        Path script = directory.resolve("soffice");
        Files.writeString(script, "#!/bin/bash\nwhile true; do sleep 1; done\n");
        Files.setPosixFilePermissions(script, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE));
        Process process = new ProcessBuilder(script.toString(), "--accept=socket,port=" + port).start();
        try {
            boolean listed = false;
            String command = "";
            for (int i = 0; i < 50 && !listed; i++) {
                command = process.info().commandLine().orElse("");
                listed = SofficeWatchdog.listensOnPort(command, port);
                if (!listed) {
                    Thread.sleep(20);
                }
            }
            assertTrue("command line was '" + command + "'", listed);
            assertTrue(SofficeWatchdog.terminate(port));
            assertTrue(process.waitFor(5, TimeUnit.SECONDS));
            assertFalse(process.isAlive());
        } finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            script.toFile().delete();
            directory.toFile().delete();
        }
    }

}
