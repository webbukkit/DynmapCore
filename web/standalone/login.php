<?php
include 'dynmap_login.php';

session_start();

if(isset($_POST['j_username'])) {
  $userid = $_POST['j_username'];
}
else {
  $userid = '-guest-';
}
$good = false;

if(strcmp($userid, '-guest-')) {
  if(isset($_POST['j_password'])) {
    $password = $_POST['j_password'];
  }
  else {
    $password = '';
  }
  $ctx = hash_init('sha256');
  hash_update($ctx, $pwdsalt);
  hash_update($ctx, $password);
  $hash = hash_final($ctx);
  $userid = strtolower($userid);
  if (strcasecmp($hash, $pwdhash[$userid]) == 0) {
     $_SESSION['userid'] = $userid;
     $good = true; 
  }
  else {
     $_SESSION['userid'] = '-guest-';
  }
}
else {
  $_SESSION['userid'] = '-guest-';
  $good = true;
}
if($good) {
   echo "{ \"result\": \"success\" }"; 
}
else {
   echo "{ \"result\": \"loginfailed\" }"; 
}
 
?>

