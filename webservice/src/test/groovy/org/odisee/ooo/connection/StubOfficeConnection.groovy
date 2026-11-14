package org.odisee.ooo.connection

import com.sun.star.frame.XComponentLoader
import com.sun.star.lang.XMultiComponentFactory

import java.net.InetSocketAddress

class StubOfficeConnection extends OfficeConnection {

    private final XComponentLoader componentLoader

    private final XMultiComponentFactory remoteServiceManager

    StubOfficeConnection(XComponentLoader componentLoader = null,
                         XMultiComponentFactory remoteServiceManager = null,
                         String host = '127.0.0.1',
                         int port = 2001) {
        super(new InetSocketAddress(host, port))
        this.componentLoader = componentLoader
        this.remoteServiceManager = remoteServiceManager
    }

    @Override
    boolean initializationCompleted() {
        true
    }

    @Override
    boolean isConnected() {
        true
    }

    @Override
    void connect() throws OdiseeServerException {
    }

    @Override
    XComponentLoader getXComponentLoader() {
        componentLoader
    }

    @Override
    XMultiComponentFactory getXRemoteServiceManager() {
        remoteServiceManager
    }

}
