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

import com.sun.star.beans.UnknownPropertyException
import com.sun.star.beans.XPropertySet
import com.sun.star.container.XEnumeration
import com.sun.star.container.XNameAccess
import com.sun.star.lang.XComponent
import com.sun.star.lang.XMultiServiceFactory
import com.sun.star.text.XDependentTextField
import com.sun.star.text.XTextFieldsSupplier
import com.sun.star.uno.Any
import com.sun.star.util.XRefreshable
import groovy.util.logging.Log
import org.odisee.debug.Profile
import org.odisee.uno.UnoCategory

/**
 * Category for working with Writer fields: userfields, set expressions.
 */
@Log
class OOoFieldCategory {

    /**
     * Get fully qualified userfield name.
     */
    static String toFqufn(String name) {
        def p = "com.sun.star.text.FieldMaster.User"
        if (!name.startsWith(p)) {
            "${p}.${name}" as String
        } else {
            name
        }
    }

    /**
     * Get fully qualified setexpression name.
     */
    static String toFqsen(String name) {
        def p = "com.sun.star.text.FieldMaster.SetExpression"
        if (!name.startsWith(p)) {
            "${p}.${name}" as String
        } else {
            name
        }
    }

    /**
     * Get TextFieldSupplier from OpenOffice document.
     */
    static XTextFieldsSupplier textFieldsSupplier(XComponent component) {
        Profile.time("OOoFieldCategory.textFieldsSupplier", {
            use(UnoCategory) {
                component.uno(XTextFieldsSupplier)
            }
        }) as XTextFieldsSupplier
    }

    /**
     * Does a certain userfield exist?
     */
    static Boolean hasUserField(XComponent component, String name) {
        Profile.time "OOoFieldCategory.hasUserField($name)", {
            name = toFqufn(name)
            // Get XTextFieldMasters and query for userfield
            XTextFieldsSupplier textFieldsSupplier = component.textFieldsSupplier()
            XNameAccess textFieldMasters = textFieldsSupplier.textFieldMasters
            textFieldMasters.hasByName(name) ?: false
        }
    }

    /**
     * Get reference to a userfield (prefix com.sun.star.text.FieldMaster.User)
     */
    static XPropertySet getUserField(XComponent component, String name) {
        use(OOoFieldCategory) {
            name = toFqufn(name)
            try {
                XTextFieldsSupplier textFieldsSupplier = component.textFieldsSupplier()
                XNameAccess textFieldMasters = textFieldsSupplier.textFieldMasters
                Any/*XPropertySet*/ any = textFieldMasters.getByName(name) ?: null
                return any ? (XPropertySet) any.object : null
            } catch (com.sun.star.container.NoSuchElementException e) {
                return null
            }
        }
    }

    /**
     * Get content of userfield (prefix com.sun.star.text.FieldMaster.User)
     */
    static String getUserFieldContent(XComponent component, String name) {
        name = toFqufn(name)
        use(UnoCategory) {
            XPropertySet userField = component.getUserField(name)
            userField.getPropertyValue("Content")
        }
    }

    /**
     * Create an userfield.
     */
    static void createUserField(XComponent component, String name) {
        Profile.time "OOoFieldCategory.createUserField($name)", {
            use(UnoCategory) {
                XMultiServiceFactory xMultiServiceFactory = component.uno(XMultiServiceFactory)
                // UserField
                def userField = xMultiServiceFactory.createInstance("com.sun.star.text.textfield.User")
                XDependentTextField xDependentTextField = userField.uno(XDependentTextField)
                // FieldMaster
                def fieldMaster = xMultiServiceFactory.createInstance("com.sun.star.text.fieldmaster.User")
                XPropertySet xFieldMasterPropertySet = fieldMaster.uno(XPropertySet)
                //
                xFieldMasterPropertySet.setPropertyValue("Name", name)
                xDependentTextField.attachTextFieldMaster(xFieldMasterPropertySet)
            }
        }
    }

