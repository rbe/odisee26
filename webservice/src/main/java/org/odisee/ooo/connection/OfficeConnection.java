/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 24.07.19, 07:49
 */

package org.odisee.ooo.connection;

import com.sun.star.beans.UnknownPropertyException;
import com.sun.star.beans.XPropertySet;
import com.sun.star.bridge.BridgeExistsException;
import com.sun.star.bridge.XBridge;
import com.sun.star.bridge.XBridgeFactory;
import com.sun.star.comp.helper.Bootstrap;
import com.sun.star.comp.helper.BootstrapException;
import com.sun.star.connection.ConnectionSetupException;
import com.sun.star.connection.Connector;
import com.sun.star.connection.NoConnectException;
import com.sun.star.connection.XConnection;
import com.sun.star.container.NoSuchElementException;
import com.sun.star.container.XEnumeration;
import com.sun.star.container.XEnumerationAccess;
import com.sun.star.frame.XComponentLoader;
import com.sun.star.frame.XDesktop;
import com.sun.star.lang.WrappedTargetException;
import com.sun.star.lang.XComponent;
import com.sun.star.lang.XMultiComponentFactory;
import com.sun.star.lib.uno.helper.UnoUrl;
import com.sun.star.uno.Any;
import com.sun.star.uno.XComponentContext;
import com.sun.star.util.CloseVetoException;
import com.sun.star.util.XCloseable;
import org.odisee.document.OfficeDocument;
import org.odisee.document.OfficeDocumentType;
import org.odisee.ooo.process.OfficeProcess;
import org.odisee.uno.UnoHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.sun.star.uno.UnoRuntime.queryInterface;

