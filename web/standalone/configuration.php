<?php
include 'dynmap_access.php';

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
	$useridlc = strtolower($userid);
	$json->loggedin = $loggedin;
	$wcnt = count($json->worlds);
	for($i = 0; $i < $wcnt; $i++) {
		$w = $json->worlds[$i];
		if($w->protected) {
		    $uid = '[' . $useridlc . ']';
		    $ss = stristr($worldaccess[$w->name], $uid);
			if($ss !== false) {
				$newworlds[] = $w;
			}
		}
		else {
			$newworlds[] = $w;
		}
	}
	$json->worlds = $newworlds;
	
	echo json_encode($json);
}


 
?>

