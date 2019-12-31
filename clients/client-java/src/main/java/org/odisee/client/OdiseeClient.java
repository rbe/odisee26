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

import de.odisee.xml.server.request.Action;
import de.odisee.xml.server.request.Archive;
import de.odisee.xml.server.request.Input;
import de.odisee.xml.server.request.Instructions;
import de.odisee.xml.server.request.Macro;
import de.odisee.xml.server.request.ObjectFactory;
import de.odisee.xml.server.request.Odisee;
import de.odisee.xml.server.request.Parameter;
import de.odisee.xml.server.request.PostProcess;
import de.odisee.xml.server.request.Request;
import de.odisee.xml.server.request.Template;
import de.odisee.xml.server.request.Userfield;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

import static java.nio.file.StandardOpenOption.CREATE;

public final class OdiseeClient {

    public static final String LANGUAGE_BASIC = "Basic";

    public static final String LOCATION_DOCUMENT = "document";

    private final ObjectFactory factory;

    private final Request actualRequest;

    private String serviceURL;

    private final Odisee odisee;

    private OdiseeHttpHelper httpHelper;

    private OdiseeClient() {
        factory = new ObjectFactory();
        odisee = factory.createOdisee();
        actualRequest = factory.createRequest();
    }

    public OdiseeClient(final String serviceURL) {
        this();
        Objects.requireNonNull(serviceURL);
        this.serviceURL = serviceURL;
        httpHelper = new OdiseeHttpHelper();
    }

    public OdiseeClient(final String serviceURL, final String username, final String password) {
        this();
        this.serviceURL = serviceURL;
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        httpHelper = new OdiseeHttpHelper(username, password);
    }

    private Instructions getInstructions(final Request request) {
        final List<Instructions> allInstructions = request.getInstructions();
        Instructions instructions;
        if (allInstructions.isEmpty()) {
            instructions = new Instructions();
            request.getInstructions().add(instructions);
        }
        return allInstructions.get(allInstructions.size() - 1);
    }

    private List<Object> getInstructionsObject(final Request request) {
        return getInstructions(request).getAutotextAndBookmarkAndMacro();
    }

    public OdiseeClient mergeDocumentAtEnd(final Path path) {
        final PostProcess postProcess = factory.createPostProcess();
        final Action action = factory.createAction();
        action.setType("merge-with");
        action.setResultPlaceholder("");
        final Input input = factory.createInput();
        input.setFilename(path.toString());
        action.setInput(input);
        postProcess.getAction().add(action);
        odisee.getPostProcess().add(postProcess);
        return this;
    }

    public OdiseeClient executeMacro(final String macroName, final String location, final String language,
                                     final List<String> parameters) {
        final Macro macro = factory.createMacro();
        macro.setName(macroName);
        macro.setLocation(location);
        macro.setLanguage(language);
        for (String p : parameters) {
            final Parameter parameter = factory.createParameter();
            parameter.setValue(p);
            macro.getParameter().add(parameter);
        }
        getInstructionsObject(actualRequest).add(macro);
        return this;
    }

    public OdiseeClient executeBasicMacroInDocument(final String macroName, final List<String> parameters) {
        return executeMacro(macroName, LOCATION_DOCUMENT, LANGUAGE_BASIC, parameters);
    }

    public OdiseeClient executeBasicMacroInDocument(final String macroName) {
        return executeMacro(macroName, LOCATION_DOCUMENT, LANGUAGE_BASIC, null);
    }

    public OdiseeClient setTableCellValue(final String tableName, final String coordinate, final String value) {
        final Userfield userfieldTableCell = factory.createUserfield();
        userfieldTableCell.setName(String.format("%s!%s", tableName, coordinate));
        userfieldTableCell.setContent(value);
        getInstructionsObject(actualRequest).add(userfieldTableCell);
        return this;
    }

    public OdiseeClient setUserfield(final String userfieldName, final String value) {
        final Userfield userfield = factory.createUserfield();
        userfield.setName(userfieldName);
        userfield.setContent(value);
        getInstructionsObject(actualRequest).add(userfield);
        return this;
    }

    /**
     * @deprecated Since 2.6 there's no archiving.
     */
    @Deprecated(since = "2.6", forRemoval = true)
    public OdiseeClient setArchive(final Request request, boolean database, boolean files) {
        final Archive archive = request.getArchive();
        if (null == archive) {
            request.setArchive(factory.createArchive());
        }
        request.getArchive().setDatabase(database);
        request.getArchive().setFiles(files);
        return this;
    }

    /**
     * @deprecated Since 2.6 there's no archiving.
     */
    @Deprecated(since = "2.6", forRemoval = true)
    public OdiseeClient setArchive(final boolean database, final boolean filesystem) {
        return setArchive(actualRequest, database, filesystem);
    }

    public OdiseeClient setLatestTemplate(final String template, final OutputFormat outputFormat) {
        final Template requestTemplate = actualRequest.getTemplate();
        if (null == requestTemplate) {
            actualRequest.setTemplate(factory.createTemplate());
        }
        actualRequest.getTemplate().setName(template);
        actualRequest.getTemplate().setRevision("LATEST");
        actualRequest.getTemplate().setOutputFormat(outputFormat.name());
        return this;
    }

    public Request createRequest(final String template, final OutputFormat outputFormat) {
        odisee.getRequest().add(actualRequest);
        setLatestTemplate(template, outputFormat);
        return actualRequest;
    }

    public byte[] process(final boolean sendCompressed) {
        try {
            final Writer odiseeXml = new StringWriter();
            OdiseeJaxbHelper.marshal(Odisee.class, odisee, odiseeXml);
            return sendCompressed
                    ? httpHelper.postCompressed(new URL(serviceURL), odiseeXml.toString())
                    : httpHelper.post(new URL(serviceURL), odiseeXml.toString());
        } catch (IOException e) {
            throw new OdiseeClientException(e);
        }
    }

    public void saveCompressedRequestTo(final Path path) {
        try (final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(path.toFile()))) {
            final String odiseeXmlString = getOdiseeXmlAsString();
            gzipOutputStream.write(odiseeXmlString.getBytes());
        } catch (IOException e) {
            throw new OdiseeClientException(e);
        }
    }

    public void saveRequestTo(final Path path) {
        try (final OutputStream outputStream = Files.newOutputStream(path, CREATE)) {
            final String odiseeXmlString = getOdiseeXmlAsString();
            outputStream.write(odiseeXmlString.getBytes());
        } catch (IOException e) {
            throw new OdiseeClientException(e);
        }
    }

    private String getOdiseeXmlAsString() {
        final Writer odiseeXml = new StringWriter();
        OdiseeJaxbHelper.marshal(Odisee.class, odisee, odiseeXml);
        return odiseeXml.toString();
    }

}
