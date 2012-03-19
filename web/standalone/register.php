<?php
include 'dynmap_login.php';

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
     }
  }
}
if($good) {
  header('Location: ../index.html');
}
else {
  header('Location: ../login.html?error=registerfailed');
}
   
?>

