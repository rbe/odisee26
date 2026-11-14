/*
 * Odisee
 * httputil - Utitlities for LOBs
 *
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann
 * Copyright (C) 2011 art of coding UG (haftungsbeschränkt)
 * Alle Rechte vorbehalten. All rights reserved.
 */

CREATE OR REPLACE PACKAGE BODY httputil
AS
    PROCEDURE apply_authorization(
        httpreq IN OUT UTL_HTTP.req
        , bearer_token IN VARCHAR2
    )
    AS
        trimmed VARCHAR2(4096) := TRIM(bearer_token);
    BEGIN
        IF trimmed IS NOT NULL AND LENGTH(trimmed) > 0 THEN
            UTL_HTTP.SET_HEADER(httpreq, 'Authorization', 'Bearer ' || trimmed);
        END IF;
    END;

    PROCEDURE write_utf8_body(
        httpreq IN OUT UTL_HTTP.req
        , data IN VARCHAR2
    )
    AS
        raw_body RAW(32767);
        byte_len PLS_INTEGER;
    BEGIN
        raw_body := UTL_I18N.STRING_TO_RAW(NVL(data, ''), 'AL32UTF8');
        byte_len := NVL(UTL_RAW.LENGTH(raw_body), 0);
        UTL_HTTP.SET_HEADER(httpreq, 'Content-Length', TO_CHAR(byte_len));
        IF byte_len > 0 THEN
            UTL_HTTP.WRITE_RAW(httpreq, raw_body);
        END IF;
    END;

    /*
     * Get data by a GET request and receive text.
     */
    PROCEDURE get(
        url IN VARCHAR2
        , result OUT CLOB
        , bearer_token IN VARCHAR2 DEFAULT NULL
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        httpreq := UTL_HTTP.begin_request(url, 'GET', 'HTTP/1.1');
        apply_authorization(httpreq, bearer_token);
        httpresp := UTL_HTTP.get_response(httpreq);
        process_response(url, httpresp, result);
    END;
    /*
     * Get data by a GET request and receive binary data.
     */
    PROCEDURE get(
        url IN VARCHAR2
        , result OUT BLOB
        , bearer_token IN VARCHAR2 DEFAULT NULL
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        httpreq := UTL_HTTP.begin_request(url, 'GET', 'HTTP/1.1');
        apply_authorization(httpreq, bearer_token);
        httpresp := UTL_HTTP.get_response(httpreq);
        process_response(url, httpresp, result);
    END;
    /*
     * Post a request and receive text.
     */
    PROCEDURE post(
        url IN VARCHAR2
        , data IN VARCHAR2
        , charset IN VARCHAR2 DEFAULT 'UTF-8'
        , result OUT CLOB
        , bearer_token IN VARCHAR2 DEFAULT NULL
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        httpreq := UTL_HTTP.begin_request(url, 'POST', 'HTTP/1.1');
        UTL_HTTP.set_body_charset(httpreq, 'UTF-8');
        UTL_HTTP.set_header(httpreq, 'Content-Type', 'text/xml; charset=UTF-8');
        apply_authorization(httpreq, bearer_token);
        write_utf8_body(httpreq, data);
        httpresp := UTL_HTTP.get_response(httpreq);
        process_response(url, httpresp, result);
    END;
    /*
     * Post a request and receive binary data.
     */
    PROCEDURE post(
        url IN VARCHAR2
        , data IN VARCHAR2
        , charset IN VARCHAR2 DEFAULT 'UTF-8'
        , result OUT BLOB
        , bearer_token IN VARCHAR2 DEFAULT NULL
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        post_document(
            url => url
            , data => data
            , content_type => 'text/xml; charset=UTF-8'
            , charset => charset
            , result => result
            , bearer_token => bearer_token
        );
    END;
    /*
     * Post a request with an explicit Content-Type and receive binary data.
     */
    PROCEDURE post_document(
        url IN VARCHAR2
        , data IN VARCHAR2
        , content_type IN VARCHAR2
        , charset IN VARCHAR2 DEFAULT 'UTF-8'
        , result OUT BLOB
        , bearer_token IN VARCHAR2 DEFAULT NULL
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        httpreq := UTL_HTTP.BEGIN_REQUEST(url, 'POST', 'HTTP/1.1');
        UTL_HTTP.SET_BODY_CHARSET(httpreq, 'UTF-8');
        UTL_HTTP.SET_HEADER(httpreq, 'Content-Type', content_type);
        apply_authorization(httpreq, bearer_token);
        write_utf8_body(httpreq, data);
        httpresp := UTL_HTTP.GET_RESPONSE(httpreq);
        process_response(url, httpresp, result);
    END;
    /*
     *
     */
    PROCEDURE process_response(
        url IN VARCHAR2
        , resp IN OUT UTL_HTTP.resp
        , result OUT CLOB
    )
    AS
        content_type VARCHAR2(100);
        html VARCHAR2(32767);
        status_code PLS_INTEGER := resp.status_code;
    BEGIN
        IF status_code >= 400 THEN
            UTL_HTTP.END_RESPONSE(resp);
            RAISE_APPLICATION_ERROR(-20001, 'HTTP ' || status_code);
        END IF;
        IF status_code = 200 THEN
            UTL_HTTP.GET_HEADER_BY_NAME(r => resp, name => 'Content-Type', value => content_type);
            DBMS_LOB.CREATETEMPORARY(result, FALSE);
            IF INSTR(content_type, 'text/') > 0 OR INSTR(content_type, 'application/json') > 0 THEN
                LOOP
                    UTL_HTTP.READ_TEXT(resp, html, 32767);
                    DBMS_LOB.WRITEAPPEND(result, LENGTH(html), html);
                END LOOP;
            END IF;
        END IF;
        UTL_HTTP.END_RESPONSE(resp);
    EXCEPTION
        WHEN UTL_HTTP.END_OF_BODY THEN
            UTL_HTTP.END_RESPONSE(resp);
    END;
    /*
     *
     */
    PROCEDURE process_response(
        url IN VARCHAR2
        , resp IN OUT UTL_HTTP.resp
        , result OUT BLOB
    )
    AS
        content_type VARCHAR2(100);
        bin RAW(32767);
        status_code PLS_INTEGER := resp.status_code;
    BEGIN
        IF status_code >= 400 THEN
            UTL_HTTP.END_RESPONSE(resp);
            RAISE_APPLICATION_ERROR(-20001, 'HTTP ' || status_code);
        END IF;
        IF status_code = 200 THEN
            UTL_HTTP.GET_HEADER_BY_NAME(r => resp, name => 'Content-Type', value => content_type);
            DBMS_LOB.CREATETEMPORARY(result, FALSE);
            IF INSTR(content_type, 'image/') > 0 OR INSTR(content_type, 'application/') > 0 THEN
                LOOP
                    UTL_HTTP.READ_RAW(resp, bin, 32767);
                    DBMS_LOB.WRITEAPPEND(result, UTL_RAW.length(bin), bin);
                END LOOP;
            END IF;
        END IF;
        UTL_HTTP.END_RESPONSE(resp);
    EXCEPTION
        WHEN UTL_HTTP.END_OF_BODY THEN
            UTL_HTTP.END_RESPONSE(resp);
    END;
    /*
     * Stream a BLOB to a HTTP client.
     */
    PROCEDURE stream_blob(
        document IN BLOB
        , mimetype IN VARCHAR2 DEFAULT 'application/octet-stream'
    )
    AS
        v_amt NUMBER DEFAULT 4096;
        v_off NUMBER DEFAULT 1;
        v_raw RAW(4096);
    BEGIN
        OWA_UTIL.MIME_HEADER(mimetype);
        BEGIN
            LOOP
                -- Read the BLOB
                DBMS_LOB.READ(document, v_amt, v_off, v_raw);
                -- Display BLOB
                HTP.PRN(UTL_RAW.CAST_TO_VARCHAR2(v_raw));
                v_off := v_off + v_amt;
                v_amt := 4096;
            END LOOP;
        EXCEPTION
            WHEN NO_DATA_FOUND THEN
                NULL;
        END;
    END;
END;
/
SHOW ERRORS;
