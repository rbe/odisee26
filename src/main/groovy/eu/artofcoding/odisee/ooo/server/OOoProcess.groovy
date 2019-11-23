/*
 * Odisee(R)
 *
 * Copyright (C) 2011-2015 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 02.02.15 18:36
 */
package eu.artofcoding.odisee.ooo.server

import com.sun.star.lib.uno.helper.UnoUrl
import com.sun.star.lib.util.NativeLibraryLoader
import eu.artofcoding.odisee.OdiseeException
import eu.artofcoding.odisee.helper.GroovyJvmHelper

/*
// Try to find an OOo installation if oooProgram is not set
// Maybe we don't need it as we use a TCP/IP connection to another server?
try {
    // Take first installation found
    if (!oooProgram) {
        oooProgram = findOOoInstallation()[0]
    }
} catch (e) {
    // Ignore
    println "Could not find local OpenOffice installation"
}
println "Found OOo installation at ${oooProgram}"
*/

/**
 *
 */
class OOoProcess {

    /**
     *
     */
    private static final String ONE_SPACE = ' '
    private static final String GREP = 'grep'
    private static final String SOFFICE = 'soffice'
    private static final String OPTION_V = '-v'
    private Integer startupTimeout = 5

    /**
     *
     */
    private String oooProgram

    /**
     * Options for starting an soffce process.
     */
    private String[] oooOptions

    /**
     *
     */
    private String pipe

    /**
     *
     */
    private String host

    /**
     *
     */
    private Integer port

    /**
     * String or Map (pipe or host, port)
     */
    private unoURL

    /**
     * Parsed UNO URL.
     */
    private UnoUrl parsedUnoURL

    /**
     * An soffice process.
     */
    private Process oooProcess

    /**
     * Set UNO URL.
     * @param unoURL String or Map (pipe or host, port)
     */
    void setUnoURL(unoURL) {
        // Don't do twice
        if (parsedUnoURL) {
            return
        }
        //println "setUnoURL(${unoURL.dump()})"
        switch (unoURL) {
            case { it instanceof String }:
                // Do nothing, assume complete UNO URL
                this.unoURL = unoURL
                break
            case { it instanceof Map }:
                if (unoURL.pipe) {
                    this.unoURL = "pipe,name=${unoURL.pipe ?: 'odisee1'};urp;StarOffice.ServiceManager" as String
                } else if (unoURL.host || unoURL.port) {
                    this.unoURL = "socket,host=${unoURL.host},port=${unoURL.port},tcpNoDelay=1;urp;StarOffice.ServiceManager" as String
                }
                break
            default:
                throw new OdiseeException("Don't know how to handle UNO URL: ${unoURL}")
        }
        //println "setUnoURL: unoURL=${this.unoURL.dump()}"
        // Helper function to parse an UNO URL
        parsedUnoURL = UnoUrl.parseUnoUrl(this.unoURL)
        //println "setUnoURL: parsedUnoURL=${this.parsedUnoURL.dump()}"
        /*
          //println "unoURL.getConnection     =" + parsedUnoURL.getConnection()
          //println "unoURL.getConnection..As =" + parsedUnoURL.getConnectionAndParametersAsString()
          //println "unoURL.getConnectionPara =" + parsedUnoURL.getConnectionParameters().keySet()
          //println "unoURL.getProtocol       =" + parsedUnoURL.getProtocol()
          //println "unoURL.getProtocolPara   =" + parsedUnoURL.getProtocolParameters().keySet()
          //println "unoURL.rootOid           =" + parsedUnoURL.getRootOid()
          */
    }

    /**
     * Return parsed UNO URL.
     */
    UnoUrl getParsedUnoURL() {
        if (!parsedUnoURL) {
            setUnoURL(unoURL)
        }
        parsedUnoURL
    }

