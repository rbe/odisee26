/*
 * Odisee
 *
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann
 * Copyright (C) 2011 art of coding UG (haftungsbeschränkt)
 * Alle Rechte vorbehalten. All rights reserved.
 */

CREATE OR REPLACE PACKAGE odisee
AS
    -- Where is the Odisee service?
    odisee_service_url VARCHAR(1000);
    /*
     * Create header for XML request.
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
    );
    /*
     * Create footer for XML request.
     */
    PROCEDURE footer;
    /*
     * Add a VARCHAR2 field.
     */
    PROCEDURE addfield(
        name IN VARCHAR2
        , value IN VARCHAR2 DEFAULT ''
        , post_set_macro IN VARCHAR2 DEFAULT ''
    );
    /*
     * Add a NUMBER field.
     */
    PROCEDURE addfield(
        name IN VARCHAR2
        , value IN NUMBER DEFAULT ''
        , post_set_macro IN VARCHAR2 DEFAULT ''
    );
    /*
     * Add a DATE field.
     */
    PROCEDURE addfield(
        name IN VARCHAR2
        , value IN DATE DEFAULT ''
        , format IN VARCHAR2 DEFAULT 'DD.MM.YYYY'
        , post_set_macro IN VARCHAR2 DEFAULT ''
    );
    /*
     * Set/add a VARCHAR2 in a table cell.
     */
    PROCEDURE addtablecell(
        tablename IN VARCHAR2
        , rownm IN NUMBER
        , colnum IN NUMBER
        , value IN VARCHAR2 DEFAULT ''
        , post_set_macro IN VARCHAR2 DEFAULT ''
    );
    /*
     * Set/add a NUMBER in a table cell.
     */
    PROCEDURE addtablecell(
        tablename IN VARCHAR2
        , rownm IN NUMBER
        , colnum IN NUMBER
        , value IN NUMBER DEFAULT ''
        , post_set_macro IN VARCHAR2 DEFAULT ''
    );
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
    );
    /*
     * Set width of a table cell.
     */
    PROCEDURE settablecellwidth(
        tablename IN VARCHAR2
        , rownm IN NUMBER
        , colnum IN NUMBER
        , width IN NUMBER
        , post_set_macro IN VARCHAR2 DEFAULT ''
    );
    /*
     * Align a table cell.
     */
    PROCEDURE aligntablecell(
        tablename IN VARCHAR2
        , alings IN VARCHAR2
        , post_set_macro IN VARCHAR2 DEFAULT ''
    );
    /*
     * Insert a graphic at a bookmark.
     */
    PROCEDURE insertgraphic(
        bookmark IN VARCHAR2
        , url IN VARCHAR2
    );
    /*
     * Generate the XML request, post it to the OOo Service and return generated document as BLOB.
     */
    PROCEDURE generate(
        document OUT BLOB
        , oooxml OUT CLOB
        , charset IN VARCHAR2 DEFAULT 'UTF-8'
    );
END;
/
SHOW ERRORS;
