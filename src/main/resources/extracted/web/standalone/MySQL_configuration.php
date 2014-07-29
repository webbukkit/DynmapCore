<?php
ob_start();
include('MySQL_config.php');
ob_end_clean();

ob_start();
include('MySQL_access.php');
ob_end_clean();

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

$serverid = 0;
if(isset($_REQUEST['serverid'])) {
  $serverid = $_REQUEST['serverid'];
}

$db = mysqli_connect('p:' . $dbhost, $dbuserid, $dbpassword, $dbname, $dbport);
if (mysqli_connect_errno()) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Error opening database";
	$db->close();
    exit;
}

$fname = 'dynmap_config.json';
$stmt = $db->prepare('SELECT Content from StandaloneFiles WHERE FileName=? AND ServerID=?');
$stmt->bind_param('si', $fname, $serverid);
$res = $stmt->execute();
$stmt->bind_result($content);
if (!$stmt->fetch()) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo 'Error reading database - ' . $fname . ' #' . $serverid;
	$db->close();
    exit;
}

header('Content-type: text/plain; charset=utf-8');

if (!$loginenabled) {
	echo $content;
}
else if($json->loginrequired && !$loggedin) {
    echo "{ \"error\": \"login-required\" }";
}
else {
	$json = json_decode(implode($content));
	$uid = '[' . strtolower($userid) . ']';
	$json->loggedin = $loggedin;
	$wcnt = count($json->worlds);
	for($i = 0; $i < $wcnt; $i++) {
		$w = $json->worlds[$i];
		if($w->protected) {
		    $ss = stristr($worldaccess[$w->name], $uid);
			if($ss !== false) {
				$newworlds[] = $w;
			}
			else {
				$w = null;
			}
		}
		else {
			$newworlds[] = $w;
		}
		if($w != null) {
			$mcnt = count($w->maps);
			$newmaps = array();
			for($j = 0; $j < $mcnt; $j++) {
				$m = $w->maps[$j];
				if($m->protected) {
				    $ss = stristr($mapaccess[$w->name . '.' . $m->prefix], $uid);
					if($ss !== false) {
						$newmaps[] = $m;
					}
				}
				else {
					$newmaps[] = $m;
				}
			}
			$w->maps = $newmaps;		
		}
	}
	$json->worlds = $newworlds;
	
	echo json_encode($json);
}
$stmt->close();
$db->close();
 
?>

