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

import com.sun.star.frame.*
import com.sun.star.lang.XComponent
import com.sun.star.script.provider.XScript
import com.sun.star.script.provider.XScriptProviderSupplier
import com.sun.star.util.CloseVetoException
import com.sun.star.util.XCloseable
import com.sun.star.util.XRefreshable
import org.odisee.api.OdiseeException
import org.odisee.io.OdiseePath
import org.odisee.debug.Profile
import org.odisee.ooo.connection.OfficeConnection
import org.odisee.uno.UnoCategory

import java.nio.file.Files
import java.nio.file.Path

/**
 * Things we can do with any OpenOffice.org document.
 */
class OOoDocumentCategory {

    /**
     * Standard properties for opening a document.
     */
    public static final Map STD_LOAD_PROP = [
            Hidden: Boolean.TRUE,
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
        //'file:///' + ((file.toURL() as String) - 'file:')
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
        // Add connection to template's File object
        file.metaClass._oooConnection = oooConnection
        file.metaClass.getOooConnection = {-> _oooConnection }
        // Properties for loading component
        List properties = []
        // Is this a template?
        String filename = file.fileName.toString()
        if (filename.length() > 3 && filename.toLowerCase()[-3..-1] ==~ TEMPLATE_EXT_REGEX) {
            // Check if template exists
            if (!Files.exists(file)) {
                throw new OdiseeException("Template ${file} not found")
            }
            // Set property for loading
            properties << makePropertyValue('AsTemplate', Boolean.TRUE)
        }
        // Copy given properties
        props?.each { k, v ->
            properties << makePropertyValue(k as String, v)
        }
        String sURL = null
        // If we got a regular file and no private: URL
        if (file ==~ PRIVATE_URL_REGEX) {
            // URL must be private:... for this
            sURL = file.toString()
        } else {
            // Does the file exist? If not, create an empty file
            if (!Files.exists(file)) {
                Files.createFile(file)
            }
            // Ensure correct URL to prevent "URL seems to be an unsupported one"
            sURL = getFileURL(file)
        }
        // Open document
        XComponentLoader xComponentLoader = oooConnection.getXComponentLoader()
        if (!xComponentLoader) {
            throw new OdiseeException('No XComponentLoader!')
        }
        XComponent xComponent = xComponentLoader.loadComponentFromURL(sURL, '_blank', 0, (properties ?: null) as com.sun.star.beans.PropertyValue[])
        xComponent.metaClass._oooConnection = oooConnection
        xComponent.metaClass.getOooConnection = {-> _oooConnection }
        xComponent.metaClass._file = file
        xComponent.metaClass.getFile = {-> _file }
        // Return component
        xComponent
    }

    /**
     * Save an OpenOffice document.
     * @param component com.sun.star.lang.XComponent
     */
    static save(XComponent component) {
        use(UnoCategory) {
            // Refresh component
            refresh(component)
            // Refresh text fields
            use(OOoFieldCategory) {
                component.refreshTextFields()
            }
            // Get XStorable and call store()
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
            // Properties for saving component
            List properties = []
            // Filter for saving?
            String ext = file.fileName.toString().toLowerCase()[-3..-1]
            if (!(ext ==~ /od[gpst]/)) {
                properties << makePropertyValue('FilterName', OdiseeFileFormat[ext].filter)
            }
            // Copy given properties
            props?.each { k, v ->
                properties << makePropertyValue(k, v)
            }
            /*
            // Refresh
            refresh(component)
            */
/*
            // Refresh text fields
            use(OOoFieldCategory) {
                component.refreshTextFields()
            }
*/
            // Save document
            String fileURL = getFileURL(file)
            try {
                XStorable xStorable = (XStorable) component.uno(XStorable)
                xStorable.storeToURL(fileURL, (properties ?: null) as com.sun.star.beans.PropertyValue[])
            } catch (e) {
                throw new OdiseeException("Could not save document at ${fileURL}", e)
            }
        }
    }

