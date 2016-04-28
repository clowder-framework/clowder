<?php
$dts = isset($_REQUEST['dts']) ? $_REQUEST['dts'] : "";
$file = isset($_REQUEST['file']) ? $_REQUEST['file'] : "";
$output = isset($_REQUEST['output']) ? $_REQUEST['output'] : "";
$prefix = isset($_REQUEST['prefix']) ? $_REQUEST['prefix'] : "";
$run = isset($_REQUEST['run']) ? filter_var($_REQUEST['run'], FILTER_VALIDATE_BOOLEAN) : true;		//Set to false to return results from previous run
$mail = isset($_REQUEST['mail']) ? filter_var($_REQUEST['mail'], FILTER_VALIDATE_BOOLEAN) : false;
$temp_path = "tmp/";

$input_filename = basename(urldecode($file));
$output_filename = basename($input_filename, pathinfo($input_filename, PATHINFO_EXTENSION)) . "txt";

if($prefix) {	
	$output_file = $temp_path . $prefix . "_" . $output_filename;
} else {
	$output_file = $temp_path . $output_filename;
}

//Run file through Medici's extractors
if($run) {
	$username = "";
	$password = "";

	//Add authentication if required
	if(strpos($dts,'@') !== false) {
		$parts = explode('@', $dts);
		$dts = $parts[2];
		$parts = explode(':', $parts[0] . '@' . $parts[1]);
		$username = $parts[0];
		$password = $parts[1];
	}

	$api_call = "http://" . $dts . "/dts/extract.php?url=" . urlencode($file);

	if($username && $password) {
		$command = "wget --user=" . $username . " --password=" . $password . " -O " . $output_file . " " . $api_call;
	} else {
		$command = "wget -O " . $output_file . " " . $api_call;
	}

	exec($command);
}

//Check output for expected tags
$success = false;

if(file_exists($output_file)) {
	$json = file_get_contents($output_file);

	//If output is a file load its contents	
	if(substr($output, 0, 4) === "http"	|| substr($output, 0, 5) === "!http") {					
		$output = trim(file_get_contents($output));
	}

	//echo $json . "<br>" . $output;
	
	if($output[0] == '!'){
		//if(!in_array_multi(substr($output, 1), $json)){
		if(!find(substr($output, 1), $json)){
			$success = true;
		}
	}else{
		//if(in_array_multi($output, $json)){
		if(find($output, $json)){
			$success = true;
		}
	}
}

if($success) {
	echo 1;
} else {
	if($mail) {
		$watchers = file("watchers.txt");

		foreach($watchers as $address) {
			$message = "Test-" . $prefix . " failed.  Expected output \"" . $output . "\" was not extracted from:\n\n" . $file . "\n\n";
			$message .= "Report of last run can be seen here: \n\n http://" . $_SERVER['SERVER_NAME'] . "/dts/tests/tests.php?run=false&start=true\n";

			mail($address, "DTS Test Failed", $message);
		}
	}

	echo 0;
}

//Multi-dimensional array search
function in_array_multi($needle, $haystack) {
	foreach($haystack as $item) {
		if($item === $needle || (is_array($item) && in_array_multi($needle, $item))) {
			return true;
		}
	}

	return false;
}

//Find the expected value anywhere in the answer. 
//This will take the arguments and convert to associative arrays if needed. It will first check to see if the expected json
//object matches the answer, if not it will loop through all levels of the answer to find a match with the expected value.
function find($expected, $answer) {
  if(is_string($expected)) {
    $tmp = json_decode($expected, true);

    if($tmp != "") {
      $expected = $tmp;
    }
  }

  if(is_string($answer)) {
    $tmp = json_decode($answer, true);

    if ($tmp != "") {
      $answer = $tmp;
    }
  }

  if(compareArrays($expected, $answer)) {
    return true;
  }else if(is_array($answer)) {
    foreach($answer as $item) {
      if(find($expected, $item)) {
        return true;
      }
    }

    return false;
  }else{
    return false;
  }
}

//Compare two arrays.
//This will try and see if the two arrays match, it will always try and see if the values in the expected value can be found the answer.
function compareArrays($expected, $answer) {
  if(!is_array($expected)) {
    if(is_array($answer)) {
      return false;
    }

    return $expected === $answer;
  }else if(is_assoc($expected)) {
    if(!is_assoc($answer)) {
      return false;
    }

    foreach($expected as $key => $val) {
      if(array_key_exists($key, $answer)) {
        if(!compareArrays($val, $answer[$key])) {
          return false;
        }
      }else{
        return false;
      }
    }

    return true;
  }else{
    if(is_assoc($answer)) {
      return false;
    }

    foreach($expected as $expect) {
      $foundit = false;

      foreach($answer as $item) {  
        if(compareArrays($expect, $item)) {
          $foundit = true;
          break;
        }
      }

      if(!$foundit) {
        return false;
      }
    }

    return true;
  }
}

function is_assoc($arr) {
  return is_array($arr) && array_keys($arr) !== range(0, count($arr) - 1);
}
?>
