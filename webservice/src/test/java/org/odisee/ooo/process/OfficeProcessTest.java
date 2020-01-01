package org.odisee.ooo.process;

import org.junit.Ignore;
import org.junit.Test;
import org.odisee.ooo.connection.OdiseeServerException;
import org.odisee.ooo.connection.OdiseeServerRuntimeException;

import java.net.InetSocketAddress;

public class OfficeProcessTest {

    @Test
    @Ignore
    public void shouldStartOfficeProcess() throws InterruptedException {
        final OfficeProcess officeProcess = new OfficeProcess();
        final InetSocketAddress socketAddress = new InetSocketAddress("127.0.0.1", 2001);
        Runnable r = () -> {
            try {
                officeProcess.startOfficeProcess(socketAddress);
            } catch (OdiseeServerException e) {
                throw new OdiseeServerRuntimeException(e);
            }
        };
        final Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
        Thread.sleep(5 * 1000);
    }

}
