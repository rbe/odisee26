/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 17.03.17, 08:04
 */

package org.odisee.document;

import com.sun.star.beans.PropertyValue;
import com.sun.star.beans.PropertyVetoException;
import com.sun.star.container.XEnumerationAccess;
import com.sun.star.frame.XController;
import com.sun.star.frame.XFrame;
import com.sun.star.frame.XModel;
import com.sun.star.frame.XStorable;
import com.sun.star.lang.XComponent;
import com.sun.star.text.XTextFieldsSupplier;
import com.sun.star.util.CloseVetoException;
import com.sun.star.util.XCloseable;
import com.sun.star.util.XModifiable;
import com.sun.star.util.XRefreshable;
import org.odisee.ooo.connection.OdiseeServerException;
import org.odisee.ooo.connection.OdiseeServerRuntimeException;
import org.odisee.ooo.connection.OfficeConnection;
import org.odisee.shared.OdiseeConstant;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.sun.star.uno.UnoRuntime.queryInterface;
import static org.odisee.uno.UnoHelper.makePropertyValue;

public class OfficeDocument {

    /**
     * The connection to use for dealing with the document.
     */
    private final OfficeConnection officeConnection;

    /**
     * Type of this document.
     */
    private OfficeDocumentType officeDocumentType;

    /**
     * The document.
     */
    private XComponent xComponent;

    /**
     * Constructor, checks state of connection and throws {@link OdiseeServerRuntimeException} when connection is not initialized.
     * @param officeConnection A previously created connection to an running Office.
     */
    public OfficeDocument(final OfficeConnection officeConnection) {
        checkState(officeConnection);
        this.officeConnection = officeConnection;
    }

    // TODO Do not check in every method of this class
    private void checkState(final OfficeConnection officeConnection) {
        if (!officeConnection.initializationCompleted()) {
            throw new OdiseeServerRuntimeException("Not initialized");
        }
    }

    /**
     * Get type of this document.
     * @return OfficeDocumentType
     */
    public OfficeDocumentType getOfficeDocumentType() {
        return officeDocumentType;
    }

    /**
     * Create a new document by type.
     * @param officeDocumentType Type of document.
     * @return A newly created XComponent.
     * @throws OdiseeServerException
     */
    public XComponent newDocument(final OfficeDocumentType officeDocumentType) throws OdiseeServerException {
        final String loadUrl = String.format("private:factory/%s", officeDocumentType.getInternalType());
        final PropertyValue[] loadProps = new PropertyValue[0];
        try {
            xComponent = officeConnection.getXComponentLoader().loadComponentFromURL(loadUrl, OdiseeConstant.BLANK, 0, loadProps);
            this.officeDocumentType = officeDocumentType;
            return xComponent;
        } catch (com.sun.star.io.IOException | com.sun.star.lang.IllegalArgumentException e) {
            throw new OdiseeServerException(e);
        }
    }

    /**
     * Create a new document (from a template) by opening an existing one: document or a template.
     * To create an empty document use {@link #newDocument(OfficeDocumentType)}.
     * @param url URL to create the new document from.
     * @return A newly created XComponent.
     * @throws OdiseeServerException
     */
    public XComponent newDocument(final URL url) throws OdiseeServerException {
        final List<PropertyValue> loadProps = makeTemplateLoadProperties(url);
        // Load template
        try {
            final String templateUrl = makeTemplateUrl(url);
            // Open template
            final PropertyValue[] propertyValues = loadProps.toArray(new PropertyValue[loadProps.size()]);
            xComponent = officeConnection.getXComponentLoader().loadComponentFromURL(templateUrl, OdiseeConstant.BLANK, 0, propertyValues);
            // Remember document type
            final int len = templateUrl.length();
            this.officeDocumentType = OfficeDocumentType.find(templateUrl.substring(len - 3));
            return xComponent;
        } catch (URISyntaxException | java.io.IOException | com.sun.star.io.IOException | com.sun.star.lang.IllegalArgumentException e) {
            throw new OdiseeServerException(e);
        }
    }

