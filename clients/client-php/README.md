# Odisee(R) Client/PHP

## Loading Odisee class

    :::php
    // Autoloading
    define('ODISEE_CLASS_DIR', dirname(__FILE__) . '/class/');
    set_include_path(get_include_path() . PATH_SEPARATOR . ODISEE_CLASS_DIR);
    spl_autoload_extensions('.class.php');
    spl_autoload_register();
    // Odisee uses namespaces
    use \Odisee\Odisee;

## Working with a single request

    :::php
    // Create Odisee client with service URL and authentication
    $odisee = Odisee::createClient('http://service.odisee.de', 'username', 'password');
    // Create a new request for template HalloOdisee
    $request = $odisee->createRequest('HalloOdisee');
    // Set value for userfield 'hallo'
    $odisee->setUserfield($request, 'hallo', 'Odisee');
    // Set value in table 'Tabelle1' cell 'A4'
    $odisee->setTableCellValue($request, 'Tabelle1', 'A4', 'value in a table cell');
    // Generate document, PDF by default
    $document = $odisee->process();

## Using the fluent API

    :::php
    // Example using fluent API
    $odisee = Odisee::createClient('http://service.odisee.de', 'username', 'password');
    $request = $odisee->createRequest('HalloOdisee')
    $odisee->setUserfield($request, 'hallo', 'Odisee von PHP am ' . $d)
           ->setTableCellValue($request, 'Tabelle1', 'A4', 'support@odisee.de')
    $document = $odisee->process();
