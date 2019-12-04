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
    /*
     * Get data by a GET request and receive text.
     */
    PROCEDURE get(
        url IN VARCHAR2
        , result OUT CLOB
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        httpreq := UTL_HTTP.begin_request(url, 'GET', 'HTTP/1.1');
        httpresp := UTL_HTTP.get_response(httpreq);
        process_response(url, httpresp, result);
    END;
    /*
     * Get data by a GET request and receive binary data.
     */
    PROCEDURE get(
        url IN VARCHAR2
        , result OUT BLOB
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        httpreq := UTL_HTTP.begin_request(url, 'GET', 'HTTP/1.1');
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
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        httpreq := UTL_HTTP.begin_request(url, 'POST', 'HTTP/1.1');
        UTL_HTTP.set_body_charset(httpreq, charset);
        UTL_HTTP.set_header(httpreq, 'Content-Type', 'text/xml');
        UTL_HTTP.set_header(httpreq, 'Content-Length', LENGTH(data));
        UTL_HTTP.write_text(httpreq, data);
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
    )
    AS
        httpreq UTL_HTTP.req;
        httpresp UTL_HTTP.resp;
    BEGIN
        httpreq := UTL_HTTP.BEGIN_REQUEST(url, 'POST', 'HTTP/1.1');
        UTL_HTTP.SET_BODY_CHARSET(httpreq, charset);
        UTL_HTTP.SET_HEADER(httpreq, 'Content-Type', 'text/xml');
        UTL_HTTP.SET_HEADER(httpreq, 'Content-Length', LENGTH(data));
        UTL_HTTP.write_text(httpreq, data);
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
    BEGIN
        -- Success?
        IF resp.status_code = 200 THEN
            -- Read answer
            UTL_HTTP.GET_HEADER_BY_NAME(r => resp, name => 'Content-Type', value => content_type);
            -- Initialize the CLOB.
            DBMS_LOB.CREATETEMPORARY(result, FALSE);
            -- BLOB?
            IF INSTR(content_type, 'text/') > 0 THEN
                -- Read the CLOB
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
    BEGIN
        -- Success?
        IF resp.status_code = 200 THEN
            -- Read answer
            UTL_HTTP.GET_HEADER_BY_NAME(r => resp, name => 'Content-Type', value => content_type);
            -- Initialize the BLOB.
            DBMS_LOB.CREATETEMPORARY(result, FALSE);
            -- BLOB?
            IF INSTR(content_type, 'image/') > 0 OR INSTR(content_type, 'application/') > 0 THEN
                -- Read the BLOB
                LOOP
                    UTL_HTTP.READ_RAW(resp, bin, 32767);
                    DBMS_LOB.WRITEAPPEND(result, UTL_RAW.length(bin), bin);
                END LOOP;
            -- Relase the resources associated with the temporary LOB.
            --DBMS_LOB.freetemporary(result);
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
