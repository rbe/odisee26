/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 02.02.15 18:19
 */

package org.odisee.document

import com.sun.star.lang.XComponent
import groovy.util.logging.Log
import groovy.xml.XmlSlurper
import org.odisee.api.OdiseeException
import org.odisee.debug.Profile
import org.odisee.io.SafePaths
import org.odisee.ooo.connection.OdiseeServerRuntimeException
import org.odisee.ooo.connection.OfficeConnection
import org.odisee.ooo.connection.OfficeConnectionFactory
import org.odisee.ooo.connection.UnoCall
import org.odisee.ooo.connection.UnoDeadlineExceeded

import java.util.concurrent.Callable
import org.odisee.shared.OdiseeConstant
import org.odisee.writer.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileAttribute
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.TimeUnit

/**
 * Apply values and instructions from a simple XML file to generate an OpenOffice document.
 * A document is an instance of a certain revision of a template.
 * Templates, merge inputs, and output live under $ODISEE_VAR/user/{name}/.
 */
@Log
class OdiseeXmlCategory {

    /**
     * Highest {@code *_revN.ott} in a directory. Numeric order, so revision 10 outranks 9.
     */
    static Path findLatestRevision(Path dir) {
        Path directory = dir != null && Files.isDirectory(dir) ? dir : dir?.parent
        if (directory == null || !Files.isDirectory(directory)) {
            throw new OdiseeException("Cannot find template in directory '${dir}'", OdiseeException.NOT_FOUND)
        }
        Path best = null
        int bestRevision = -1
        Files.newDirectoryStream(directory, '*_rev*.ott').withCloseable { stream ->
            stream.each { Path candidate ->
                def matcher = (candidate.fileName.toString() =~ /_rev(\d+)\.ott$/)
                if (matcher.find()) {
                    int revision = Integer.parseInt(matcher.group(1))
                    if (revision > bestRevision) {
                        bestRevision = revision
                        best = candidate
                    }
                }
            }
        }
        if (best == null) {
            throw new OdiseeException("Cannot find a revised template in '${directory}'", OdiseeException.NOT_FOUND)
        }
        best
    }

    /**
     * Find OpenOffice template by revision and return File object.
     * A concrete path and revision were stored on the template element by {@link TemplateService}.
     * {@code LATEST} falls back to {@link TemplateLocator}.
     * @param xmlTemplate Node xml.request.template from request XML.
     */
    static Path findTemplate(xmlTemplate) {
        if (!xmlTemplate) {
            throw new OdiseeException('No template specified', OdiseeException.BAD_REQUEST)
        }
        String templatePath = xmlTemplate.'@path'?.toString()?.trim()
        if (!templatePath) {
            throw new OdiseeException('No path to template given', OdiseeException.BAD_REQUEST)
        }
        String revision = xmlTemplate.'@revision'?.toString()?.trim()
        String name = xmlTemplate.'@name'?.toString()?.trim()
        Path hinted = Paths.get(templatePath)
        if (revision && !revision.equalsIgnoreCase(OdiseeConstant.S_LATEST)) {
            if (!Files.exists(hinted)) {
                throw new OdiseeException("Odisee: Template '${hinted}' does not exist!", OdiseeException.NOT_FOUND)
            }
            return hinted
        }
        Path directory = Files.isDirectory(hinted) ? hinted : hinted.parent
        TemplateLocator.locate(directory, name, OdiseeConstant.S_LATEST)
    }

    /**
     * Process something: execute a closure and call a macro.
     */
    static void processInstruction(XComponent template, closure, macro = null) {
        String pre = macro?.pre?.name?.toString()?.trim()
        if (pre) {
            MacroNames.requireReference(pre)
            use(OOoDocumentCategory) {
                template.executeMacro(pre, (macro.pre.params ?: []) as Object[])
            }
        }
        // Execute closure
        closure(template)
        String post = macro?.post?.name?.toString()?.trim()
        if (post) {
            MacroNames.requireReference(post)
            use(OOoDocumentCategory) {
                template.executeMacro(post, (macro.post.params ?: []) as Object[])
            }
        }
    }

