/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 20.12.14, 12:36
 */

package eu.artofcoding.odisee.server;

import com.sun.star.lib.util.NativeLibraryLoader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

public final class OfficeProcess {

    public String officeProgramPath = "/Applications/LibreOffice.app/Contents/MacOS";

    private static void pipe(final InputStream in, final PrintStream out, final String prefix) {
        new Thread("Pipe: " + prefix) {
            @Override
            public void run() {
                BufferedReader r = new BufferedReader(new InputStreamReader(in));
                try {
                    for (; ; ) {
                        String s = r.readLine();
                        if (s == null) {
                            break;
                        }
                        out.println(prefix + s);
                    }
                } catch (java.io.IOException e) {
                    e.printStackTrace(System.err);
                }
            }
        }.start();
    }

    private static List<String> getHeadlessRunOptions() {
        final List<String> options = new ArrayList<>();
        options.add("--headless");
        options.add("--nologo");
        options.add("--nodefault");
        options.add("--norestore");
        options.add("--nolockcheck");
        return options;
    }

    public Process startOfficeProcess(final InetSocketAddress socketAddress) throws OdiseeServerException {
        final String sOffice = System.getProperty("os.name").startsWith("Windows") ? "soffice.exe" : "soffice";
        File officeExecutable;
        try {
            final URL[] oooExecFolderURL = new URL[]{new File(officeProgramPath).toURI().toURL()};
            final URLClassLoader loader = new URLClassLoader(oooExecFolderURL);
            officeExecutable = NativeLibraryLoader.getResource(loader, sOffice);
            if (officeExecutable == null) {
                throw new OdiseeServerException("No office executable found!");
            }
        } catch (MalformedURLException e) {
            final String message = String.format("Invalid path (%s) to office executable", officeProgramPath);
            throw new OdiseeServerException(message, e);
        }
        final File programDir = officeExecutable.getParentFile();
        final String unoURL = UnoHelper.makeUnoUrl(socketAddress);
        final String odiseeTmpDirectory = JvmHelper.findTemporaryDirectory();
        final String[] oooCommand = makeCommandLineOptions(officeExecutable, unoURL, odiseeTmpDirectory);
        try {
            final Process process = Runtime.getRuntime().exec(oooCommand, null, programDir);
            pipe(process.getInputStream(), System.out, "CO> ");
            pipe(process.getErrorStream(), System.err, "CE> ");
            return process;
        } catch (IOException e) {
            throw new OdiseeServerException("Cannot start office process", e);
        }
    }

    private String[] makeCommandLineOptions(final File officeExecutable, final String unoURL, final String odisee_tmp) throws OdiseeServerException {
        final List<String> options = new ArrayList<>();
        //final String userInstallation = String.format("-env:UserInstallation=\"file:///%s/odisee_port%d\"", odisee_tmp, UnoHelper.getPort(unoURL));
        options.add("-env:UserInstallation=\"file:///" + odisee_tmp + "/odisee_port" + UnoHelper.getPort(unoURL) + "\"".replaceAll("//", "/"));
        options.add(String.format("--accept=\"%s\"", unoURL));
        options.addAll(getHeadlessRunOptions());
        final int arguments = options.size() + 1;
        final String[] cmd = new String[arguments];
        final String prefix = System.getProperty("os.name").startsWith("Windows") ? "" : "./soffice";
        cmd[0] = prefix + officeExecutable.getName();
        for (int i = 0; i < options.size(); i++) {
            cmd[i + 1] = options.get(i);
        }
        return cmd;
    }

    /*
    public static void main(String[] args) throws Exception {
        final OfficeProcess officeProcess = new OfficeProcess();
        final InetSocketAddress socketAddress = new InetSocketAddress("127.0.0.1", 2001);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    officeProcess.startOfficeProcess(socketAddress);
                } catch (OdiseeServerException e) {
                    throw new OdiseeServerRuntimeException(e);
                }
            }
        };
        final Thread t = new Thread(r);
        t.setDaemon(true);
        t.start();
        Thread.sleep(5 * 1000);
    }
    */

}
