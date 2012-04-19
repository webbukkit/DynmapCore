<?php
include 'dynmap_access.php';

if(!isset($tilespath)) {
  $tilespath = "../tiles";
}

//Use this to force specific tiles path, versus using passed value
//$tilespath = 'my-tiles-path';

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

$path = $_SERVER['PATH_INFO'];
$fname = $tilespath . $path;

list($space, $world, $prefix, $tilename) = explode("/", $path);

$uid = '[' . strtolower($userid) . ']';

if(isset($worldaccess[$world])) {
    $ss = stristr($worldaccess[$world], $uid);
	if($ss === false) {
		$fname = "../images/blank.png";
	}
}
$mapid = $world . "." . $prefix;
if(isset($mapaccess[$mapid])) {
    $ss = stristr($mapaccess[$mapid], $uid);
	if($ss === false) {
		$fname = "../images/blank.png";
	}
}

if (!file_exists($fname)) {
  $fname = "../images/blank.png";
}
$fp = fopen($fname, 'rb');
if (strstr($path, ".png"))
  header("Content-Type: image/png");
else
  header("Content-Type: image/jpeg");

header("Content-Length: " . filesize($fname));

fpassthru($fp);
exit;
?>