    /**
     * Save an OpenOffice.org document to an alternate URL or format.
     * @param component com.sun.star.lang.XComponent
     */
    static saveAsPDF_A(XComponent component, Path file) {
        List<com.sun.star.beans.PropertyValue> pdfFilterData = new LinkedList<>();

        // Specify that PDF related permissions of this file must be
        // restricted. It is meaningfull only if the “PermissionPassword”
        // property is not empty
        pdfFilterData << makePropertyValue("RestrictPermissions", Boolean.TRUE);

        // Set the password that a user will need to change the permissions
        // of the exported PDF. The password should be in clear text.
        // Must be used with the “RestrictPermissions” property
        pdfFilterData << makePropertyValue("PermissionPassword", "nopermission");

        // Specifies printing of the document:
        //   0: PDF document cannot be printed
        //   1: PDF document can be printed at low resolution only
        //   2: PDF document can be printed at maximum resolution.
        pdfFilterData << makePropertyValue("Printing", 2);

        // Specifies the changes allowed to the document:
        //   0: PDF document cannot be changed
        //   1: Inserting, deleting and rotating pages is allowed
        //   2: Filling of form field is allowed
        //   3: Filling of form field and commenting is allowed
        //   4: All the changes of the previous selections are permitted,
        //      with the only exclusion of page extraction
        pdfFilterData << makePropertyValue("Changes", 0);

        // Specifies that the pages and the PDF document content can be
        // extracted to be used in other documents: Copy from the PDF
        // document and paste eleswhere
        pdfFilterData << makePropertyValue("EnableCopyingOfContent", Boolean.FALSE);

        // Specifies that the PDF document content can be extracted to
        // be used in accessibility applications
        pdfFilterData << makePropertyValue("EnableTextAccessForAccessibilityTools", Boolean.FALSE);

        // Specifies if graphics are exported to PDF using a
        // lossless compression. If this property is set to true,
        // it overwrites the "Quality" property
        pdfFilterData << makePropertyValue("UseLosslessCompression", Boolean.TRUE);

        // Specifies whether form fields are exported as widgets or
        // only their fixed print representation is exported
        pdfFilterData << makePropertyValue("ExportFormFields", Boolean.FALSE);

        // Specifies the action to be performed when the PDF document
        // is opened:
        //   0: Opens with default zoom magnification
        //   1: Opens magnified to fit the entire page within the window
        //   2: Opens magnified to fit the entire page width within
        //      the window
        //   3: Opens magnified to fit the entire width of its boundig
        //      box within the window (cuts out margins)
        //   4: Opens with a zoom level given in the “Zoom” property
        pdfFilterData << makePropertyValue("Magnification", 1);

        // Specifies that automatically inserted empty pages are
        // suppressed. This option only applies for storing Writer
        // documents.
        pdfFilterData << makePropertyValue("IsSkipEmptyPages", Boolean.TRUE);

        // Specifies the PDF version that should be generated:
        //   0: PDF 1.4 (default selection)
        //   1: PDF/A-1 (ISO 19005-1:2005)
        pdfFilterData << makePropertyValue("SelectPdfVersion", 1);

        com.sun.star.beans.PropertyValue[] pfdArray = pdfFilterData.toArray(new com.sun.star.beans.PropertyValue[pdfFilterData.size()])

        List<com.sun.star.beans.PropertyValue> conversionProperties = new LinkedList<>();
        conversionProperties << makePropertyValue("FilterName", "writer_pdf_Export");
        conversionProperties << makePropertyValue("Overwrite", Boolean.TRUE);
        conversionProperties << makePropertyValue("FilterData", pfdArray);
        com.sun.star.beans.PropertyValue[] cpArray = conversionProperties.toArray(new com.sun.star.beans.PropertyValue[conversionProperties.size()])

        use(UnoCategory) {
            String fileURL = getFileURL(file)
            try {
                XStorable xStorable = (XStorable) component.uno(XStorable)
                xStorable.storeToURL(fileURL, cpArray)
            } catch (e) {
                throw new OdiseeException("Could not save document at ${fileURL}", e)
            }
        }
    }

