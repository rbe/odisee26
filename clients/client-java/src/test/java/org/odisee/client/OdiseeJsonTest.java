/*
 * odisee-client-java
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 */

package org.odisee.client;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

public class OdiseeJsonTest {

    @Test
    public void singleOdtRequest() throws IOException {
        final String xml = resource("test_HalloOdisee_single_odt.xml");
        final String json = OdiseeJson.fromXml(xml);
        assertEquals("{\"request\":[{\"name\":\"HalloOdisee\",\"template\":{\"name\":\"HalloOdisee\","
                + "\"outputFormat\":\"odt\"},\"archive\":{\"files\":false},\"instructions\":["
                + "{\"instruction\":\"userfield\",\"name\":\"Hallo\",\"value\":\"art of coding UG "
                + "(haftungsbeschränkt)\"},{\"instruction\":\"userfield\",\"name\":\"Tabelle1!A4\","
                + "\"value\":\"support@odisee.de\"}]}]}", json);
    }

    @Test
    public void macroMergeAndImage() {
        final String xml = "<odisee>"
                + "<request name=\"HalloOdisee3\">"
                + "<template name=\"HalloOdisee\" outputFormat=\"pdf\" pre-save-macro=\"Standard.Module1.Before\"/>"
                + "<instructions>"
                + "<macro name=\"Standard.Module1.myMacro\" language=\"Basic\" location=\"document\">"
                + "<parameter value=\"a\"/>"
                + "<parameter>b</parameter>"
                + "</macro>"
                + "<image type=\"image/png\" bookmark=\"Header\" width=\"1000\" height=\"200\">BASE64</image>"
                + "</instructions>"
                + "<post-process><instructions><action type=\"merge-with\">"
                + "<result-placeholder/>"
                + "<input file=\"pdf/AGB.pdf\"/>"
                + "</action></instructions></post-process>"
                + "</request>"
                + "<response><base64>false</base64></response>"
                + "</odisee>";
        assertEquals("{\"request\":[{\"name\":\"HalloOdisee3\",\"template\":{\"name\":\"HalloOdisee\","
                + "\"outputFormat\":\"pdf\",\"preSaveMacro\":\"Standard.Module1.Before\"},\"instructions\":["
                + "{\"instruction\":\"macro\",\"language\":\"Basic\",\"location\":\"document\","
                + "\"name\":\"Standard.Module1.myMacro\",\"parameter\":[{\"value\":\"a\"},{\"value\":\"b\"}]},"
                + "{\"instruction\":\"image\",\"bookmark\":\"Header\",\"height\":200,\"type\":\"image/png\","
                + "\"width\":1000,\"value\":\"BASE64\"}],\"postProcess\":{\"action\":[{\"type\":\"merge-with\","
                + "\"content\":[{\"element\":\"result-placeholder\"},{\"element\":\"input\","
                + "\"filename\":\"pdf/AGB.pdf\"}]}]}}],\"response\":{\"base64\":false}}", OdiseeJson.fromXml(xml));
    }

    @Test
    public void deliveryAttributeIsAJsonField() {
        final String xml = "<odisee delivery=\"both\"><request name=\"One\">"
                + "<template name=\"Letter\" outputFormat=\"pdf\"/>"
                + "<instructions><userfield name=\"Hallo\">x</userfield></instructions>"
                + "</request></odisee>";
        assertEquals("{\"request\":[{\"name\":\"One\",\"template\":{\"name\":\"Letter\",\"outputFormat\":\"pdf\"},"
                + "\"instructions\":[{\"instruction\":\"userfield\",\"name\":\"Hallo\",\"value\":\"x\"}]}],"
                + "\"delivery\":\"both\"}", OdiseeJson.fromXml(xml));
    }

    private static String resource(final String name) throws IOException {
        try (InputStream in = OdiseeJsonTest.class.getResourceAsStream("/" + name)) {
            if (in == null) {
                throw new IOException("Missing " + name);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
