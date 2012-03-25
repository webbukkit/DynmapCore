<?php
require('dynmap_login.php');

session_start();

if(isset($_POST['j_password'])) {
  $password = $_POST['j_password'];
}
else {
  $password = '';
}
if(isset($_POST['j_verify_password'])) {
  $verify = $_POST['j_verify_password'];
}
else {
  $verify = '';
}
if(strcmp($password, $verify)) {
  headeer('Location: ../login.html?error=verifyfailed');
  return;
}

if(isset($_POST['j_username'])) {
  $userid = $_POST['j_username'];
}
else {
  $userid = '-guest-';
}
if(isset($_POST['j_passcode'])) {
  $passcode = $_POST['j_passcode'];
}
else {
  $passcode = '';
}
$good = false;

$userid = strtolower($userid);

$_SESSION['userid'] = '-guest-';

$good = false;

if(strcmp($userid, '-guest-')) {
  if(isset($pendingreg[$userid])) {
     if(!strcmp($passcode, $pendingreg[$userid])) {
        $ctx = hash_init('sha256');
        hash_update($ctx, $pwdsalt);
        hash_update($ctx, $password);
        $hash = hash_final($ctx);
        $_SESSION['userid'] = $userid;
        $good = true;

		$newlines[] = '<?php /*';
		$lines = file('dynmap_reg.php');
		if(!empty($lines)) {
			$cnt = count($lines) - 1;
			for($i=1; $i < $cnt; $i++) {
				list($uid, $pc, $hsh) = split('=', rtrim($lines[$i]));
				if($uid == $userid) continue;
				if(array_key_exists($uid, $pendingreg)) {
					$newlines[] = $uid . '=' . $hsh;
				}
			}
		}
		$newlines[] = $userid . '=' . $pc . '=' . $hash;
		$newlines[] = '*/ ?>';
		file_put_contents('dynmap_reg.php', implode("\n", $newlines));
     }
  }
}
if($good) {
   echo "{ \"result\": \"success\" }"; 
}
else {
   echo "{ \"result\": \"registerfailed\" }"; 
}
   
?>
