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
import de.odisee.xml.server.request.Input;
import de.odisee.xml.server.request.Instructions;
import de.odisee.xml.server.request.Macro;
import de.odisee.xml.server.request.ObjectFactory;
import de.odisee.xml.server.request.Odisee;
import de.odisee.xml.server.request.Parameter;
import de.odisee.xml.server.request.PostProcess;
import de.odisee.xml.server.request.Request;
import de.odisee.xml.server.request.ResultPlaceholder;
import de.odisee.xml.server.request.Template;
import de.odisee.xml.server.request.Userfield;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.zip.GZIPOutputStream;

import static java.nio.file.StandardOpenOption.CREATE;

public final class OdiseeClient {

    public static final String LANGUAGE_BASIC = "Basic";

    public static final String LOCATION_DOCUMENT = "document";

    private final ObjectFactory factory;

    private Request actualRequest;

    private String serviceURL;

    private final Odisee odisee;

    private OdiseeHttpHelper httpHelper;

    private RequestFormat format = RequestFormat.XML;

    private OdiseeClient() {
        factory = new ObjectFactory();
        odisee = factory.createOdisee();
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

    /**
     * The access token is the caller's Keycloak client-credentials token.
     * The client id inside that token is the Odisee user.
     */
    public static OdiseeClient bearer(final String serviceURL, final String accessToken) {
        final OdiseeClient client = new OdiseeClient();
        client.serviceURL = Objects.requireNonNull(serviceURL);
        Objects.requireNonNull(accessToken);
        client.httpHelper = OdiseeHttpHelper.bearer(accessToken);
        return client;
    }

    /**
     * Post the request as JSON. The service converts it back to the XML generator input.
     */
    public OdiseeClient useJson() {
        this.format = RequestFormat.JSON;
        return this;
    }

    /**
     * Post the request as XML. This is the default.
     */
    public OdiseeClient useXml() {
        this.format = RequestFormat.XML;
        return this;
    }

    public RequestFormat format() {
        return format;
    }

    private Request ensureActualRequest() {
        if (actualRequest == null) {
            actualRequest = factory.createRequest();
            odisee.getRequest().add(actualRequest);
        }
        return actualRequest;
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
        return getInstructions(request).getInstruction();
    }

    public OdiseeClient mergeDocumentAtEnd(final Path path) {
        PostProcess postProcess = odisee.getPostProcess();
        if (postProcess == null) {
            postProcess = factory.createPostProcess();
            odisee.setPostProcess(postProcess);
        }
        final Action action = factory.createAction();
        action.setType("merge-with");
        action.getResultPlaceholder().add(new ResultPlaceholder());
        final Input input = factory.createInput();
        input.setFilename(path.toString());
        action.getInput().add(input);
        postProcess.getAction().add(action);
        return this;
    }

    public OdiseeClient executeMacro(final String macroName, final String location, final String language,
                                     final List<String> parameters) {
        final Macro macro = factory.createMacro();
        macro.setName(macroName);
        macro.setLocation(location);
        macro.setLanguage(language);
        if (parameters != null) {
            for (String p : parameters) {
                final Parameter parameter = factory.createParameter();
                parameter.setValue(p);
                macro.getParameter().add(parameter);
            }
        }
        getInstructionsObject(ensureActualRequest()).add(macro);
        return this;
    }

    public OdiseeClient executeBasicMacroInDocument(final String macroName, final List<String> parameters) {
        return executeMacro(macroName, LOCATION_DOCUMENT, LANGUAGE_BASIC, parameters);
    }

    public OdiseeClient executeBasicMacroInDocument(final String macroName) {
        return executeMacro(macroName, LOCATION_DOCUMENT, LANGUAGE_BASIC, Collections.emptyList());
    }

    public OdiseeClient setTableCellValue(final Request request, final String tableName, final String coordinate,
                                          final String value) {
        Objects.requireNonNull(request);
        final Userfield userfieldTableCell = factory.createUserfield();
        userfieldTableCell.setName(String.format("%s!%s", tableName, coordinate));
        userfieldTableCell.setContent(value);
        getInstructionsObject(request).add(userfieldTableCell);
        return this;
    }

    public OdiseeClient setTableCellValue(final String tableName, final String coordinate, final String value) {
        return setTableCellValue(ensureActualRequest(), tableName, coordinate, value);
    }

    public OdiseeClient setUserfield(final Request request, final String userfieldName, final String value) {
        Objects.requireNonNull(request);
        final Userfield userfield = factory.createUserfield();
        userfield.setName(userfieldName);
        userfield.setContent(value);
        getInstructionsObject(request).add(userfield);
        return this;
    }

    public OdiseeClient setUserfield(final String userfieldName, final String value) {
        return setUserfield(ensureActualRequest(), userfieldName, value);
    }

    public OdiseeClient setLatestTemplate(final String template, final OutputFormat outputFormat) {
        final Request target = ensureActualRequest();
        final Template requestTemplate = target.getTemplate();
        if (null == requestTemplate) {
            target.setTemplate(factory.createTemplate());
        }
        target.getTemplate().setName(template);
        target.getTemplate().setRevision("LATEST");
        target.getTemplate().setOutputFormat(outputFormat.name());
        return this;
    }

    /**
     * {@code stream}, {@code store}, or {@code both}. Absent means stream.
     */
    public OdiseeClient delivery(final String mode) {
        odisee.setDelivery(mode);
        return this;
    }

    public Request createRequest(final String template) {
        return createRequest(template, OutputFormat.PDF);
    }

    public Request createRequest(final String template, final OutputFormat outputFormat) {
        actualRequest = factory.createRequest();
        odisee.getRequest().add(actualRequest);
        setLatestTemplate(template, outputFormat);
        return actualRequest;
    }

    public byte[] process() {
        return process(false);
    }

    public byte[] process(final boolean sendCompressed) {
        try {
            final URL url = toServiceUrl(serviceURL);
            final String body = getRequestBody();
            final String contentType = contentType();
            return sendCompressed
                    ? httpHelper.postCompressed(url, body, contentType)
                    : httpHelper.post(url, body, contentType);
        } catch (IOException e) {
            throw new OdiseeClientException(e);
        }
    }

    private static URL toServiceUrl(final String rawUrl) throws IOException {
        if (rawUrl == null) {
            throw new MalformedURLException(OdiseeConstant.ERR_NO_SERVICE_URL);
        }
        try {
            return URI.create(rawUrl).toURL();
        } catch (IllegalArgumentException e) {
            throw new MalformedURLException(e.getMessage());
        }
    }

    public void saveCompressedRequestTo(final Path path) {
        try (final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(new FileOutputStream(path.toFile()))) {
            gzipOutputStream.write(getRequestBody().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new OdiseeClientException(e);
        }
    }

    public void saveRequestTo(final Path path) {
        try (final OutputStream outputStream = Files.newOutputStream(path, CREATE)) {
            outputStream.write(getRequestBody().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new OdiseeClientException(e);
        }
    }

    private String getRequestBody() {
        final String xml = getOdiseeXmlAsString();
        if (format == RequestFormat.JSON) {
            return OdiseeJson.fromXml(xml);
        }
        return xml;
    }

    private String contentType() {
        return format == RequestFormat.JSON
                ? "application/json; charset=UTF-8"
                : "text/xml; charset=UTF-8";
    }

    private String getOdiseeXmlAsString() {
        final Writer odiseeXml = new StringWriter();
        OdiseeJaxbHelper.marshal(Odisee.class, odisee, odiseeXml);
        return odiseeXml.toString();
    }

}
