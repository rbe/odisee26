<?php

/**
 * Checks that the PHP client emits the JSON document the service accepts.
 */

require_once dirname(__FILE__) . '/../main/php/class/Odisee/OdiseeException.class.php';
require_once dirname(__FILE__) . '/../main/php/class/Odisee/OdiseeJson.class.php';

use Odisee\OdiseeJson;

function fail_test($message)
{
    fwrite(STDERR, $message . PHP_EOL);
    exit(1);
}

$doc = new DOMDocument();
if (!$doc->load(dirname(__FILE__) . '/test_HalloOdisee_single_odt.xml')) {
    fail_test('Cannot load sample XML');
}
$json = OdiseeJson::fromDom($doc);
$actual = json_decode($json, true);
$expected = array(
    'request' => array(
        array(
            'name' => 'HalloOdisee',
            'template' => array('name' => 'HalloOdisee', 'outputFormat' => 'odt'),
            'archive' => array('files' => false),
            'instructions' => array(
                array('instruction' => 'userfield', 'name' => 'Hallo', 'value' => 'art of coding UG (haftungsbeschränkt)'),
                array('instruction' => 'userfield', 'name' => 'Tabelle1!A4', 'value' => 'support@odisee.de'),
            ),
        ),
    ),
);
if ($actual != $expected) {
    fail_test("JSON mismatch:\n" . $json);
}
if (strpos($json, '"files":false') === false) {
    fail_test('archive.files must be a JSON boolean');
}

$merge = new DOMDocument();
$merge->loadXML('<odisee>'
    . '<request name="HalloOdisee3">'
    . '<template name="HalloOdisee" outputFormat="pdf" pre-save-macro="Standard.Module1.Before"/>'
    . '<instructions>'
    . '<macro name="Standard.Module1.myMacro" language="Basic" location="document">'
    . '<parameter value="a"/>'
    . '<parameter>b</parameter>'
    . '</macro>'
    . '<image type="image/png" bookmark="Header" width="1000" height="200">BASE64</image>'
    . '</instructions>'
    . '<post-process><instructions><action type="merge-with">'
    . '<result-placeholder/>'
    . '<input file="pdf/AGB.pdf"/>'
    . '</action></instructions></post-process>'
    . '</request>'
    . '<response><base64>false</base64></response>'
    . '</odisee>');
$mergeRaw = OdiseeJson::fromDom($merge);
$expectedMerge = '{"request":[{"name":"HalloOdisee3","template":{"name":"HalloOdisee","outputFormat":"pdf","preSaveMacro":"Standard.Module1.Before"},"instructions":[{"instruction":"macro","language":"Basic","location":"document","name":"Standard.Module1.myMacro","parameter":[{"value":"a"},{"value":"b"}]},{"instruction":"image","bookmark":"Header","height":200,"type":"image/png","width":1000,"value":"BASE64"}],"postProcess":{"action":[{"type":"merge-with","content":[{"element":"result-placeholder"},{"element":"input","filename":"pdf/AGB.pdf"}]}]}}],"response":{"base64":false}}';
if ($mergeRaw !== $expectedMerge) {
    fail_test("PHP JSON does not match the Java client:\n" . $mergeRaw);
}
$mergeJson = json_decode($mergeRaw, true);
$macro = $mergeJson['request'][0]['instructions'][0];
if ($macro['instruction'] !== 'macro' || $macro['parameter'][1]['value'] !== 'b') {
    fail_test('macro parameters were not converted');
}
if ($mergeJson['request'][0]['template']['preSaveMacro'] !== 'Standard.Module1.Before') {
    fail_test('pre-save-macro was not renamed');
}
$image = $mergeJson['request'][0]['instructions'][1];
if ($image['width'] !== 1000 || $image['value'] !== 'BASE64') {
    fail_test('image instruction was not converted');
}
$input = $mergeJson['request'][0]['postProcess']['action'][0]['content'][1];
if ($input['element'] !== 'input' || $input['filename'] !== 'pdf/AGB.pdf') {
    fail_test('input file was not renamed to filename');
}
if ($mergeJson['response']['base64'] !== false) {
    fail_test('response.base64 must be a JSON boolean');
}

echo "ok\n";
