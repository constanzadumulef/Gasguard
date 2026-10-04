<?php
// DB CREDENCIALES DE USUARIO.
define('DB_HOST','10.16.1.248');
define('DB_USER','');
define('DB_PASS','pi');
define('DB_NAME','sitio1');
 
// Ahora, establecemos la conexión.
try
{
// Ejecutamos las variables y aplicamos UTF8
$connect = new PDO("mysql:host=".DB_HOST.";dbname=".DB_NAME,DB_USER, DB_PASS,
array(PDO::MYSQL_ATTR_INIT_COMMAND => "SET NAMES 'utf8'"));
}
catch (PDOException $e)
{
exit("Error: " . $e->getMessage());
}
?>
