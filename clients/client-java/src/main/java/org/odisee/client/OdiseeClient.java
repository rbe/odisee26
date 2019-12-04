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

    private final ObjectFactory factory;

    private final String serviceURL;

    private final OdiseeHttpHelper httpHelper;

    private final Odisee odisee;

    private Request actualRequest;

    public OdiseeClient(final String serviceURL) {
        Objects.requireNonNull(serviceURL);
        this.serviceURL = serviceURL;
        httpHelper = new OdiseeHttpHelper();
        factory = new ObjectFactory();
        odisee = factory.createOdisee();
    }

    public OdiseeClient(final String serviceURL, final String username, final String password) {
        Objects.requireNonNull(serviceURL);
        this.serviceURL = serviceURL;
        Objects.requireNonNull(username);
        Objects.requireNonNull(password);
        httpHelper = new OdiseeHttpHelper(username, password);
        factory = new ObjectFactory();
        odisee = factory.createOdisee();
    }

    //
    // Odisee Client Factory
    //

    /**
     * @deprecated Use constructor.
     */
    public static OdiseeClient createLocalClient() {
        return createClient("localhost");
    }

    /**
     * @deprecated Use constructor.
     */
    public static OdiseeClient createClient(final String serviceURL) {
        return new OdiseeClient(serviceURL);
    }

    /**
     * @deprecated Use constructor.
     */
    public static OdiseeClient createClient(final String serviceURL,
                                            final String username, final String password) {
        return new OdiseeClient(serviceURL, username, password);
    }

    //
    // Odisee Client
    //

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

    //
    // Odisee Client API
    //

    public OdiseeClient mergeDocumentAtEnd(final Request request, final Path path) {
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

    public OdiseeClient mergeDocumentAtEnd(final Path path) {
        return mergeDocumentAtEnd(actualRequest, path);
    }

    public OdiseeClient executeMacro(final Request request,
                                     final String macroName, final String location, final String language,
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

    public OdiseeClient executeMacro(final String macroName, final String location, final String language,
                                     final List<String> parameters) {
        return executeMacro(actualRequest, macroName, location, language, parameters);
    }

    public OdiseeClient executeBasicMacroInDocument(final Request request, final String macroName,
                                                    final List<String> parameters) {
        return executeMacro(request, macroName, "document", "Basic", parameters);
    }

    public OdiseeClient executeBasicMacroInDocument(final String macroName, final List<String> parameters) {
        return executeMacro(actualRequest, macroName, "document", "Basic", parameters);
    }

    public OdiseeClient executeBasicMacroInDocument(final String macroName) {
        return executeMacro(actualRequest, macroName, "document", "Basic", null);
    }

    public OdiseeClient setTableCellValue(final Request request,
                                          final String tableName, final String coordinate, final String value) {
        final Userfield userfieldTableCell = factory.createUserfield();
        userfieldTableCell.setName(String.format("%s!%s", tableName, coordinate));
        userfieldTableCell.setContent(value);
        getInstructionsObject(actualRequest).add(userfieldTableCell);
        return this;
    }

    public OdiseeClient setTableCellValue(final String tableName, final String coordinate, final String value) {
        return setTableCellValue(actualRequest, tableName, coordinate, value);
    }

    public OdiseeClient setUserfield(final Request request, final String userfieldName, final String value) {
        final Userfield userfield = factory.createUserfield();
        userfield.setName(userfieldName);
        userfield.setContent(value);
        getInstructionsObject(actualRequest).add(userfield);
        return this;
    }

    public OdiseeClient setUserfield(final String userfieldName, final String value) {
        return setUserfield(actualRequest, userfieldName, value);
    }

    /**
     * @deprecated Since 2.6 there's no archiving.
     */
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
    public OdiseeClient setArchive(boolean database, boolean filesystem) {
        return setArchive(actualRequest, database, filesystem);
    }

    public OdiseeClient setLatestTemplate(final Request request, final String template, final String outputFormat) {
        final Template requestTemplate = request.getTemplate();
        if (null == requestTemplate) {
            actualRequest.setTemplate(factory.createTemplate());
        }
        request.getTemplate().setName(template);
        request.getTemplate().setRevision("LATEST");
        request.getTemplate().setOutputFormat(outputFormat);
        return this;
    }

    public OdiseeClient setLatestTemplate(final String template, final String outputFormat) {
        return setLatestTemplate(actualRequest, template, outputFormat);
    }

    public OdiseeClient setLatestTemplate(final String template) {
        return setLatestTemplate(template, "pdf");
    }

    public Request createRequest() {
        actualRequest = factory.createRequest();
        odisee.getRequest().add(actualRequest);
        return actualRequest;
    }

    public Request createRequest(final String template, final String outputFormat) {
        final Request request = createRequest();
        setLatestTemplate(request, template, outputFormat);
        return request;
    }

    public Request createRequest(final String template) {
        return createRequest(template, "pdf");
    }

    public byte[] process(final boolean sendCompressed) throws OdiseeClientException {
        byte[] result;
        try {
            final Writer odiseeXml = new StringWriter();
            OdiseeJaxbHelper.marshal(Odisee.class, odisee, odiseeXml);
            if (sendCompressed) {
                result = httpHelper.postCompressed(new URL(serviceURL), odiseeXml.toString());
            } else {
                result = httpHelper.post(new URL(serviceURL), odiseeXml.toString());
            }
        } catch (IOException e) {
            throw new OdiseeClientException(e);
        }
        return result;
    }

    public void saveCompressedRequestTo(final Path path) throws OdiseeClientException {
        try (final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(path.toFile()))) {
            final String odiseeXmlString = getOdiseeXmlAsString();
            gzipOutputStream.write(odiseeXmlString.getBytes());
        } catch (IOException e) {
            throw new OdiseeClientException(e);
        }
    }

    public void saveRequestTo(final Path path) throws OdiseeClientException {
        try {
            final String odiseeXmlString = getOdiseeXmlAsString();
            Files.newOutputStream(path, CREATE).write(odiseeXmlString.getBytes());
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
