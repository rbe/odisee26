/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 02.02.15 18:35
 */
package org.odisee.writer

import com.sun.star.beans.XPropertySet
import com.sun.star.drawing.XDrawPagesSupplier
import com.sun.star.frame.*
import com.sun.star.lang.XComponent
import com.sun.star.lang.XMultiComponentFactory
import com.sun.star.presentation.XPresentationSupplier
import com.sun.star.script.provider.XScript
import com.sun.star.script.provider.XScriptProvider
import com.sun.star.script.provider.XScriptProviderSupplier
import com.sun.star.sheet.XSpreadsheetDocument
import com.sun.star.text.XTextDocument
import com.sun.star.uno.Any
import com.sun.star.uno.XComponentContext
import com.sun.star.util.CloseVetoException
import com.sun.star.util.XCloseable
import com.sun.star.util.XRefreshable
import groovy.util.logging.Log
import org.odisee.api.OdiseeException
import org.odisee.debug.Profile
import org.odisee.document.OfficeDocumentType
import org.odisee.io.OdiseePath
import org.odisee.ooo.connection.OfficeConnection
import org.odisee.uno.UnoCategory

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

/**
 * Things we can do with any OpenOffice.org document.
 */
@Log
class OOoDocumentCategory {

    private static final ReferenceQueue<XComponent> COMPONENT_QUEUE = new ReferenceQueue<>()

    private static final ConcurrentHashMap<ComponentKey, ComponentState> COMPONENT_STATE = new ConcurrentHashMap<>()

    private static final class ComponentKey extends WeakReference<XComponent> {
        private final int hash

        ComponentKey(XComponent referent) {
            super(referent)
            this.hash = System.identityHashCode(referent)
        }

        ComponentKey(XComponent referent, ReferenceQueue<XComponent> queue) {
            super(referent, queue)
            this.hash = System.identityHashCode(referent)
        }

        @Override
        int hashCode() {
            hash
        }

        @Override
        boolean equals(Object other) {
            if (this.is(other)) {
                return true
            }
            if (!(other instanceof ComponentKey)) {
                return false
            }
            XComponent left = this.get()
            XComponent right = ((ComponentKey) other).get()
            left != null && left.is(right)
        }
    }

    private static final class ComponentState {
        final OfficeConnection connection
        final Path file

        ComponentState(OfficeConnection connection, Path file) {
            this.connection = connection
            this.file = file
        }
    }

    private static void expungeComponentState() {
        ComponentKey expired
        while ((expired = (ComponentKey) COMPONENT_QUEUE.poll()) != null) {
            COMPONENT_STATE.remove(expired)
        }
    }

    static void bindConnection(XComponent component, OfficeConnection connection, Path file = null) {
        expungeComponentState()
        if (component != null && connection != null) {
            COMPONENT_STATE.put(new ComponentKey(component, COMPONENT_QUEUE), new ComponentState(connection, file))
        }
    }

    static void unbindConnection(XComponent component) {
        expungeComponentState()
        if (component != null) {
            COMPONENT_STATE.remove(new ComponentKey(component))
        }
    }

    static OfficeConnection getOooConnection(XComponent component) {
        expungeComponentState()
        component == null ? null : COMPONENT_STATE.get(new ComponentKey(component))?.connection
    }

    static Path getFile(XComponent component) {
        expungeComponentState()
        component == null ? null : COMPONENT_STATE.get(new ComponentKey(component))?.file
    }

    static int trackedComponentCount() {
        expungeComponentState()
        COMPONENT_STATE.size()
    }

    static XMultiComponentFactory remoteServiceManager(OfficeConnection connection) {
        connection?.getXRemoteServiceManager()
    }

    static XComponentContext remoteComponentContext(OfficeConnection connection) {
        XMultiComponentFactory serviceManager = remoteServiceManager(connection)
        if (serviceManager == null) {
            return null
        }
        use(UnoCategory) {
            XPropertySet props = (XPropertySet) serviceManager.uno(XPropertySet)
            if (props == null) {
                return null
            }
            Object raw = props.getPropertyValue('DefaultContext')
            if (raw instanceof Any) {
                raw = ((Any) raw).object
            }
            if (raw instanceof XComponentContext) {
                return (XComponentContext) raw
            }
            raw == null ? null : (XComponentContext) raw.uno(XComponentContext)
        }
    }