    /**
     * Set content of an userfield.
     */
    static void setUserFieldContent(XComponent component, String name, Object content) {
        Profile.time "OOoFieldCategory.setUserFieldContent($name)", {
            use(UnoCategory) {
                try {
                    // Get userfield and set its content
                    XPropertySet uf = component.getUserField(name)
                    //println "Odisee: OOoFieldCategory.setUserFieldContent($name): uf=${uf} content='${content}'"
                    //uf.uno(com.sun.star.beans.XPropertySet)
                    uf?.setPropertyValue('Content', content ?: '' as String)
                    //println "Odisee: OOoFieldCategory.setUserFieldContent($name): content='${component.getUserFieldContent(name)}'"
                } catch (UnknownPropertyException upe) {
                    // If that failed with an UnknownPropertyException
                    log.warn "Unknown property 'Content' for userfield '${name}': ${upe.message}"
                    // TODO WHY THE HELL... maybe this was done to support SetExpressions???
                    def textFields = component.textFieldsSupplier().textFields
                    XEnumeration elts = textFields.createEnumeration()
                    XDependentTextField xDependentTextField
                    def field
                    XPropertySet tfm
                    XPropertySet fieldProperties
                    while (elts.hasMoreElements()) {
                        field = elts.nextElement()
                        xDependentTextField = (XDependentTextField) field.uno(XDependentTextField)
                        tfm = xDependentTextField.textFieldMaster
                        try {
                            if (tfm.getPropertyValue("Name") == name) {
                                fieldProperties = (XPropertySet) field.uno(XPropertySet)
                                if (fieldProperties.getPropertyValue("NumberingType") == "5001") {
                                    fieldProperties.setPropertyValue("Content", content)
                                    fieldProperties.setPropertyValue("Value", (content ?: 0.0d) as Double)
                                } else {
                                    fieldProperties.setPropertyValue("Content", content)
                                }
                                break
                            }
                        } catch (e) {
                            log.error "", e
                        }
                    }
                }
            }
        }
    }

    /**
     * Set content of many userfields.
     * @return Map key = userfield name, value = old value of userfield
     */
    static Map setUserFieldContent(XComponent component, Map nameContent) {
        def s = [:]
        nameContent.each { k, v ->
            // TODO Don't get old content
            //s[k] = component.setUserFieldContent(k, v)
            use(OOoFieldCategory) {
                component.setUserFieldContent(k, v)
            }
        }
        s
    }

    /**
     * Refresh all text fields.
     */
    static void refreshTextFields(XComponent component) {
        Profile.time "OOoFieldCategory.refreshTextFields", {
            use(UnoCategory) {
                //println "OOoFieldCategory.refreshTextFields: ${component}"
                def supplier = component.textFieldsSupplier()
                def textfields = supplier.textFields
                def xRefreshable = textfields.uno(XRefreshable)
                xRefreshable.refresh()
                /*
                // Get XTextFieldsSupplier interface
                XTextFieldsSupplier xTextFieldsSupplier = queryInterface(XTextFieldsSupplier.class, component);
                // Access the TextFieldMasters collections
                XEnumerationAccess xEnumeratedFields = xTextFieldsSupplier.getTextFields();
                // Afterwards we must refresh the textfields collection
                XRefreshable xRefreshable = queryInterface(XRefreshable.class, xEnumeratedFields);
                xRefreshable.refresh();
                */
            }
        }
    }

    /**
     * Get content of a userfield by using:
     * <pre>
     * use (OOoFieldCategory) {*     odt["field"]
     *}* </pre>
     */
    def static get(XComponent component, String name) {
        use(OOoFieldCategory) {
            component.getUserFieldContent(name)
        }
    }

    /**
     * Set content of a userfield by using:
     * <pre>
     * use (OOoFieldCategory) {*     odt["field"] = "new value"
     *}* </pre>
     */
    def static set(XComponent component, String name, Object value) {
        use(OOoFieldCategory) {
            component.setUserFieldContent(name, value)
        }
    }

}
