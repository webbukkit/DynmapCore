<?php
ob_start();
include('MySQL_config.php');
ob_end_clean();

session_start();

$db = mysqli_connect('p:' . $dbhost, $dbuserid, $dbpassword, $dbname, $dbport);
if (mysqli_connect_errno()) {
    header('HTTP/1.0 500 Error');
    echo "<h1>500 Error</h1>";
    echo "Error opening database";
	$db->close();
    exit;
}

$serverid = 0;
if(isset($_REQUEST['serverid'])) {
  $serverid = $_REQUEST['serverid'];
}

$fname = 'dynmap_config.json';
$stmt = $db->prepare('SELECT Content from StandaloneFiles WHERE FileName=? AND ServerID=?');
$stmt->bind_param('si', $fname, $serverid);
$res = $stmt->execute();
$stmt->bind_result($content);
if ($stmt->fetch()) {
   $config = json_decode($content, true);
   $msginterval = $config['webchat-interval'];
}
else {
   $msginterval = 2000;
}
$stmt->close();

if(isset($_SESSION['lastchat']))
    $lastchat = $_SESSION['lastchat'];
else
    $lastchat = 0;

if($_SERVER['REQUEST_METHOD'] == 'POST' && $lastchat < time())
{
	$micro = microtime(true);
	$timestamp = round($micro*1000.0);
	
	$data = json_decode(trim(file_get_contents('php://input')));
	$data->timestamp = $timestamp;
	$data->ip = $_SERVER['REMOTE_ADDR'];
	if(isset($_SESSION['userid'])) {
		$uid = $_SESSION['userid'];
		if(strcmp($uid, '-guest-')) {
		   $data->userid = $uid;
		}
	}
	if(isset($_SERVER['HTTP_X_FORWARDED_FOR']))
		$data->ip = $_SERVER['HTTP_X_FORWARDED_FOR'];
	$fname = 'dynmap_webchat.json';
	$stmt = $db->prepare('SELECT Content from StandaloneFiles WHERE FileName=? AND ServerID=?');
	$stmt->bind_param('si', $fname, $serverid);
	$res = $stmt->execute();
	$stmt->bind_result($content);
	$gotold = false;
	if ($stmt->fetch()) {
		$old_messages = json_decode($content, true);
		$gotold = true;
	}
	$stmt->close();
	
	if(!empty($old_messages))
	{
		foreach($old_messages as $message)
		{
			if(($timestamp - $config['updaterate'] - 10000) < $message['timestamp'])
				$new_messages[] = $message;
		}
	}
	$new_messages[] = $data;

	$fname = 'dynmap_webchat.json';
	if ($gotold) {
		$stmt = $db->prepare('UPDATE StandaloneFiles SET Content=? WHERE FileName=? AND ServerID=?');
	}
	else {
		$stmt = $db->prepare('INSERT INTO StandaloneFiles (Content,FileName,ServerID) VALUES (?,?,?);');
	}
	$stmt->bind_param('ssi', json_encode($new_messages), $fname, $serverid);
	$res = $stmt->execute();
	$stmt->close();
	
	$_SESSION['lastchat'] = time()+$msginterval;
	echo "{ \"error\" : \"none\" }";
}
elseif($_SERVER['REQUEST_METHOD'] == 'POST' && $lastchat > time())
{
	header('HTTP/1.1 403 Forbidden');
}
else {
	echo "{ \"error\" : \"none\" }";
}
$db->close();

?>