    /**
     * Set values in userfields.
     */
    static void processUserfield(XComponent template, Map arg, userfield) {
        String ufName = userfield.'@name'.toString()
        Profile.time "OOoTextTableCategory.processUserfield(${ufName})", {
            OdiseeXmlCategory.processInstruction template, { t ->
                String ufContent = userfield.text()?.toString() ?: ''
                switch (ufName) {
                // A text table; coordinates
                    case { it ==~ /.*\$.*/ || it ==~ /.*\!.*/ }:
                        use(OOoTextTableCategory) {
                            t[ufName] = ufContent
                        }
                        break
                // Everything else is a variable
                    default:
                        use(OOoFieldCategory) {
                            t[ufName] = ufContent
                        }
                }
            }, [post: [name: userfield.'@post-macro'.toString()]]
        }
    }

    /**
     * Set values in texttables.
     */
    static void processTexttable(XComponent template, Map arg, texttable) {
        String ttName = texttable.'@name'.toString()
        Profile.time "OOoTextTableCategory.processTexttable(${ttName})", {
            OdiseeXmlCategory.processInstruction template, { t ->
                String ttContent = texttable.text()?.toString() ?: ''
                use(OOoTextTableCategory) {
                    t[ttName] = ttContent
                }
            }, [post: [name: texttable.'@post-macro'.toString()]]
        }
    }

    /**
     * Set text at bookmark.
     */
    static void processBookmark(XComponent template, Map arg, bookmark) {
        OdiseeXmlCategory.processInstruction template, { t ->
            String bmName = bookmark.'@name'.toString()
            String bmContent = bookmark.text()?.toString() ?: ''
            use(OOoBookmarkCategory) {
                t[bmName] = bmContent
            }
        }, [post: [name: bookmark.'@post-macro'.toString()]]
    }

    /**
     * Insert autotext.
     */
    static void processAutotext(XComponent template, Map arg, autotext) {
        //
        String autotextGroup = autotext.'@group'.toString() ?: 'Standard'
        String autotextName = autotext.'@name'.toString()
        String bookmark = autotext.'@bookmark'.toString()
        String atend = autotext.'@atend'.toString() == OdiseeConstant.S_TRUE
        //
        OdiseeXmlCategory.processInstruction template, { t ->
            use(OOoAutotextCategory) {
                if (bookmark) {
                    t.insertAutotextAtBookmark(autotextGroup, autotextName, bookmark)
                } else if (atend) {
                    t.insertAutotextAtEnd(autotextGroup, autotextName)
                }
            }
        }, [post: [name: autotext.'@post-macro'.toString()]]
    }

    /**
     * Insert an image.
     */
    static void processImage(XComponent template, Map arg, image) {
        final String imageType = image.'@type'.toString()
        final String imageUrl = image.'@url'.toString()
        int imageWidth = 0
        try {
            imageWidth = image.'@width'?.toString()?.toInteger() ?: 0
        } catch (NumberFormatException e) {
            // ignore
        }
        int imageHeight = 0
        try {
            imageHeight = image.'@height'?.toString()?.toInteger() ?: 0
        } catch (NumberFormatException e) {
            // ignore
        }
        final String bookmarkName = image.'@bookmark'.toString()
        OdiseeXmlCategory.processInstruction template, { t ->
            final String imageContent = image.text()?.toString() ?: ''
            use(OOoImageCategory) {
                if (imageContent && bookmarkName) {
                    String _imageUrl = saveImageToFile(arg, imageType, imageContent)
                    t.insertImageAtBookmark(imageType, bookmarkName, _imageUrl, imageWidth, imageHeight)
                } else if (imageUrl && bookmarkName) {
                    t.insertImageAtBookmark(imageType, bookmarkName, imageUrl, imageWidth, imageHeight)
                }
            }
        }, [post: [name: image.'@post-macro'.toString()]]
    }

