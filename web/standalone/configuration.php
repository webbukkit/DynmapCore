<?php
session_start();

if(isset($_SESSION['userid'])) {
  $userid = $_SESSION['userid'];
}
else {
  $userid = '-guest-';
}

$loggedin = false;
if(strcmp($userid, '-guest-')) {
  $loggedin = true;
}

$lines = file('dynmap_config.php');
array_shift($lines);
array_pop($lines);
$json = json_decode(implode(' ',$lines));

header('Content-type: text/plain; charset=utf-8');

if($json->loginrequired && !$loggedin) {
    echo "{ \"error\": \"login-required\" }";
}
else {
	$json->loggedin = $loggedin;
	
	echo json_encode($json);
}


 
?>

