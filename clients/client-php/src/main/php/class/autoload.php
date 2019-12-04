<?php
    
// Autoloading
define('ODISEE_CLASS_DIR', dirname(__FILE__) . '/class/');
set_include_path(get_include_path() . PATH_SEPARATOR . ODISEE_CLASS_DIR);
spl_autoload_extensions('.class.php');
spl_autoload_register();

?>
