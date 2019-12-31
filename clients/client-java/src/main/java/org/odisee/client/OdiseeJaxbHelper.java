/*
 * odisee-client-java
 * odisee-client-java
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 14.01.13 12:36
 */

package org.odisee.client;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

public final class OdiseeJaxbHelper {

    private OdiseeJaxbHelper() {
        throw new AssertionError();
    }

    @SuppressWarnings({"unchecked"})
    public static <T> T unmarshal(final Class<T> clazz, final File file) {
        T odisee;
        try {
            // create a JAXBContext capable of handling classes generated into package
            JAXBContext jaxbContext = JAXBContext.newInstance(clazz.getPackage().getName());
            // create an Unmarshaller
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            // unmarshal an instance document into a tree of Java content
            // objects composed of classes from the package.
            odisee = (T) unmarshaller.unmarshal(new FileInputStream(file));
        } catch (JAXBException | IOException e) {
            throw new OdiseeClientException(e);
        }
        return odisee;
    }

    public static <T> void marshal(final Class<T> clazz, final T objectToMarshal, final Writer writer) {
        try {
            // create a JAXBContext capable of handling classes generated into package
            JAXBContext jaxbContext = JAXBContext.newInstance(clazz.getPackage().getName());
            // create a Marshaller and do marshal
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(objectToMarshal, writer);
            writer.flush();
        } catch (JAXBException | IOException e) {
            throw new OdiseeClientException(e);
        }
    }

    public static <T> void marshal(final Class<T> clazz, final T objectToMarshal, final OutputStream stream) {
        try {
            // create a JAXBContext capable of handling classes generated into package
            JAXBContext jaxbContext = JAXBContext.newInstance(clazz.getPackage().getName());
            // create a Marshaller and do marshal
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(objectToMarshal, stream);
        } catch (JAXBException e) {
            throw new OdiseeClientException(e);
        }
    }

    public static <T> void marshal(final Class<T> clazz, final T objectToMarshal, final File file) {
        try {
            // create a JAXBContext capable of handling classes generated into package
            JAXBContext jaxbContext = JAXBContext.newInstance(clazz.getPackage().getName());
            // create a Marshaller and do marshal
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.marshal(objectToMarshal, new FileOutputStream(file));
        } catch (JAXBException | FileNotFoundException e) {
            throw new OdiseeClientException(e);
        }
    }

}