    /**
     * Close a document.
     * http://wiki.services.openoffice.org/wiki/Documentation/DevGuide/OfficeDev/Closing_Documents
     */
    static close(XComponent component) {
        try {
            use(UnoCategory) {
                // Close through XModel
                XModel xModel = (XModel) component.uno(XModel)
                if (xModel) {
                    XCloseable xCloseable = (XCloseable) xModel.uno(XCloseable)
                    try {
                        xCloseable.close(true)
                    } catch (CloseVetoException e) {
                        // Ignore
                    }
                } else {
                    /*
                    XCloseable xCloseable = component.uno(com.sun.star.util.XCloseable)
                    xCloseable.close(true)
                    */
                    // Dispose document
                    component.dispose()
                }
            }
        } finally {
            // Remove references
            component.metaClass._oooConnection = null
            component.metaClass.getOooConnection = null
            component.metaClass._file = null
            component.metaClass.getFile = null
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
                // Get XMultiComponentFactory
                //OOoConnection oooConnection = (OOoConnection) component.oooConnection
                OfficeConnection oooConnection = (OfficeConnection) component.oooConnection
                if (oooConnection) {
                    Object o = oooConnection.xMultiComponentFactory.createInstanceWithContext('com.sun.star.frame.DispatchHelper', oooConnection.xOfficeComponentContext)
                    // XDispatchHelper
                    XDispatchHelper dispatchHelper = (XDispatchHelper) o.uno(XDispatchHelper)
                    com.sun.star.beans.PropertyValue[] props = params.collect { k, v ->
                        makePropertyValue(k, v)
                    } as com.sun.star.beans.PropertyValue[]
                    // XModel
                    XModel xModel = (XModel) component.uno(XModel)
                    // XController
                    XController xController = xModel.currentController //getCurrentController()
                    // XFrame
                    XFrame xFrame = xController.frame //getFrame()
                    // Call dispatcher
                    XDispatchProvider xDispatchProvider = (XDispatchProvider) xFrame.uno(XDispatchProvider)
                    dispatchHelper.executeDispatch(xDispatchProvider, name, '', 0, props)
                } else {
                    println "${this}.executeDispatch(${name}): No connection!"
                }
            }
        }
    }

    /**
     * Execute a macro.
     */
    static executeMacro(XComponent component, String macroName, Object[] params = []) {
        Profile.time "OOoDocumentCategory.executeMacro($macroName)", {
            def name = new StringBuilder()
            // No URL?
            if (!macroName.startsWith('vnd')) {
                name << 'vnd.sun.star.script:'
            }
            // Append macro name; no information about language and location? Append default.
            if (macroName ==~ MACRO_URL_W_OPT) {
                name << macroName
            } else {
                name << (macroName << '?language=Basic&location=document')
            }
            // Ensure a java.lang.String and replace HTML entities
            name = name.toString().replaceAll('&amp;', '&')
            if (OdiseePath.ODISEE_DEBUG) {
                println "Odisee: Processing macro '${name}'"
            }
            try {
                use(UnoCategory) {
                    XScriptProviderSupplier xScriptPS = (XScriptProviderSupplier) component.uno(XScriptProviderSupplier)
                    XScript xScript = xScriptPS.scriptProvider.getScript(name.toString())
                    short[][] outParamIndex = [TWO_ZERO, TWO_ZERO] as short[][]
                    Object[][] outParam = [TWO_NULL, TWO_NULL]/* as Object[][]*/
                    xScript.invoke(params/* as Object[]*/, outParamIndex, outParam)
                }
            } catch (e) {
                println "Odisee: Cannot execute macro: ${name}: ${e}"
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
