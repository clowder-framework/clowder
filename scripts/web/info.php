<?php
$servers = $extractors = $count = $inputs = $requests = $headings = "";
if(isset($_REQUEST['servers'])) $servers = $_REQUEST["servers"];
if(isset($_REQUEST['extractors'])) $extractors = $_REQUEST["extractors"];
if(isset($_REQUEST['count'])) $count = $_REQUEST["count"];
if(isset($_REQUEST['inputs'])) $inputs = $_REQUEST["inputs"];
if(isset($_REQUEST['requests'])) $requests = $_REQUEST["requests"];
if(isset($_REQUEST['headings'])) $headings = $_REQUEST["headings"];

try {
	$m = new MongoClient();
	$db = $m->test;

	//Servers
	if($servers) {
		$collection = $db->{"extractor.servers"};
		$cursor = $collection->find();

		if($headings) {
			echo "<h2>Servers</h2>\n";
		}

		foreach($cursor as $document) {
			echo $document["server"] . "<br>\n";
		}
	}

	//Extractors
	if($extractors){
		if($headings) {
			echo "<h2>Extractors</h2>\n";
		}
		
		if($count) {
			$collection = $db->{"extractor.details"};
			$cursor = $collection->find();
			$extractors = array();

			//Tally extractor counts across servers
			foreach($cursor as $document) {
				$extractors[$document["name"]] += $document["count"];
			}

			//Print out extractor conts
			$keys = array_keys($extractors);

			foreach($keys as $key) {
				echo $key . " (" . $extractors[$key] . ")<br>\n";
			}
		} else {
			$collection = $db->{"extractor.names"};
			$cursor = $collection->find();
			
			foreach($cursor as $document) {
				echo $document["name"] . "<br>\n";
			}
		}
	}

	//Inputs
	if($inputs){
		$collection = $db->{"extractor.inputtypes"};
		$cursor = $collection->find();
		$FIRST = true;

		if($headings) {
			echo "<h2>Supported Inputs</h2>\n";
		}

		foreach($cursor as $document) {
			if($FIRST) {
				$FIRST = false;
			} else {
				echo ", ";
			}

			echo $document["inputType"];
		}
	}

	//Requests
	if($requests){
		$collection = $db->dtsrequests;
		$cursor = $collection->find();

		if($headings) {
			echo "<br>\n";
			echo "<h2>Requests</h2>\n";
		}

		echo "<table border=\"1\">\n";
		echo "<tr><th>Address</th><th>Filename</th><th>Filesize</th><th>Input</th><th>Extractors</th><th>Start Time</th><th>End Time</th><th>Success</th></tr>\n";

		foreach($cursor as $document) {
			echo "<tr>";
			echo "<td>" . (isset($document["clientIP"]) ? $document["clientIP"] : "") . "</td>";
			echo "<td>" . (isset($document["filename"]) ? $document["filename"] : "") . "</td>";
			echo "<td>" . (isset($document["filesize"]) ? $document["filesize"] : "") . "</td>";
			echo "<td>" . (isset($document["input"]) ? $document["input"] : "") . "</td>";
			echo "<td>" . (isset($document["extractors"]) ? $document["extractors"] : "") . "</td>";
			echo "<td>" . (isset($document["startTime"]) ? $document["startTime"] : "") . "</td>";
			echo "<td>" . (isset($document["endTime"]) ? $document["endTime"] : "") . "</td>";
			echo "<td></td>";
			//echo "<td>" . $document["success"] . "</td>";
		}

		echo "</table>\n";
	}
} catch (Exception $e) {
    echo 'Caught exception: ',  $e->getMessage(), "\n";
}

?>
