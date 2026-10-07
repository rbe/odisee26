package org.odisee.ooo.connection;

import java.net.InetSocketAddress;

/**
 * Office connection whose {@link #connect()} succeeds or fails on demand.
 */
public final class ScriptedOfficeConnection extends OfficeConnection {

    private boolean failConnect;

    private boolean connected;

    private boolean retired;

    private boolean hangProbe;

    public ScriptedOfficeConnection(final boolean failConnect) {
        super(new InetSocketAddress("127.0.0.1", 9));
        this.failConnect = failConnect;
    }

    public void setFailConnect(final boolean failConnect) {
        this.failConnect = failConnect;
    }

    public void setHangProbe(final boolean hangProbe) {
        this.hangProbe = hangProbe;
    }

    public boolean retired() {
        return retired;
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
    public boolean responds() throws OdiseeServerException {
        if (hangProbe) {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new OdiseeServerException("interrupted", e);
            }
        }
        connect();
        return isConnected();
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

    @Override
    public boolean releaseForWatchdog() {
        connected = false;
        retired = true;
        return true;
    }

}
