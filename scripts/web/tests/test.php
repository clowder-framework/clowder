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
	$api_call = $dts . "/dts/extract.php?url=" . urlencode($file);
	$command = "wget -O " . $output_file . " " . $api_call;

	exec($command);
}

//Check output for expected tags
$success = false;

if(file_exists($output_file)) {
	$json = json_decode(file_get_contents($output_file), true);
	
	if($output[0] == '!'){
		if(!in_array_multi(substr($output, 1), $json)){
			$success = true;
		}
	}else{
		if(in_array_multi($output, $json)){
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
?>
