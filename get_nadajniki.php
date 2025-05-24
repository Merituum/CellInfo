<?php
header('Content-Type: application/json');
header('Access-Control-Allow-Origin: *');

$servername = "localhost";
$username = "root";
$password = "";
$dbname = "nadajniki_poznan";

try {
    $pdo = new PDO("mysql:host=$servername;dbname=$dbname;charset=utf8", $username, $password);
    $pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);
    
    $stmt = $pdo->prepare("SELECT siec_id, miejscowosc, lokalizacja, LONGuke, LATIuke FROM nadajniki WHERE LONGuke IS NOT NULL AND LATIuke IS NOT NULL");
    $stmt->execute();
    
    $nadajniki = $stmt->fetchAll(PDO::FETCH_ASSOC);
    echo json_encode($nadajniki);
    
} catch(PDOException $e) {
    echo json_encode(["error" => $e->getMessage()]);
}
?>
