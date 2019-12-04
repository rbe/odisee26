<?php

/**
 * odisee
 * odisee-client-php
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 *
 * rbe
 */

// Error reporting
error_reporting(E_ALL);
ini_set('display_errors', 'On');

// Autoloading
include 'class/autoload.php';

use \Odisee\Odisee;

if (class_exists('Odisee\Odisee', true)) {
    // Actual date and time
    $d = date('d. M Y H:i:s');
    // Create Odisee client with service URL and authentication
    $odisee = Odisee::createClient('http://service.odisee.de', 'odisee', 'odisee');
    // Create a new request for template HalloOdisee
    $request = $odisee->createRequest('HalloOdisee');
    // Set value for userfield 'hallo'
    $odisee->setUserfield($request, 'hallo', 'Odisee von PHP am ' . $d);
    // Set value in table 'Tabelle1' cell 'A4'
    $odisee->setTableCellValue($request, 'Tabelle1', 'A4', 'support@odisee.de');
    // Generate document, PDF by default
    $document = $odisee->process();
     // Set content type and stream generated document
    header("Content-Type: application/pdf", TRUE);
    echo file_get_contents($document);
}

?>