    private static String saveImageToFile(Map arg, String imageType, String imageContent) {
        byte[] imageData = Base64.decoder.decode(imageContent.toString().trim())
        Path outputPath = Paths.get(arg.outputPath)
        FileAttribute<Set<PosixFilePermission>> fileAttribute = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))
        String extension
        switch (imageType) {
            case { it =~ /.*png/ }:
                extension = "png"
                break
            case { it =~ /.*jp?g/ }:
                extension = "jpeg"
                break
            default:
                throw new OdiseeServerRuntimeException("Unsupported image type ${imageType}")
        }
        Path tempImage = Files.createTempFile(outputPath, "image", ".${extension}", fileAttribute)
        Files.write(tempImage, imageData, StandardOpenOption.WRITE)
        String _imageUrl = tempImage.toAbsolutePath().toUri().toString()
        _imageUrl
    }

    /**
     * Execute a macro.
     */
    static void processMacro(XComponent template, Map arg, macro) {
        // Get name, library, and language of macro. A bad name is HTTP 400.
        String macroName = macro.'@name'.toString()
        String library = macro.'@location'.toString() ?: 'document'
        String language = macro.'@language'.toString() ?: 'Basic'
        MacroNames.requireParts(macroName, library, language)
        String macroUrl = "${macroName}?language=${language}&location=${library}"
        OdiseeXmlCategory.processInstruction template, { t ->
            int paramCount = macro.parameter.size()
            Object[] params = null
            if (paramCount > 0) {
                params = new Object[paramCount]
                macro.parameter.eachWithIndex { p, i ->
                    params[i] = p.toString()
                }
            } else {
                params = [] as Object[]
            }
            use(OOoDocumentCategory) {
                template.executeMacro(macroUrl, params)
            }
        }
    }

    /**
     * Set a Calc cell. {@code sheet} is the sheet name and {@code coordinate} is the cell, for example A1.
     */
    static void processCell(XComponent template, Map arg, cell) {
        String sheetName = cell.'@sheet'?.toString()?.trim()
        String coordinate = cell.'@coordinate'?.toString()?.trim()
        if (!sheetName || !coordinate) {
            throw new OdiseeException('cell requires sheet and coordinate', OdiseeException.BAD_REQUEST)
        }
        String value = cell.text()?.toString() ?: ''
        Map parsed = Coordinate.parseCoordinate(coordinate)
        int column = parsed.columnIndex as int
        int row = parsed.rowIndex as int
        OdiseeXmlCategory.processInstruction template, { t ->
            use(org.odisee.uno.UnoCategory) {
                def spreadsheetDocument = t.uno(com.sun.star.sheet.XSpreadsheetDocument)
                if (spreadsheetDocument == null) {
                    throw new OdiseeException('cell applies to a Calc document', OdiseeException.UNPROCESSABLE)
                }
                def sheetObject = spreadsheetDocument.getSheets().getByName(sheetName)
                def sheet = sheetObject.uno(com.sun.star.sheet.XSpreadsheet)
                def xcell = sheet.getCellByPosition(column, row)
                def text = xcell.uno(com.sun.star.text.XText)
                if (text != null) {
                    text.setString(value)
                } else {
                    xcell.setFormula(value)
                }
            }
        }
    }

    /**
     * Set the text of a named Impress shape.
     */
    static void processShape(XComponent template, Map arg, shape) {
        String shapeName = shape.'@name'?.toString()?.trim()
        if (!shapeName) {
            throw new OdiseeException('shape requires a name', OdiseeException.BAD_REQUEST)
        }
        String value = shape.text()?.toString() ?: ''
        OdiseeXmlCategory.processInstruction template, { t ->
            use(org.odisee.uno.UnoCategory) {
                def supplier = t.uno(com.sun.star.drawing.XDrawPagesSupplier)
                if (supplier == null) {
                    throw new OdiseeException('shape applies to an Impress document', OdiseeException.UNPROCESSABLE)
                }
                def pages = supplier.getDrawPages()
                boolean found = false
                for (int pageIndex = 0; pageIndex < pages.getCount(); pageIndex++) {
                    def page = pages.getByIndex(pageIndex)
                    def shapes = page.uno(com.sun.star.drawing.XShapes)
                    if (shapes == null) {
                        continue
                    }
                    for (int shapeIndex = 0; shapeIndex < shapes.getCount(); shapeIndex++) {
                        def candidate = shapes.getByIndex(shapeIndex)
                        def named = candidate.uno(com.sun.star.container.XNamed)
                        if (named != null && shapeName == named.getName()) {
                            def text = candidate.uno(com.sun.star.text.XText)
                            if (text == null) {
                                throw new OdiseeException("shape '${shapeName}' has no text", OdiseeException.UNPROCESSABLE)
                            }
                            text.setString(value)
                            found = true
                        }
                    }
                }
                if (!found) {
                    throw new OdiseeException("shape '${shapeName}' was not found", OdiseeException.UNPROCESSABLE)
                }
            }
        }
    }

    /**
     * The open document selects the instruction set. The template extension is the fallback.
     */
    static OfficeDocumentType applicationOf(XComponent component, Path template) {
        if (component != null) {
            use(org.odisee.uno.UnoCategory) {
                if (component.uno(com.sun.star.sheet.XSpreadsheetDocument)) {
                    return OfficeDocumentType.SPREADSHEET
                }
                if (component.uno(com.sun.star.presentation.XPresentationSupplier)) {
                    return OfficeDocumentType.PRESENTATION
                }
                if (component.uno(com.sun.star.text.XTextDocument)) {
                    return OfficeDocumentType.TEXT
                }
            }
        }
        OfficeDocumentType.fromFileName(template?.fileName?.toString())
    }

    static String instructionLabel(instr) {
        String name = instr?.'@name'?.toString()?.trim()
        if (name) {
            return name
        }
        String sheet = instr?.'@sheet'?.toString()?.trim()
        String coordinate = instr?.'@coordinate'?.toString()?.trim()
        [sheet, coordinate].findAll { it }.join(' ')
    }

    /**
     * Read request and return map.
     */
    static Map readRequest(Path file, int requestNumber) {
        Map arg = [:]
        // The HTTP body was validated against the v2 schema before this file was written.
        final String xmlText = file.toFile().getText(OdiseeConstant.S_UTF8)
        arg.xml = new XmlSlurper().parseText(xmlText)
        //
        def request = arg.xml.request[requestNumber]
        // The ID, if none given use actual date and time
        arg.id = request.'@id'?.toString()
        arg.id ?: (arg.id = new Date().format(OdiseeConstant.FILE_DATEFORMAT_SSSS))
        // Get File reference to certain or latest revision of template
        arg.template = OdiseeXmlCategory.findTemplate(request.template)
        arg.revision = TemplateLocator.revisionOf(arg.template)
        // Return map
        arg
    }

    static ArrayList processTemplate(Map arg, OfficeConnection oooConnection, OfficeConnectionFactory officeConnectionFactory) {
        // Result is one or more document(s)
        def output = []
        // Get XML request element
        def request = arg.xml.request[arg.activeRequestIndex]
        def template = request.template[0]
        final String outputPath = template.'@outputPath'.toString()
        arg.outputPath = outputPath
        // Set basename for document(s) to generate: dir for template, name of template including revision and ID
        // TODO name must be generated to avoid name clashes with multiple requests
        String documentBasename = null
        if (request.'@name'?.toString()?.trim()) {
            documentBasename = SafePaths.requireSimpleName(request.'@name'.toString(), 'document name')
        } else {
            final String filename = arg.template.fileName.toString()
            final List strings = filename.split('\\.')[0..-2]
            final String join = strings.join('.')
            documentBasename = "${join}-id${arg.id}"
        }
        List<OutputFormats.Choice> formats = OutputFormats.fromRequest(request)
        if (!formats) {
            throw new OdiseeException('No output format specified', OdiseeException.UNPROCESSABLE)
        }
        final Path outputDir = Paths.get(outputPath)
        Map<Path, List<Map<String, String>>> optionsByFile = new LinkedHashMap<>()
        formats.each { OutputFormats.Choice format ->
            String extension = SafePaths.requireSimpleName(format.extension, 'output format')
            Path file = outputDir.resolve("${documentBasename}.${extension}")
            output << file
            optionsByFile[file] = format.options
        }
        use(OOoDocumentCategory) {
            // Should we hide OpenOffice?
            boolean hidden = Boolean.TRUE
            // User supplied debug attribute
            boolean localDebug = Boolean.valueOf(request.'@local-debug'.toString())
            if (localDebug) {
                // local-debug="true" ... so OpenOffice's window should shown (Hidden attribute is false)
                hidden = !localDebug
            }
            XComponent xComponent = null
            boolean leavePool = false
            try {
                xComponent = (XComponent) withinOffice('open', UnoCall.deadlineMillis()) {
                    use(OOoDocumentCategory) {
                        arg.template.open(oooConnection, [Hidden: hidden])
                    }
                }
                OfficeDocumentType application = applicationOf(xComponent, arg.template as Path)
                List<String> failures = []
                request.instructions.'*'.each { instr ->
                    String tagName = instr.name()?.toString() ?: ''
                    String instruction = instructionLabel(instr)
                    String methodName = tagName ? tagName[0].toUpperCase() + (tagName.length() > 1 ? tagName[1..-1] : '') : ''
                    Profile.time "OdiseeXmlCategory.toDocument(${request.'@name'}, instruction ${instruction})", {
                        if (!InstructionSets.accepts(application, methodName)) {
                            failures << "Unsupported instruction '${tagName}'"
                            officeConnectionFactory?.recordInstructionFailure()
                            return
                        }
                        withinOffice('instruction', UnoCall.deadlineMillis()) {
                            try {
                                OdiseeXmlCategory."process${methodName}"(xComponent, arg, instr)
                            } catch (Throwable e) {
                                keepOrRecord(e, failures, "${tagName} ${instruction}", officeConnectionFactory)
                            }
                            null
                        }
                    }
                }
                if (failures) {
                    throw new OdiseeException("Document instructions failed: ${failures.join('; ')}", OdiseeException.UNPROCESSABLE)
                }
                if (application == OfficeDocumentType.TEXT) {
                    withinOffice('instruction', UnoCall.deadlineMillis()) {
                        use(OOoFieldCategory) {
                            xComponent.refreshTextFields()
                        }
                        null
                    }
                }
                if (writesFile(arg)) {
                    final String preSaveMacro = template.'@pre-save-macro'.toString()?.trim()
                    if (preSaveMacro) {
                        MacroNames.requireReference(preSaveMacro)
                        withinOffice('instruction', UnoCall.deadlineMillis()) {
                            use(OOoDocumentCategory) {
                                xComponent.executeMacro(preSaveMacro)
                            }
                            null
                        }
                    }
                output.each { Path file ->
                    Files.createDirectories(file.parent)
                    Map<String, Object> filterData = FormatOptions.filterData(optionsByFile[file])
                    withinOffice('save', UnoCall.deadlineMillis()) {
                        use(OOoDocumentCategory) {
                            if (filterData && FormatOptions.pdfFamily(file.fileName.toString())) {
                                xComponent.saveAsPdf(file, application.pdfExportFilter, filterData)
                            } else if (FormatOptions.pdfaFallback(file.fileName.toString(), filterData)) {
                                xComponent.saveAsPDF_A(file)
                            } else {
                                xComponent.saveAs(file)
                            }
                        }
                        null
                    }
                }
                    final String postSaveMacro = template.'@post-save-macro'.toString()?.trim()
                    if (postSaveMacro) {
                        MacroNames.requireReference(postSaveMacro)
                        withinOffice('instruction', UnoCall.deadlineMillis()) {
                            use(OOoDocumentCategory) {
                                xComponent.executeMacro(postSaveMacro)
                            }
                            null
                        }
                    }
                } else {
                    output.clear()
                }
            } catch (Throwable error) {
                if (dropsSlot(error)) {
                    leavePool = true
                }
                throw error
            } finally {
                if (xComponent != null) {
                    long closeBudget = leavePool ? UnoCall.closeDeadlineMillis() : UnoCall.deadlineMillis()
                    try {
                        withinOffice('close', closeBudget) {
                            use(OOoDocumentCategory) {
                                xComponent.close()
                            }
                            null
                        }
                    } catch (UnoDeadlineExceeded closeDeadline) {
                        leavePool = true
                        log.error 'Odisee: Closing the office document exceeded its deadline', closeDeadline
                    } catch (Throwable closeError) {
                        log.error 'Odisee: Could not close the office document', closeError
                    }
                }
                if (leavePool) {
                    officeConnectionFactory?.dropSlot(oooConnection)
                }
            }
        }
        // Return generated document(s)
        output
    }

    /**
     * Read a XML file and generate an OpenOffice document.
     * @param file The Odisee XML request file to operate on.
     * @param oooConnectionManager
     * @param requestNumber If multiple requests are contained in the XML file, the number of the request to process, defaults to 0.
     * @param requestOverride
     * @return Map
     */
    static Map toDocument(Path file, OfficeConnectionFactory officeConnectionFactory, int requestNumber, requestOverride = null) {
        final long start = System.nanoTime()
        // Read XML from file
        Map arg = readRequest(file, requestNumber)
        // The request
        arg.activeRequestIndex = requestNumber
        // Add overrides
        if (requestOverride) {
            arg += requestOverride
        }
        // The connection
        OfficeConnection oooConnection = null
        // Our return value is a map with timing and output information
        final Map result = [output: [], retries: 0, wallTime: -1]
        try {
            // Get connection to OpenOffice. The request's group name selects the pool.
            final String group = groupOf(arg.xml.request[requestNumber])
            oooConnection = officeConnectionFactory.fetchConnection(group, false)
            if (!oooConnection) {
                throw new OdiseeException("Could not acquire connection from group '${group}'")
            } else {
                // Process template
                def output = OdiseeXmlCategory.processTemplate(arg, oooConnection, officeConnectionFactory)
                if (output) {
                    result.output += output
                }
                // Wall clock time
                result.wallTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                // A dry run is done when instructions resolve. It has no saved file.
                if (writesFile(arg) && output?.size() == 0) {
                    throw new OdiseeException('Got zero bytes from office process')
                }
            }
        } catch (e) {
            throw e
        } finally {
            if (officeConnectionFactory != null) {
                officeConnectionFactory.recordGenerationMillis(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
            }
            // A deadline already removed the slot so odiwatchdog can restart soffice.
            if (oooConnection && !oooConnection.wasDropped()) {
                officeConnectionFactory.repositConnection(oooConnection)
            }
        }
        result
    }

    /**
     * The v2 {@code <group name="..."/>} element selects the pool.
     * A missing element is {@code group0}. The legacy {@code ooo} element is not read.
     */
    static String groupOf(request) {
        String named = request?.group?.'@name'?.toString()?.trim()
        if (!named) {
            return OdiseeConstant.S_GROUP0
        }
        SafePaths.requireSimpleName(named, 'group')
    }

    /**
     * A dry run applies instructions and does not save the document.
     */
    static boolean writesFile(Map arg) {
        arg?.dryRun != true
    }

    /**
     * A deadline drops the office slot. A bad macro name does not.
     * A dry-run instruction failure is the same: 422, and the slot stays unless the deadline fired.
     */
    static boolean dropsSlot(Throwable error) {
        error instanceof UnoDeadlineExceeded
    }

    /**
     * A bad name stays HTTP 400. An ordinary instruction failure is recorded and becomes HTTP 422.
     */
    static void keepOrRecord(Throwable error, List<String> failures, String detail, OfficeConnectionFactory factory) {
        if (error instanceof UnoDeadlineExceeded) {
            throw (UnoDeadlineExceeded) error
        }
        if (error instanceof OdiseeException && ((OdiseeException) error).httpStatus == OdiseeException.BAD_REQUEST) {
            throw (OdiseeException) error
        }
        log.log(java.util.logging.Level.SEVERE, "Odisee: Could not execute instruction '${detail}'", error)
        failures << "${detail}: ${error.message ?: error.class.simpleName}"
        factory?.recordInstructionFailure()
    }

    /**
     * UNO work runs on another thread. Categories are thread-local, so the body applies its own.
     */
    private static Object withinOffice(String phase, long deadlineMs, Closure<?> body) {
        UnoCall.within(phase, deadlineMs, new Callable<Object>() {
            @Override
            Object call() {
                body.call()
            }
        })
    }

}
