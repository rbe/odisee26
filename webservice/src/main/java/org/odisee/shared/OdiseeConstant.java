/*
 * Odisee
 *
 * Copyright (C) 2011-2019 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Nutzung unterliegt Lizenzbedingungen. Use is subject to license terms.
 *
 * rbe, 16.03.17, 18:28
 */

package org.odisee.shared;

public final class OdiseeConstant {

    public static final String BLANK = "_blank";

    public static final String NATIVE_TEMPLATE_REGEX = ".*\\.ot[gpst]";

    public static final String NATIVE_DOCUMENT_REGEX = ".*\\.od[gpst]";

    public static final String WRITER_EXT_REGEX = ".o\\wt$";

    public static final String REVISION_REGEX = "rev\\d+";

    public static final int THOUSAND = 1000;

    public static final int TIMEOUT_1SEC = 1 * THOUSAND;

    public static final int UNUSABLE_TIMEOUT = 30 * THOUSAND;

    public static final int SPIN_TIMEOUT = 1 * THOUSAND;

    public static final int MINUS_ONE = -1;

    public static final String S_DOT = ".";
    
    public static final String S_UNDERSCORE = "_";

    public static final String S_UTF8 = "UTF-8";

    public static final String S_TRUE = "true";

    public static final String S_FALSE = "false";

    public static final String S_GROUP0 = "group0";

    public static final String S_LATEST = "LATEST";
    
    public static final String S_ETC_ODIINST = "etc/odiinst";
    
    public static final String S_ODISEE_DEBUG = "ODISEE_DEBUG";
    
    public static final String S_ODISEE_PROFILE = "ODISEE_PROFILE";
    
    public static final String S_ODISEE_HOME = "ODISEE_HOME";

    public static final String S_ODISEE_DEPLOY = "ODISEE_DEPLOY";

    public static final String S_ODISEE_VAR = "ODISEE_VAR";

    public static final String S_ODISEE_USER = "ODISEE_USER";

    public static final String S_ODISEE_TMP = "ODISEE_TMP";

    public static final String S_VAR = "var";

    public static final String S_VAR_DEPLOY = "var/deploy";
    
    public static final String S_VAR_TMP = "var/tmp";

    public static final String S_USER = "user";

    public static final String S_TEMPLATE = "template";

    public static final String S_DOCUMENT = "document";

    public static final String S_PDF = "pdf";

    public static final String S_ID = "id";

    public static final String S_REVISION = "revision";

    public static final String FILE_DATEFORMAT_SSSS = "yyyyMMdd-HHmmss_SSSS";

    public static final String MIME_TYPE_OCTET_STREAM = "application/octet-stream";

    public static final String MIME_TYPE_ODT = "application/vnd.oasis.opendocument.text";

    public static final String MIME_TYPE_WORD97 = "application/vnd.oasis.opendocument.text";

    public static final String MIME_TYPE_WORDXML = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    public static final String MIME_TYPE_PDF = "application/pdf";

    private OdiseeConstant() {
        throw new AssertionError();
    }

}
