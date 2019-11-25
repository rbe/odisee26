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
import org.odisee.api.OdiseeException
import org.odisee.debug.Profile
import org.odisee.shared.OdiseeConstant
import org.odisee.ooo.connection.OdiseeServerRuntimeException
import org.odisee.ooo.connection.OfficeConnection
import org.odisee.ooo.connection.OfficeConnectionFactory
import org.odisee.writer.OOoAutotextCategory
import org.odisee.writer.OOoBookmarkCategory
import org.odisee.writer.OOoDocumentCategory
import org.odisee.writer.OOoFieldCategory
import org.odisee.writer.OOoImageCategory
import org.odisee.writer.OOoTextTableCategory
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.xml.bind.DatatypeConverter
import java.nio.charset.Charset
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
 * $ODISEE_VAR/template/name without extension/revision/name_revision.ott
 * $ODISEE_VAR/document/name without extension/name_revision.odt, .pdf
 */
class OdiseeXmlCategory {

    private static final Logger LOGGER = LoggerFactory.getLogger(OdiseeXmlCategory.class)

    /**
     * Find latest revision of a file with name following this convention:
     * name_revN.ext
     */
    static Path findLatestRevision(Path dir) {
        if (!Files.exists(dir)) {
            throw new OdiseeException("Cannot find template in directory '${dir}'")
        }
        Path[] fs = dir.listFiles()
        // TODO NullPointer when cN == directory
        fs.inject fs[0], { Path o, Path n ->
            // Strip extension and find _rev in filename
            def c1 = (o.name - ~OdiseeConstant.WRITER_EXT_REGEX).split(OdiseeConstant.S_UNDERSCORE).find { it ==~ OdiseeConstant.REVISION_REGEX }
            def c2 = (n.name - ~OdiseeConstant.WRITER_EXT_REGEX).split(OdiseeConstant.S_UNDERSCORE).find { it ==~ OdiseeConstant.REVISION_REGEX }
            c2.compareTo(c1) == 1 ? n : o
        }
    }

    /**
     * Find OpenOffice template by revision and return File object.
     * @param xmlTemplate Node xml.request.template from request XML.
     */
    static Path findTemplate(xmlTemplate) {
        // Check argument
        if (!xmlTemplate) {
            throw new OdiseeException('No template specified')
        }
        // Get data
        String templatePath = xmlTemplate.'@path'?.toString()
        if (!templatePath) {
            throw new OdiseeException('No path to template given')
        }
        String revision = xmlTemplate.'@revision'?.toString()?.toUpperCase()
        // Get template
        Path template = null
        if (templatePath && revision != OdiseeConstant.S_LATEST) {
            template = Paths.get(templatePath)
        }
        // Find latest revision of template
        if (!revision || revision == OdiseeConstant.S_LATEST) {
            Path p = template.parent.resolve(template.fileName)
            template = OdiseeXmlCategory.findLatestRevision(p)
        }
        // Does template exist?
        if (!Files.exists(template)) {
            throw new OdiseeException("Odisee: Template '${template}' does not exist!")
        }
        template
    }