    private List<PropertyValue> makeTemplateLoadProperties(final URL url) {
        // Define load properties according to com.sun.star.document.MediaDescriptor
        // The boolean property AsTemplate tells the office to create a new document from the given file
        final List<PropertyValue> loadProps = new ArrayList<>();
        // Open as template?
        if (url.toString().matches(OdiseeConstant.NATIVE_TEMPLATE_REGEX)) {
            loadProps.add(makePropertyValue("AsTemplate", Boolean.TRUE));
        }
        // TODO ODISEE_DEBUG
        loadProps.add(makePropertyValue("Hidden", Boolean.TRUE));
        // Always execute macros w/o warning
        loadProps.add(makePropertyValue("MacroExecutionMode", com.sun.star.document.MacroExecMode.ALWAYS_EXECUTE_NO_WARN));
        return loadProps;
    }

    private String makeTemplateUrl(final URL url) throws URISyntaxException, IOException {
        final StringBuilder templateUrl = new StringBuilder();
        if (url.getProtocol().equals("file")) {
            final File file = new File(url.toURI());
            templateUrl.append("file:///").append(file.getCanonicalPath().replace('\\', '/'));
        } else {
            templateUrl.append(url.toString());
        }
        return templateUrl.toString();
    }

    /**
     * Convenience method for {@link #newDocument(java.net.URL)} using a File reference.
     * @param file File object.
     * @return A document.
     * @throws OdiseeServerException
     */
    public XComponent newDocument(final File file) throws OdiseeServerException {
        try {
            return newDocument(file.toURI().toURL());
        } catch (MalformedURLException e) {
            throw new OdiseeServerException("Cannot create document from template due to malformed URL", e);
        }
    }

    public XComponent getXComponent() throws OdiseeServerException {
        return xComponent;
    }

    public XController getXController() throws OdiseeServerException {
        final XController xController = queryInterface(XController.class, xComponent);
        if (null == xController) {
            throw new OdiseeServerException("No XController available");
        }
        return xController;
    }

    private XFrame getXFrame() throws OdiseeServerException {
        XFrame xFrame = null;
        final XController xController = queryInterface(XController.class, xComponent);
        if (null != xController) {
            if (xController.suspend(true)) {
                xFrame = xController.getFrame();
                if (null == xFrame) {
                    throw new OdiseeServerException("There is no XFrame");
                }
            }
        }
        return xFrame;
    }

    public void refreshAll() throws OdiseeServerException {
        refreshTextFields();
        refreshDocument();
    }

    private void refreshTextFields() throws OdiseeServerException {
        final XTextFieldsSupplier xTextFieldsSupplier = queryInterface(XTextFieldsSupplier.class, xComponent);
        final XEnumerationAccess xEnumeratedFields = xTextFieldsSupplier.getTextFields();
        final XRefreshable xRefreshable = queryInterface(XRefreshable.class, xEnumeratedFields);
        xRefreshable.refresh();
    }

    private void refreshDocument() throws OdiseeServerException {
        final XRefreshable xRefresh = queryInterface(XRefreshable.class, xComponent);
        xRefresh.refresh();
    }

    public void save() throws OdiseeServerException {
        try {
            final XStorable xStorable = queryInterface(XStorable.class, xComponent);
            xStorable.store();
        } catch (com.sun.star.io.IOException e) {
            throw new OdiseeServerException("Cannot save document", e);
        }
    }

    public void saveAs(final URL url) throws OdiseeServerException {
        final StringBuilder builder = new StringBuilder();
        if (url.getProtocol().equals("file")) {
            try {
                final File file = new File(url.toURI());
                final String fileWoBackslashes = file.getCanonicalPath().replace('\\', '/');
                builder.append("file:///").append(fileWoBackslashes);
            } catch (URISyntaxException | java.io.IOException e) {
                throw new OdiseeServerException("Cannot convert file: URL to save document as " + url.toString(), e);
            }
        } else {
            builder.append(url.toString());
        }
        final String saveAsUrl = builder.toString();
        final List<PropertyValue> saveProps = new ArrayList<>();
        // When not saving in native format apply filter, e.g. export as PDF
        if (!saveAsUrl.matches(OdiseeConstant.NATIVE_DOCUMENT_REGEX)) {
            saveProps.add(makePropertyValue("FilterName", officeDocumentType.getPdfExportFilter()));
        }
        // Save as
        try {
            final XStorable xStorable = queryInterface(XStorable.class, xComponent);
            final PropertyValue[] propertyValues = saveProps.toArray(new PropertyValue[saveProps.size()]);
            xStorable.storeToURL(saveAsUrl, propertyValues);
        } catch (com.sun.star.io.IOException e) {
            throw new OdiseeServerException("Cannot save document as " + saveAsUrl, e);
        }
    }