    static OfficeDocumentType applicationOf(XComponent component, Path fallbackFile = null) {
        if (component != null) {
            OfficeDocumentType detected = use(UnoCategory) {
                if (component.uno(XSpreadsheetDocument)) {
                    return OfficeDocumentType.SPREADSHEET
                }
                if (component.uno(XPresentationSupplier)) {
                    return OfficeDocumentType.PRESENTATION
                }
                if (component.uno(XTextDocument)) {
                    return OfficeDocumentType.TEXT
                }
                if (component.uno(XDrawPagesSupplier)) {
                    return OfficeDocumentType.PRESENTATION
                }
                return null
            }
            if (detected != null) {
                return detected
            }
        }
        Path known = fallbackFile ?: getFile(component)
        OfficeDocumentType.fromFileName(known?.fileName?.toString())
    }

    /**
     * Standard properties for opening a document.
     */
    public static final Map STD_LOAD_PROP = [
            Hidden            : Boolean.TRUE,
            MacroExecutionMode: 'com.sun.star.document.MacroExecMode.ALWAYS_EXECUTE_NO_WARN'
    ]

    /**
     * Standard properties for saving a document.
     */
    public static final Map STD_SAVE_PROP = [
            Overwrite: Boolean.TRUE
    ]

    private static final String PRIVATE_URL_REGEX = /private.*/

    private static final String TEMPLATE_EXT_REGEX = /ot[gpst]/

    private static final List<Integer> TWO_ZERO = [0, 0]

    private static final List TWO_NULL = [null, null]

    /**
     * Macro URL with vnd.sun.star.script:xxx?opt=value
     */
    private static final String MACRO_URL_W_OPT = /.*\?.*/

    /**
     * Ensure proper file URL.
     */
    static String getFileURL(Path file) {
        file.toUri().toString()
    }

    /**
     * Make a PropertyValue object.
     */
    static com.sun.star.beans.PropertyValue makePropertyValue(Object name, Object value) {
        new com.sun.star.beans.PropertyValue(Name: name as String, Value: value)
    }

    /**
     * Open an OpenOffice.org document.
     */
    static XComponent open(Path file, OfficeConnection oooConnection, props = null) {
        List properties = []
        String filename = file.fileName.toString()
        if (filename.length() > 3 && filename.toLowerCase(Locale.ROOT)[-3..-1] ==~ TEMPLATE_EXT_REGEX) {
            if (!Files.exists(file)) {
                throw new OdiseeException("Template ${file} not found", OdiseeException.NOT_FOUND)
            }
            properties << makePropertyValue('AsTemplate', Boolean.TRUE)
        }
        props?.each { k, v ->
            properties << makePropertyValue(k as String, v)
        }
        String sURL = null
        if (file.toString() ==~ PRIVATE_URL_REGEX) {
            sURL = file.toString()
        } else {
            if (!Files.exists(file)) {
                Files.createFile(file)
            }
            sURL = getFileURL(file)
        }
        XComponentLoader xComponentLoader = oooConnection?.getXComponentLoader()
        if (!xComponentLoader) {
            throw new OdiseeException('No XComponentLoader!', OdiseeException.UNPROCESSABLE)
        }
        XComponent xComponent = xComponentLoader.loadComponentFromURL(sURL, '_blank', 0, (properties ?: null) as com.sun.star.beans.PropertyValue[])
        bindConnection(xComponent, oooConnection, file)
        xComponent
    }

    /**
     * Save an OpenOffice document.
     * @param component com.sun.star.lang.XComponent
     */
    static save(XComponent component) {
        use(UnoCategory) {
            refresh(component)
            use(OOoFieldCategory) {
                component.refreshTextFields()
            }
            XStorable xStorable = (XStorable) component.uno(XStorable)
            xStorable.store()
        }
    }