    /**
     * Process something: execute a closure and call a macro.
     */
    static void processInstruction(XComponent template, closure, macro = null) {
        // Execute pre-macro
        if (macro?.pre?.name) {
            use(OOoDocumentCategory) {
                template.executeMacro(macro.pre.name, (macro.pre.params ?: []) as Object[])
            }
        }
        // Execute closure
        closure(template)
        // Execute post-macro
        if (macro?.post?.name) {
            use(OOoDocumentCategory) {
                template.executeMacro(macro.post.name, (macro.post.params ?: []) as Object[])
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
        byte[] imageData = DatatypeConverter.parseBase64Binary(imageContent)
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
        // Get name, location and language of macro
        String macroName = macro.'@name'.toString()
        String location = macro.'@location'.toString() ?: 'document'
        String language = macro.'@language'.toString() ?: 'Basic'
        String macroUrl = "${macroName}?language=${language}&location=${location}"
        //
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
     * Read request and return map.
     */
    static Map readRequest(Path file, int requestNumber) {
        Map arg = [:]
        /*
        // TODO Use validating XmlSlurper
        validator = javax.xml.validation.SchemaFactory
                .newInstance(javax.xml.XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new javax.xml.transform.stream.StreamSource(xsdStream))
                .newValidator()
        // XmlSlurper for reading XML
        xmlSlurper = new XmlSlurper()
        // Read XML using locally cached DTDs
        xmlSlurper.setEntityResolver(com.bensmann.griffon.CachedDTD.entityResolver)
        */
        final String xmlText = file.toFile().getText(OdiseeConstant.S_UTF8)
        arg.xml = new XmlSlurper().parseText(xmlText)
        //
        def request = arg.xml.request[requestNumber]
        // The ID, if none given use actual date and time
        arg.id = request.'@id'?.toString()
        arg.id ?: (arg.id = new Date().format(OdiseeConstant.FILE_DATEFORMAT_SSSS))
        // Get File reference to certain or latest revision of template
        arg.template = OdiseeXmlCategory.findTemplate(request.template)
        // Set revision from found template
        arg.revision = (arg.template.fileName.toString() - ~OdiseeConstant.WRITER_EXT_REGEX).split('_rev').last()
        // Return map
        arg
    }

    static ArrayList processTemplate(Map arg, OfficeConnection oooConnection) {
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
        if (request.'@name') {
            documentBasename = request.'@name'.toString()
            final Charset utf8 = Charset.forName('UTF-8')
            final byte[] requestNameAsUTF8 = documentBasename.getBytes(utf8)
            documentBasename = new String(requestNameAsUTF8, utf8)
        } else {
            final String filename = arg.template.fileName.toString()
            final List strings = filename.split('\\.')[0..-2]
            final String join = strings.join('.')
            documentBasename = "${join}-id${arg.id}"
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
            // Create new document from template
            final XComponent xComponent = arg.template.open(oooConnection, [Hidden: hidden])
            // Process all instructions
            String methodName = null
            request.instructions.'*'.each { instr ->
                String tagName = instr.name()
                String instruction = instr.'@name'
                Profile.time "OdiseeXmlCategory.toDocument(${request.'@name'}, instruction ${instruction})", {
                    try {
                        // Construct method name from element name
                        methodName = tagName[0].toUpperCase() + tagName[1..-1]
                        OdiseeXmlCategory."process${methodName}"(xComponent, arg, instr)
                    } catch (e) {
                        LOGGER.error "Odisee: Could not execute instruction '${tagName} ${instruction}'", e
                    }
                }
            }
            // Refresh text fields
            use(OOoFieldCategory) {
                xComponent.refreshTextFields()
            }
            // Execute pre-save macro
            final String preSaveMacro = template.'@pre-save-macro'.toString()
            if (preSaveMacro) {
                xComponent.executeMacro(preSaveMacro)
            }
            // Create Path references for outputFormats from XML
            final Path outputDir = Paths.get(outputPath)
            template.'@outputFormat'?.toString()?.split(',')?.each { format ->
                output << outputDir.resolve("${documentBasename}.${format}")
            }
            // Save document to disk
            output.each { Path file ->
                Files.createDirectories(file.parent)
                boolean isPDFA = file.toString().endsWith('.pdfa')
                if (isPDFA) {
                    xComponent.saveAsPDF_A(file)
                } else {
                    xComponent.saveAs(file)
                }
            }
            // Execute post-save macro
            final String postSaveMacro = template.'@post-save-macro'.toString()
            if (postSaveMacro) {
                xComponent.executeMacro(postSaveMacro)
            }
            // Close document
            xComponent.close()
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
            // Get connection to OpenOffice
            final String group = 'group0'
            oooConnection = officeConnectionFactory.fetchConnection(false)
            if (!oooConnection) {
                throw new OdiseeException("Could not acquire connection from group '${group}'")
            } else {
                // Process template
                def output = OdiseeXmlCategory.processTemplate(arg, oooConnection)
                if (output) {
                    result.output += output
                }
                // Wall clock time
                result.wallTime += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)
                // Check result
                if (output?.size() == 0) {
                    /* TODO Should this class decide this?
                    if (oooConnection) {
                        oooConnection.setFaulted(true)
                    }
                    */
                    throw new OdiseeException('Got zero bytes from office process')
                }
            }
        } catch (e) {
            throw e
        } finally {
            // Release connection to pool
            if (oooConnection) {
                officeConnectionFactory.repositConnection(oooConnection)
            }
        }
        result
    }

}
