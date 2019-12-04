/*
 * Odisee
 * httputil - Utitlities for LOBs
 *
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann
 * Copyright (C) 2011 art of coding UG (haftungsbeschränkt)
 * Alle Rechte vorbehalten. All rights reserved.
 */

CREATE OR REPLACE PACKAGE httputil
AS
    PROCEDURE get(
        url IN VARCHAR2
        , result OUT CLOB
    );
    PROCEDURE get(
        url IN VARCHAR2
        , result OUT BLOB
    );
    PROCEDURE post(
        url IN VARCHAR2
        , data IN VARCHAR2
        , charset IN VARCHAR2 DEFAULT 'UTF-8'
        , result OUT CLOB
    );
    PROCEDURE post(
        url IN VARCHAR2
        , data IN VARCHAR2
        , charset IN VARCHAR2 DEFAULT 'UTF-8'
        , result OUT BLOB
    );
    PROCEDURE process_response(
        url IN VARCHAR2
        , resp IN OUT UTL_HTTP.resp
        , result OUT CLOB
    );
    PROCEDURE process_response(
        url IN VARCHAR2
        , resp IN OUT UTL_HTTP.resp
        , result OUT BLOB
    );
    PROCEDURE stream_blob(
        document IN BLOB
        , mimetype IN VARCHAR2 DEFAULT 'application/octet-stream'
    );
END;
/
SHOW ERRORS;