    /**
     * Save an OpenOffice.org document to an alternate URL or format.
     * @param component com.sun.star.lang.XComponent
     */
    static saveAs(XComponent component, Path file, props = null) {
        use(UnoCategory) {
            List properties = []
            String ext = OdiseeFileFormat.extensionOf(file.fileName.toString())
            if (!(ext ==~ /od[gpst]/)) {
                OfficeDocumentType application = applicationOf(component)
                Map format = OdiseeFileFormat.get(ext, application)
                if (format == null) {
                    throw new OdiseeException("Unsupported output format '${ext}'", OdiseeException.UNPROCESSABLE)
                }
                properties << makePropertyValue('FilterName', format.filter)
            }
            props?.each { k, v ->
                properties << makePropertyValue(k, v)
            }
            String fileURL = getFileURL(file)
            try {
                XStorable xStorable = (XStorable) component.uno(XStorable)
                if (xStorable == null) {
                    throw new OdiseeException("Could not save document at ${fileURL}: component is not storable", OdiseeException.UNPROCESSABLE)
                }
                xStorable.storeToURL(fileURL, (properties ?: null) as com.sun.star.beans.PropertyValue[])
            } catch (OdiseeException e) {
                throw e
            } catch (e) {
                OdiseeException ex = new OdiseeException("Could not save document at ${fileURL}", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
        }
    }

    /**
     * Save an OpenOffice.org document as ISO 19005-1 PDF/A-1 using the application's PDF export filter.
     * Encryption/permission passwords are omitted because PDF/A forbids an Encrypt trailer dictionary.
     */
    static saveAsPDF_A(XComponent component, Path file, String filterName = null) {
        OfficeDocumentType application = applicationOf(component)
        String resolvedFilter = filterName ?: (OdiseeFileFormat.get('pdf', application)?.filter ?: application.pdfExportFilter)
        List<com.sun.star.beans.PropertyValue> pdfFilterData = new LinkedList<>()
        pdfFilterData << makePropertyValue('SelectPdfVersion', 1)
        pdfFilterData << makePropertyValue('UseTaggedPDF', Boolean.TRUE)
        pdfFilterData << makePropertyValue('UseLosslessCompression', Boolean.TRUE)
        pdfFilterData << makePropertyValue('ExportFormFields', Boolean.FALSE)
        if (resolvedFilter == 'writer_pdf_Export') {
            pdfFilterData << makePropertyValue('IsSkipEmptyPages', Boolean.TRUE)
        }

        com.sun.star.beans.PropertyValue[] pfdArray = pdfFilterData.toArray(new com.sun.star.beans.PropertyValue[pdfFilterData.size()])

        List<com.sun.star.beans.PropertyValue> conversionProperties = new LinkedList<>()
        conversionProperties << makePropertyValue('FilterName', resolvedFilter)
        conversionProperties << makePropertyValue('Overwrite', Boolean.TRUE)
        conversionProperties << makePropertyValue('FilterData', pfdArray)
        com.sun.star.beans.PropertyValue[] cpArray = conversionProperties.toArray(new com.sun.star.beans.PropertyValue[conversionProperties.size()])

        use(UnoCategory) {
            String fileURL = getFileURL(file)
            try {
                XStorable xStorable = (XStorable) component.uno(XStorable)
                if (xStorable == null) {
                    throw new OdiseeException("Could not save document at ${fileURL}: component is not storable", OdiseeException.UNPROCESSABLE)
                }
                xStorable.storeToURL(fileURL, cpArray)
            } catch (OdiseeException e) {
                throw e
            } catch (e) {
                OdiseeException ex = new OdiseeException("Could not save document at ${fileURL}", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
        }
    }

    /**
     * Save as PDF with the application's export filter and the request's filter data.
     */
    static saveAsPdf(XComponent component, Path file, String filterName, Map<String, Object> filterData) {
        List<com.sun.star.beans.PropertyValue> pdfFilterData = new LinkedList<>()
        filterData?.each { String key, Object value ->
            pdfFilterData << makePropertyValue(key, value)
        }
        com.sun.star.beans.PropertyValue[] filterArray = pdfFilterData.toArray(new com.sun.star.beans.PropertyValue[pdfFilterData.size()])
        List<com.sun.star.beans.PropertyValue> conversionProperties = new LinkedList<>()
        String resolvedFilter = filterName ?: (OdiseeFileFormat.get('pdf', applicationOf(component))?.filter ?: 'writer_pdf_Export')
        conversionProperties << makePropertyValue('FilterName', resolvedFilter)
        conversionProperties << makePropertyValue('Overwrite', Boolean.TRUE)
        conversionProperties << makePropertyValue('FilterData', filterArray)
        com.sun.star.beans.PropertyValue[] properties = conversionProperties.toArray(new com.sun.star.beans.PropertyValue[conversionProperties.size()])
        use(UnoCategory) {
            String fileURL = getFileURL(file)
            try {
                XStorable xStorable = (XStorable) component.uno(XStorable)
                if (xStorable == null) {
                    throw new OdiseeException("Could not save document at ${fileURL}: component is not storable", OdiseeException.UNPROCESSABLE)
                }
                xStorable.storeToURL(fileURL, properties)
            } catch (OdiseeException e) {
                throw e
            } catch (e) {
                OdiseeException ex = new OdiseeException("Could not save document at ${fileURL}", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
        }
    }

    /**
     * Close a document.
     * http://wiki.services.openoffice.org/wiki/Documentation/DevGuide/OfficeDev/Closing_Documents
     */
    static close(XComponent component) {
        try {
            if (component != null) {
                use(UnoCategory) {
                    XModel xModel = (XModel) component.uno(XModel)
                    XCloseable xCloseable = xModel == null ? (XCloseable) component.uno(XCloseable) : (XCloseable) xModel.uno(XCloseable)
                    if (xCloseable != null) {
                        try {
                            xCloseable.close(true)
                        } catch (CloseVetoException e) {
                            // Ignore
                        }
                    } else {
                        component.dispose()
                    }
                }
            }
        } finally {
            unbindConnection(component)
        }
    }

    /**
     * Refresh a document.
     */
    static refresh(XComponent component) {
        Profile.time 'OOoDocumentCategory.refresh', {
            use(UnoCategory) {
                XRefreshable xRefreshable = (XRefreshable) component.uno(XRefreshable)
                xRefreshable.refresh()
            }
        }
    }

    /**
     * Execute a dispatch.
     */
    static executeDispatch(XComponent component, String name, Map params = null) {
        Profile.time "OOoDocumentCategory.executeDispatch($name)", {
            use(UnoCategory) {
                OfficeConnection oooConnection = getOooConnection(component)
                if (!oooConnection) {
                    throw new OdiseeException("Cannot execute dispatch '${name}': no office connection", OdiseeException.UNPROCESSABLE)
                }
                XMultiComponentFactory serviceManager = remoteServiceManager(oooConnection)
                XComponentContext componentContext = remoteComponentContext(oooConnection)
                if (!serviceManager || !componentContext) {
                    throw new OdiseeException("Cannot execute dispatch '${name}': office context is not available", OdiseeException.UNPROCESSABLE)
                }
                Object o = serviceManager.createInstanceWithContext('com.sun.star.frame.DispatchHelper', componentContext)
                XDispatchHelper dispatchHelper = o == null ? null : (XDispatchHelper) o.uno(XDispatchHelper)
                if (dispatchHelper == null) {
                    throw new OdiseeException("Cannot execute dispatch '${name}': DispatchHelper is not available", OdiseeException.UNPROCESSABLE)
                }
                com.sun.star.beans.PropertyValue[] props = (params ?: [:]).collect { k, v ->
                    makePropertyValue(k, v)
                } as com.sun.star.beans.PropertyValue[]
                XModel xModel = (XModel) component.uno(XModel)
                XController xController = xModel?.currentController
                XFrame xFrame = xController?.frame
                XDispatchProvider xDispatchProvider = xFrame == null ? null : (XDispatchProvider) xFrame.uno(XDispatchProvider)
                if (xDispatchProvider == null) {
                    throw new OdiseeException("Cannot execute dispatch '${name}': document frame is not available", OdiseeException.UNPROCESSABLE)
                }
                dispatchHelper.executeDispatch(xDispatchProvider, name, '', 0, props)
            }
        }
    }

    /**
     * Execute a macro.
     */
    static executeMacro(XComponent component, String macroName, Object[] params = []) {
        Profile.time "OOoDocumentCategory.executeMacro($macroName)", {
            StringBuilder builder = new StringBuilder()
            if (!macroName.startsWith('vnd')) {
                builder << 'vnd.sun.star.script:'
            }
            builder << macroName
            if (!(macroName ==~ MACRO_URL_W_OPT)) {
                builder << '?language=Basic&location=document'
            }
            String name = builder.toString().replaceAll('&amp;', '&')
            if (OdiseePath.ODISEE_DEBUG) {
                log.fine "Processing macro '${name}'"
            }
            try {
                use(UnoCategory) {
                    XScriptProviderSupplier xScriptPS = component == null ? null : (XScriptProviderSupplier) component.uno(XScriptProviderSupplier)
                    XScriptProvider provider = xScriptPS?.scriptProvider
                    if (provider == null) {
                        throw new OdiseeException("Cannot execute macro '${name}': script provider is not available", OdiseeException.UNPROCESSABLE)
                    }
                    XScript xScript = provider.getScript(name)
                    if (xScript == null) {
                        throw new OdiseeException("Macro '${name}' was not found", OdiseeException.UNPROCESSABLE)
                    }
                    short[][] outParamIndex = [TWO_ZERO, TWO_ZERO] as short[][]
                    Object[][] outParam = [TWO_NULL, TWO_NULL]
                    xScript.invoke((params ?: [] as Object[]), outParamIndex, outParam)
                }
            } catch (OdiseeException e) {
                throw e
            } catch (Exception e) {
                OdiseeException ex = new OdiseeException("Cannot execute macro '${name}': ${e.message ?: e.class.simpleName}", e)
                ex.httpStatus = OdiseeException.UNPROCESSABLE
                throw ex
            }
        }
    }

    /**
     * Execute an Odisee macro. Odisee must be installed as a shared library.
     * @param macroName Just name of Odisee Basic module and Sub/Function name. e.g. UNO.isWriter().
     */
    static executeOdiseeMacro(XComponent component, String macroName, Object[] params) {
        executeMacro(component, "Odisee.${macroName}?language=Basic&location=application", params)
    }

}
