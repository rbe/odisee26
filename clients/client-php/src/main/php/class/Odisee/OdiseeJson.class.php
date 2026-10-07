<?php

/**
 * odisee
 * odisee-client-php
 * Copyright (C) 2011-2013 art of coding UG, http://www.art-of-coding.eu
 * Copyright (C) 2005-2010 Informationssysteme Ralf Bensmann, http://www.bensmann.com
 *
 * Alle Rechte vorbehalten. Nutzung unterliegt Lizenzbedingungen.
 * All rights reserved. Use is subject to license terms.
 */

namespace Odisee;

use \DOMDocument;
use \DOMElement;
use \DOMNode;

/**
 * Converts an Odisee XML request into the JSON document the service accepts.
 */
class OdiseeJson
{

    /**
     * @param DOMDocument $doc
     * @return string
     * @throws OdiseeException
     */
    public static function fromDom(DOMDocument $doc)
    {
        $root = $doc->documentElement;
        if (!$root || self::localName($root) !== 'odisee') {
            throw new OdiseeException('XML request root must be odisee');
        }
        $body = array('request' => array());
        foreach (self::elements($root) as $child) {
            switch (self::localName($child)) {
                case 'request':
                    $body['request'][] = self::request($child);
                    break;
                case 'post-process':
                    $body['postProcess'] = self::postProcess($child);
                    break;
                case 'response':
                    $body['response'] = self::response($child);
                    break;
            }
        }
        $flags = 0;
        if (defined('JSON_UNESCAPED_UNICODE')) {
            $flags |= JSON_UNESCAPED_UNICODE;
        }
        if (defined('JSON_UNESCAPED_SLASHES')) {
            $flags |= JSON_UNESCAPED_SLASHES;
        }
        $json = json_encode($body, $flags);
        if ($json === false) {
            throw new OdiseeException('Cannot encode JSON request');
        }
        return $json;
    }

    /**
     * @param DOMElement $element
     * @return array
     */
    private static function request(DOMElement $element)
    {
        $map = self::attributes($element);
        $instructions = array();
        $sawInstructions = false;
        $requestPostProcess = null;
        foreach (self::elements($element) as $child) {
            switch (self::localName($child)) {
                case 'template':
                case 'archive':
                case 'group':
                    $map[self::localName($child)] = self::attributes($child);
                    break;
                case 'instructions':
                    $sawInstructions = true;
                    foreach (self::elements($child) as $instruction) {
                        $instructions[] = self::instruction($instruction);
                    }
                    break;
                case 'post-process':
                    $requestPostProcess = self::postProcess($child);
                    break;
            }
        }
        if ($sawInstructions) {
            $map['instructions'] = $instructions;
        }
        if ($requestPostProcess !== null) {
            $map['postProcess'] = $requestPostProcess;
        }
        return $map;
    }

    /**
     * @param DOMElement $element
     * @return array
     */
    private static function instruction(DOMElement $element)
    {
        $name = self::localName($element);
        $map = array('instruction' => $name);
        $attrs = self::attributes($element);
        ksort($attrs);
        foreach ($attrs as $key => $value) {
            $map[$key] = $value;
        }
        if ($name === 'macro') {
            $parameters = array();
            foreach (self::elements($element) as $child) {
                if (self::localName($child) !== 'parameter') {
                    continue;
                }
                $attribute = $child->getAttribute('value');
                $text = self::directText($child);
                $parameters[] = array('value' => ($attribute !== '' ? $attribute : $text));
            }
            if (count($parameters) > 0) {
                $map['parameter'] = $parameters;
            }
        } else {
            $text = self::directText($element);
            if ($text !== '') {
                $map['value'] = $text;
            }
        }
        return $map;
    }

    /**
     * @param DOMElement $element
     * @return array
     */
    private static function postProcess(DOMElement $element)
    {
        return array('action' => self::collectActions($element));
    }

