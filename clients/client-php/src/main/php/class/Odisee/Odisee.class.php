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

use \DOMDocument;
use \DOMElement;

//require_once(dirname(__FILE__) . '/OdiseeException.class.php');
//require_once(dirname(__FILE__) . '/OdiseeStringHelper.class.php');
//require_once(dirname(__FILE__) . '/OdiseeHttpHelper.class.php');

/**
 * Odisee client.
 */
class Odisee
{

    /**
     * @var array Cache instances of OdiseeClient created by createClient().
     */
    private static $_instances = array();

    /**
     * @var string URL of Odisee service, w/o last part: /odisee/document/generate.
     */
    private $serviceURL;

    /**
     * @var string Odisee user.
     */
    private $username;

    /**
     * @var string Password.
     */
    private $password;

    /**
     * @var null|string Authkey for Odisee service.
     */
    private $authkey;

    /**
     * @var DOMDocument XML document for Odisee request.
     */
    private $xmlDoc;

    /**
     * @var OdiseeRequest Actual Odisee request XML element.
     */
    private $actualRequest;

    /**
     * Private constructor, setup XML document.
     * @param string $serviceURL URL of Odisee service.
     * @param string $username The username.
     * @param string $password The password.
     * @param null|string $authkey Authentication key, used w/ authenticating w/o password.
     */
    private function __construct($serviceURL, $username, $password, $authkey = null)
    {
        $this->serviceURL = $serviceURL;
        $this->username = $username;
        $this->password = $password;
        $this->authkey = $authkey;
        $this->initializeXmlDocument();
    }

    /**
     * Initialize Odisee XML document.
     */
    private function initializeXmlDocument()
    {
        if (class_exists('DOMDocument')) {
            $this->xmlDoc = new DOMDocument('1.0', 'UTF-8');
            $root = $this->xmlDoc->createElement('odisee');
            $this->xmlDoc->appendChild($root);
        } else {
            throw new OdiseeException('Requirements not met: install DOM');
        }
    }

    //<editor-fold desc="Odisee Client Factory">

    /**
     * @param string $serviceURL URL of Odisee service.
     * @param string $authkey Authentication key, used w/ authenticating w/o password.
     * @return Odisee
     */
    public static function createClientWithAuthkey($serviceURL, $authkey)
    {
        $ident = $serviceURL . "_" . $authkey;
        if (!isset(self::$_instances[$ident])) {
            self::$_instances[$ident] = new self($serviceURL, null, null, $authkey);
        }
        return self::$_instances[$ident];
    }

    /**
     * @param string $serviceURL URL of Odisee service.
     * @param string $username The username.
     * @param string $password The password.
     * @return Odisee
     */
    public static function createClient($serviceURL, $username, $password)
    {
        $ident = $serviceURL . "_" . $username;
        if (!isset(self::$_instances[$ident])) {
            self::$_instances[$ident] = new self($serviceURL, $username, $password);
        }
        return self::$_instances[$ident];
    }

    //</editor-fold>

    /**
     * Get last instructions element or create a new one.
     * @param DOMElement $request
     * @return DOMElement //request/instructions[last()]
     */
    private function getInstructions($request)
    {
        $instructions = $request->getElementsByTagName('instructions');
        if ($instructions->length == 0) {
            $instructions = $this->xmlDoc->createElement('instructions');
            $request->appendChild($instructions);
        }
        $instructions = $request->getElementsByTagName('instructions');
        return $instructions->item($instructions->length > 0 ? $instructions->length - 1 : 0);
    }

    /**
     * Append an instruction to a request.
     * @param DOMElement $request
     * @param DOMElement $instruction
     */
    private function appendInstruction(&$request, $instruction)
    {
        $this->getInstructions($request)->appendChild($instruction);
    }

    //<editor-fold desc="Odisee Client API">

    /**
     * @param OdiseeRequest $request
     * @param string $path Path to a file.
     * @return Odisee
     */
    public function &mergeDocumentAtEnd(&$request, $path)
    {
        if (null == $request) {
            $request = $this->actualRequest;
        }
        return $this;
    }

    /**
     * @param OdiseeRequest $request The request.
     * @param string $macroName Name of macro.
     * @param string $location Where is the macro? E.g. document, application.
     * @param string $language Programming language of macro, e.g. Basic.
     * @param null|array $parameters
     * @return Odisee
     */
    public function &executeMacro(&$request, $macroName, $location = 'document', $language = 'Basic', $parameters = null)
    {
        if (null == $request) {
            $request = $this->actualRequest;
        }
        $elt = $this->xmlDoc->createElement('macro');
        $elt->setAttribute('name', $macroName);
        $elt->setAttribute('location', $location);
        $elt->setAttribute('language', $language);
        if ($parameters) {
            foreach ($parameters as $p) {
                $elt_p = $this->xmlDoc->createElement('parameter');
                $elt_p->nodeValue = $p;
                $elt->appendChild($elt_p);
            }

        }
        $this->appendInstruction($request, $elt);
        return $this;
    }

