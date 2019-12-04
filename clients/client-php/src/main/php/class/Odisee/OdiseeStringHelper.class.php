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

class OdiseeStringHelper
{

    /**
     * Is it a valid string?
     * @param $s String to check.
     * @return bool
     */
    public static function checkStr($s)
    {
        return null != $s && is_string($s);
    }

}

?>
