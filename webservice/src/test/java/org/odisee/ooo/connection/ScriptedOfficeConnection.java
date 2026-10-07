package org.odisee.ooo.connection;

import java.net.InetSocketAddress;

/**
 * Office connection whose {@link #connect()} succeeds or fails on demand.
 */
public final class ScriptedOfficeConnection extends OfficeConnection {

    private final boolean failConnect;

    private boolean connected;

    public ScriptedOfficeConnection(final boolean failConnect) {
        super(new InetSocketAddress("127.0.0.1", 9));
        this.failConnect = failConnect;
    }

    @Override
    void connect() throws OdiseeServerException {
        if (failConnect) {
            throw new OdiseeServerException("down");
        }
        connected = true;
    }

    @Override
    boolean isConnected() {
        return connected;
    }

    @Override
    public void setFaulted(final boolean faulted) {
        if (faulted) {
            connected = false;
        }
    }

    @Override
    public void close() {
        connected = false;
    }

}