@SuppressWarnings("java:S1191")
public class OfficeConnection {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfficeConnection.class);

    /**
     * Was this instance already successfully bootstraped?
     */
    private boolean wasBootstrappedAlready;

    private boolean connected;

    private List<OfficeDocument> managedDocuments;

    /**
     * Host and port.
     */
    private InetSocketAddress socketAddress;

    /**
     * The UNO URL.
     */
    private String unoURL;

    /**
     * The remote XMultiComponentFactory.
     */
    private XMultiComponentFactory xRemoteServiceManager;

    /**
     * The XBridge.
     */
    private XBridge xBridge;

    /**
     * The remote desktop.
     */
    private Object desktop;

    /**
     * The component loader.
     */
    private XComponentLoader xComponentLoader;

    /**
     * Constructor.
     */
    OfficeConnection(InetSocketAddress socketAddress) {
        managedDocuments = new ArrayList<>();
        this.socketAddress = socketAddress;
    }

    @Override
    public String toString() {
        return String.format("OfficeConnection{unoURL=%s}", unoURL);
    }

    public boolean initializationCompleted() {
        boolean b = false;
        if (wasBootstrappedAlready) {
            b = true;
        }
        return b;
    }

    //<editor-fold desc="Bootstrap, Connections">

    /**
     * Bootstrap local office.
     * @throws OdiseeServerException
     */
    private void bootstrapLocalOffice() throws OdiseeServerException {
        // Check state
        if (initializationCompleted()) {
            throw new OdiseeServerRuntimeException("Cannot initialize twice");
        }
        // Get the remote office component context
        XComponentContext xRemoteContext;
        try {
            xRemoteContext = Bootstrap.bootstrap();
            if (xRemoteContext == null) {
                throw new OdiseeServerException("Could not bootstrap default office, no remote XComponentContext");
            }
            try {
                // Get remote service manager
                xRemoteServiceManager = xRemoteContext.getServiceManager();
                // Create desktop service
                desktop = xRemoteServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", xRemoteContext);
                // Query the XComponentLoader interface from the desktop
                xComponentLoader = queryInterface(XComponentLoader.class, desktop);
                // This instance was bootstrapped already
                wasBootstrappedAlready = true;
            } catch (com.sun.star.uno.Exception e) {
                throw new OdiseeServerException(e);
            }
        } catch (BootstrapException e) {
            throw new OdiseeServerException("Cannot connect", e);
        }
    }

    /**
     * Bootstrap local office as a TCP/IP server.
     * @param socketAddress TCP/IP address, host and port.
     * @param start If true we try to start the Office instance.
     * @throws OdiseeServerException
     */
    private void bootstrap(InetSocketAddress socketAddress, boolean start) throws OdiseeServerException {
        this.socketAddress = socketAddress;
        unoURL = UnoHelper.makeUnoUrl(socketAddress);
        // Start a process if address is 127.0.0.1
        if (start && socketAddress.getHostName().equals("127.0.0.1")) {
            OfficeProcess officeProcess = new OfficeProcess();
            officeProcess.startOfficeProcess(socketAddress);
        }
    }

    void bootstrap(boolean start) throws OdiseeServerException {
        if (null != socketAddress) {
            bootstrap(socketAddress, start);
        } else {
            bootstrapLocalOffice();
        }
        enumerateComponents("bootstrap");
    }

    boolean isConnected() {
        return connected;
    }

    void connect() throws OdiseeServerException {
        // Check state
        if (isConnected() && initializationCompleted()) {
            return;
        }
        // Create default local component context
        XComponentContext xLocalContext = null;
        try {
            xLocalContext = Bootstrap.createInitialComponentContext(null);
        } catch (Exception e) {
            throw new OdiseeServerException("Cannot create intial component context", e);
        }
        //
        // Initial service manager
        //
        XMultiComponentFactory xLocalServiceManager = xLocalContext.getServiceManager();
        //
        // XBridgeFactory
        //
        Object oBridgeFactory = null;
        try {
            oBridgeFactory = xLocalServiceManager.createInstanceWithContext("com.sun.star.bridge.BridgeFactory", xLocalContext);
        } catch (com.sun.star.uno.Exception e) {
            throw new OdiseeServerException("Could not create BridgeFactory", e);
        }
        XBridgeFactory factory = queryInterface(XBridgeFactory.class, oBridgeFactory);
        UnoUrl parsedUnoUrl = null;
        final String UNO_PARSE_ERROR = "Could not parse UNOHelper url: ";
        try {
            parsedUnoUrl = UnoUrl.parseUnoUrl(unoURL);
        } catch (com.sun.star.lang.IllegalArgumentException e) {
            throw new OdiseeServerException(UNO_PARSE_ERROR + unoURL, e);
        }
        if (null == parsedUnoUrl) {
            throw new OdiseeServerException(UNO_PARSE_ERROR + unoURL);
        }
        //
        // XConnection
        //
        XConnection xConnection = null;
        final String CONNECT_ERROR = "Could not connect to ";
        try {
            xConnection = Connector.create(xLocalContext).connect(parsedUnoUrl.getConnectionAndParametersAsString());
        } catch (NoConnectException | ConnectionSetupException e) {
            throw new OdiseeServerException(CONNECT_ERROR + unoURL, e);
        }
        if (null == xConnection) {
            throw new OdiseeServerException(CONNECT_ERROR + unoURL);
        }
        final String XBRIGE_ERROR = "Could not setup XBridge";
        try {
            xBridge = factory.createBridge("", parsedUnoUrl.getProtocolAndParametersAsString(), xConnection, null);
        } catch (BridgeExistsException | com.sun.star.lang.IllegalArgumentException e) {
            throw new OdiseeServerException(XBRIGE_ERROR, e);
        }
        //
        // Get the remote service manager
        //
        Object o = xBridge.getInstance("StarOffice.ServiceManager");
        xRemoteServiceManager = queryInterface(XMultiComponentFactory.class, o);
        // Retrieve the component context (it's not yet exported from the office)
        // Query for the XPropertySet interface.
        XPropertySet xProperySet = queryInterface(XPropertySet.class, xRemoteServiceManager);
        // Get the default context from the office server.
        Object oDefaultContext = null;
        final String DEFAULTCONTEXT_ERROR = "Cannot get DefaultContext";
        try {
            oDefaultContext = xProperySet.getPropertyValue("DefaultContext");
        } catch (UnknownPropertyException | WrappedTargetException e) {
            throw new OdiseeServerException(DEFAULTCONTEXT_ERROR, e);
        }
        // Query for the interface XComponentContext.
        XComponentContext xComponentContext = queryInterface(XComponentContext.class, oDefaultContext);
        // Get remote service manager
        xRemoteServiceManager = xComponentContext.getServiceManager();
        // Now create the desktop service
        // NOTE: use the office component context here!
        try {
            desktop = xRemoteServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", xComponentContext);
        } catch (com.sun.star.uno.Exception e) {
            throw new OdiseeServerException("Cannot get Desktop", e);
        }
        // Query the XComponentLoader interface from the desktop
        xComponentLoader = queryInterface(XComponentLoader.class, desktop);
        // This instance was bootstrapped already
        wasBootstrappedAlready = true;
        // Connection is established
        connected = true;
        /*
        // Create a UnoUrlResolver
        Object urlResolver = null;
        try {
            urlResolver = xLocalServiceManager.createInstanceWithContext("com.sun.star.bridge.UnoUrlResolver", xLocalContext);
        } catch (com.sun.star.uno.Exception e) {
            throw new OdiseeServerException("Cannot create UNO url resolver", e);
        }
        // Query for the XUnoUrlResolver interface
        XUnoUrlResolver xUrlResolver = queryInterface(XUnoUrlResolver.class, urlResolver);
        Object rInitialObject = null;
        try {
            // Import the object
            rInitialObject = xUrlResolver.resolve(unoURL);
        } catch (com.sun.star.connection.NoConnectException e) {
            throw new OdiseeServerException("Couldn't connect to remote server", e);
        } catch (com.sun.star.connection.ConnectionSetupException e) {
            throw new OdiseeServerException("Couldn't access necessary local resource to establish the interprocess connection", e);
        } catch (com.sun.star.lang.IllegalArgumentException e) {
            throw new OdiseeServerException("uno-url is syntactical illegal (" + unoURL + ")", e);
        } catch (com.sun.star.uno.RuntimeException e) {
            throw new OdiseeServerException("Got UNO RuntimeException", e);
        }
        if (null != rInitialObject) {
            XMultiComponentFactory xOfficeFactory = queryInterface(XMultiComponentFactory.class, rInitialObject);
            // Retrieve the component context as property (it is not yet exported from the office)
            // Query for the XPropertySet interface.
            XPropertySet xProperySet = queryInterface(XPropertySet.class, xOfficeFactory);
            // Get the default context from the office server.
            Object oDefaultContext = null;
            try {
                oDefaultContext = xProperySet.getPropertyValue("DefaultContext");
            } catch (UnknownPropertyException e) {
                throw new OdiseeServerException("Cannot get DefaultContext", e);
            } catch (WrappedTargetException e) {
                throw new OdiseeServerException("Cannot get DefaultContext", e);
            }
            // Query for the interface XComponentContext.
            XComponentContext xComponentContext = queryInterface(XComponentContext.class, oDefaultContext);
            // Get remote service manager
            xRemoteServiceManager = xComponentContext.getServiceManager();
            // Now create the desktop service
            // NOTE: use the office component context here!
            try {
                desktop = xOfficeFactory.createInstanceWithContext("com.sun.star.frame.Desktop", xComponentContext);
            } catch (com.sun.star.uno.Exception e) {
                throw new OdiseeServerException("Cannot get Desktop", e);
            }
            // Query the XComponentLoader interface from the desktop
            xComponentLoader = queryInterface(XComponentLoader.class, desktop);
            // This instance was bootstrapped already
            wasBootstrappedAlready = true;
        } else {
            throw new OdiseeServerException("Given initial-object name unknown at server side");
        }
        */
    }

    public void setFaulted(final boolean faulted) {
        if (faulted) {
            try {
                close();
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
    }

    public void close() throws OdiseeServerException {
        enumerateComponents("close");
        // Cleanup all managed documents
        for (OfficeDocument officeDocument : managedDocuments) {
            try {
                closeDocument(officeDocument, true);
            } catch (OdiseeServerException e) {
                // ignore
            }
        }
        // Connection is dropped
        connected = false;
        // Dispose the bridge
        if (null != xBridge) {
            try {
                XComponent xBridgeXComponent = queryInterface(XComponent.class, xBridge);
                xBridgeXComponent.dispose();
            } catch (Exception e) {
                // ignore
            }
        }
        // Cleanup references
        desktop = null;
        xRemoteServiceManager = null;
        xComponentLoader = null;
        xBridge = null;
    }

    //</editor-fold>

    //<editor-fold desc="Getters...">

    private XDesktop getXDesktop() {
        assertInitialized();
        return queryInterface(XDesktop.class, desktop);
    }

    private void assertInitialized() {
        if (!initializationCompleted()) {
            throw new OdiseeServerRuntimeException("Not initialized");
        }
    }

    public XMultiComponentFactory getXRemoteServiceManager() {
        assertInitialized();
        return xRemoteServiceManager;
    }

    public XComponentLoader getXComponentLoader() {
        assertInitialized();
        return xComponentLoader;
    }

    //</editor-fold>

    //<editor-fold desc="Create documents">

    public OfficeDocument createDocument(OfficeDocumentType type) throws OdiseeServerException {
        OfficeDocument officeDocument = new OfficeDocument(this);
        /*XComponent xComponent = */officeDocument.newDocument(type);
        managedDocuments.add(officeDocument);
        return officeDocument;
    }

    private OfficeDocument createDocument(URL url) throws OdiseeServerException {
        OfficeDocument officeDocument = new OfficeDocument(this);
        /*XComponent xComponent = */officeDocument.newDocument(url);
        managedDocuments.add(officeDocument);
        return officeDocument;
    }

    public OfficeDocument createDocument(File file) throws OdiseeServerException {
        try {
            return createDocument(file.toURI().toURL());
        } catch (MalformedURLException e) {
            throw new OdiseeServerException("URL invalid", e);
        }
    }

    //</editor-fold>

    //<editor-fold desc="Save documents">

    public void saveDocument(OfficeDocument officeDocument, URL saveAsUrl) throws OdiseeServerException {
        officeDocument.saveAs(saveAsUrl);
    }

    public void saveAndCloseDocument(OfficeDocument officeDocument, URL saveAsUrl, boolean force) throws OdiseeServerException {
        officeDocument.saveAs(saveAsUrl);
        closeDocument(officeDocument, force);
    }

    //</editor-fold>

    //<editor-fold desc="Close documents">

    private void closeDocument(OfficeDocument officeDocument, boolean force) throws OdiseeServerException {
        officeDocument.closeDocument(force);
        managedDocuments.remove(officeDocument);
    }

    public void closeAllComponents() throws OdiseeServerException {
        XEnumerationAccess xEnumerationAccess = getXDesktop().getComponents();
        if (xEnumerationAccess.hasElements()) {
            XComponent xComponent = null;
            XEnumeration xEnumeration = xEnumerationAccess.createEnumeration();
            while (xEnumeration.hasMoreElements()) {
                try {
                    Any any = (Any) xEnumeration.nextElement();
                    xComponent = (XComponent) any.getObject();
                    queryInterface(XCloseable.class, xComponent).close(true);
                } catch (NoSuchElementException | WrappedTargetException e) {
                    // ignore
                } catch (CloseVetoException e) {
                    throw new OdiseeServerException("Component " + xComponent + "does not agree to close", e);
                }
            }
        }
    }

    //</editor-fold>

    private void enumerateComponents(final String action) {
        try {
            final XEnumerationAccess xEnumerationAccess = getXDesktop().getComponents();
            if (xEnumerationAccess.hasElements()) {
                XEnumeration xEnumeration = xEnumerationAccess.createEnumeration();
                while (xEnumeration.hasMoreElements()) {
                    debugLogXEnumerationComponent(action, xEnumeration);
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private void debugLogXEnumerationComponent(final String action, final XEnumeration xEnumeration) {
        try {
            final Object o = xEnumeration.nextElement();
            LOGGER.debug("[{}] {} found {}", unoURL, action, o);
        } catch (NoSuchElementException | WrappedTargetException e) {
            LOGGER.error("");
        }
    }

}
