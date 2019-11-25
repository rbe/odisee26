/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 01.02.15, 15:03
 */

package org.odisee.ooo.process;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProcessIOStreamerThread extends Thread {

    private final AtomicBoolean work = new AtomicBoolean(true);

    private final InputStream in;

    private final PrintStream out;

    private final String prefix;

    public ProcessIOStreamerThread(final InputStream in, final PrintStream out, final String prefix) {
        super(String.format("%s-Pipe: %s", ProcessIOStreamerThread.class.getSimpleName(), prefix));
        this.in = in;
        this.out = out;
        this.prefix = prefix;
    }

    @Override
    public void run() {
        try (final BufferedReader r = new BufferedReader(new InputStreamReader(in))) {
            while (work.get()) {
                final String s = r.readLine();
                if (s == null) {
                    break;
                }
                out.printf("%s%s%n", prefix, s);
            }
        } catch (IOException e) {
            e.printStackTrace(System.err);
        }
    }

    public void stopWork() {
        work.getAndSet(false);
    }

    public static ProcessIOStreamerThread pipe(final InputStream input, final PrintStream output, final String prefix) {
        ProcessIOStreamerThread t = new ProcessIOStreamerThread(input, output, prefix);
        t.start();
        return t;
    }

}
