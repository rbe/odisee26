<html>
<head>
    <title>Odisee Client/PHP</title>
</head>
<body>
<h1>Odisee&reg;</h1>
<p>
    <a href="HalloOdisee.php" target="_blank">Click here to generate a HelloWorld-PDF with Odisee</a>
</p>
<h2>PHP Extensions Check</h2>
<?php
$needed_exts = array('dom', 'spl');
foreach (get_loaded_extensions() as $i => $ext) {
    $v = phpversion($ext);
    if (null != $v && in_array(strtolower($ext), $needed_exts)) {
        echo "<span style='color: blue;'>{$ext}</span> => {$v} -- <span style='color: green;'>OK</span><br/>";
    }
}
?>
</body>
</html>
