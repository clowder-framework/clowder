<?php
$bins = isset($_REQUEST["bins"]) ? $_REQUEST["bins"] : "minutes";
$time_unit = 60*1000;						//Milli-seconds in a minute
$time_window = 24*60*60;				//Seconds in one day

if($bins == "hours"){
	$time_unit = 60*60*1000;
}else if($bins == "days"){
	$time_unit = 24*60*60*1000;
	$time_window = 7*24*60*60;
}else if($bins != "minutes"){		//Make sure a bins is one of these 3 values, default to minutes
	$bins = "minutes";
}

$tasks_per_x = array();

//Connect to mongo
$m = new MongoClient();
$db = $m->{"test"};
$collection = $db->{"dtsrequests"};

//Bin the start times of the tasks
$cursor = $collection->find();

foreach($cursor as $document) {
	//print_r($document);

	$timestamp = round($document["uploadDate"]->sec * 1000 / $time_unit);
	//echo $timestamp . "<br>\n";

	//if(!array_key_exists($timestamp, $tasks_per_x)) $tasks_per_x[$timestamp] = 0;	//ToDo: Why is this always true now?
	$tasks_per_x[$timestamp]++;
}

//Save the resulting histogram to a text file
$keys = array_keys($tasks_per_x);
$fp = fopen("tmp/$bins.txt", "w+");

foreach($keys as $key) {
	if($time_unit <= 3600000){		//If less than or equal to an hour
		$point = date('Y-m-d H:i', $key*$time_unit/1000) . " " . $key . " " . $tasks_per_x[$key];
	}else{
		$point = date('Y-m-d m-d', $key*$time_unit/1000) . " " . $key . " " . $tasks_per_x[$key];
	}

	fwrite($fp, "$point\n");

	//echo "$point<br>\n";
}

fclose($fp);

//Call GNUPlot to generate a plot
exec("gnuplot $bins.gnuplot");

//Print out overall mean performance
echo array_sum(array_values($tasks_per_x))/count($tasks_per_x) . " ";
?>
