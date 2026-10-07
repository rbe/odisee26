package org.odisee.client;

/**
 * Wire format used when posting a document request.
 * XML is the default and remains what the service executes.
 */
public enum RequestFormat {
    XML,
    JSON
}
