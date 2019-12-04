/*
 * odisee-client-java
 * odisee-client-java
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe, 14.01.13 11:15
 */

package org.odisee.client;

public class OdiseeConstant {

    /**
     * XPath for last request below &lt;odisee> root element.
     */
    public static final String LAST_REQUEST = "//request[last()]";

    /**
     * XPath to select last instruction of a request element. Use when you 'have' the request element.
     */
    public static final String LAST_INSTRUCTION_OF_REQUEST = "instructions[last()]";

    /**
     * XPath to select last instruction of a post-pocress element. Use when you 'have' the post-process element.
     */
    public static final String LAST_INSTRUCTION_OF_POSTPROCESS = "instructions[last()]";

    /**
     * XPath for a selecting a request by name.
     */
    public static final String REQUEST_NAME = "//request[@name]";

    /**
     * Request cannot be processed, no service URL given.
     */
    public static final String ERR_NO_SERVICE_URL = "Cannot process request, no Odisee service URL";

    /**
     * Request cannot be processed. No authentication information, no username or and/or no password.
     */
    public static final String ERR_NO_AUTH_INFO = "Cannot process request, insufficient auth info";

}