    /**
     * @param OdiseeRequest $request
     * @param string $macroName
     * @param null|array $parameters
     * @return Odisee
     */
    public function &executeBasicMacroInDocument(&$request, $macroName, $parameters = null)
    {
        return $this->executeMacro($request, $macroName, 'document', 'Basic', $parameters);
    }

    /**
     * @param OdiseeRequest $request
     * @param string $tableName
     * @param string $coordinate
     * @param string $value
     * @return Odisee
     */
    public function &setTableCellValue(&$request, $tableName, $coordinate, $value)
    {
        return $this->setUserfield($request, $tableName . '!' . $coordinate, $value);
    }

    /**
     * @param OdiseeRequest $request
     * @param string $userfieldName
     * @param string $value
     * @return Odisee
     */
    public function &setUserfield(&$request, $userfieldName, $value)
    {
        if (null == $request) {
            $request = $this->actualRequest;
        }
        $elt = $this->xmlDoc->createElement('userfield');
        $elt->setAttribute('name', $userfieldName);
        $elt->nodeValue = $value;
        $this->appendInstruction($request, $elt);
        return $this;
    }

    /**
     * @param OdiseeRequest $request
     * @param bool $database
     * @param bool $files
     * @return Odisee
     */
    public function &setArchive(&$request, $database, $files)
    {
        if (null == $request) {
            $request = $this->actualRequest;
        }
        $elt = $this->xmlDoc->createElement('archive');
        $elt->setAttribute('database', $database ? 'true' : 'false');
        $elt->setAttribute('files', $files ? 'true' : 'false');
        $request->appendChild($elt);
        return $this;
    }

    /**
     * @param OdiseeRequest $request
     * @param string $template
     * @param string $outputFormat
     * @return Odisee
     */
    public function &setLatestTemplate(&$request, $template, $outputFormat = 'pdf')
    {
        if (null == $request) {
            $request = $this->actualRequest;
        }
        $elt = $this->xmlDoc->createElement('template');
        $elt->setAttribute('name', $template);
        $elt->setAttribute('outputFormat', $outputFormat);
        $request->appendChild($elt);
        return $this;
    }

    /**
     * @param string $template
     * @param string $outputFormat
     * @return OdiseeRequest
     */
    public function &createRequest($template, $outputFormat = 'pdf')
    {
        $request = new OdiseeRequest(); //$this->xmlDoc->createElement('request');
        $this->xmlDoc->documentElement->appendChild($request); //getElementsByTagName('odisee')->item(0)
        $this->actualRequest = $request;
        $this->setLatestTemplate($request, $template, $outputFormat);
        return $request;
    }

    /**
     * Process Odisee request and return a document.
     * @param bool $debug
     * @return mixed Odisee generated document on success, false otherwise.
     * @throws OdiseeException
     */
    public function process($debug = FALSE)
    {
        if (OdiseeStringHelper::checkStr($this->serviceURL) && strpos($this->serviceURL, 'http') == 0) {
            if ($debug) {
                $this->xmlDoc->formatOutput = TRUE;
                echo $this->xmlDoc->saveXML();
                $this->xmlDoc->formatOutput = FALSE;
            }
            $data = $this->xmlDoc->saveXML();
            $referer = '';
            if (isset($_SERVER['SERVER_PROTOCOL']) && isset($_SERVER['SERVER_NAME']) && isset($_SERVER['SERVER_PORT'])) {
                $referer = $_SERVER['SERVER_PROTOCOL'] . '://' . $_SERVER['SERVER_NAME'] . ':' . $_SERVER['SERVER_PORT'];
            }
            $document = OdiseeHttpHelper::post($this->serviceURL, $this->username, $this->password, $referer, $data);
            return $document;
        } else {
            throw new OdiseeException('Incorrect service URL!');
        }
    }

    //</editor-fold>

    /**
     * @param string $name
     * @param array $arguments
     */
    public function __call($name, $arguments)
    {
        $request = null;
        $args = array();
        foreach ($arguments as $arg) {
            if ($arg instanceof OdiseeRequest) {
                $request = $arg;
            } else {
                $args[] = $arg;
            }
        }
        if (null == $request) {
            $request = $this->actualRequest;
        }
        $n = '_dyn_' . $name;
        $a = implode(', ', $args);
        $this->$n($request, $a);
    }

}

?>