    private boolean setModified(final boolean modified) throws OdiseeServerException {
        boolean bModified = false;
        // Check supported functionality of the document (model or controller).
        final XModel xModel = queryInterface(XModel.class, xComponent);
        if (null != xModel) {
            final XModifiable xModify = queryInterface(XModifiable.class, xModel);
            try {
                xModify.setModified(modified);
            } catch (PropertyVetoException e) {
                // Can be thrown by "setModified()" call on model. It disagree with our request.
                // But there is nothing to do then. Following "dispose()" call wasn't never called (because we catch it before).
                throw new OdiseeServerException("Document disagreed to be " + (modified ? "" : "un") + "modified", e);
            }
            bModified = true;
        }
        return bModified;
    }

    /**
     * Close this document.
     * @param force Should we close the document even when having unsaved changes?
     * @return Boolean to indicate closing was successful.
     * @throws OdiseeServerException
     */
    public boolean closeDocument(boolean force) throws OdiseeServerException {
        boolean bClosed = false;
        try {
            // Set modified flag in case we should forcibly close the document
            if (force) {
                setModified(false);
            }
            //
            // Try 1: Close document though XModel/XCloseable
            // Get XModel
            final XModel xModel = queryInterface(XModel.class, xComponent);
            if (null != xModel) {
                final XCloseable xCloseable = queryInterface(XCloseable.class, xModel);
                try {
                    xCloseable.close(true);
                    // Calling xModel.dispose(); results in com.sun.star.lang.DisposedException
                    bClosed = true;
                } catch (CloseVetoException e) {
                    bClosed = false;
                }
            }
            //
            if (!bClosed) {
                // Try 2: Close document though XFrame/XCloseable
                // Get XFrame
                final XFrame xFrame = getXFrame();
                if (null != xFrame) {
                    // First try the new way: use new interface XCloseable
                    // It replaced the deprecated XTask::close() and should be preferred ... if it can be queried.
                    final XCloseable xCloseable = queryInterface(XCloseable.class, xFrame);
                    if (xCloseable != null) {
                        // We deliver the owner ship of this frame not to the (possible) source which throw a CloseVetoException.
                        // We whishto have it under our own control.
                        try {
                            xCloseable.close(false);
                            bClosed = true;
                        } catch (CloseVetoException e) {
                            bClosed = false;
                        }
                    }
                }
            }
            //
            if (!bClosed) {
                // Try 3: Close document though XController/XCloseable
                try {
                    // It's a document which supports a controller .. or may by a pure window only.
                    // If it's at least a controller - we can try to suspend it. But - it can disagree with that!
                    final XController xController = queryInterface(XController.class, xComponent);
                    if (xController != null) {
                        if (xController.suspend(true)) {
                            // Note: Don't dispose the controller - destroy the frame to make it right!
                            // Get XFrame
                            final XFrame xFrame = getXFrame();
                            if (null != xFrame) {
                                xFrame.dispose();
                                bClosed = true;
                            }
                        }
                    }
                } catch (com.sun.star.uno.RuntimeException e) {
                    // DisposedException
                    // If an UNO object was already disposed before - he throw this special runtime exception.
                    // Of course every UNO call must be look for that - but it's a question of error handling.
                    // For demonstration this exception is handled here.
                    // RuntimeException
                    // Every UNO call can throw that.
                    // Do nothing - closing failed - that's it.
                    throw new OdiseeServerException(e);
                }
            }
        } catch (com.sun.star.uno.RuntimeException e) {
            throw new OdiseeServerException("Could not close document", e);
        }
        // Return
        return bClosed;
    }

}