    /**
     * Start an OpenOffice instance.
     * @return Process ID of started OpenOffice instance.
     */
    int start() {
        // Check if process is running
        int pid = getProcessId()
        if (oooProcess || pid) {
            //println "OOoProcess.start: Process already running: oooProcess=${oooProcess?.dump()} pid=${pid?.dump()}"
            return pid
        }
        // Check program folder
        if (!oooProgram) {
            throw new OdiseeException('Cannot start instance, no program folder')
        }
        //
        URL[] oooProgramURL = [new File(oooProgram).toURI().toURL()] //as URL[]
        URLClassLoader loader = new URLClassLoader(oooProgramURL)
        // Find OpenOffice executable
        String sofficeCmd = GroovyJvmHelper.OS_NAME ==~ GroovyJvmHelper.OS_WINDOWS ? 'soffice.exe' : SOFFICE
        File soffice = NativeLibraryLoader.getResource(loader, sofficeCmd)
        if (!soffice) {
            throw new OdiseeException("Cannot find executable ${sofficeCmd}")
        }
        // Set UNO URL
        setUnoURL(unoURL)
        // Start instance
        String cmd = "${soffice.path} -accept=${unoURL} ${oooOptions.join(" ")}"
        //println "OOoProcess: starting openoffice: ${cmd}"
        oooProcess = cmd.execute()
        // Give instance chance to come up
        print "waiting ${startupTimeout} seconds for instance to start..."
        try { Thread.sleep(startupTimeout * 1000) } catch (e) {}
        // Return process id
        getProcessId()
    }

    /**
     * Get process id of OpenOffice instance.
     * Windows: there are only 2 processes in any case: soffice.bin and soffice.exe.
     * @param all Get process ids for all OpenOffice instances.
     */
    List<Integer> getProcessId(all = false) {
        // Set UNO URL
        setUnoURL(unoURL)
        if (!unoURL) {
            throw new IllegalStateException('Cannot look for processes: no UNO URL')
        }
        // Process id
        List pid = []
        switch (GroovyJvmHelper.OS_NAME) {
            case { it ==~ GroovyJvmHelper.OS_DARWIN || it == GroovyJvmHelper.OS_LINUX || it == GroovyJvmHelper.OS_SUNOS || it ==~ GroovyJvmHelper.OS_BSD }:
                Process pse = ['/bin/sh', '-c', 'ps', '-e'].execute()
                Process grepv1 = [GREP, OPTION_V, GREP].execute()
                Process grepv2 = [GREP, OPTION_V, 'PID'].execute()
                // Find all OpenOffice instances or a certain pipe or socket?
                Process grepsoffice
                if (all) {
                    grepsoffice = [GREP, SOFFICE].execute()
                } else {
                    grepsoffice = [GREP, parsedUnoURL.getConnectionAndParametersAsString()].execute()
                }
                // Pipes processes
                pse | grepsoffice | grepv1 | grepv2
                grepv2.waitFor()
                // Split output to create list of process ids
                List lines = grepv2.text?.trim()?.split('\n')
                pid = lines.collect { it.split(ONE_SPACE)[0] }
                break
            case { it ==~ GroovyJvmHelper.OS_WINDOWS }:
                Process taskkill = 'tasklist /nh /fi \'IMAGENAME eq soffice.bin\''.execute()
                taskkill.waitFor()
                List lines = taskkill.text.trim().split('\r\n')
                // INFORMATION: Es werden keine Aufgaben mit den angegebenen Kriterien ausgef?hrt. ??
                if (pid == 'Es') {
                    pid = null
                } else {
                    pid = lines.collect { it.split(ONE_SPACE)[1] }
                }
                break
        }
        // Return list of found process ids; strip empty strings
        pid.findAll { !it?.empty } as List<Integer>
    }

    /**
     * Stop an OpenOffice instance we started earlier.
     */
    void stop() {
        killLocal()
        oooProcess = null
    }

    /**
     * Kill OpenOffice instance(s) by submitting OS commands.
     * @param pid Process ID to kill.
     * @return int ID of killed process.
     */
    private int killLocal(pid = null) {
        // Get process id and kill it
        pid = pid ?: processId
        if (pid && !pid.empty) {
            switch (GroovyJvmHelper.OS_NAME) {
                case { it ==~ GroovyJvmHelper.OS_DARWIN || it == GroovyJvmHelper.OS_LINUX || it == GroovyJvmHelper.OS_SUNOS || it ==~ GroovyJvmHelper.OS_BSD }:
                    pid.each {
                        "kill ${it}".execute()
                    }
                    break
                case { it ==~ GroovyJvmHelper.OS_WINDOWS }:
                    'taskkill /f /im soffice*'.execute()
                    break
            }
            pid
        } else {
            throw new IllegalStateException('Cannot kill process, no pid')
        }
    }

    /**
     * String representation of this OOo instance.
     */
    @Override
    String toString() {
        "${unoURL}@${processId.join('pid=')}" as String
    }

}
