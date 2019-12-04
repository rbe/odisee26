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

namespace Odisee;

//require_once(dirname(__FILE__) . '/OdiseeException.class.php');
//require_once(dirname(__FILE__) . '/Odisee.class.php');

class Template
{

    /**
     * @var Odisee Instance of Odisee client.
     */
    private $odisee;

    /**
     * Public constructor.
     * @param Odisee $odisee Instance of Odisee client created by Odisee::createClient...().
     */
    public function __construct($odisee)
    {
        $this->odisee = $odisee;
    }
    
    public function createHalloOdisee() {
        $request = $this->odisee->createRequest('HalloOdisee');
        $this->odisee->setUserfield($request, 'hallo', 'Odisee von PHP am ');
        $this->odisee->setTableCellValue($request, 'Tabelle1', 'A4', 'support@odisee.de');
        return $this->odisee->process();
    }

    public function createInvoice()
    {
        $request = $this->odisee->createRequest('Invoice');
        // Sender
        // Recipient
        // Invoice data
        return $this->odisee->process();
    }

}

?>
