/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 */
package org.odisee.document

import groovy.json.JsonOutput

/**
 * JSON for the job routes. {@code POST /document/jobs} is 202 and a job id.
 * {@code GET /document/jobs/{id}} is the status, the failed instruction, and the file.
 */
final class JobResponses {

    static final int ACCEPTED = 202

    private JobResponses() {
    }

    static String accepted(String id) {
        JsonOutput.toJson([id: id])
    }

    static String statusBody(Map job, byte[] file) {
        JsonOutput.toJson([
                id               : job?.id,
                status           : job?.status,
                failedInstruction: job?.failedInstruction,
                filename         : job?.filename,
                file             : file == null ? null : Base64.encoder.encodeToString(file)
        ])
    }

    static String callbackBody(Map job) {
        JsonOutput.toJson([
                id               : job?.id,
                status           : job?.status,
                failedInstruction: job?.failedInstruction,
                file             : job?.id ? "/odisee/document/jobs/${job.id}" : null
        ])
    }

    static String failedInstruction(Throwable error) {
        if (!(error instanceof org.odisee.api.OdiseeException)) {
            return null
        }
        org.odisee.api.OdiseeException odisee = (org.odisee.api.OdiseeException) error
        if (odisee.httpStatus != org.odisee.api.OdiseeException.UNPROCESSABLE) {
            return null
        }
        String message = odisee.message ?: ''
        String prefix = 'Document instructions failed: '
        message.startsWith(prefix) ? message.substring(prefix.length()) : message
    }

}
