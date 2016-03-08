<?php

# ------------------------------------------------------------------------------
# BEGIN CONFIGURATION
# ------------------------------------------------------------------------------

$crowd_appname="";
$crowd_password="";
$crowd_endpoint="http://localhost:8095/crowd/";

# ------------------------------------------------------------------------------
# END CONFIGURATION
# ------------------------------------------------------------------------------

# check for required pieces
if (!isset($_REQUEST['redirecturl'])) {
  header('HTTP/1.1 400 Bad Request', true, 400);
  exit(0);
}
$redirecturl=$_REQUEST['redirecturl'];
if (!isset($_REQUEST['token'])) {
  header('HTTP/1.1 400 Bad Request', true, 400);
  exit(0);
}
$token=$_REQUEST['token'];

# check to see if we have a redirecturl
if (isset($_REQUEST['username']) && isset($_REQUEST['password'])) {
  $user = crowd_session_login($_REQUEST['username'], $_REQUEST['password']);
  if ($user !== false) {
    header("Location: ${redirecturl}?token=${token}&user=" . urlencode(json_encode($user)));
    exit(0);
  }
} else {
  $user = crowd_session_check();
  if ($user !== false) {
    header("Location: ${redirecturl}?token=${token}&user=" . urlencode(json_encode($user)));
    exit(0);
  }
}

# checks to see if the user is logged in. This will use the default cookie
# set by atlassian to see if the user is already loggged in. If so it will
# return the user information. If this fails it will destroy the cookie and
# return false.
function crowd_session_check() {
  if (!isset($_COOKIE['crowd_token_key'])) {
    return false;
  }
  return crowd_cookie_check($_COOKIE['crowd_token_key']);
}

# login to crowd with the given username and password. If this is successful
# it will set the appropriate cookie.
function crowd_session_login($username, $password) {
  $data = "<authentication-context><username>{$username}</username><password>{$password}</password><validation-factors><validation-factor><name>remote_address</name><value>127.0.0.1</value></validation-factor></validation-factors></authentication-context>";
  $result=crowd_call("session", "POST", $data);
  if ($result === false) {
    return false;
  }
  setcookie('crowd.token_key', $result->token, time()+3600, '/', $_SERVER['SERVER_NAME']);

  return crowd_cookie_check($result->token);
}

# delete the crowd token
function crowd_session_logout() {
  setcookie('crowd.token_key', '', time()-3600, '/', $_SERVER['SERVER_NAME']);
  unset($_COOKIE['crowd_token_key']);
}

# check if cookie is valid, will return FALSE or user object
function crowd_cookie_check($cookie) {
  $result=crowd_call("session/${cookie}");
  if ($result === false) {
    crowd_session_logout();
    return false;
  }
  if (empty($result->user[0]->active) || !$result->user[0]->active) {
    crowd_session_logout();
    return false;
  }
  if (empty($result->user[0]['name'])) {
    crowd_session_logout();
    return false;
  }

  $data = $result->user[0];
  $user = array();
  $user['userId'] = (string)$data['name'];
  $user['firstName'] = (string)$data->{'first-name'};
  $user['lastName'] = (string)$data->{'last-name'};
  $user['fullName'] = (string)$data->{'display-name'};
  $user['email'] =  (string)$data->{'email'};
  $user['avatarURL'] = (string)$data->{'avatar'};
  return $user;
}

# Call crowd rest api. This will use the global variables defined at the top
# of this file for the actual call to crowd.
#
# $path = actual rest api path
# $request = type of request, default is GET
# $data = actual data to be send, default is no data
function crowd_call($path, $request="GET", $data="") {
  global $crowd_appname;
  global $crowd_password;
  global $crowd_endpoint;

  $ch=curl_init();
  curl_setopt($ch, CURLOPT_HTTPAUTH, CURLAUTH_BASIC);
  curl_setopt($ch, CURLOPT_USERPWD, $crowd_appname . ":" . $crowd_password);
  curl_setopt($ch, CURLOPT_URL, $crowd_endpoint . "rest/usermanagement/latest/" . $path);
  curl_setopt($ch, CURLOPT_HEADER, true);
  curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
  curl_setopt($ch, CURLOPT_CUSTOMREQUEST, $request);
  curl_setopt($ch, CURLOPT_HTTPHEADER, Array("Content-Type: application/xml"));
  if ($data != "") {
    curl_setopt($ch, CURLOPT_POST, 1);
    curl_setopt($ch, CURLOPT_POSTFIELDS, $data);
  }
  #curl_setopt($ch, CURLOPT_VERBOSE, true);
  $response=curl_exec($ch);
  $header_size = curl_getinfo($ch, CURLINFO_HEADER_SIZE);
  $headers=substr($response, 0, $header_size);
  $body=substr($response, $header_size);
  $info=curl_getinfo($ch);
  $return_code=curl_getinfo($ch, CURLINFO_HTTP_CODE);
  curl_close($ch);

  if ($return_code >= 200 && $return_code <= 299) {
    if ($body == "") {
      return "";
    } else {
      return new SimpleXMLElement(substr($response, $header_size));
    }
  } else {
    return false;
  }
}

# the actual html code, this could be a lot prettier
?>
<html>
  <head>
    <title>CROWD LOGIN FORM</title>
  </head>
  <body>
    <h1>Crowd Login for clowder</h1>
    <form>
      <input type="hidden" name="redirecturl" value="<?php echo $redirecturl; ?>" />
      <input type="hidden" name="token" value="<?php echo $token; ?>" />
      <div>
        <label for="username">Username</label><br/>
        <input type="text" id="username" name="username" />
      </div>
      <div>
        <label for="password">Password</label><br/>
        <input type="password" id="password" name="password" />
      </div>
      <div>
        <input type="submit" />
      </div>
    </form>
  </body>
</html>
