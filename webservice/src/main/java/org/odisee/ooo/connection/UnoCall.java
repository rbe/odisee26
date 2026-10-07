/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */

package org.odisee.ooo.connection;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs one UNO call on a daemon thread and gives up when the deadline passes.
 * A wedged {@code soffice} does not unblock the caller. The pool then drops that slot.
 */
public final class UnoCall {

    public static final String DEADLINE_PROPERTY = "odisee.uno.deadline.ms";

    public static final String CLOSE_DEADLINE_PROPERTY = "odisee.uno.close.deadline.ms";

    public static final String RECOVER_DEADLINE_PROPERTY = "odisee.uno.recover.deadline.ms";

    public static final long DEFAULT_DEADLINE_MILLIS = 120_000L;

    public static final long DEFAULT_CLOSE_DEADLINE_MILLIS = 2_000L;

    public static final long DEFAULT_RECOVER_DEADLINE_MILLIS = 500L;

    private static final ExecutorService WORKERS = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "odisee-uno");
        thread.setDaemon(true);
        return thread;
    });

    private UnoCall() {
    }

    public static long deadlineMillis() {
        return positiveProperty(DEADLINE_PROPERTY, DEFAULT_DEADLINE_MILLIS);
    }

    public static long closeDeadlineMillis() {
        return positiveProperty(CLOSE_DEADLINE_PROPERTY, DEFAULT_CLOSE_DEADLINE_MILLIS);
    }

    public static long recoverDeadlineMillis() {
        return positiveProperty(RECOVER_DEADLINE_PROPERTY, DEFAULT_RECOVER_DEADLINE_MILLIS);
    }

    public static <T> T within(final String phase, final Callable<T> call) throws UnoDeadlineExceeded, OdiseeServerException {
        return within(phase, deadlineMillis(), call);
    }

    public static <T> T within(final String phase, final long deadlineMs, final Callable<T> call)
            throws UnoDeadlineExceeded, OdiseeServerException {
        Future<T> future = WORKERS.submit(call);
        try {
            return future.get(deadlineMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw new UnoDeadlineExceeded(phase, deadlineMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new OdiseeServerException("Interrupted during office " + phase, e);
        } catch (ExecutionException e) {
            throw unwrap(phase, e);
        }
    }

    private static OdiseeServerException unwrap(final String phase, final ExecutionException failure) {
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        if (cause instanceof UnoDeadlineExceeded deadline) {
            return deadline;
        }
        if (cause instanceof OdiseeServerException server) {
            return server;
        }
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (cause instanceof Error error) {
            throw error;
        }
        return new OdiseeServerException("Office " + phase + " failed", cause);
    }

    private static long positiveProperty(final String name, final long fallback) {
        String raw = System.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            long parsed = Long.parseLong(raw.trim());
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

}