    /**
     * @param DOMElement $element
     * @return array
     */
    private static function collectActions(DOMElement $element)
    {
        $actions = array();
        foreach (self::elements($element) as $child) {
            $name = self::localName($child);
            if ($name === 'action') {
                $actions[] = self::action($child);
            } elseif ($name === 'instructions') {
                foreach (self::collectActions($child) as $action) {
                    $actions[] = $action;
                }
            }
        }
        return $actions;
    }

    /**
     * @param DOMElement $element
     * @return array
     */
    private static function action(DOMElement $element)
    {
        $map = self::attributes($element);
        $content = array();
        foreach (self::elements($element) as $child) {
            $name = self::localName($child);
            $step = array('element' => $name);
            foreach (self::attributes($child) as $key => $value) {
                $step[$key] = $value;
            }
            if ($name === 'input' && array_key_exists('file', $step) && !array_key_exists('filename', $step)) {
                $step['filename'] = $step['file'];
                unset($step['file']);
            }
            $content[] = $step;
        }
        if (count($content) > 0) {
            $map['content'] = $content;
        }
        return $map;
    }

    /**
     * @param DOMElement $element
     * @return array
     */
    private static function response(DOMElement $element)
    {
        $map = array();
        foreach (self::elements($element) as $child) {
            if (self::localName($child) === 'base64') {
                $map['base64'] = self::jsonScalar('base64', self::directText($child));
            }
        }
        return $map;
    }

    /**
     * @param DOMElement $element
     * @return array
     */
    private static function attributes(DOMElement $element)
    {
        $map = array();
        if ($element->hasAttributes()) {
            foreach ($element->attributes as $attr) {
                $name = $attr->localName ? $attr->localName : $attr->nodeName;
                if (strpos($name, 'xmlns') === 0 || $name === 'schemaLocation') {
                    continue;
                }
                $map[self::jsonKey($name)] = self::jsonScalar($name, $attr->nodeValue);
            }
        }
        ksort($map);
        return $map;
    }

    /**
     * @param string $xmlAttr
     * @return string
     */
    private static function jsonKey($xmlAttr)
    {
        switch ($xmlAttr) {
            case 'pre-save-macro':
                return 'preSaveMacro';
            case 'post-save-macro':
                return 'postSaveMacro';
            case 'post-macro':
            case 'post-set-macro':
                return 'postMacro';
            case 'local-debug':
                return 'localDebug';
            case 'cell-align':
                return 'cellAlign';
            case 'cell-width':
                return 'cellWidth';
            default:
                return $xmlAttr;
        }
    }

    /**
     * @param string $xmlName
     * @param string $raw
     * @return bool|int|string
     */
    private static function jsonScalar($xmlName, $raw)
    {
        if ($xmlName === 'files' || $xmlName === 'database' || $xmlName === 'atend'
            || $xmlName === 'local-debug' || $xmlName === 'base64') {
            if (strcasecmp($raw, 'true') === 0) {
                return true;
            }
            if (strcasecmp($raw, 'false') === 0) {
                return false;
            }
        }
        if (($xmlName === 'width' || $xmlName === 'height') && preg_match('/^-?\d+$/', $raw)) {
            return (int)$raw;
        }
        return $raw;
    }

    /**
     * @param DOMNode $parent
     * @return DOMElement[]
     */
    private static function elements(DOMNode $parent)
    {
        $list = array();
        foreach ($parent->childNodes as $child) {
            if ($child instanceof DOMElement) {
                $list[] = $child;
            }
        }
        return $list;
    }

    /**
     * @param DOMElement $element
     * @return string
     */
    private static function directText(DOMElement $element)
    {
        $text = '';
        foreach ($element->childNodes as $child) {
            if ($child->nodeType === XML_TEXT_NODE || $child->nodeType === XML_CDATA_SECTION_NODE) {
                $text .= $child->nodeValue;
            }
        }
        return trim($text);
    }

    /**
     * @param DOMNode $node
     * @return string
     */
    private static function localName(DOMNode $node)
    {
        if ($node->localName) {
            return $node->localName;
        }
        $name = $node->nodeName;
        $colon = strpos($name, ':');
        return $colon === false ? $name : substr($name, $colon + 1);
    }

}

?>
