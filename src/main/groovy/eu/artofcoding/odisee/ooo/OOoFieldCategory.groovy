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
package eu.artofcoding.odisee.ooo

import eu.artofcoding.odisee.helper.Profile

/**
 * Category for working with Writer fields: userfields, set expressions.
 */
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
    static com.sun.star.text.XTextFieldsSupplier textFieldsSupplier(com.sun.star.lang.XComponent component) {
        Profile.time "OOoFieldCategory.textFieldsSupplier", {
            use(UnoCategory) {
                component.uno(com.sun.star.text.XTextFieldsSupplier)
            }
        }
    }

    /**
     * Does a certain userfield exist?
     */
    static Boolean hasUserField(com.sun.star.lang.XComponent component, String name) {
        Profile.time "OOoFieldCategory.hasUserField($name)", {
            name = toFqufn(name)
            // Get XTextFieldMasters and query for userfield
            component.textFieldsSupplier()?.textFieldMasters?.hasByName(name) ?: false
        }
    }

    /**
     * Get reference to a userfield (prefix com.sun.star.text.FieldMaster.User)
     */
    static com.sun.star.beans.XPropertySet getUserField(com.sun.star.lang.XComponent component, String name) {
        name = toFqufn(name)
        // Get userfield from XTextFieldMasters
        // com.sun.star.uno.Any
        def any
        try {
            any = component.textFieldsSupplier().textFieldMasters.getByName(name) ?: null
            return any ? (com.sun.star.beans.XPropertySet) any.object : null
        } catch (com.sun.star.container.NoSuchElementException e) {
            return null
        }
    }

    /**
     * Get content of userfield (prefix com.sun.star.text.FieldMaster.User)
     */
    static String getUserFieldContent(com.sun.star.lang.XComponent component, String name) {
        name = toFqufn(name)
        use(UnoCategory) {
            component.getUserField(name)?.getPropertyValue("Content")
        }
    }

    /**
     * Create an userfield.
     */
    static void createUserField(com.sun.star.lang.XComponent component, String name) {
        Profile.time "OOoFieldCategory.createUserField($name)", {
            use(UnoCategory) {
                def xMultiServiceFactory = component.uno(com.sun.star.lang.XMultiServiceFactory)
                // UserField
                def userField = xMultiServiceFactory.createInstance("com.sun.star.text.textfield.User")
                def xDependentTextField = userField.uno(com.sun.star.text.XDependentTextField)
                // FieldMaster
                def fieldMaster = xMultiServiceFactory.createInstance("com.sun.star.text.fieldmaster.User")
                def xFieldMasterPropertySet = fieldMaster.uno(com.sun.star.beans.XPropertySet)
                //
                xFieldMasterPropertySet.setPropertyValue("Name", name)
                xDependentTextField.attachTextFieldMaster(xFieldMasterPropertySet)
            }
        }
    }

    /**
     * Set content of an userfield.
     */
    static void setUserFieldContent(com.sun.star.lang.XComponent component, String name, Object content) {
        Profile.time "OOoFieldCategory.setUserFieldContent($name)", {
            // If userfield does not exist, create it
            // otherwise save old content
            // TODO Don't get old content
            // def oldContent = null
            /* TODO Don't automatically create userfield
               if (!component.hasUserField(name)) {
                   component.createUserField(name)
               }
               */
            /*
               // TODO Don't get old content
               else {
                   oldContent = component.getUserFieldContent(name)
               }
               */
            use(UnoCategory) {
                try {
                    // Get userfield and set its content
                    def uf = component.getUserField(name)
                    //println "Odisee: OOoFieldCategory.setUserFieldContent($name): uf=${uf} content='${content}'"
                    //uf.uno(com.sun.star.beans.XPropertySet)
                    uf?.setPropertyValue('Content', content ?: '' as String)
                    //println "Odisee: OOoFieldCategory.setUserFieldContent($name): content='${component.getUserFieldContent(name)}'"
                } catch (com.sun.star.beans.UnknownPropertyException upe) {
                    // If that failed with an UnknownPropertyException
                    println "Odisee: OOoFieldCategory.setUserFieldContent: WARNING: Unknown property 'Content' for userfield '${name}': ${upe.message}"
                    // TODO WHY THE HELL... maybe this was done to support SetExpressions???
                    def textFields = component.textFieldsSupplier().textFields
                    com.sun.star.container.XEnumeration elts = textFields.createEnumeration()
                    com.sun.star.text.XDependentTextField xDependentTextField
                    def field
                    def tfm
                    com.sun.star.beans.XPropertySet fieldProperties
                    while (elts.hasMoreElements()) {
                        field = elts.nextElement()
                        xDependentTextField = field.uno(com.sun.star.text.XDependentTextField)
                        tfm = xDependentTextField.textFieldMaster
                        try {
                            if (tfm.getPropertyValue("Name") == name) {
                                fieldProperties = field.uno(com.sun.star.beans.XPropertySet)
                                if (fieldProperties.getPropertyValue("NumberingType") == "5001") {
                                    fieldProperties.setPropertyValue("Content", content)
                                    fieldProperties.setPropertyValue("Value", (content ?: 0.0d) as Double)
                                } else {
                                    fieldProperties.setPropertyValue("Content", content)
                                }
                                break
                            }
                        } catch (e) {
                            // Ignore e.printStackTrace()
                        }
                    }
                }
            }
            // TODO Don't return old content
            // Return old content
            //oldContent
        }
    }

    /**
     * Set content of many userfields.
     * @return Map key = userfield name, value = old value of userfield
     */
    static Map setUserFieldContent(com.sun.star.lang.XComponent component, Map nameContent) {
        def s = [:]
        nameContent.each { k, v ->
            // TODO Don't get old content
            //s[k] = component.setUserFieldContent(k, v)
            component.setUserFieldContent(k, v)
        }
        s
    }

    /**
     * Refresh all text fields.
     */
    static void refreshTextFields(com.sun.star.lang.XComponent component) {
        Profile.time "OOoFieldCategory.refreshTextFields", {
            use(UnoCategory) {
                //println "OOoFieldCategory.refreshTextFields: ${component}"
                def supplier = component.textFieldsSupplier()
                def textfields = supplier.textFields
                def xRefreshable = textfields.uno(com.sun.star.util.XRefreshable)
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
    def static get(com.sun.star.lang.XComponent component, String name) {
        component.getUserFieldContent(name)
    }

    /**
     * Set content of a userfield by using:
     * <pre>
     * use (OOoFieldCategory) {*     odt["field"] = "new value"
     *}* </pre>
     */
    def static set(com.sun.star.lang.XComponent component, String name, Object value) {
        component.setUserFieldContent(name, value)
    }

}
