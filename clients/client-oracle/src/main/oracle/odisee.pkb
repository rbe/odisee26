/*
 * Odisee
 *
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann
 * Copyright (C) 2011 art of coding UG (haftungsbeschränkt)
 * Alle Rechte vorbehalten. All rights reserved.
 */

CREATE OR REPLACE PACKAGE BODY odisee
AS
    -- The OOo XML request, or JSON fragments when request_format = 'json'
    TYPE xmltype IS TABLE OF VARCHAR2(32767);
    xml xmltype := xmltype();
    json_first_instruction BOOLEAN := TRUE;

    FUNCTION json_escape(value IN VARCHAR2) RETURN VARCHAR2
    IS
        escaped VARCHAR2(32767) := NVL(value, '');
    BEGIN
        escaped := REPLACE(escaped, '\', '\\');
        escaped := REPLACE(escaped, '"', '\"');
        escaped := REPLACE(escaped, CHR(8), '\b');
        escaped := REPLACE(escaped, CHR(12), '\f');
        escaped := REPLACE(escaped, CHR(10), '\n');
        escaped := REPLACE(escaped, CHR(13), '\r');
        escaped := REPLACE(escaped, CHR(9), '\t');
        RETURN escaped;
    END;

    FUNCTION xml_escape(value IN VARCHAR2) RETURN VARCHAR2
    IS
        escaped VARCHAR2(32767) := NVL(value, '');
    BEGIN
        escaped := REPLACE(escaped, '&', '&amp;');
        escaped := REPLACE(escaped, '<', '&lt;');
        escaped := REPLACE(escaped, '>', '&gt;');
        escaped := REPLACE(escaped, '"', '&quot;');
        escaped := REPLACE(escaped, '''', '&apos;');
        RETURN escaped;
    END;

    FUNCTION json_bool(flag IN BOOLEAN) RETURN VARCHAR2
    IS
    BEGIN
        IF flag THEN
            RETURN 'true';
        END IF;
        RETURN 'false';
    END;

    FUNCTION image_type(url IN VARCHAR2) RETURN VARCHAR2
    IS
        lower_url VARCHAR2(32767) := LOWER(NVL(url, ''));
    BEGIN
        IF lower_url LIKE '%.jpg' OR lower_url LIKE '%.jpeg' THEN
            RETURN 'image/jpeg';
        END IF;
        RETURN 'image/png';
    END;

    PROCEDURE append_instruction(fragment IN VARCHAR2)
    IS
    BEGIN
        xml.EXTEND;
        IF request_format = 'json' THEN
            IF json_first_instruction THEN
                xml(xml.LAST) := fragment;
                json_first_instruction := FALSE;
            ELSE
                xml(xml.LAST) := ',' || fragment;
            END IF;
        ELSE
            xml(xml.LAST) := fragment;
        END IF;
    END;

    FUNCTION userfield_json(
        field_name IN VARCHAR2
        , field_value IN VARCHAR2
        , post_set_macro IN VARCHAR2
    ) RETURN VARCHAR2
    IS
        fragment VARCHAR2(32767);
    BEGIN
        fragment := '{"instruction":"userfield","name":"' || json_escape(field_name)
            || '","value":"' || json_escape(field_value) || '"';
        IF post_set_macro IS NOT NULL AND LENGTH(post_set_macro) > 0 THEN
            fragment := fragment || ',"postMacro":"' || json_escape(post_set_macro) || '"';
        END IF;
        RETURN fragment || '}';
    END;

    FUNCTION userfield_xml(
        field_name IN VARCHAR2
        , field_value IN VARCHAR2
        , post_set_macro IN VARCHAR2
    ) RETURN VARCHAR2
    IS
        fragment VARCHAR2(32767);
    BEGIN
        fragment := '<userfield name="' || xml_escape(field_name) || '"';
        IF post_set_macro IS NOT NULL AND LENGTH(post_set_macro) > 0 THEN
            fragment := fragment || ' post-macro="' || xml_escape(post_set_macro) || '"';
        END IF;
        RETURN fragment || '>' || xml_escape(field_value) || '</userfield>';
    END;

    FUNCTION endpoint_url RETURN VARCHAR2
    IS
        base VARCHAR2(1000) := RTRIM(NVL(odisee_service_url, ''), '/');
    BEGIN
        IF base LIKE '%/odisee/document/generate' THEN
            RETURN base;
        ELSIF base LIKE '%/odisee' THEN
            RETURN base || '/document/generate';
        END IF;
        RETURN base || '/odisee/document/generate';
    END;

    PROCEDURE set_bearer_token(token IN VARCHAR2)
    IS
    BEGIN
        bearer_token := token;
    END;

    PROCEDURE use_json
    IS
    BEGIN
        request_format := 'json';
    END;

    PROCEDURE use_xml
    IS
    BEGIN
        request_format := 'xml';
    END;
    /*
     * Create header for v2 XML or JSON request.
     */
    PROCEDURE header(
        ooo_host IN VARCHAR2 DEFAULT '127.0.0.1'
        , ooo_port IN NUMBER DEFAULT 2002
        , template IN VARCHAR2
        , id IN NUMBER
        , output IN VARCHAR2
        , output_format IN VARCHAR2 DEFAULT 'pdf'
        , pre_save_macro IN VARCHAR2 DEFAULT ''
        , archivedb IN BOOLEAN DEFAULT TRUE
        , archivefiles IN BOOLEAN DEFAULT FALSE
    )
    AS
        template_tag VARCHAR2(32767);
    BEGIN
        IF request_format = 'json' THEN
            json_first_instruction := TRUE;
            xml.DELETE;
            xml.EXTEND;
            xml(1) := '{"request":[{"name":"' || json_escape(template) || '","id":"' || json_escape(TO_CHAR(id))
                || '","template":{"name":"' || json_escape(template) || '","outputFormat":"' || json_escape(output_format)
                || '","revision":"LATEST"';
            IF pre_save_macro IS NOT NULL AND LENGTH(pre_save_macro) > 0 THEN
                xml(1) := xml(1) || ',"preSaveMacro":"' || json_escape(pre_save_macro) || '"';
            END IF;
            xml(1) := xml(1) || '},"archive":{"database":' || json_bool(archivedb) || ',"files":' || json_bool(archivefiles)
                || '},"instructions":[';
            RETURN;
        END IF;
        template_tag := '<template name="' || xml_escape(template)
            || '" outputFormat="' || xml_escape(output_format)
            || '" revision="LATEST"';
        IF pre_save_macro IS NOT NULL AND LENGTH(pre_save_macro) > 0 THEN
            template_tag := template_tag || ' pre-save-macro="' || xml_escape(pre_save_macro) || '"';
        END IF;
        template_tag := template_tag || '/>';
        xml.DELETE;
        xml.EXTEND(6);
        xml(1) := '<?xml version="1.0" encoding="UTF-8"?>';
        xml(2) := '<odisee xmlns="http://xmlns.odisee.de/v2/request">';
        xml(3) := '<request name="' || xml_escape(template) || '" id="' || xml_escape(TO_CHAR(id)) || '">';
        xml(4) := template_tag;
        xml(5) := '<archive database="' || json_bool(archivedb) || '" files="' || json_bool(archivefiles) || '"/>';
        xml(6) := '<instructions>';
    END;
    /*
     * Create footer for v2 XML or JSON request.
     */
    PROCEDURE footer
    AS
    BEGIN
        IF request_format = 'json' THEN
            xml.EXTEND;
            xml(xml.LAST) := ']}]}';
            RETURN;
        END IF;
        xml.EXTEND(3);
        xml(xml.LAST - 2) := '</instructions>';
        xml(xml.LAST - 1) := '</request>';
        xml(xml.LAST) := '</odisee>';
    END;
    /*
     * Add a VARCHAR2 field.
     */
    PROCEDURE addfield(
        name IN VARCHAR2
        , value IN VARCHAR2 DEFAULT ''
        , post_set_macro IN VARCHAR2 DEFAULT ''
    )
    AS
    BEGIN
        IF request_format = 'json' THEN
            append_instruction(userfield_json(name, value, post_set_macro));
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := userfield_xml(name, value, post_set_macro);
    END;
    /*
     * Add a number field.
     */
    PROCEDURE addfield(
        name IN VARCHAR2
        , value IN NUMBER DEFAULT ''
        , post_set_macro IN VARCHAR2 DEFAULT ''
    )
    AS
    BEGIN
        IF request_format = 'json' THEN
            append_instruction(userfield_json(name, TO_CHAR(value), post_set_macro));
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := userfield_xml(name, TO_CHAR(value), post_set_macro);
    END;
    /*
     * Add a DATE field.
     */
    PROCEDURE addfield(
        name IN VARCHAR2
        , value IN DATE DEFAULT ''
        , format IN VARCHAR2 DEFAULT 'DD.MM.YYYY'
        , post_set_macro IN VARCHAR2 DEFAULT ''
    )
    AS
    BEGIN
        IF request_format = 'json' THEN
            append_instruction(userfield_json(name, TO_CHAR(value, format), post_set_macro));
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := userfield_xml(name, TO_CHAR(value, format), post_set_macro);
    END;
    /*
     * Set/add a VARCHAR2 in a table cell.
     */
    PROCEDURE addtablecell(
        tablename IN VARCHAR2
        , rownm IN NUMBER
        , colnum IN NUMBER
        , value IN VARCHAR2 DEFAULT ''
        , post_set_macro IN VARCHAR2 DEFAULT ''
    )
    AS
        table_coord VARCHAR2(100);
    BEGIN
        table_coord := tablename || '$' || rownm || '$' || colnum;
        IF request_format = 'json' THEN
            append_instruction(userfield_json(table_coord, value, post_set_macro));
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := userfield_xml(table_coord, value, post_set_macro);
    END;
    /*
     * Set/add a NUMBER in a table cell.
     */
    PROCEDURE addtablecell(
        tablename IN VARCHAR2
        , rownm IN NUMBER
        , colnum IN NUMBER
        , value IN NUMBER DEFAULT ''
        , post_set_macro IN VARCHAR2 DEFAULT ''
    )
    AS
        table_coord VARCHAR2(100);
    BEGIN
        table_coord := tablename || '$' || rownm || '$' || colnum;
        IF request_format = 'json' THEN
            append_instruction(userfield_json(table_coord, TO_CHAR(value), post_set_macro));
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := userfield_xml(table_coord, TO_CHAR(value), post_set_macro);
    END;
    /*
     * Set/add a DATE in a table cell.
     */
    PROCEDURE addtablecell(
        tablename IN VARCHAR2
        , rownm IN NUMBER
        , colnum IN NUMBER
        , value IN DATE DEFAULT ''
        , format IN VARCHAR2 DEFAULT 'DD.MM.YYYY'
        , post_set_macro IN VARCHAR2 DEFAULT ''
    )
    AS
        table_coord VARCHAR2(100);
    BEGIN
        table_coord := tablename || '$' || rownm || '$' || colnum;
        IF request_format = 'json' THEN
            append_instruction(userfield_json(table_coord, TO_CHAR(value, format), post_set_macro));
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := userfield_xml(table_coord, TO_CHAR(value, format), post_set_macro);
    END;
    /*
     * Set width of a table cell.
     */
    PROCEDURE settablecellwidth(
        tablename IN VARCHAR2
        , widths IN VARCHAR2
        , post_set_macro IN VARCHAR2 DEFAULT ''
    )
    AS
    BEGIN
        IF request_format = 'json' THEN
            append_instruction(userfield_json('setwidth_' || tablename, widths, post_set_macro));
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := userfield_xml('setwidth_' || tablename, widths, post_set_macro);
    END;
    /*
     * Align a table cell.
     */
    PROCEDURE aligntablecell(
        tablename IN VARCHAR2
        , aligns IN VARCHAR2
        , post_set_macro IN VARCHAR2 DEFAULT ''
    )
    AS
    BEGIN
        IF request_format = 'json' THEN
            append_instruction(userfield_json('align_' || tablename, aligns, post_set_macro));
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := userfield_xml('align_' || tablename, aligns, post_set_macro);
    END;
    /*
     * Insert a graphic at a bookmark.
     */
    PROCEDURE insertgraphic(
        bookmark IN VARCHAR2
        , url IN VARCHAR2
    )
    AS
    BEGIN
        IF request_format = 'json' THEN
            append_instruction('{"instruction":"image","bookmark":"' || json_escape(bookmark)
                || '","url":"' || json_escape(url) || '","type":"' || image_type(url) || '"}');
            RETURN;
        END IF;
        xml.EXTEND;
        xml(xml.LAST) := '<image type="' || xml_escape(image_type(url))
            || '" bookmark="' || xml_escape(bookmark)
            || '" url="' || xml_escape(url) || '"/>';
    END;
    /*
     * Generate the request, post it to the Odisee service with Bearer auth, and return generated document as BLOB.
     */
    PROCEDURE generate(
        document OUT BLOB
        , oooxml OUT CLOB
        , charset IN VARCHAR2 DEFAULT 'UTF-8'
    )
    AS
    BEGIN
        oooxml := '';
        FOR i IN xml.FIRST .. xml.LAST LOOP
            oooxml := oooxml || xml(i);
        END LOOP;
        IF request_format = 'json' THEN
            httputil.post_document(
                url => endpoint_url
                , data => oooxml
                , content_type => 'application/json; charset=UTF-8'
                , charset => charset
                , result => document
                , bearer_token => bearer_token
            );
        ELSE
            httputil.post_document(
                url => endpoint_url
                , data => oooxml
                , content_type => 'text/xml; charset=UTF-8'
                , charset => charset
                , result => document
                , bearer_token => bearer_token
            );
        END IF;
    END;
END;
/
SHOW ERRORS;
